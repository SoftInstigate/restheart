package org.restheart.mqtt.pipeline;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.restheart.mqtt.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.jayway.jsonpath.JsonPath;

/**
 * Pipeline stage that aggregates messages over fixed time windows.
 *
 * Collects messages for a specified duration, then emits one aggregated result.
 * The window "tumbles" - after emitting, a new window starts from scratch.
 *
 * Supports aggregation functions:
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
 * // Collect messages for 1 second, emit average temperature
 * new TumblingWindowAggregator(1000, "avg", "$.temperature")
 *
 * // Collect messages for 5 seconds, emit count
 * new TumblingWindowAggregator(5000, "count", null)
 *
 * // Collect messages for 2 seconds, emit as JSON array
 * new TumblingWindowAggregator(2000, "array", null)
 * </pre>
 *
 * @author SoftInstigate
 */
public class TumblingWindowAggregator implements MqttEventStage {

    private static final Logger LOGGER = LoggerFactory.getLogger(TumblingWindowAggregator.class);
    private static final Gson GSON = new Gson();

    private final long windowMs;
    private final String function;
    private final String field;

    private long windowStartTime;
    private final List<MqttMessage> window = new ArrayList<>();

    /**
     * Create a tumbling window aggregator
     *
     * @param windowMs Window duration in milliseconds
     * @param function Aggregation function (avg, min, max, count, sum, array, last)
     * @param field JSONPath to numeric field (required for avg/min/max/sum)
     */
    public TumblingWindowAggregator(long windowMs, String function, String field) {
        if (windowMs <= 0) {
            throw new IllegalArgumentException("windowMs must be positive");
        }

        this.windowMs = windowMs;
        this.function = function != null ? function.toLowerCase() : "array";
        this.field = field;
        this.windowStartTime = System.currentTimeMillis();

        // Validate function requires field
        if (requiresField(this.function) && field == null) {
            throw new IllegalArgumentException("Function '" + function + "' requires a field parameter");
        }
    }

    @Override
    public synchronized Optional<MqttMessage> process(MqttMessage message) {
        long now = System.currentTimeMillis();

        // Check if window has closed
        if (now - windowStartTime >= windowMs) {
            // Window closed - emit aggregated result
            Optional<MqttMessage> result = emitAggregatedMessage(message.getTopic(), message.getQos());

            // Start new window
            window.clear();
            windowStartTime = now;

            // Add current message to new window
            window.add(message);

            return result;
        } else {
            // Window still open - accumulate message
            window.add(message);
            return Optional.empty(); // Don't emit yet
        }
    }

    /**
     * Emit the aggregated message for the closed window
     */
    private Optional<MqttMessage> emitAggregatedMessage(String topic, int qos) {
        if (window.isEmpty()) {
            return Optional.empty();
        }

        try {
            String aggregatedPayload = computeAggregation();

            MqttMessage aggregated = new MqttMessage(
                topic,
                aggregatedPayload,
                qos,
                Instant.now()
            );

            LOGGER.debug("Emitting aggregated message: {} messages -> {}",
                window.size(), function);

            return Optional.of(aggregated);

        } catch (Exception e) {
            LOGGER.error("Failed to compute aggregation: {}", e.getMessage(), e);
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
                return window.get(window.size() - 1).getPayload();
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
        return computeSum() / window.size();
    }

    /**
     * Compute minimum of numeric field
     */
    private double computeMin() {
        return extractNumericValues().stream()
            .mapToDouble(Double::doubleValue)
            .min()
            .orElse(0.0);
    }

    /**
     * Compute maximum of numeric field
     */
    private double computeMax() {
        return extractNumericValues().stream()
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0.0);
    }

    /**
     * Compute sum of numeric field
     */
    private double computeSum() {
        return extractNumericValues().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
    }

    /**
     * Extract numeric values from all messages using JSONPath
     */
    private List<Double> extractNumericValues() {
        List<Double> values = new ArrayList<>();

        for (MqttMessage msg : window) {
            try {
                Object value = JsonPath.read(msg.getPayload(), field);

                if (value instanceof Number) {
                    values.add(((Number) value).doubleValue());
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to extract field '{}' from message: {}", field, e.getMessage());
            }
        }

        return values;
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
    public synchronized int getWindowSize() {
        return window.size();
    }

    /**
     * @return Time remaining in current window (ms)
     */
    public synchronized long getTimeRemaining() {
        long elapsed = System.currentTimeMillis() - windowStartTime;
        return Math.max(0, windowMs - elapsed);
    }

    @Override
    public synchronized Optional<MqttMessage> close() {
        if (window.isEmpty()) {
            return Optional.empty();
        }
        Optional<MqttMessage> result = emitAggregatedMessage(
            window.get(window.size() - 1).getTopic(),
            window.get(window.size() - 1).getQos()
        );
        window.clear();
        return result;
    }
}