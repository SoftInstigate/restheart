package org.restheart.mqtt.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.restheart.mqtt.model.MqttMessage;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.jayway.jsonpath.JsonPath;

/**
 * Shared aggregation arithmetic for {@link TumblingWindowAggregator} and
 * {@link SlidingWindowAggregator}.
 *
 * Both stages support the same set of aggregation functions over a window of
 * {@link MqttMessage}s: "array", "count", "last", "avg", "min", "max" and
 * "sum". This class centralizes the JSONPath numeric extraction and the
 * arithmetic for those functions so the two stages cannot compute different
 * results for the same input.
 *
 * For "avg", "min", "max" and "sum", if none of the messages in the window
 * yield a numeric value for the configured field, callers get
 * {@link Optional#empty()} back rather than a misleading {@code 0.0}.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
final class WindowAggregation {

    private static final Gson GSON = new Gson();

    private WindowAggregation() {
        // utility class
    }

    /**
     * Compute the payload for the given aggregation function over the window.
     *
     * @param function One of "array", "count", "last", "avg", "min", "max", "sum"
     * @param window The messages currently in the window (never empty)
     * @param field JSONPath to the numeric field, required for avg/min/max/sum
     * @param logger Logger used to report extraction failures and empty results
     * @return The computed payload, or empty if a numeric function found no values
     * @throws IllegalArgumentException if the function is not recognized
     */
    static Optional<String> aggregate(String function, List<MqttMessage> window, String field, Logger logger) {
        return switch (function) {
            case "array" -> Optional.of(array(window));
            case "count" -> Optional.of(count(window));
            case "last" -> Optional.of(last(window));
            case "avg" -> emptyIfNoValues(function, avg(extractNumericValues(window, field, logger)), logger);
            case "min" -> emptyIfNoValues(function, min(extractNumericValues(window, field, logger)), logger);
            case "max" -> emptyIfNoValues(function, max(extractNumericValues(window, field, logger)), logger);
            case "sum" -> emptyIfNoValues(function, sum(extractNumericValues(window, field, logger)), logger);
            default -> throw new IllegalArgumentException("Unknown aggregation function: " + function);
        };
    }

    private static Optional<String> emptyIfNoValues(String function, Optional<Double> value, Logger logger) {
        if (value.isEmpty()) {
            logger.debug("Aggregation function '{}' found no numeric values in window; emitting nothing", function);
            return Optional.empty();
        }
        return Optional.of(String.valueOf(value.get()));
    }

    /**
     * Collect all messages in the window into a JSON array payload.
     */
    static String array(List<MqttMessage> window) {
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
     * @return The number of messages in the window, as a string.
     */
    static String count(List<MqttMessage> window) {
        return String.valueOf(window.size());
    }

    /**
     * @return The payload of the last message in the window.
     */
    static String last(List<MqttMessage> window) {
        return window.get(window.size() - 1).getPayload();
    }

    /**
     * Extract numeric values from all messages in the window using JSONPath.
     * Messages whose payload doesn't parse, whose field is missing, or whose
     * field is non-numeric are skipped and logged at warn level.
     */
    static List<Double> extractNumericValues(List<MqttMessage> window, String field, Logger logger) {
        List<Double> values = new ArrayList<>();

        for (MqttMessage msg : window) {
            try {
                Object value = JsonPath.read(msg.getPayload(), field);

                if (value instanceof Number number) {
                    values.add(number.doubleValue());
                }
            } catch (Exception e) {
                logger.warn("Failed to extract field '{}' from message: {}", field, e.getMessage());
            }
        }

        return values;
    }

    /**
     * @return The average of the given values, or empty if the list is empty.
     */
    static Optional<Double> avg(List<Double> values) {
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(values.stream().mapToDouble(Double::doubleValue).sum() / values.size());
    }

    /**
     * @return The minimum of the given values, or empty if the list is empty.
     */
    static Optional<Double> min(List<Double> values) {
        return toOptional(values.stream().mapToDouble(Double::doubleValue).min());
    }

    /**
     * @return The maximum of the given values, or empty if the list is empty.
     */
    static Optional<Double> max(List<Double> values) {
        return toOptional(values.stream().mapToDouble(Double::doubleValue).max());
    }

    /**
     * @return The sum of the given values, or empty if the list is empty.
     */
    static Optional<Double> sum(List<Double> values) {
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(values.stream().mapToDouble(Double::doubleValue).sum());
    }

    private static Optional<Double> toOptional(OptionalDouble value) {
        return value.isPresent() ? Optional.of(value.getAsDouble()) : Optional.empty();
    }
}
