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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.restheart.mqtt.pipeline.FilterStage;
import org.restheart.mqtt.pipeline.MapStage;
import org.restheart.mqtt.pipeline.MqttEventPipeline;
import org.restheart.mqtt.pipeline.SlidingWindowAggregator;
import org.restheart.mqtt.pipeline.ThrottleStage;
import org.restheart.mqtt.pipeline.TumblingWindowAggregator;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.SseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hivemq.client.mqtt.datatypes.MqttQos;

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
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name = "mqtt-sse",
    description = "Streams MQTT topic messages as Server-Sent Events",
    defaultURI = "/mqtt-sse",
    secure = false
)
public class MqttSseService implements SseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttSseService.class);
    private static final Gson GSON = new Gson();

    @Inject("config")
    private Map<String, Object> config;

    private String defaultTopic;
    private int defaultQos;
    private int perConnectionQueueCapacity;
    private boolean payloadEnvelope;
    private boolean lastMessageCacheEnabled;

    private MqttEventPipeline defaultPipeline = MqttEventPipeline.identity();

    @OnInit
    public void init() {
        defaultTopic = argOrDefault(config, "default-topic", "sensors/#");
        defaultQos = argOrDefault(config, "default-qos", 1);
        perConnectionQueueCapacity = argOrDefault(config, "per-connection-queue-capacity", 256);
        payloadEnvelope = argOrDefault(config, "payload-envelope", false);
        lastMessageCacheEnabled = argOrDefault(config, "last-message-cache", true);

        // Build pipeline from config
        defaultPipeline = buildPipelineFromConfig();

        LOGGER.info("MqttSseService initialized: defaultTopic={}, defaultQos={}, queueCapacity={}, envelope={}",
            defaultTopic, defaultQos, perConnectionQueueCapacity, payloadEnvelope);
    }

    @Override
    public void onConnect(ServerSentEventConnection conn, String lastEventId) {
        String queryString = conn.getQueryString();
        String topicFilter = resolveTopicFilter(queryString);
        int qos = resolveQos(queryString);

        LOGGER.debug("SSE client connected: topic={}, qos={}", topicFilter, qos);

        // Per-connection bounded queue
        ArrayBlockingQueue<MqttMessage> queue = new ArrayBlockingQueue<>(perConnectionQueueCapacity);

        // Send cached message if available
        if (lastMessageCacheEnabled) {
            MqttMessage cached = MqttMessageRouter.getInstance().getLastMessage(topicFilter);
            if (cached != null) {
                String payload = formatPayload(cached, true);
                conn.send(payload, "mqtt-message", cached.getTopic() + "-" + cached.getReceivedAt().toEpochMilli(), null);
            }
        }

        // Subscribe to topic via router
        Consumer<MqttMessage> listener = msg -> {
            if (!queue.offer(msg)) {
                LOGGER.warn("SSE client queue full for topic {}, dropping message", topicFilter);
            }
        };
        MqttMessageRouter.getInstance().subscribe(topicFilter, MqttQos.fromCode(qos), listener);

        // Drain queue on virtual thread
        Thread.ofVirtual().start(() -> {
            try {
                while (conn.isOpen()) {
                    MqttMessage msg = queue.poll(1, TimeUnit.SECONDS);
                    if (msg == null) continue;

                    Optional<MqttMessage> processed = defaultPipeline.process(msg);
                    if (processed.isPresent()) {
                        String payload = formatPayload(processed.get(), false);
                        String eventId = processed.get().getTopic() + "-" + processed.get().getReceivedAt().toEpochMilli();
                        conn.send(payload, "mqtt-message", eventId, null);
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
            MqttMessageRouter.getInstance().unsubscribe(topicFilter, listener);
            LOGGER.debug("SSE client disconnected: topic={}", topicFilter);
        });
    }

    /**
     * Resolves the topic filter from the query string.
     * Falls back to the configured default topic if not provided.
     *
     * @param queryString the HTTP query string
     * @return the topic filter
     */
    String resolveTopicFilter(String queryString) {
        if (queryString != null) {
            Map<String, String> params = parseQueryString(queryString);
            String topic = params.get("topic");
            if (topic != null) {
                return topic; // Already decoded by parseQueryString
            }
        }
        return defaultTopic;
    }

    /**
     * Resolves the QoS level from the query string.
     * Falls back to the configured default QoS if not provided.
     *
     * @param queryString the HTTP query string
     * @return the QoS level (0, 1, or 2)
     */
    int resolveQos(String queryString) {
        if (queryString != null) {
            Map<String, String> params = parseQueryString(queryString);
            String qosStr = params.get("qos");
            if (qosStr != null) {
                try {
                    return Integer.parseInt(qosStr);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Invalid QoS value '{}', using default", qosStr);
                }
            }
        }
        return defaultQos;
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
     * Processes a message through the default pipeline.
     * Package-private for testing.
     *
     * @param message the message to process
     * @return the processed message, or empty if dropped
     */
    Optional<MqttMessage> processThroughPipeline(MqttMessage message) {
        return defaultPipeline.process(message);
    }

    /**
     * Sets the pipeline. Package-private for testing.
     *
     * @param pipeline the pipeline to set
     */
    void setPipeline(MqttEventPipeline pipeline) {
        this.defaultPipeline = pipeline;
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
     * Builds the default pipeline from the YAML configuration.
     */
    @SuppressWarnings("unchecked")
    private MqttEventPipeline buildPipelineFromConfig() {
        List<Map<String, Object>> pipelineConfig = (List<Map<String, Object>>) config.get("pipeline");
        if (pipelineConfig == null || pipelineConfig.isEmpty()) {
            return MqttEventPipeline.identity();
        }

        // For now, build a single pipeline from the first topic's stages
        // TODO: per-topic pipeline support in MqttSseService.onConnect
        Map<String, Object> firstEntry = pipelineConfig.get(0);
        List<Map<String, Object>> stages = (List<Map<String, Object>>) firstEntry.get("stages");
        if (stages == null || stages.isEmpty()) {
            return MqttEventPipeline.identity();
        }

        MqttEventPipeline.Builder builder = MqttEventPipeline.builder();
        for (Map<String, Object> stageConfig : stages) {
            String type = (String) stageConfig.get("type");
            if (type == null) continue;

            switch (type) {
                case "throttle" -> {
                    int maxEvents = ((Number) stageConfig.getOrDefault("max-events-per-second", 10)).intValue();
                    builder.addStage(new ThrottleStage(maxEvents));
                }
                case "filter" -> {
                    String jsonPath = (String) stageConfig.get("jsonpath");
                    String condition = (String) stageConfig.get("condition");
                    if (jsonPath != null) {
                        builder.addStage(new FilterStage(jsonPath, condition));
                    }
                }
                case "map" -> {
                    String extractField = (String) stageConfig.get("extract-field");
                    if (extractField != null) {
                        builder.addStage(new MapStage(extractField));
                    }
                }
                case "tumbling-window" -> {
                    long windowMs = ((Number) stageConfig.getOrDefault("window-ms", 1000)).longValue();
                    String function = (String) stageConfig.getOrDefault("function", "count");
                    String field = (String) stageConfig.get("field");
                    builder.addStage(new TumblingWindowAggregator(windowMs, function, field));
                }
                case "sliding-window" -> {
                    int windowSize = ((Number) stageConfig.getOrDefault("window-size", 10)).intValue();
                    String function = (String) stageConfig.getOrDefault("function", "count");
                    String field = (String) stageConfig.get("field");
                    builder.addStage(new SlidingWindowAggregator(windowSize, function, field));
                }
                default -> LOGGER.warn("Unknown pipeline stage type: {}", type);
            }
        }

        return builder.build();
    }

    /**
     * Parses a query string into a map of key-value pairs.
     */
    private Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new java.util.HashMap<>();
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
        try {
            return (V) args.get(key);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
}
