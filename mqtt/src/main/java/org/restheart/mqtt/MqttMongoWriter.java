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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.Binary;
import org.restheart.metrics.MetricNameAndLabels;
import org.restheart.metrics.Metrics;
import org.restheart.mqtt.buffer.MessageBuffer;
import org.restheart.mqtt.buffer.MessageBuffer.Strategy;
import org.restheart.mqtt.model.Mqtt5Properties;
import org.restheart.mqtt.model.MqttMessage;
import org.restheart.mqtt.model.Qos;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * batches to MongoDB. Documents produced by {@code id-strategy: payload-field}
 * are keyed on a field of the message itself and written with {@code bulkWrite}
 * using upserting {@link ReplaceOneModel}s, so redelivery of the same message
 * (e.g. from another RESTHeart node) converges on one document; the {@code auto}
 * strategy uses a plain {@code insertMany} and every redelivery is a new
 * document.
 * </p>
 * <p>
 * A message is acknowledged to the broker as soon as it is buffered, not once it is in MongoDB:
 * MQTT acknowledgements are ordered, so holding one until the write succeeds blocks every
 * acknowledgement behind it and the broker stops delivering to this client altogether - live
 * consumers included - as soon as its in-flight window fills. The buffer is memory, so this
 * process dying loses what is in it; an unreachable MongoDB, by contrast, is retried for as long
 * as the writer runs, and only documents MongoDB <em>refuses</em> go to the dead-letter
 * collection. See "Durability" in the module's README.
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
 *       strategy: "blocking-queue"  # "blocking-queue" (default) | "ring-buffer" | "drop-incoming"
 *       capacity: 10000
 *       max-bytes: 67108864       # 64 MB; whichever ceiling is reached first applies backpressure
 *     drain:
 *       batch-size: 200
 *       flush-interval-ms: 500
 *       max-retries: 3
 *       retry-delay-ms: 1000
 *     id-strategy: "auto"       # "auto" or "payload-field"
 *     id-field: "messageId"
 *     dead-letter-collection: "mqtt-dead-letter"
 *     mongo-sink:
 *       - topic: "sensors/#"
 *         database: "iot"
 *         collection: "sensor-events"
 * </pre>
 * </p>
 * <p>
 * <strong>Tier 2 of 2 - opt-in surface.</strong> Registered with {@code enabledByDefault = false}
 * so that arming the module (enabling {@code mqtt-client}, Tier 1) does not, by itself, start
 * writing MQTT traffic into MongoDB: an operator who only wants the injectable
 * {@code mqtt-router} - as {@code examples/mqtt-logger} does - gets it without this writer
 * running. Persisting messages to MongoDB is therefore a second, separate opt-in, made once the
 * module is armed.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name = "mqtt-mongo-writer",
    description = "Persists MQTT messages to MongoDB with buffered async writes",
    initPoint = InitPoint.AFTER_STARTUP,
    enabledByDefault = false
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
    private String deadLetterCollection;

    /**
     * How long {@link #close()} may spend draining the buffer into MongoDB before giving up on
     * what is left. Deliberately shorter than Docker's 10-second default grace period between
     * SIGTERM and SIGKILL: overshooting it does not buy more time, it just means being killed
     * mid-drain.
     */
    private long shutdownTimeoutMs;

    private List<MongoSink> sinks;
    private volatile boolean running;
    /** Whether new messages are still taken from the router; cleared first thing on shutdown. */
    private volatile boolean accepting;
    /** When a shutdown must stop retrying a write, {@link Long#MAX_VALUE} while running normally. */
    private volatile long writeDeadlineMillis = Long.MAX_VALUE;

    /** The buffer no longer carries a per-message callback: see the durable subscription in {@link #onInit()}. */
    private static final Runnable NO_OP = () -> { };

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
        // blocking-queue by default, not ring-buffer. This is a persistence path: someone who
        // configures a mongo-sink is asking for their messages to be stored, and a default that
        // silently evicts the oldest under load answers a question they did not ask. Backpressure
        // is the honest response, and it is cheap here - MqttMessageRouter dispatches every
        // message on its own virtual thread, so a full buffer parks a virtual thread rather than
        // a platform one.
        //
        // It does NOT make the path lossless on its own: the router's rate limit
        // (max-inflight-messages-per-second, 5000 by default) drops before any consumer is
        // reached, and the HiveMQ client has already acknowledged the message to the broker by
        // then. Watch mqtt_router_messages_dropped, not just mqtt_buffer_dropped.
        String strategyStr = configOrDefault(bufferConfig, "strategy", "blocking-queue");
        int capacity = configOrDefault(bufferConfig, "capacity", 10000);
        long maxWaitMs = configOrDefault(bufferConfig, "max-wait-ms", MessageBuffer.DEFAULT_MAX_WAIT_MS);
        // Both ceilings matter: capacity says how many messages, max-bytes how much heap. Ten
        // thousand readings are a megabyte, ten thousand images are a gigabyte.
        long maxBytes = configOrDefault(bufferConfig, "max-bytes", MessageBuffer.DEFAULT_MAX_BYTES);
        Strategy strategy = Strategy.fromConfigValue(strategyStr);
        buffer = new MessageBuffer(capacity, strategy, maxWaitMs, maxBytes);

        // Exposes the buffer's live depth/throughput via GET /metrics/mqtt_buffer_* outside the JVM
        Metrics.registerGauge(MetricNameAndLabels.of("mqtt_buffer_size"), buffer::size);
        Metrics.registerGauge(MetricNameAndLabels.of("mqtt_buffer_capacity"), buffer::capacity);
        Metrics.registerGauge(MetricNameAndLabels.of("mqtt_buffer_bytes"), buffer::bytes);
        Metrics.registerGauge(MetricNameAndLabels.of("mqtt_buffer_max_bytes"), buffer::maxBytes);
        Metrics.registerGauge(MetricNameAndLabels.of("mqtt_buffer_accepted"), buffer::acceptedCount);
        Metrics.registerGauge(MetricNameAndLabels.of("mqtt_buffer_dropped"), buffer::droppedCount);
        Metrics.registerGauge(MetricNameAndLabels.of("mqtt_buffer_duplicates"), duplicateCount::get);

        // Drain config
        @SuppressWarnings("unchecked")
        Map<String, Object> drainConfig = (Map<String, Object>) config.getOrDefault("drain", Map.of());
        batchSize = configOrDefault(drainConfig, "batch-size", 200);
        flushIntervalMs = configOrDefault(drainConfig, "flush-interval-ms", 500L);
        maxRetries = configOrDefault(drainConfig, "max-retries", 3);
        retryDelayMs = configOrDefault(drainConfig, "retry-delay-ms", 1000L);
        shutdownTimeoutMs = configOrDefault(drainConfig, "shutdown-timeout-ms", 5000L);

        // ID strategy
        idStrategy = configOrDefault(config, "id-strategy", "auto");
        validateIdStrategy(idStrategy);
        idField = configOrDefault(config, "id-field", "messageId");
        validateIdField(idStrategy, idField);
        // Resolved to an absolute path once, here. The configured default is relative, and a
        // relative path resolves against the process working directory - which for a forked or
        // containerised RESTHeart is not where the operator is standing. Logging the resolved
        // path is the difference between a recoverable incident and a file nobody ever finds.
        // A collection, not a file: a dead letter is a message MongoDB refused, so MongoDB was
        // reachable when it happened, and a queue nobody can query is not a queue. It lives in
        // the same database as the sink whose write failed.
        deadLetterCollection = configOrDefault(config, "dead-letter-collection", "mqtt-dead-letter");

        // Mongo sinks
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sinkConfigs = (List<Map<String, Object>>) config.getOrDefault("mongo-sink", List.of());
        sinks = new ArrayList<>();
        for (Map<String, Object> sinkConfig : sinkConfigs) {
            String topic = requireSinkStringValue(sinkConfig, "topic");
            String database = requireSinkStringValue(sinkConfig, "database");
            String collection = requireSinkStringValue(sinkConfig, "collection");
            sinks.add(new MongoSink(topic, database, collection));
        }

        // Subscribe to topics
        for (MongoSink sink : sinks) {
            // The message is acknowledged to the broker once it is in the buffer, not once it is
            // in MongoDB. MQTT acknowledgements are ordered - hivemq-mqtt-client holds a PUBACK
            // back until every earlier message has been acknowledged - so a message held through
            // a MongoDB outage blocks every acknowledgement behind it, the broker's in-flight
            // window fills (20 messages with Mosquitto's defaults) and it stops delivering to
            // this client at all, live SSE consumers included. Taking responsibility at the
            // buffer is what keeps ingestion independent of the database.
            //
            // The cost, and the module's stated guarantee: what is in the buffer is in memory
            // only, so a SIGKILL or a power cut loses it, and the broker will not redeliver
            // because it was told the messages were taken. A clean shutdown drains the buffer
            // into MongoDB instead, having stopped accepting first. See "Durability" in
            // README.md.
            //
            // A full buffer applies backpressure instead: offer() waits for room (the default),
            // and only a configuration that asks for it drops messages.
            router.subscribeDurable(sink.topic(), Qos.AT_LEAST_ONCE, (msg, taken) -> {
                if (!accepting) {
                    // Shutting down: deliberately neither buffered nor acknowledged, so the broker
                    // keeps this message and redelivers it to the next instance that resumes the
                    // session. Taking it now would mean racing the shutdown deadline with it.
                    return;
                }
                buffer.offer(new MessageBuffer.Pending(msg, NO_OP));
                taken.run();
            });
            LOGGER.info("Subscribed to topic {} → {}.{}", sink.topic(), sink.database(), sink.collection());
        }

        // Start drain loop
        accepting = true;
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
        // Deliberately NOT conditioned on `running`. close() clears that flag before draining, so
        // a loop that tested it would stop after a single batch - which is exactly what used to
        // happen on a clean shutdown: 200 messages written, the rest of the buffer discarded. The
        // drain loop does not need the flag here either; it tests `running` in its own while.
        do {
            flush();
        } while (buffer.size() > 0 && System.currentTimeMillis() < deadlineMillis);
    }

    /**
     * Flushes buffered messages to MongoDB in batches.
     */
    void flush() {
        List<MessageBuffer.Pending> batch = buffer.drain(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        // Group by sink
        for (MongoSink sink : sinks) {
            List<Document> documents = new ArrayList<>();
            for (MessageBuffer.Pending pending : batch) {
                MqttMessage msg = pending.message();
                if (MqttTopicMatcher.matches(msg.getTopic(), sink.topic())) {
                    documents.add(toDocument(msg));
                }
            }

            if (!documents.isEmpty()) {
                insertWithRetry(sink, documents);
            }
        }

        // Every message in this batch has now either been written or dead-lettered - both count
        // as having taken responsibility - so the broker may forget them. Released after the sink
        // loop rather than per sink, because one message can match several sinks and must not be
        // reported taken until the last of them is done with it.
        //
        // insertWithRetry never propagates: a batch it cannot write ends in the dead-letter file.
        // If that ever changes, this release has to move inside the success path, or a message
        // would be acknowledged without being anywhere.
        for (MessageBuffer.Pending pending : batch) {
            try {
                pending.taken().run();
            } catch (Exception e) {
                LOGGER.error("Failed to report a message as taken for topic {}",
                    pending.message().getTopic(), e);
            }
        }
    }

    /**
     * Writes documents into MongoDB with bounded retries, retrying only the documents that
     * actually failed on a partial bulk-write failure.
     * <p>
     * {@code payload-field} writes with {@code bulkWrite} using upserting
     * {@link ReplaceOneModel}s keyed on {@code _id}; {@code auto} writes with a plain
     * {@code insertMany}. Either way a duplicate-key error (code
     * {@value #DUPLICATE_KEY_ERROR_CODE}) means the document is already stored - under
     * {@code auto} the {@code _id} is an ObjectId generated for that very document, so it can only
     * be there because this writer put it there - and is counted as a success rather than retried
     * or dead-lettered.
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
        int outageAttempt = 0;

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
                    // A duplicate key means this document is already stored, whatever the id
                    // strategy: under auto the _id is an ObjectId the driver generated for this very
                    // document, so it can only already be in the collection because we put it
                    // there. This used to be gated on the strategy, and under auto a retry after a
                    // connection-level failure - where the batch is re-sent whole, and part of it
                    // may already have landed - treated the already-stored documents as failures
                    // and eventually dead-lettered documents that were in the database.
                    if (err.getCode() == DUPLICATE_KEY_ERROR_CODE) {
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
                    // every error was a duplicate key: everything in this batch is stored
                    return;
                }
                List<Document> retryDocs = failedIndices.stream()
                    .sorted()
                    .map(pending::get)
                    .collect(Collectors.toList());
                // These documents were rejected one by one, so MongoDB is reachable and it is
                // the documents it does not accept - a failed validation, a size or type it
                // refuses. Retrying a few times covers a transient rejection; past that they are
                // dead-lettered, which is what a dead-letter file is for. An outage takes the
                // other branch below and is never dead-lettered.
                attempt++;
                if (attempt > maxRetries) {
                    LOGGER.error("Giving up on {} of {} documents into {}.{} after {} retries; "
                        + "dead-lettering them", retryDocs.size(), documents.size(),
                        sink.database(), sink.collection(), maxRetries, e);
                    deadLetter(sink, retryDocs, e);
                    return;
                }
                pending = retryDocs;
                if (!backoff(attempt)) {
                    deadLetter(sink, pending, e);
                    return;
                }
            } catch (Exception e) {
                // MongoDB is unreachable, failing over, or timing out: nothing is wrong with these
                // documents, so there is nothing to dead-letter. They are already acknowledged to
                // the broker, which will not redeliver them, so giving up would lose them. Retry
                // for as long as this instance runs; the buffer behind fills up and applies
                // backpressure, which is the intended way for an outage to be felt.
                outageAttempt++;
                if (System.currentTimeMillis() > writeDeadlineMillis) {
                    // Shutting down, and MongoDB did not come back within the shutdown budget.
                    // These messages were acknowledged to the broker when they were buffered, so
                    // nobody else is holding them: they are lost, and saying so is the honest
                    // thing to do. The buffer is memory; this is the guarantee, not a surprise.
                    LOGGER.warn("Shutting down with MongoDB unreachable: {} messages for {}.{} are lost",
                        pending.size(), sink.database(), sink.collection());
                    return;
                }
                if (outageAttempt == 1 || outageAttempt % 10 == 0) {
                    LOGGER.warn("Cannot write {} documents into {}.{} (attempt {}); retrying until "
                        + "MongoDB is back: {}", pending.size(), sink.database(), sink.collection(),
                        outageAttempt, e.toString());
                }
                if (!backoff(outageAttempt)) {
                    LOGGER.warn("Interrupted while retrying: {} messages for {}.{} are lost",
                        pending.size(), sink.database(), sink.collection());
                    return;
                }
            }
        }
    }

    /**
     * Returns {@code true} if the configured id strategy keys documents on something the message
     * itself carries, so that the same message written twice converges on one document. Such
     * documents are written with an upserting {@code bulkWrite} rather than a plain
     * {@code insertMany}.
     * <p>
     * Only {@code payload-field} qualifies, and that is not an accident of the current list:
     * nothing the receiver computes locally can be stable across two receptions of the same
     * message, so convergence needs an identity that travels with it. {@code receivedAt} is
     * assigned on reception and the retain flag depends on subscription timing, so neither can
     * serve.
     * </p>
     */
    private boolean isDeduplicatingStrategy() {
        return "payload-field".equals(idStrategy);
    }

    /** The only accepted values for the {@code id-strategy} configuration key. */
    private static final Set<String> VALID_ID_STRATEGIES = Set.of("auto", "payload-field");

    /**
     * Validates the {@code id-strategy} configuration key, failing fast at {@link #onInit()}
     * rather than silently falling back to a default: an unrecognized value (e.g. a typo like
     * {@code "paylod-field"}) would otherwise silently disable deduplication, which in a
     * multi-node deployment means every node inserts its own copy of every message.
     *
     * @param value the configured {@code id-strategy} value
     * @throws IllegalArgumentException if {@code value} is not one of {@link #VALID_ID_STRATEGIES}
     */
    private static void validateIdStrategy(String value) {
        if (!VALID_ID_STRATEGIES.contains(value)) {
            throw new IllegalArgumentException(
                "Invalid value for id-strategy: \"" + value
                    + "\". Accepted values are: auto, payload-field");
        }
    }

    /**
     * Validates the {@code id-field} configuration key when {@code id-strategy} is
     * {@code payload-field}: a blank or missing field name would silently fall back to
     * {@code auto} behaviour for every message (see {@link #toDocument(MqttMessage)}), which is
     * the same silent-deduplication-loss failure mode as an unrecognized {@code id-strategy}.
     * The key is not otherwise inspected for the other id strategies, which do not use it.
     *
     * @param idStrategy the already-validated {@code id-strategy} value
     * @param idField    the configured {@code id-field} value
     * @throws IllegalArgumentException if {@code idStrategy} is {@code payload-field} and
     *                                  {@code idField} is {@code null} or blank
     */
    private static void validateIdField(String idStrategy, String idField) {
        if ("payload-field".equals(idStrategy) && (idField == null || idField.isBlank())) {
            throw new IllegalArgumentException(
                "Invalid value for id-field: \"" + idField
                    + "\". It must not be null or blank when id-strategy is \"payload-field\"");
        }
    }

    /**
     * Reads a required {@code String} key from a single {@code mongo-sink} entry, failing fast
     * at {@link #onInit()} rather than silently dropping the entry: a typo in {@code topic},
     * {@code database} or {@code collection} would otherwise mean nothing is ever persisted for
     * that entry, with nothing in the logs to say why - the same silent-configuration-loss
     * failure mode that {@link #validateIdStrategy(String)} and
     * {@link #validateIdField(String, String)} already guard against for {@code id-strategy} and
     * {@code id-field}.
     *
     * @param sinkConfig the configuration map for a single {@code mongo-sink} entry
     * @param key        the required key ({@code topic}, {@code database} or {@code collection})
     * @return the key's value
     * @throws IllegalArgumentException if {@code key} is missing from {@code sinkConfig} or its
     *                                   value is not a {@link String}
     */
    private static String requireSinkStringValue(Map<String, Object> sinkConfig, String key) {
        Object value = sinkConfig.get(key);
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException(
                "Invalid mongo-sink entry: missing or invalid \"" + key + "\": " + value
                    + " in entry " + sinkConfig);
        }
        return s;
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
    /**
     * Records documents MongoDB refused in the dead-letter collection of the sink's database.
     *
     * <p>Only documents the database rejected one by one reach this: a wrong type, a failed
     * validation, a size it will not take. An unreachable MongoDB is not a dead letter, it is a
     * write to retry - and there would be nowhere to write this either, which is exactly what a
     * dead-letter queue on a stopped server is worth.</p>
     *
     * <p>Each entry keeps the document as it was, with the collection it was meant for and the
     * reason under {@code _deadLetter}. A duplicate key here means this message is already
     * recorded, which is not a failure.</p>
     *
     * @param sink      the sink whose write failed
     * @param documents the documents MongoDB refused
     * @param cause     what MongoDB answered
     */
    private void deadLetter(MongoSink sink, List<Document> documents, Exception cause) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        var entries = documents.stream()
            .map(doc -> new Document(doc).append("_deadLetter", new Document()
                .append("collection", sink.collection())
                .append("reason", cause == null ? null : cause.getMessage())
                .append("at", Date.from(Instant.now()))))
            .toList();

        try {
            mclient.getDatabase(sink.database())
                .getCollection(deadLetterCollection)
                .insertMany(entries, new InsertManyOptions().ordered(false));
            LOGGER.warn("Wrote {} documents refused by {}.{} to {}.{}", entries.size(),
                sink.database(), sink.collection(), sink.database(), deadLetterCollection);
        } catch (MongoBulkWriteException e) {
            var refused = e.getWriteErrors().stream()
                .filter(err -> err.getCode() != DUPLICATE_KEY_ERROR_CODE)
                .count();
            if (refused > 0) {
                LOGGER.error("{} of {} documents could not be written to {}.{} either; they are lost",
                    refused, entries.size(), sink.database(), deadLetterCollection, e);
            }
        } catch (Exception e) {
            LOGGER.error("Cannot write {} dead-lettered documents to {}.{}; they are lost",
                entries.size(), sink.database(), deadLetterCollection, e);
        }
    }

    /**
     * Converts an MqttMessage to a MongoDB Document.
     */
    Document toDocument(MqttMessage msg) {
        Document doc = new Document();
        doc.append("topic", msg.getTopic());

        // A String when the payload is text, a BSON binary when it is not. BSON has a type for
        // bytes, so the type itself says which and no companion field can go stale; base64 here
        // would be a third of extra storage and not queryable as binary. What must not happen is
        // the old behaviour: decoding every payload as UTF-8 and storing the U+FFFD wreckage of
        // anything that was not text.
        if (msg.isTextPayload()) {
            doc.append("payload", msg.getPayload());
        } else {
            byte[] bytes = msg.getPayloadBytes();
            doc.append("payload", bytes == null ? null : new Binary(bytes));
        }

        // A BSON date, not its toString(). An ISO-8601 string happens to sort correctly, which is
        // why this went unnoticed, but it cannot be range-queried, indexed as a date, or aggregated
        // over time without converting it on every read.
        //
        // BSON dates are milliseconds, and receivedAt carries nanoseconds, so the remainder is kept
        // beside it: two messages in one millisecond are ordinary at sensor rates, and a collection
        // meant to be replayable must not lose the order they arrived in.
        doc.append("receivedAt", Date.from(msg.getReceivedAt()));
        doc.append("receivedAtNanos", msg.getReceivedAt().getNano() % 1_000_000);

        doc.append("qos", msg.getQos());
        // Recorded, not interpreted. A stored MQTT event is only a faithful record of what the
        // broker delivered if it keeps the retain flag: without it, a retained value - the topic's
        // last known state, possibly days old - is indistinguishable in the collection from a
        // measurement that had just been taken, and the collection cannot be replayed as the
        // message stream it came from.
        doc.append("retain", msg.isRetain());

        // The MQTT 5 properties, as a subdocument, and only when there are any: absent means either
        // MQTT 3.1.1, which has none, or a publisher that set nothing. Correlation data is BSON
        // binary rather than base64, for the same reason the payload is.
        var properties = msg.getMqtt5Properties();
        if (properties != null && !properties.isEmpty()) {
            doc.append("mqtt5", mqtt5Subdocument(properties, msg.getReceivedAt()));
        }

        // Apply ID strategy
        if (idStrategy != null) {
            switch (idStrategy) {
                case "payload-field" -> {
                    // Only a text payload can be parsed for a field. A binary one falls through to
                    // the auto strategy rather than relying on the catch below.
                    if (!msg.isTextPayload()) {
                        break;
                    }
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
                // "auto" — let MongoDB generate ObjectId
            }
        }

        return doc;
    }

    /**
     * Builds the {@code mqtt5} subdocument recording an MQTT 5 publish's properties.
     * <p>
     * {@code userProperties} is an array of {@code {name, value}} documents rather than one
     * document keyed by name, because MQTT 5 permits a repeated name and requires the order to be
     * preserved - keyed by name, a repeated property would silently overwrite its twin.
     * </p>
     * <p>
     * {@code messageExpiryInterval} is recorded as delivered, which is the <strong>remaining</strong>
     * interval rather than the one the publisher set: a server decrements it by the time the message
     * waited - measured against Mosquitto, 120 seconds published came back as 99 after a 20 second
     * wait. A stored message therefore cannot reproduce the publisher's chosen expiry, and a replay
     * built from this collection has to decide what to set instead. That is a limit of what a
     * subscriber can observe, not of what is recorded here. {@code expiresAt} is derived from it and
     * {@code receivedAt}, since the interval on its own says nothing once stored.
     * </p>
     *
     * @param properties the properties to record, neither null nor empty
     * @param receivedAt when the message arrived, for deriving {@code expiresAt}
     * @return the subdocument
     */
    private static Document mqtt5Subdocument(Mqtt5Properties properties, Instant receivedAt) {
        var sub = new Document();

        if (!properties.userProperties().isEmpty()) {
            var entries = new ArrayList<Document>(properties.userProperties().size());
            for (var property : properties.userProperties()) {
                entries.add(new Document("name", property.name()).append("value", property.value()));
            }
            sub.append("userProperties", entries);
        }
        if (properties.contentType() != null) {
            sub.append("contentType", properties.contentType());
        }
        if (properties.correlationData() != null) {
            sub.append("correlationData", new Binary(properties.correlationData()));
        }
        if (properties.responseTopic() != null) {
            sub.append("responseTopic", properties.responseTopic());
        }
        if (properties.payloadFormatIndicator() != null) {
            sub.append("payloadFormatIndicator", properties.payloadFormatIndicator());
        }
        if (properties.messageExpiryInterval() != null) {
            // Recorded as delivered: what arrived, not what was published.
            sub.append("messageExpiryInterval", properties.messageExpiryInterval());
            // And derived, because the interval alone is unusable once stored. A server decrements
            // it by the time the message waited - measured against Mosquitto, 120 s published came
            // back as 99 after a 20 s wait - so "99 seconds" only means something alongside the
            // moment it was received. As a date it can be range-queried and indexed, which is what
            // anyone asking "which of these has expired?" actually needs. This is the one derived
            // field in the document; everything else is recorded verbatim.
            sub.append("expiresAt", Date.from(receivedAt.plusSeconds(properties.messageExpiryInterval())));
        }

        return sub;
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
        // Stop taking messages before anything else: what arrives from here on stays with the
        // broker, unacknowledged, for the next instance to receive.
        accepting = false;
        running = false;
        writeDeadlineMillis = System.currentTimeMillis() + shutdownTimeoutMs;

        // Drain what is buffered, not just one batch of it. This is the only point in the whole
        // path where messages used to disappear silently: everywhere else a failure ends in the
        // dead-letter collection, but a clean shutdown wrote a single batch and dropped the rest.
        // The two failures also arrive together in practice - RESTHeart gets restarted during a
        // MongoDB incident, which is precisely when the buffer is full.
        var remaining = buffer.size();
        if (remaining > 0) {
            LOGGER.info("Shutting down with {} buffered messages; draining for up to {} ms",
                remaining, shutdownTimeoutMs);
        }
        drainUntilEmptyOrDeadline(System.currentTimeMillis() + shutdownTimeoutMs);

        // Whatever the deadline did not cover is lost, and is reported as such. The buffer is in
        // memory and these messages are already acknowledged to the broker, so there is nobody
        // left holding them; this only happens when MongoDB is unreachable at the very moment
        // RESTHeart is being stopped.
        var stranded = buffer.drain(Integer.MAX_VALUE);
        if (!stranded.isEmpty()) {
            LOGGER.warn("Shutdown deadline of {} ms elapsed with {} buffered messages not written "
                + "to MongoDB; they are lost", shutdownTimeoutMs, stranded.size());
        }
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

        var raw = args.get(key);
        if (raw == null || defaultValue == null) {
            return raw == null ? defaultValue : (V) raw;
        }

        // YAML hands back the narrowest type that fits, so a long-valued setting written as
        // "retry-delay-ms: 2000" arrives as an Integer. The cast below is erased, which is why the
        // ClassCastException it used to raise surfaced at the CALLER's assignment rather than
        // here - and why the catch that used to sit around it never fired, and a perfectly
        // reasonable configuration took the server down at startup instead of being read.
        if (defaultValue instanceof Long && raw instanceof Number n) {
            return (V) Long.valueOf(n.longValue());
        }
        if (defaultValue instanceof Integer && raw instanceof Number n) {
            return (V) Integer.valueOf(n.intValue());
        }

        if (!defaultValue.getClass().isInstance(raw)) {
            // Falling back silently is the "configuration that is present but inert" trap this
            // module has a whole sentinel for; say so instead.
            LOGGER.warn("Configuration key '{}' is a {} where a {} was expected; ignoring it and using {}",
                key, raw.getClass().getSimpleName(), defaultValue.getClass().getSimpleName(), defaultValue);
            return defaultValue;
        }

        return (V) raw;
    }
}
