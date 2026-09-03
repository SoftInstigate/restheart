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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
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
import org.restheart.mqtt.model.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivemq.client.mqtt.MqttClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

/**
 * Integration test for MqttMongoWriter.
 * <p>
 * Exercises the real plugin lifecycle — {@code @OnInit}, which parses configuration, subscribes
 * the sinks and starts the drain loop — rather than calling {@code toDocument()} and inserting
 * by hand: the writer is instantiated exactly as RESTHeart would, with its {@code config},
 * {@code router} and {@code mclient} fields set the way {@code @Inject} would set them, and
 * messages are pushed through the buffer the router would otherwise feed, so the background
 * drain loop is what actually performs the writes to MongoDB.
 * </p>
 * <p>
 * Requires a running MongoDB instance (started by core module's mongodb profile). All tests
 * are disabled if MongoDB is not available on localhost:27017.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@EnabledIf(value = "isMongoAvailable", disabledReason = "MongoDB not available on localhost:27017")
public class MqttMongoWriterIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttMongoWriterIT.class);
    private static final String MONGO_URI = "mongodb://localhost:27017";
    private static final String DB_NAME = "test_mqtt_writer";
    private static final String COLL_NAME = "test_events";

    private static com.mongodb.client.MongoClient mongoClient;
    private static MongoDatabase db;
    private static com.mongodb.client.MongoCollection<Document> coll;

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

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = MqttMongoWriter.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <V> V getField(Object target, String name) throws Exception {
        Field f = MqttMongoWriter.class.getDeclaredField(name);
        f.setAccessible(true);
        return (V) f.get(target);
    }

    /**
     * Builds a writer with its {@code @Inject}ed fields set the way RESTHeart would set them —
     * {@code config} from the given map, a real {@link MqttMessageRouter} backed by a mocked
     * broker connection (subscribing against it is a documented no-op, see
     * {@code MqttMessageRouterTest#testUnsupportedClientTypeLogsErrorAndDoesNotThrow}), and the
     * real, shared {@link com.mongodb.client.MongoClient} — then runs its real {@code @OnInit}
     * method.
     */
    private MqttMongoWriter newInitializedWriter(Map<String, Object> config) throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        setField(writer, "config", config);
        setField(writer, "router", new MqttMessageRouter(mock(MqttClient.class), 5000, true, 1000));
        setField(writer, "mclient", mongoClient);

        writer.onInit();
        return writer;
    }

    private Map<String, Object> baseConfig(String idStrategy, long flushIntervalMs) {
        return Map.of(
            "buffer", Map.of("strategy", "ring-buffer", "capacity", 1000),
            "drain", Map.of("batch-size", 50, "flush-interval-ms", flushIntervalMs, "max-retries", 1, "retry-delay-ms", 50L),
            "id-strategy", idStrategy,
            "id-field", "messageId",
            "mongo-sink", List.of(Map.of("topic", "sensors/#", "database", DB_NAME, "collection", COLL_NAME)));
    }

    /**
     * Polls {@code coll.countDocuments()} until it reaches {@code expected} or the timeout
     * elapses, since the drain loop writes asynchronously on its own schedule.
     */
    private void awaitDocumentCount(long expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (coll.countDocuments() >= expected) {
                return;
            }
            Thread.sleep(50);
        }
    }

    @Test
    @DisplayName("onInit() parses config and starts a drain loop that writes buffered messages to MongoDB")
    void testOnInitStartsDrainLoopThatWritesToMongo() throws Exception {
        coll.drop();

        MqttMongoWriter writer = newInitializedWriter(baseConfig("auto", 100));
        try {
            MessageBuffer buffer = getField(writer, "buffer");
            for (int i = 0; i < 10; i++) {
                buffer.offer(new MqttMessage("sensors/temp", "{\"temp\":" + i + "}", 0, Instant.now()));
            }

            awaitDocumentCount(10, 5000);

            assertEquals(10, coll.countDocuments(), "the real drain loop started by onInit() should have written all messages");
        } finally {
            writer.close();
        }
    }

    @Test
    @DisplayName("topic-timestamp-hash id strategy upserts idempotently across separate flush cycles")
    void testDedupIdStrategyUpsertIsIdempotentAcrossFlushes() throws Exception {
        coll.drop();

        MqttMongoWriter writer = newInitializedWriter(baseConfig("topic-timestamp-hash", 100));
        try {
            MessageBuffer buffer = getField(writer, "buffer");
            Instant ts = Instant.parse("2026-01-01T12:00:00Z");

            // Same topic + timestamp -> same deterministic _id, delivered twice as if by two nodes
            buffer.offer(new MqttMessage("sensors/temp", "{\"temp\":20}", 1, ts));
            awaitDocumentCount(1, 5000);

            buffer.offer(new MqttMessage("sensors/temp", "{\"temp\":25}", 1, ts));
            Thread.sleep(500); // give the drain loop a chance to process the redelivery

            assertEquals(1, coll.countDocuments(), "redelivery of the same message must upsert, not duplicate");
            Document stored = coll.find().first();
            assertTrue(stored.getString("payload").contains("25"), "the upsert should have replaced the document content");
        } finally {
            writer.close();
        }
    }

    @Test
    @DisplayName("close() flushes remaining buffered messages but does not close the shared MongoClient")
    void testCloseFlushesAndDoesNotCloseSharedClient() throws Exception {
        coll.drop();

        // A long flush interval so the drain loop's background wake-up does not race with close()
        MqttMongoWriter writer = newInitializedWriter(baseConfig("auto", 60_000));
        MessageBuffer buffer = getField(writer, "buffer");
        buffer.offer(new MqttMessage("sensors/temp", "{\"temp\":1}", 0, Instant.now()));

        writer.close();

        assertEquals(1, coll.countDocuments(), "close() must flush remaining buffered messages");

        // The shared client must still be usable: this would throw if close() had closed it
        mongoClient.getDatabase("admin").runCommand(new Document("ping", 1));
    }
}
