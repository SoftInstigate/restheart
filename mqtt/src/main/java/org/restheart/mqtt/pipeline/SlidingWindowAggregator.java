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

package org.restheart.mqtt.pipeline;

import java.time.Instant;
import java.util.LinkedList;
import java.util.Optional;

import org.restheart.mqtt.model.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.jayway.jsonpath.JsonPath;

/**
 * Pipeline stage that aggregates messages over a sliding window.
 *
 * Unlike tumbling windows, sliding windows advance on every message.
 * The window maintains the last N messages and emits an aggregated result
 * for each new message.
 *
 * Supports same aggregation functions as TumblingWindowAggregator:
 * - "array": Collect all messages into JSON array
 * - "count": Count of messages
 * - "avg": Average of numeric field
 * - "min": Minimum of numeric field
 * - "max": Maximum of numeric field
 * - "sum": Sum of numeric field
 * - "last": Last message only
 *
 * Examples:
 * <pre>
 * // Sliding window of last 10 messages, emit average
 * new SlidingWindowAggregator(10, "avg", "$.temperature")
 *
 * // Sliding window of last 5 messages, emit as array
 * new SlidingWindowAggregator(5, "array", null)
 * </pre>
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class SlidingWindowAggregator implements MqttEventStage {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlidingWindowAggregator.class);
    private static final Gson GSON = new Gson();
    private static final String FAILED_TO_EXTRACT_FIELD_LOG = "Failed to extract field '{}' from message: {}";

    private final int windowSize;
    private final String function;
    private final String field;

    private final LinkedList<MqttMessage> window = new LinkedList<>();

    /**
     * Create a sliding window aggregator
     *
     * @param windowSize Number of messages to keep in window
     * @param function Aggregation function (avg, min, max, count, sum, array, last)
     * @param field JSONPath to numeric field (required for avg/min/max/sum)
     */
    public SlidingWindowAggregator(int windowSize, String function, String field) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be positive");
        }

        this.windowSize = windowSize;
        this.function = function != null ? function.toLowerCase() : "array";
        this.field = field;

        // Validate function requires field
        if (requiresField(this.function) && field == null) {
            throw new IllegalArgumentException("Function '" + function + "' requires a field parameter");
        }
    }

    @Override
    public synchronized Optional<MqttMessage> process(MqttMessage message) {
        // Add new message to window
        window.addLast(message);

        // Remove oldest message if window is full
        if (window.size() > windowSize) {
            window.removeFirst();
        }

        // Emit aggregated result if window has enough messages
        if (window.size() >= windowSize) {
            try {
                String aggregatedPayload = computeAggregation();

                MqttMessage aggregated = new MqttMessage(
                    message.getTopic(),
                    aggregatedPayload,
                    message.getQos(),
                    Instant.now()
                );

                return Optional.of(aggregated);

            } catch (Exception e) {
                LOGGER.error("Failed to compute aggregation: {}", e.getMessage(), e);
                return Optional.empty();
            }
        } else {
            // Window not full yet - don't emit
            return Optional.empty();
        }
    }

    /**
     * Compute the aggregation based on the function
     */
    private String computeAggregation() {
        switch (function) {
            case "array":
                return computeArray();
            case "count":
                return String.valueOf(window.size());
            case "last":
                return window.getLast().getPayload();
            case "avg":
                return String.valueOf(computeAverage());
            case "min":
                return String.valueOf(computeMin());
            case "max":
                return String.valueOf(computeMax());
            case "sum":
                return String.valueOf(computeSum());
            default:
                throw new IllegalArgumentException("Unknown aggregation function: " + function);
        }
    }

    /**
     * Collect all messages into a JSON array
     */
    private String computeArray() {
        JsonArray array = new JsonArray();

        for (MqttMessage msg : window) {
            try {
                JsonElement element = JsonParser.parseString(msg.getPayload());
                array.add(element);
            } catch (Exception e) {
                // If not JSON, add as string
                array.add(msg.getPayload());
            }
        }

        return GSON.toJson(array);
    }

    /**
     * Compute average of numeric field
     */
    private double computeAverage() {
        double sum = 0;
        int count = 0;

        for (MqttMessage msg : window) {
            try {
                Object value = JsonPath.read(msg.getPayload(), field);
                if (value instanceof Number) {
                    sum += ((Number) value).doubleValue();
                    count++;
                }
            } catch (Exception e) {
                LOGGER.warn(FAILED_TO_EXTRACT_FIELD_LOG, field, e.getMessage());
            }
        }

        return count > 0 ? sum / count : 0.0;
    }

    /**
     * Compute minimum of numeric field
     */
    private double computeMin() {
        double min = Double.MAX_VALUE;

        for (MqttMessage msg : window) {
            try {
                Object value = JsonPath.read(msg.getPayload(), field);
                if (value instanceof Number) {
                    min = Math.min(min, ((Number) value).doubleValue());
                }
            } catch (Exception e) {
                LOGGER.warn(FAILED_TO_EXTRACT_FIELD_LOG, field, e.getMessage());
            }
        }

        return min == Double.MAX_VALUE ? 0.0 : min;
    }

    /**
     * Compute maximum of numeric field
     */
    private double computeMax() {
        double max = Double.MIN_VALUE;

        for (MqttMessage msg : window) {
            try {
                Object value = JsonPath.read(msg.getPayload(), field);
                if (value instanceof Number) {
                    max = Math.max(max, ((Number) value).doubleValue());
                }
            } catch (Exception e) {
                LOGGER.warn(FAILED_TO_EXTRACT_FIELD_LOG, field, e.getMessage());
            }
        }

        return max == Double.MIN_VALUE ? 0.0 : max;
    }

    /**
     * Compute sum of numeric field
     */
    private double computeSum() {
        double sum = 0;

        for (MqttMessage msg : window) {
            try {
                Object value = JsonPath.read(msg.getPayload(), field);
                if (value instanceof Number) {
                    sum += ((Number) value).doubleValue();
                }
            } catch (Exception e) {
                LOGGER.warn(FAILED_TO_EXTRACT_FIELD_LOG, field, e.getMessage());
            }
        }

        return sum;
    }

    /**
     * Check if function requires a field parameter
     */
    private boolean requiresField(String func) {
        return func.equals("avg") || func.equals("min") ||
               func.equals("max") || func.equals("sum");
    }

    /**
     * @return Current window size
     */
    public synchronized int getCurrentSize() {
        return window.size();
    }

    /**
     * Clear the window
     */
    public synchronized void clear() {
        window.clear();
    }
}