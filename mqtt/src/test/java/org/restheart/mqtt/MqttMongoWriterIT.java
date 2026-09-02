/*-
 * ========================LICENSE_START=================================
 * restheart-mqtt
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */

package org.restheart.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.restheart.mqtt.buffer.MessageBuffer;
import org.restheart.mqtt.buffer.MessageBuffer.Strategy;
import org.restheart.mqtt.model.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

/**
 * Integration test for MqttMongoWriter.
 * Requires a running MongoDB instance (started by core module's mongodb profile).
 * All tests are disabled if MongoDB is not available on localhost:27017.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@EnabledIf(value = "isMongoAvailable", disabledReason = "MongoDB not available on localhost:27017")
public class MqttMongoWriterIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttMongoWriterIT.class);
    private static final String MONGO_URI = "mongodb://localhost:27017";
    private static final String DB_NAME = "test_mqtt_writer";
    private static final String COLL_NAME = "test_events";

    private static MongoClient mongoClient;
    private static MongoDatabase db;
    private static MongoCollection<Document> coll;

    /**
     * Checks if MongoDB is reachable. Used by {@code @EnabledIf} to disable
     * the entire test class when MongoDB is not running.
     */
    static boolean isMongoAvailable() {
        try (var testClient = MongoClients.create(
                com.mongodb.MongoClientSettings.builder()
                    .applyConnectionString(new com.mongodb.ConnectionString(MONGO_URI))
                    .applyToSocketSettings(b -> b.connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS))
                    .applyToClusterSettings(b -> b.serverSelectionTimeout(2, java.util.concurrent.TimeUnit.SECONDS))
                    .build())) {
            testClient.getDatabase("admin").runCommand(new Document("ping", 1));
            return true;
        } catch (Exception e) {
            LOGGER.info("MongoDB not available on {} — disabling MqttMongoWriterIT", MONGO_URI);
            return false;
        }
    }

    @BeforeAll
    static void setUp() {
        mongoClient = MongoClients.create(MONGO_URI);
        db = mongoClient.getDatabase(DB_NAME);
        coll = db.getCollection(COLL_NAME);
        coll.drop();
    }

    @AfterAll
    static void tearDown() {
        if (mongoClient != null) {
            // Clean up test data
            db.drop();
            mongoClient.close();
        }
    }

    @Test
    @DisplayName("Insert single document into MongoDB")
    void testInsertSingleDocument() {
        coll.drop();
        MqttMessage msg = new MqttMessage("sensors/temp", "{\"temp\":25}", 1, Instant.now());
        MqttMongoWriter writer = new MqttMongoWriter();

        Document doc = writer.toDocument(msg);
        coll.insertOne(doc);

        Document found = coll.find(new Document("topic", "sensors/temp")).first();
        assertNotNull(found);
        assertEquals("sensors/temp", found.getString("topic"));
        assertEquals("{\"temp\":25}", found.getString("payload"));
        assertEquals(1, found.getInteger("qos"));
    }

    @Test
    @DisplayName("Bulk insert multiple documents")
    void testBulkInsert() {
        coll.drop();

        MqttMongoWriter writer = new MqttMongoWriter();
        List<Document> docs = List.of(
            writer.toDocument(new MqttMessage("sensors/temp", "{\"temp\":20}", 0, Instant.now())),
            writer.toDocument(new MqttMessage("sensors/temp", "{\"temp\":25}", 0, Instant.now())),
            writer.toDocument(new MqttMessage("sensors/humidity", "{\"humidity\":60}", 1, Instant.now()))
        );

        coll.insertMany(docs);

        assertEquals(3, coll.countDocuments());
        assertEquals(2, coll.countDocuments(new Document("topic", "sensors/temp")));
        assertEquals(1, coll.countDocuments(new Document("topic", "sensors/humidity")));
    }

    @Test
    @DisplayName("Insert with payload-field ID strategy")
    void testPayloadFieldIdStrategy() throws Exception {
        coll.drop();

        MqttMongoWriter writer = new MqttMongoWriter();
        var idStrategyField = MqttMongoWriter.class.getDeclaredField("idStrategy");
        idStrategyField.setAccessible(true);
        idStrategyField.set(writer, "payload-field");

        var idFieldField = MqttMongoWriter.class.getDeclaredField("idField");
        idFieldField.setAccessible(true);
        idFieldField.set(writer, "messageId");

        Document doc = writer.toDocument(new MqttMessage("sensors/temp",
            "{\"messageId\":\"msg-001\",\"temp\":25}", 1, Instant.now()));

        coll.insertOne(doc);

        Document found = coll.find(new Document("_id", "msg-001")).first();
        assertNotNull(found, "Document should be found by payload-field ID");
        assertEquals("sensors/temp", found.getString("topic"));
    }

    @Test
    @DisplayName("Insert with topic-timestamp-hash ID strategy for deduplication")
    void testTopicTimestampHashDeduplication() throws Exception {
        coll.drop();

        MqttMongoWriter writer = new MqttMongoWriter();
        var idStrategyField = MqttMongoWriter.class.getDeclaredField("idStrategy");
        idStrategyField.setAccessible(true);
        idStrategyField.set(writer, "topic-timestamp-hash");

        Instant ts = Instant.parse("2026-01-01T12:00:00Z");

        // Same topic + timestamp = same _id
        Document doc1 = writer.toDocument(new MqttMessage("sensors/temp", "{\"temp\":20}", 1, ts));
        Document doc2 = writer.toDocument(new MqttMessage("sensors/temp", "{\"temp\":25}", 1, ts));

        coll.insertOne(doc1);

        // Second insert with same _id should fail (duplicate key)
        try {
            coll.insertOne(doc2);
            // If no exception, the insert succeeded (unexpected)
        } catch (com.mongodb.MongoException e) {
            assertTrue(e.getMessage().contains("E11000"), "Should get duplicate key error");
        }

        assertEquals(1, coll.countDocuments(), "Should have only 1 document due to dedup");
    }

    @Test
    @DisplayName("Buffer drains to MongoDB via flush")
    void testBufferFlushToMongo() throws Exception {
        coll.drop();

        MqttMongoWriter writer = new MqttMongoWriter();

        // Set up writer with buffer
        var bufferField = MqttMongoWriter.class.getDeclaredField("buffer");
        bufferField.setAccessible(true);
        MessageBuffer buffer = new MessageBuffer(100, Strategy.RING);
        bufferField.set(writer, buffer);

        var batchSizeField = MqttMongoWriter.class.getDeclaredField("batchSize");
        batchSizeField.setAccessible(true);
        batchSizeField.set(writer, 50);

        var sinksField = MqttMongoWriter.class.getDeclaredField("sinks");
        sinksField.setAccessible(true);
        sinksField.set(writer, List.of(new MqttMongoWriter.MongoSink("#", DB_NAME, COLL_NAME)));

        var mongoClientField = MqttMongoWriter.class.getDeclaredField("mongoClient");
        mongoClientField.setAccessible(true);
        mongoClientField.set(writer, mongoClient);

        // Add messages to buffer
        for (int i = 0; i < 10; i++) {
            buffer.offer(new MqttMessage("sensors/temp", "{\"temp\":" + i + "}", 0, Instant.now()));
        }

        assertEquals(10, buffer.size());

        // Flush
        writer.flush();

        assertEquals(0, buffer.size(), "Buffer should be empty after flush");
        assertEquals(10, coll.countDocuments(), "All messages should be in MongoDB");
    }
}
