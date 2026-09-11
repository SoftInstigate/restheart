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

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.restheart.mqtt.model.MqttMessage;
import org.restheart.mqtt.model.Qos;
import org.restheart.mqtt.pipeline.FilterStage;
import org.restheart.mqtt.pipeline.MapStage;
import org.restheart.mqtt.pipeline.MqttEventPipeline;
import org.restheart.mqtt.pipeline.MqttEventStage;
import org.restheart.mqtt.pipeline.SlidingWindowAggregator;
import org.restheart.mqtt.pipeline.ThrottleStage;
import org.restheart.mqtt.pipeline.TumblingWindowAggregator;
import org.restheart.metrics.MetricNameAndLabels;
import org.restheart.metrics.Metrics;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.SseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.undertow.server.handlers.sse.ServerSentEventConnection;

/**
 * SSE service that streams MQTT topic messages as Server-Sent Events.
 * <p>
 * Clients connect via {@code GET /mqtt-sse?topic=sensors/#&qos=1} and receive
 * live MQTT messages as SSE events. An optional processing pipeline (filter, map,
 * throttle, aggregate) can be configured per topic.
 * </p>
 * <p>
 * If the last-message cache is enabled and a cached message exists for a matching
 * topic, it is sent as the first SSE event with {@code "cached": true}.
 * </p>
 * <p>
 * Every connection gets its own, freshly built {@link MqttEventPipeline}: the
 * pipeline configuration is parsed once, at {@link #init()}, into a list of
 * immutable {@link PipelineSpec specs} - a topic pattern plus an ordered list of
 * stage factories - and {@link #onConnect} instantiates a brand new pipeline (and
 * brand new stage instances) from the matching spec for each connecting client.
 * This matters because stages such as {@link ThrottleStage} and the window
 * aggregators hold per-stream mutable state: sharing one pipeline instance across
 * connections would let one client's messages throttle or pollute another's
 * aggregate.
 * </p>
 * <p>
 * <strong>Tier 2 of 2 - opt-in surface.</strong> Registered with {@code enabledByDefault = false}
 * so that arming the module (enabling {@code mqtt-client}, Tier 1) does not, by itself, expose
 * this HTTP endpoint: an operator who only wants the injectable {@code mqtt-router} - as
 * {@code examples/mqtt-logger} does - gets it without {@code /mqtt-sse} appearing. Exposing this
 * service is therefore a second, separate opt-in, made once the module is armed. Note that
 * {@link MqttTopicAuthorizer} stays enabled regardless of this switch, so once this service is
 * enabled it is already gated by ACL - there is no fail-open window in between.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name = "mqtt-sse",
    description = "Streams MQTT topic messages as Server-Sent Events",
    defaultURI = "/mqtt-sse",
    secure = true,
    enabledByDefault = false
)
public class MqttSseService implements SseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttSseService.class);
    private static final Gson GSON = new Gson();

    @Inject("config")
    private Map<String, Object> config;

    @Inject("mqtt-router")
    private MqttMessageRouter router;

    private String defaultTopic;
    private int defaultQos;
    private int perConnectionQueueCapacity;
    private boolean payloadEnvelope;
    private boolean lastMessageCacheEnabled;
    private int maxConnectionsPerTopic;

    /**
     * How often to write an SSE keep-alive comment, in milliseconds; {@code 0} disables it.
     * <p>
     * This is a liveness mechanism, not a nicety. Nothing reads from an SSE connection after the
     * handshake, so a client that goes away is not noticed until a write to its socket fails —
     * and on a quiet topic the next write may never come. Until then the connection's router
     * listener stays registered, filling a queue nobody drains, and its broker subscription stays
     * in place: every message matching both that stale filter and a broader one is then delivered
     * to this client twice by the broker, duplicating SSE events, custom-plugin callbacks and —
     * under {@code id-strategy: auto} — MongoDB documents. A periodic comment turns an indefinite
     * leak into one bounded by this interval.
     * </p>
     */
    private long keepAliveMs;

    private List<PipelineSpec> pipelineSpecs = List.of();

    /**
     * The topic filter a connection gets when it names none, i.e. the {@code default-topic}
     * default.
     * <p>
     * Package-visible on purpose: {@link MqttTopicAuthorizer} has to authorize the filter this
     * service will <em>actually</em> subscribe to, which for a request with no {@code topic}
     * parameter is this one. Two private copies of the literal would drift, and the drift would
     * be a silent authorization hole rather than a visible bug.
     * </p>
     */
    static final String DEFAULT_TOPIC = "sensors/#";

    /** Number of currently open connections, keyed by the requested topic filter. */
    private final Map<String, AtomicInteger> connectionsPerTopic = new ConcurrentHashMap<>();

    /** Cumulative SSE queue-full drops across all connections, for the mqtt_sse_dropped gauge. */
    private final AtomicLong totalDroppedMessages = new AtomicLong();

    @OnInit
    public void init() {
        defaultTopic = argOrDefault(config, "default-topic", DEFAULT_TOPIC);
        defaultQos = argOrDefault(config, "default-qos", 1);
        validateQos("default-qos", defaultQos);
        perConnectionQueueCapacity = argOrDefault(config, "per-connection-queue-capacity", 256);
        payloadEnvelope = argOrDefault(config, "payload-envelope", false);
        lastMessageCacheEnabled = argOrDefault(config, "last-message-cache", true);
        maxConnectionsPerTopic = argOrDefault(config, "max-connections-per-topic", 0);
        keepAliveMs = ((Number) argOrDefault(config, "keep-alive-ms", 20_000)).longValue();

        pipelineSpecs = buildPipelineSpecs();

        // Suppliers, not snapshots: each gauge reads the live counters on every /metrics scrape.
        Metrics.registerGauge(MetricNameAndLabels.of("mqtt_sse_dropped"), totalDroppedMessages::get);
        Metrics.registerGauge(MetricNameAndLabels.of("mqtt_sse_open_connections"), this::totalOpenConnections);

        LOGGER.info("MqttSseService initialized: defaultTopic={}, defaultQos={}, queueCapacity={}, envelope={}, "
            + "maxConnectionsPerTopic={}, keepAliveMs={}",
            defaultTopic, defaultQos, perConnectionQueueCapacity, payloadEnvelope,
            maxConnectionsPerTopic, keepAliveMs);
    }

    @Override
    public void onConnect(ServerSentEventConnection conn, String lastEventId) {
        Map<String, String> params = parseQueryString(conn.getQueryString());
        String topicFilter = resolveTopicFilter(params);
        int qos = resolveQos(params);

        if (!tryAcquireConnectionSlot(topicFilter)) {
            LOGGER.warn("Max connections per topic filter ({}) reached for '{}'; rejecting new SSE connection",
                maxConnectionsPerTopic, topicFilter);
            closeQuietly(conn);
            return;
        }

        // Armed before anything is registered on this connection, so that whatever follows is
        // covered by the liveness check rather than depending on traffic to notice a dead peer.
        if (keepAliveMs > 0) {
            conn.setKeepAliveTime(keepAliveMs);
        }

        LOGGER.debug("SSE client connected: topic={}, qos={}", topicFilter, qos);

        // Every connection gets its own pipeline instance: stages such as
        // ThrottleStage and the window aggregators hold per-stream mutable state
        // that must never be shared between connections (see class javadoc).
        MqttEventPipeline pipeline = selectPipeline(topicFilter);

        // Per-connection bounded queue
        ArrayBlockingQueue<MqttMessage> queue = new ArrayBlockingQueue<>(perConnectionQueueCapacity);
        AtomicLong eventSequence = new AtomicLong();
        AtomicLong droppedMessages = new AtomicLong();

        // Send cached messages if available
        if (lastMessageCacheEnabled) {
            for (MqttMessage cached : router.getLastMessages(topicFilter)) {
                sendEvent(conn, cached, true, eventSequence);
            }
        }

        // Subscribe to topic via router
        Consumer<MqttMessage> listener = msg -> {
            if (!queue.offer(msg)) {
                long dropped = droppedMessages.incrementAndGet();
                totalDroppedMessages.incrementAndGet();
                if (dropped % 1000 == 0) {
                    LOGGER.warn("SSE client queue full for topic {}, dropped {} messages so far",
                        topicFilter, dropped);
                }
            }
        };
        router.subscribe(topicFilter, Qos.fromCode(qos), listener);

        // Drain queue on virtual thread
        Thread.ofVirtual().start(() -> {
            try {
                while (conn.isOpen()) {
                    MqttMessage msg = queue.poll(1, TimeUnit.SECONDS);
                    if (msg != null) {
                        pipeline.process(msg).ifPresent(processed -> sendEvent(conn, processed, false, eventSequence));
                    }

                    // Every iteration - including the timeout path where poll()
                    // returned null - gives a tumbling/sliding window a chance to
                    // flush a still-open window whose duration has elapsed with
                    // no further message arriving to trigger the emission.
                    for (MqttMessage expired : pipeline.pollExpired()) {
                        sendEvent(conn, expired, false, eventSequence);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.error("Error in SSE drain loop: {}", e.getMessage(), e);
            }
        });

        // Cleanup on disconnect
        conn.addCloseTask(c -> {
            router.unsubscribe(topicFilter, listener);
            releaseConnectionSlot(topicFilter);

            if (c.isOpen()) {
                for (MqttMessage flushed : pipeline.close()) {
                    sendEvent(c, flushed, false, eventSequence);
                }
            }

            LOGGER.debug("SSE client disconnected: topic={}", topicFilter);
        });
    }

    /**
     * Resolves the topic filter from the parsed query string parameters.
     * Falls back to the configured default topic if not provided.
     *
     * @param params the query string parameters, already parsed and URL-decoded
     * @return the topic filter
     */
    String resolveTopicFilter(Map<String, String> params) {
        if (params != null) {
            String topic = params.get("topic");
            if (topic != null) {
                return topic;
            }
        }
        return defaultTopic;
    }

    /**
     * Resolves the QoS level from the parsed query string parameters.
     * Falls back to the configured, already-validated {@code default-qos} if
     * {@code qos} is absent, unparseable, or out of the 0-2 range accepted by
     * {@link Qos#fromCode(int)} - a typo in a query parameter is not worth
     * killing an SSE connection over.
     *
     * @param params the query string parameters, already parsed and URL-decoded
     * @return the QoS level (0, 1, or 2)
     */
    int resolveQos(Map<String, String> params) {
        if (params != null) {
            String qosStr = params.get("qos");
            if (qosStr != null) {
                try {
                    int qos = Integer.parseInt(qosStr);
                    if (Qos.fromCode(qos) != null) {
                        return qos;
                    }
                    LOGGER.warn("QoS value '{}' is out of range (must be 0, 1 or 2), using default", qosStr);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Invalid QoS value '{}', using default", qosStr);
                }
            }
        }
        return defaultQos;
    }

    /**
     * Validates a configured QoS value, failing fast at {@link #init()} rather than letting an
     * out-of-range value reach {@link Qos#fromCode(int)} later, where it would resolve to
     * {@code null} and blow up {@link MqttMessageRouter#subscribe} at connection time instead.
     *
     * @param key   the configuration key {@code value} was read from, used in the failure message
     * @param value the configured QoS value
     * @throws IllegalArgumentException if {@code value} is not 0, 1 or 2
     */
    private static void validateQos(String key, int value) {
        if (Qos.fromCode(value) == null) {
            throw new IllegalArgumentException(
                "Invalid value for " + key + ": " + value + ". Accepted values are: 0, 1, 2");
        }
    }

    /**
     * Formats an MQTT message as an SSE data payload.
     *
     * @param message the MQTT message
     * @param cached  whether this is a cached message
     * @return the formatted payload string
     */
    String formatPayload(MqttMessage message, boolean cached) {
        if (payloadEnvelope) {
            JsonObject envelope = new JsonObject();
            envelope.addProperty("topic", message.getTopic());
            envelope.addProperty("payload", message.getPayload());
            envelope.addProperty("receivedAt", message.getReceivedAt().toString());
            envelope.addProperty("qos", message.getQos());
            envelope.addProperty("cached", cached);
            return GSON.toJson(envelope);
        } else {
            return message.getPayload();
        }
    }

    /**
     * Sends a single MQTT message as an SSE event on the given connection.
     * <p>
     * The event id is the message topic and receive timestamp plus a
     * monotonically increasing per-connection sequence number, so that two
     * messages on the same topic received within the same millisecond still get
     * distinct event ids.
     *
     * @param conn     the connection to send the event on
     * @param message  the message to send
     * @param cached   whether this is a replayed, cached message
     * @param sequence the per-connection event sequence counter
     */
    private void sendEvent(ServerSentEventConnection conn, MqttMessage message, boolean cached, AtomicLong sequence) {
        String payload = formatPayload(message, cached);
        String eventId = message.getTopic() + "-" + message.getReceivedAt().toEpochMilli() + "-" + sequence.getAndIncrement();
        conn.send(payload, "mqtt-message", eventId, null);
    }

    /**
     * Attempts to reserve a connection slot for the given topic filter, enforcing
     * {@code max-connections-per-topic}.
     * <p>
     * The slot is reserved optimistically and rolled back immediately if the
     * limit would be exceeded, so a rejected connection never leaks a permit.
     *
     * @param topicFilter the requested topic filter
     * @return {@code true} if a slot was reserved, {@code false} if the limit for
     *         {@code topicFilter} has been reached
     */
    private boolean tryAcquireConnectionSlot(String topicFilter) {
        // The counter is kept even when the limit is unlimited, which is the default. It used to
        // be skipped entirely in that case, and mqtt_sse_open_connections - which reads it - then
        // reported 0 on every default installation, however many clients were connected. Counting
        // always and enforcing only when a limit is set keeps the gauge honest.
        // The whole decision happens inside compute(), under the same per-key lock
        // releaseConnectionSlot uses. Incrementing outside it would race with the eviction of a
        // now-idle counter there: the entry could be removed between this thread obtaining the
        // counter and incrementing it, leaving a live connection accounted for on an object no
        // longer in the map - and its later release decrementing a different one.
        var accepted = new boolean[1];
        connectionsPerTopic.compute(topicFilter, (k, existing) -> {
            var counter = existing == null ? new AtomicInteger() : existing;
            // Rejection cannot leave a zero-valued entry behind: a limit is at least 1 here, so a
            // freshly created counter reads 0 and is always accepted.
            if (maxConnectionsPerTopic > 0 && counter.get() >= maxConnectionsPerTopic) {
                accepted[0] = false;
                return counter;
            }
            counter.incrementAndGet();
            accepted[0] = true;
            return counter;
        });
        return accepted[0];
    }

    /**
     * Releases a connection slot previously reserved by
     * {@link #tryAcquireConnectionSlot(String)}. Always paired with it, limit or no limit, since
     * the counter is maintained either way.
     *
     * @param topicFilter the requested topic filter the connection was accepted on
     */
    private void releaseConnectionSlot(String topicFilter) {
        // compute(), so the decrement and the eviction of a now-idle counter are one atomic step
        // under the map's per-key lock. Left to grow, this map accumulated one permanent entry
        // per distinct topic filter ever connected on - and the filters come from clients, so a
        // caller authorized for sensors/# could add entries without bound by connecting on
        // sensors/1, sensors/2 and so on. That it only bit when max-connections-per-topic was
        // set made it worse, not better: that is precisely when an operator is trying to cap
        // resource use.
        //
        // A plain decrementAndGet() followed by remove() would not do: between the two, a
        // concurrent tryAcquireConnectionSlot could take the same counter back up to 1 and then
        // have its entry removed underneath it, losing the accounting for a live connection.
        connectionsPerTopic.compute(topicFilter, (k, counter) -> {
            if (counter == null) {
                return null;
            }
            return counter.decrementAndGet() <= 0 ? null : counter;
        });
    }

    /**
     * Sums the per-topic-filter connection counts in {@link #connectionsPerTopic}, for the
     * {@code mqtt_sse_open_connections} gauge.
     *
     * @return the total number of currently open SSE connections across all topic filters
     */
    private int totalOpenConnections() {
        return connectionsPerTopic.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    /**
     * Closes a rejected connection, swallowing any {@link IOException} since the
     * connection is being discarded anyway.
     *
     * @param conn the connection to close
     */
    private void closeQuietly(ServerSentEventConnection conn) {
        try {
            conn.close();
        } catch (IOException e) {
            LOGGER.debug("Error closing rejected SSE connection: {}", e.getMessage());
        }
    }

    /**
     * Returns the per-connection queue capacity. Package-private for testing.
     *
     * @return the queue capacity
     */
    int getPerConnectionQueueCapacity() {
        return perConnectionQueueCapacity;
    }

    /**
     * Selects the pipeline to use for a connection requesting {@code requestedFilter},
     * and builds a brand new {@link MqttEventPipeline} instance (with brand new stage
     * instances) from it. Called once per connection from {@link #onConnect}; never
     * caches or reuses the built pipeline, since its stages may hold per-connection
     * mutable state.
     * <p>
     * Selection rule, in order:
     * <ol>
     *   <li>the first configured entry whose {@code topic} equals {@code requestedFilter}
     *       exactly, as a string;</li>
     *   <li>failing that, the first configured entry whose {@code topic} matches
     *       {@code requestedFilter} via {@link MqttTopicMatcher#matches(String, String)};</li>
     *   <li>failing that, an identity pipeline that passes every message through unchanged.</li>
     * </ol>
     *
     * @param requestedFilter the topic filter requested by the connecting client
     * @return a freshly built pipeline, never shared with another connection
     */
    MqttEventPipeline selectPipeline(String requestedFilter) {
        for (PipelineSpec spec : pipelineSpecs) {
            if (requestedFilter.equals(spec.topic())) {
                return spec.build();
            }
        }

        for (PipelineSpec spec : pipelineSpecs) {
            if (MqttTopicMatcher.matches(requestedFilter, spec.topic())) {
                return spec.build();
            }
        }

        return MqttEventPipeline.identity();
    }

    /**
     * Parses the {@code pipeline} configuration list into immutable
     * {@link PipelineSpec specs}, once, at {@link #init()}. Each spec pairs a
     * topic pattern with an ordered list of stage factories: instantiating the
     * actual stages is deferred to {@link PipelineSpec#build()}, called fresh for
     * every connection. An entry missing a {@code topic} key fails fast here, at
     * {@link #init()}, rather than silently building a spec that can never be
     * selected by {@link #selectPipeline(String)}.
     *
     * @return the parsed pipeline specs, in configuration order; empty if no
     *         {@code pipeline} configuration is present
     * @throws IllegalArgumentException if an entry is missing a valid {@code topic} key
     */
    @SuppressWarnings("unchecked")
    private List<PipelineSpec> buildPipelineSpecs() {
        List<Map<String, Object>> pipelineConfig = (List<Map<String, Object>>) config.get("pipeline");
        if (pipelineConfig == null || pipelineConfig.isEmpty()) {
            return List.of();
        }

        List<PipelineSpec> specs = new ArrayList<>();
        for (Map<String, Object> entry : pipelineConfig) {
            Object rawTopic = entry.get("topic");
            if (!(rawTopic instanceof String topic)) {
                throw new IllegalArgumentException(
                    "Pipeline configuration entry is missing a valid 'topic' key: " + entry);
            }
            List<Map<String, Object>> stageConfigs = (List<Map<String, Object>>) entry.get("stages");

            List<Supplier<MqttEventStage>> factories = new ArrayList<>();
            if (stageConfigs != null) {
                for (Map<String, Object> stageConfig : stageConfigs) {
                    Supplier<MqttEventStage> factory = buildStageFactory(stageConfig);
                    if (factory != null) {
                        factories.add(factory);
                    }
                }
            }
            specs.add(new PipelineSpec(topic, List.copyOf(factories)));
        }
        return List.copyOf(specs);
    }

    /**
     * Builds a factory for a single pipeline stage from its configuration.
     * <p>
     * Every parameter is read defensively: a value of the wrong type (e.g. a
     * quoted number in YAML) or an unknown stage {@code type} fails fast here,
     * at {@link #init()}, with an {@link IllegalArgumentException} naming the
     * stage type, the offending key, and its value - rather than surfacing as an
     * opaque {@link ClassCastException} or being silently skipped.
     *
     * @param stageConfig the configuration map for a single stage
     * @return a supplier that builds a new, independent stage instance each time
     *         it is called, or {@code null} if the stage configuration resolves
     *         to a no-op (e.g. a {@code map} stage with no transformation configured)
     * @throws IllegalArgumentException if {@code type} is missing, unknown, or a
     *                                  parameter has an invalid value
     */
    private Supplier<MqttEventStage> buildStageFactory(Map<String, Object> stageConfig) {
        Object rawType = stageConfig.get("type");
        if (!(rawType instanceof String type)) {
            throw new IllegalArgumentException("Pipeline stage configuration is missing a valid 'type' key: " + rawType);
        }

        return switch (type) {
            case "throttle" -> {
                int maxEvents = readIntParam(stageConfig, type, "max-events-per-second", 10);
                yield () -> new ThrottleStage(maxEvents);
            }
            case "filter" -> {
                String jsonPath = readStringParam(stageConfig, type, "jsonpath");
                String condition = readStringParam(stageConfig, type, "condition");
                String topicRegex = readStringParam(stageConfig, type, "topic-regex");
                Integer minQos = readNullableIntParam(stageConfig, type, "min-qos");
                yield () -> FilterStage.of(jsonPath, condition, topicRegex, minQos);
            }
            case "map" -> {
                String extractField = readStringParam(stageConfig, type, "extract-field");
                if (extractField == null) {
                    yield null;
                }
                yield () -> MapStage.extract(extractField);
            }
            case "tumbling-window" -> {
                long windowMs = readLongParam(stageConfig, type, "window-ms", 1000L);
                String function = readStringParam(stageConfig, type, "function", "count");
                String field = readStringParam(stageConfig, type, "field");
                yield () -> new TumblingWindowAggregator(windowMs, function, field);
            }
            case "sliding-window" -> {
                int windowSize = readIntParam(stageConfig, type, "window-size", 10);
                String function = readStringParam(stageConfig, type, "function", "count");
                String field = readStringParam(stageConfig, type, "field");
                yield () -> new SlidingWindowAggregator(windowSize, function, field);
            }
            default -> throw new IllegalArgumentException(
                "Unknown pipeline stage type '" + type + "'; supported types are: "
                    + "throttle, filter, map, tumbling-window, sliding-window");
        };
    }

    /**
     * Reads an integer stage parameter, accepting either a {@link Number} or a
     * numeric {@link String} (e.g. a value quoted in YAML).
     *
     * @param stageConfig  the stage configuration map
     * @param stageType    the stage type, used in the failure message
     * @param key          the parameter key
     * @param defaultValue the value to return if {@code key} is absent
     * @return the parsed value, or {@code defaultValue} if absent
     * @throws IllegalArgumentException if the value is present but not a valid integer
     */
    private static int readIntParam(Map<String, Object> stageConfig, String stageType, String key, int defaultValue) {
        Integer value = readNullableIntParam(stageConfig, stageType, key);
        return value != null ? value : defaultValue;
    }

    /**
     * Reads an optional integer stage parameter, accepting either a
     * {@link Number} or a numeric {@link String}.
     *
     * @param stageConfig the stage configuration map
     * @param stageType   the stage type, used in the failure message
     * @param key         the parameter key
     * @return the parsed value, or {@code null} if {@code key} is absent
     * @throws IllegalArgumentException if the value is present but not a valid integer
     */
    private static Integer readNullableIntParam(Map<String, Object> stageConfig, String stageType, String key) {
        Object raw = stageConfig.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                throw invalidStageParam(stageType, key, raw);
            }
        }
        throw invalidStageParam(stageType, key, raw);
    }

    /**
     * Reads a long stage parameter, accepting either a {@link Number} or a
     * numeric {@link String} (e.g. a value quoted in YAML).
     *
     * @param stageConfig  the stage configuration map
     * @param stageType    the stage type, used in the failure message
     * @param key          the parameter key
     * @param defaultValue the value to return if {@code key} is absent
     * @return the parsed value, or {@code defaultValue} if absent
     * @throws IllegalArgumentException if the value is present but not a valid long
     */
    private static long readLongParam(Map<String, Object> stageConfig, String stageType, String key, long defaultValue) {
        Object raw = stageConfig.get(key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                throw invalidStageParam(stageType, key, raw);
            }
        }
        throw invalidStageParam(stageType, key, raw);
    }

    /**
     * Reads an optional string stage parameter.
     *
     * @param stageConfig the stage configuration map
     * @param stageType   the stage type, used in the failure message
     * @param key         the parameter key
     * @return the value, or {@code null} if {@code key} is absent
     * @throws IllegalArgumentException if the value is present but not a string
     */
    private static String readStringParam(Map<String, Object> stageConfig, String stageType, String key) {
        return readStringParam(stageConfig, stageType, key, null);
    }

    /**
     * Reads a string stage parameter, falling back to {@code defaultValue} if absent.
     *
     * @param stageConfig  the stage configuration map
     * @param stageType    the stage type, used in the failure message
     * @param key          the parameter key
     * @param defaultValue the value to return if {@code key} is absent
     * @return the value, or {@code defaultValue} if absent
     * @throws IllegalArgumentException if the value is present but not a string
     */
    private static String readStringParam(Map<String, Object> stageConfig, String stageType, String key, String defaultValue) {
        Object raw = stageConfig.get(key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof String s) {
            return s;
        }
        throw invalidStageParam(stageType, key, raw);
    }

    /**
     * Builds the {@link IllegalArgumentException} raised when a pipeline stage
     * parameter has an invalid type or value, naming the stage type, the key and
     * the offending value so the failure is diagnosable from the message alone.
     *
     * @param stageType the stage type
     * @param key       the offending parameter key
     * @param value     the offending value
     * @return the exception to throw
     */
    private static IllegalArgumentException invalidStageParam(String stageType, String key, Object value) {
        return new IllegalArgumentException(
            "Invalid pipeline stage configuration: stage type '" + stageType + "', parameter '" + key
                + "' has an invalid value: " + value);
    }

    /**
     * Parses a query string into a map of key-value pairs. Package-private for testing.
     *
     * @param queryString the raw HTTP query string
     * @return the parsed, URL-decoded query parameters
     */
    Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new HashMap<>();
        if (queryString == null || queryString.isEmpty()) {
            return params;
        }
        for (String pair : queryString.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                params.put(
                    URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8)
                );
            } else {
                params.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            }
        }
        return params;
    }

    /**
     * Helper for reading config values with defaults.
     */
    @SuppressWarnings("unchecked")
    private <V> V argOrDefault(Map<String, ?> args, String key, V defaultValue) {
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

    /**
     * An immutable pipeline specification parsed once from configuration: a
     * topic pattern plus an ordered list of stage factories. {@link #build()} is
     * called fresh for every connection so that no stage instance - and no
     * {@link MqttEventPipeline} instance - is ever shared between connections.
     *
     * @param topic          the topic pattern this spec applies to, matched
     *                       against a connection's requested topic filter by
     *                       {@link #selectPipeline(String)}
     * @param stageFactories the ordered stage factories, each producing a new,
     *                       independent {@link MqttEventStage} instance
     */
    private record PipelineSpec(String topic, List<Supplier<MqttEventStage>> stageFactories) {

        /**
         * Builds a brand new pipeline from this spec's stage factories.
         *
         * @return a new {@link MqttEventPipeline}, independent of any pipeline
         *         previously built from this same spec
         */
        MqttEventPipeline build() {
            MqttEventPipeline.Builder builder = MqttEventPipeline.builder();
            for (Supplier<MqttEventStage> factory : stageFactories) {
                builder.addStage(factory.get());
            }
            return builder.build();
        }
    }
}
