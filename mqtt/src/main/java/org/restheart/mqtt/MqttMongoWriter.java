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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.restheart.mqtt.buffer.MessageBuffer;
import org.restheart.mqtt.buffer.MessageBuffer.Strategy;
import org.restheart.mqtt.model.MqttMessage;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

/**
 * Buffered async writer that persists MQTT messages to MongoDB.
 * <p>
 * Subscribes to configured topic filters via the {@link MqttMessageRouter},
 * buffers incoming messages in a {@link MessageBuffer}, and drains them in
 * batches to MongoDB using {@code insertMany(ordered=false)}.
 * </p>
 * <p>
 * Configuration in {@code restheart-config.yml}:
 * <pre>
 * plugins-args:
 *   mqtt-mongo-writer:
 *     enabled: true
 *     mongo-uri: "mongodb://localhost:27017"
 *     buffer:
 *       strategy: "ring"        # "ring" or "non-blocking"
 *       capacity: 10000
 *     drain:
 *       batch-size: 200
 *       flush-interval-ms: 500
 *       max-retries: 3
 *       retry-delay-ms: 1000
 *     id-strategy: "auto"       # "auto", "payload-field", "topic-timestamp-hash"
 *     id-field: "messageId"
 *     mongo-sink:
 *       - topic: "sensors/#"
 *         database: "iot"
 *         collection: "sensor-events"
 * </pre>
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name = "mqtt-mongo-writer",
    description = "Persists MQTT messages to MongoDB with buffered async writes"
)
public class MqttMongoWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttMongoWriter.class);

    @Inject("config")
    private Map<String, Object> config;

    @Inject("mqtt-router")
    private MqttMessageRouter router;

    private MongoClient mongoClient;
    private MessageBuffer buffer;
    private int batchSize;
    private long flushIntervalMs;
    private int maxRetries;
    private long retryDelayMs;
    private String idStrategy;
    private String idField;
    private List<MongoSink> sinks;
    private volatile boolean running;

    /**
     * Default constructor used by RESTHeart plugin instantiation.
     */
    public MqttMongoWriter() {
    }

    /**
     * Package-private constructor for unit tests, allowing a test double for the router to be
     * supplied without going through {@link Inject} field injection.
     *
     * @param router the router to subscribe to configured topic filters
     */
    MqttMongoWriter(MqttMessageRouter router) {
        this.router = router;
    }

    @OnInit
    public void init() {
        String mongoUri = argOrDefault(config, "mongo-uri", "mongodb://localhost:27017");

        // Buffer config
        @SuppressWarnings("unchecked")
        Map<String, Object> bufferConfig = (Map<String, Object>) config.getOrDefault("buffer", Map.of());
        String strategyStr = argOrDefault(bufferConfig, "strategy", "ring");
        int capacity = argOrDefault(bufferConfig, "capacity", 10000);
        Strategy strategy = "non-blocking".equals(strategyStr) ? Strategy.NON_BLOCKING : Strategy.RING;
        buffer = new MessageBuffer(capacity, strategy);

        // Drain config
        @SuppressWarnings("unchecked")
        Map<String, Object> drainConfig = (Map<String, Object>) config.getOrDefault("drain", Map.of());
        batchSize = argOrDefault(drainConfig, "batch-size", 200);
        flushIntervalMs = argOrDefault(drainConfig, "flush-interval-ms", 500L);
        maxRetries = argOrDefault(drainConfig, "max-retries", 3);
        retryDelayMs = argOrDefault(drainConfig, "retry-delay-ms", 1000L);

        // ID strategy
        idStrategy = argOrDefault(config, "id-strategy", "auto");
        idField = argOrDefault(config, "id-field", "messageId");

        // Mongo sinks
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sinkConfigs = (List<Map<String, Object>>) config.getOrDefault("mongo-sink", List.of());
        sinks = new ArrayList<>();
        for (Map<String, Object> sinkConfig : sinkConfigs) {
            String topic = (String) sinkConfig.get("topic");
            String database = (String) sinkConfig.get("database");
            String collection = (String) sinkConfig.get("collection");
            if (topic != null && database != null && collection != null) {
                sinks.add(new MongoSink(topic, database, collection));
            }
        }

        // Connect to MongoDB
        mongoClient = MongoClients.create(mongoUri);

        // Subscribe to topics
        for (MongoSink sink : sinks) {
            router.subscribe(sink.topic, MqttQos.AT_LEAST_ONCE, buffer::offer);
            LOGGER.info("Subscribed to topic {} → {}.{}", sink.topic, sink.database, sink.collection);
        }

        // Start drain loop
        running = true;
        Thread.ofVirtual().start(this::drainLoop);

        LOGGER.info("MqttMongoWriter initialized: uri={}, sinks={}, buffer={}, batchSize={}",
            mongoUri, sinks.size(), strategy, batchSize);
    }

    /**
     * Drain loop: flushes buffer to MongoDB periodically.
     */
    private void drainLoop() {
        while (running) {
            try {
                Thread.sleep(flushIntervalMs);
                flush();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("Error in drain loop: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Flushes buffered messages to MongoDB in batches.
     */
    void flush() {
        List<MqttMessage> batch = buffer.drain(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        // Group by sink
        for (MongoSink sink : sinks) {
            List<Document> documents = new ArrayList<>();
            for (MqttMessage msg : batch) {
                if (MqttTopicMatcher.matches(msg.getTopic(), sink.topic)) {
                    documents.add(toDocument(msg));
                }
            }

            if (!documents.isEmpty()) {
                insertWithRetry(sink, documents);
            }
        }
    }

    /**
     * Inserts documents into MongoDB with retry logic.
     */
    private void insertWithRetry(MongoSink sink, List<Document> documents) {
        MongoDatabase db = mongoClient.getDatabase(sink.database);
        MongoCollection<Document> coll = db.getCollection(sink.collection);

        int attempt = 0;
        while (attempt <= maxRetries) {
            try {
                coll.insertMany(documents, new com.mongodb.client.model.InsertManyOptions().ordered(false));
                LOGGER.debug("Inserted {} documents into {}.{}", documents.size(), sink.database, sink.collection);
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt > maxRetries) {
                    LOGGER.error("Failed to insert after {} retries into {}.{}: {}",
                        maxRetries, sink.database, sink.collection, e.getMessage());
                    // TODO: dead-letter log
                } else {
                    try {
                        Thread.sleep(retryDelayMs * attempt); // exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    /**
     * Converts an MqttMessage to a MongoDB Document.
     */
    Document toDocument(MqttMessage msg) {
        Document doc = new Document();
        doc.append("topic", msg.getTopic());
        doc.append("payload", msg.getPayload());
        doc.append("receivedAt", msg.getReceivedAt().toString());
        doc.append("qos", msg.getQos());

        // Apply ID strategy
        if (idStrategy != null) {
            switch (idStrategy) {
                case "payload-field" -> {
                    try {
                        Document payload = Document.parse(msg.getPayload());
                        Object idValue = payload.get(idField);
                        if (idValue != null) {
                            doc.append("_id", idValue);
                        }
                    } catch (Exception e) {
                        // Payload is not valid JSON or field missing — use auto
                    }
                }
                case "topic-timestamp-hash" -> {
                    doc.append("_id", computeHash(msg.getTopic(), msg.getReceivedAt().toString()));
                }
                // "auto" — let MongoDB generate ObjectId
            }
        }

        return doc;
    }

    /**
     * Computes a SHA-256 hash of topic + timestamp, truncated to 24 hex chars (12 bytes).
     */
    static String computeHash(String topic, String timestamp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((topic + timestamp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Stops the drain loop and closes the MongoDB client.
     */
    public void close() {
        running = false;
        // Flush remaining messages
        flush();
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    /**
     * Returns the current buffer size.
     */
    public int getBufferSize() {
        return buffer.size();
    }

    /**
     * A topic → database.collection mapping.
     */
    record MongoSink(String topic, String database, String collection) {}

    @SuppressWarnings("unchecked")
    private <V> V argOrDefault(Map<String, ?> args, String key, V defaultValue) {
        if (args == null || !args.containsKey(key)) {
            return defaultValue;
        }
        try {
            return (V) args.get(key);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
}
