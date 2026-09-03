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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.bson.Document;
import org.restheart.mqtt.buffer.MessageBuffer;
import org.restheart.mqtt.buffer.MessageBuffer.Strategy;
import org.restheart.mqtt.model.MqttMessage;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.WriteModel;

/**
 * Buffered async writer that persists MQTT messages to MongoDB.
 * <p>
 * Subscribes to configured topic filters via the {@link MqttMessageRouter},
 * buffers incoming messages in a {@link MessageBuffer}, and drains them in
 * batches to MongoDB. Documents produced by a deduplicating id strategy
 * ({@code payload-field} or {@code topic-timestamp-hash}) are written with
 * {@code bulkWrite} using upserting {@link ReplaceOneModel}s, so redelivery
 * of the same message (e.g. from another RESTHeart node) is idempotent; the
 * {@code auto} strategy uses a plain {@code insertMany}.
 * </p>
 * <p>
 * This class is a RESTHeart {@link Initializer}: {@code @OnInit} fires as soon as
 * injection is complete and does all the real setup (parsing configuration,
 * connecting the configured sinks, subscribing to the router and starting the
 * drain loop), while {@link #init()} — invoked by RESTHeart at the
 * {@code initPoint} configured on {@link RegisterPlugin} — is intentionally
 * empty, mirroring the idiom used by {@code RHMongoClients}. RESTHeart has no
 * plugin shutdown callback, so a JVM shutdown hook (registered at most once)
 * calls {@link #close()} to stop the drain loop and flush any remaining
 * messages; it does not close the injected {@link MongoClient}, which this
 * class does not own.
 * </p>
 * <p>
 * Configuration in {@code restheart-config.yml}:
 * <pre>
 * plugins-args:
 *   mqtt-mongo-writer:
 *     enabled: true
 *     buffer:
 *       strategy: "ring-buffer"  # "ring-buffer" | "drop-incoming" | "blocking-queue"
 *       capacity: 10000
 *     drain:
 *       batch-size: 200
 *       flush-interval-ms: 500
 *       max-retries: 3
 *       retry-delay-ms: 1000
 *     id-strategy: "auto"       # "auto", "payload-field", "topic-timestamp-hash"
 *     id-field: "messageId"
 *     dead-letter-file: "./mqtt-dead-letter.log"
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
    description = "Persists MQTT messages to MongoDB with buffered async writes",
    initPoint = InitPoint.AFTER_STARTUP
)
public class MqttMongoWriter implements Initializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttMongoWriter.class);

    /** MongoDB duplicate-key error code. */
    private static final int DUPLICATE_KEY_ERROR_CODE = 11000;

    /** Upper bound for the exponential retry backoff delay. */
    private static final long MAX_BACKOFF_MS = 30_000L;

    /** Guards against registering the shutdown hook more than once per classloader. */
    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);

    /** Package-private, for test visibility only: the currently registered shutdown hook, if any. */
    static volatile Thread mqttMongoWriterShutdownHookThread;

    @Inject("config")
    private Map<String, Object> config;

    @Inject("mqtt-router")
    private MqttMessageRouter router;

    /**
     * The MongoDB client, injected by name from the {@code mclient} provider
     * that RESTHeart uses for all its own MongoDB access. This class does not
     * open its own connection: if the {@code mclient} provider is unavailable,
     * RESTHeart disables this plugin, which is the correct outcome.
     */
    @Inject("mclient")
    private MongoClient mclient;

    private MessageBuffer buffer;
    private int batchSize;
    private long flushIntervalMs;
    private int maxRetries;
    private long retryDelayMs;
    private String idStrategy;
    private String idField;
    private String deadLetterFile;
    private List<MongoSink> sinks;
    private volatile boolean running;

    private final AtomicLong duplicateCount = new AtomicLong();

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

    /**
     * Performs all real setup: parses configuration, connects the configured sinks, subscribes
     * to the router and starts the drain loop.
     * <p>
     * Named {@code onInit} rather than {@code init} because this class also implements
     * {@link Initializer}, which declares {@link #init()}; the two are kept deliberately
     * separate (see the class Javadoc).
     * </p>
     */
    @OnInit
    public void onInit() {
        // Buffer config
        @SuppressWarnings("unchecked")
        Map<String, Object> bufferConfig = (Map<String, Object>) config.getOrDefault("buffer", Map.of());
        String strategyStr = configOrDefault(bufferConfig, "strategy", "ring-buffer");
        int capacity = configOrDefault(bufferConfig, "capacity", 10000);
        Strategy strategy = Strategy.fromConfigValue(strategyStr);
        buffer = new MessageBuffer(capacity, strategy);

        // Drain config
        @SuppressWarnings("unchecked")
        Map<String, Object> drainConfig = (Map<String, Object>) config.getOrDefault("drain", Map.of());
        batchSize = configOrDefault(drainConfig, "batch-size", 200);
        flushIntervalMs = configOrDefault(drainConfig, "flush-interval-ms", 500L);
        maxRetries = configOrDefault(drainConfig, "max-retries", 3);
        retryDelayMs = configOrDefault(drainConfig, "retry-delay-ms", 1000L);

        // ID strategy
        idStrategy = configOrDefault(config, "id-strategy", "auto");
        idField = configOrDefault(config, "id-field", "messageId");
        deadLetterFile = configOrDefault(config, "dead-letter-file", "./mqtt-dead-letter.log");

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

        // Subscribe to topics
        for (MongoSink sink : sinks) {
            router.subscribe(sink.topic(), MqttQos.AT_LEAST_ONCE, buffer::offer);
            LOGGER.info("Subscribed to topic {} → {}.{}", sink.topic(), sink.database(), sink.collection());
        }

        // Start drain loop
        running = true;
        Thread.ofVirtual().start(this::drainLoop);

        // RESTHeart has no plugin shutdown callback, so a JVM shutdown hook is the only way
        // to stop the drain loop and flush pending messages cleanly.
        registerShutdownHookOnce(this::close);

        LOGGER.info("MqttMongoWriter initialized: sinks={}, buffer={}, batchSize={}",
            sinks.size(), strategy, batchSize);
    }

    /**
     * Invoked by RESTHeart at the {@code initPoint} configured on {@link RegisterPlugin}.
     * Intentionally empty: all real setup happens in {@link #onInit()}, which fires as soon as
     * dependency injection completes (see the class Javadoc).
     */
    @Override
    public void init() {
    }

    /**
     * Registers, at most once per classloader, a JVM shutdown hook that runs {@code closeAction}.
     *
     * @param closeAction the action to run on JVM shutdown, typically {@link #close()}
     */
    private static void registerShutdownHookOnce(Runnable closeAction) {
        if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            final Thread hook = new Thread(closeAction, "mqtt-mongo-writer-shutdown");
            Runtime.getRuntime().addShutdownHook(hook);
            mqttMongoWriterShutdownHookThread = hook;
        }
    }

    /**
     * Drain loop: on every wake-up, keeps flushing batches to MongoDB until the buffer is empty
     * or the flush interval has elapsed, so throughput is bounded by MongoDB rather than by an
     * arbitrary per-wake-up batch limit.
     */
    private void drainLoop() {
        while (running) {
            try {
                Thread.sleep(flushIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                drainUntilEmptyOrDeadline(System.currentTimeMillis() + flushIntervalMs);
            } catch (Exception e) {
                LOGGER.error("Error in drain loop: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Repeatedly flushes batches to MongoDB until the buffer is empty or {@code deadlineMillis}
     * has passed, whichever comes first. This lets several batches worth of buffered messages
     * be drained within a single wake-up, instead of leaving all but one batch behind.
     * <p>
     * Retry backoff performed by {@link #insertWithRetry} is bounded by {@code max-retries} and
     * an exponential delay capped at {@link #MAX_BACKOFF_MS}, so a slow or failing MongoDB
     * cannot stall this loop indefinitely.
     * </p>
     *
     * @param deadlineMillis the {@link System#currentTimeMillis()} value after which draining
     *                        stops for this wake-up, even if the buffer is not yet empty
     */
    void drainUntilEmptyOrDeadline(long deadlineMillis) {
        do {
            flush();
        } while (running && buffer.size() > 0 && System.currentTimeMillis() < deadlineMillis);
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
                if (MqttTopicMatcher.matches(msg.getTopic(), sink.topic())) {
                    documents.add(toDocument(msg));
                }
            }

            if (!documents.isEmpty()) {
                insertWithRetry(sink, documents);
            }
        }
    }

    /**
     * Writes documents into MongoDB with bounded retries, retrying only the documents that
     * actually failed on a partial bulk-write failure.
     * <p>
     * Deduplicating id strategies ({@code payload-field}, {@code topic-timestamp-hash}) write
     * with {@code bulkWrite} using upserting {@link ReplaceOneModel}s keyed on {@code _id}: a
     * duplicate-key error (code {@value #DUPLICATE_KEY_ERROR_CODE}) then means another node
     * already stored the message, so it is counted as a success, not retried and not
     * dead-lettered. The {@code auto} strategy writes with a plain {@code insertMany}, where a
     * duplicate-key error is a genuine failure.
     * </p>
     * <p>
     * Once {@code max-retries} is exhausted for the documents still failing, they are appended
     * to the dead-letter file via {@link #deadLetter(List)}.
     * </p>
     *
     * @param sink      the sink (database + collection) to write to
     * @param documents the documents to write
     */
    private void insertWithRetry(MongoSink sink, List<Document> documents) {
        MongoDatabase db = mclient.getDatabase(sink.database());
        MongoCollection<Document> coll = db.getCollection(sink.collection());
        boolean dedup = isDeduplicatingStrategy();

        List<Document> pending = documents;
        int attempt = 0;

        while (!pending.isEmpty()) {
            try {
                if (dedup) {
                    coll.bulkWrite(buildUpsertModels(pending), new BulkWriteOptions().ordered(false));
                } else {
                    coll.insertMany(pending, new InsertManyOptions().ordered(false));
                }
                LOGGER.debug("Wrote {} documents into {}.{}", pending.size(), sink.database(), sink.collection());
                return;
            } catch (MongoBulkWriteException e) {
                List<BulkWriteError> writeErrors = e.getWriteErrors();
                Set<Integer> failedIndices = new HashSet<>();
                int duplicates = 0;
                for (BulkWriteError err : writeErrors) {
                    if (dedup && err.getCode() == DUPLICATE_KEY_ERROR_CODE) {
                        duplicates++;
                    } else {
                        failedIndices.add(err.getIndex());
                    }
                }
                if (duplicates > 0) {
                    duplicateCount.addAndGet(duplicates);
                    LOGGER.debug("{} of {} documents already present in {}.{} (duplicate key) — treated as already stored",
                        duplicates, pending.size(), sink.database(), sink.collection());
                }
                if (failedIndices.isEmpty()) {
                    // every error was a duplicate key on a deduplicating strategy: overall success
                    return;
                }
                List<Document> retryDocs = failedIndices.stream()
                    .sorted()
                    .map(pending::get)
                    .collect(Collectors.toList());
                attempt++;
                if (attempt > maxRetries) {
                    LOGGER.error("Giving up on {} of {} documents into {}.{} after {} retries",
                        retryDocs.size(), documents.size(), sink.database(), sink.collection(), maxRetries, e);
                    deadLetter(retryDocs);
                    return;
                }
                pending = retryDocs;
                if (!backoff(attempt)) {
                    deadLetter(pending);
                    return;
                }
            } catch (Exception e) {
                attempt++;
                if (attempt > maxRetries) {
                    LOGGER.error("Giving up on {} documents into {}.{} after {} retries",
                        pending.size(), sink.database(), sink.collection(), maxRetries, e);
                    deadLetter(pending);
                    return;
                }
                if (!backoff(attempt)) {
                    deadLetter(pending);
                    return;
                }
            }
        }
    }

    /**
     * Returns {@code true} if the configured id strategy is one of the deduplicating strategies
     * ({@code payload-field} or {@code topic-timestamp-hash}), for which documents are written
     * with an upserting {@code bulkWrite} rather than a plain {@code insertMany}.
     */
    private boolean isDeduplicatingStrategy() {
        return "payload-field".equals(idStrategy) || "topic-timestamp-hash".equals(idStrategy);
    }

    /**
     * Builds the write models for an upserting {@code bulkWrite}: documents that carry a
     * deterministic {@code _id} (the common case for deduplicating strategies) are written as
     * an upserting {@link ReplaceOneModel} keyed on that {@code _id}; documents without one
     * (e.g. {@code payload-field} when the configured field is absent from the payload) fall
     * back to a plain {@link InsertOneModel}, since there is no id to deduplicate on.
     *
     * @param documents the documents to convert
     * @return the corresponding list of write models, in the same order as {@code documents}
     */
    private List<WriteModel<Document>> buildUpsertModels(List<Document> documents) {
        List<WriteModel<Document>> models = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            Object id = doc.get("_id");
            if (id != null) {
                models.add(new ReplaceOneModel<>(Filters.eq("_id", id), doc, new ReplaceOptions().upsert(true)));
            } else {
                models.add(new InsertOneModel<>(doc));
            }
        }
        return models;
    }

    /**
     * Sleeps for an exponentially growing backoff delay ({@code retryDelayMs * 2^(attempt-1)},
     * capped at {@link #MAX_BACKOFF_MS}) before a retry, so retries cannot stall the drain loop
     * for an unbounded amount of time.
     *
     * @param attempt the 1-based retry attempt number
     * @return {@code true} if the sleep completed normally; {@code false} if the thread was
     *         interrupted while waiting, in which case no further retry should be attempted
     */
    private boolean backoff(int attempt) {
        long delay = Math.min(retryDelayMs * (1L << Math.min(attempt - 1, 20)), MAX_BACKOFF_MS);
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Appends documents that could not be written after exhausting retries to the dead-letter
     * file, one JSON document per line, so they are not silently lost. A failure to write the
     * dead-letter file is logged but never propagated: it must not stop the drain loop.
     *
     * @param documents the documents to append; a null or empty list is a no-op
     */
    private void deadLetter(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        try (BufferedWriter out = new BufferedWriter(new FileWriter(deadLetterFile, true))) {
            for (Document doc : documents) {
                out.write(doc.toJson());
                out.newLine();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to write {} documents to dead-letter file {}", documents.size(), deadLetterFile, e);
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
     * Stops the drain loop and flushes remaining buffered messages to MongoDB.
     * <p>
     * Does <strong>not</strong> close the injected {@link MongoClient}: it is owned by
     * RESTHeart's {@code mclient} provider, shared with the rest of the server, and must
     * outlive this plugin.
     * </p>
     */
    public void close() {
        running = false;
        // Flush remaining messages
        flush();
    }

    /**
     * Returns the current buffer size.
     */
    public int getBufferSize() {
        return buffer.size();
    }

    /**
     * Returns the cumulative number of documents that were not written because a duplicate-key
     * error indicated another node already stored them (deduplicating id strategies only).
     *
     * @return lifetime count of documents skipped as duplicates
     */
    public long getDuplicateCount() {
        return duplicateCount.get();
    }

    /**
     * A topic → database.collection mapping.
     */
    record MongoSink(String topic, String database, String collection) {}

    @SuppressWarnings("unchecked")
    private <V> V configOrDefault(Map<String, ?> args, String key, V defaultValue) {
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
