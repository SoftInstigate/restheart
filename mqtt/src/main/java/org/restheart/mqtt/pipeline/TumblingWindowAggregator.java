package org.restheart.mqtt.pipeline;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.restheart.mqtt.model.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class TumblingWindowAggregator implements MqttEventStage {

    private static final Logger LOGGER = LoggerFactory.getLogger(TumblingWindowAggregator.class);

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
     * Emit the aggregated message for the closed window.
     *
     * Aggregation arithmetic is delegated to {@link WindowAggregation} so this
     * stage and {@link SlidingWindowAggregator} cannot diverge. If the
     * aggregation function found no numeric values, nothing is emitted.
     */
    private Optional<MqttMessage> emitAggregatedMessage(String topic, int qos) {
        if (window.isEmpty()) {
            return Optional.empty();
        }

        try {
            Optional<String> aggregatedPayload = WindowAggregation.aggregate(function, window, field, LOGGER);

            if (aggregatedPayload.isEmpty()) {
                return Optional.empty();
            }

            MqttMessage aggregated = new MqttMessage(
                topic,
                aggregatedPayload.get(),
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

    /**
     * Emit the current window's aggregate if it has been open for at least
     * {@code windowMs} with no new message arriving to trigger the emission.
     *
     * This is the time-driven counterpart to {@link #process(MqttMessage)}:
     * without it, a tumbling window whose source goes quiet never emits its
     * last, still-open window. Both routes share the same emission logic via
     * {@link #emitAggregatedMessage(String, int)}.
     *
     * @return The aggregated message if the window has elapsed, otherwise empty
     */
    @Override
    public synchronized Optional<MqttMessage> pollExpired() {
        if (window.isEmpty()) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        if (now - windowStartTime < windowMs) {
            return Optional.empty();
        }

        Optional<MqttMessage> result = emitAggregatedMessage(
            window.get(window.size() - 1).getTopic(),
            window.get(window.size() - 1).getQos()
        );
        window.clear();
        windowStartTime = now;
        return result;
    }
}