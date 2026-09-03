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
                Optional<String> aggregatedPayload = WindowAggregation.aggregate(function, window, field, LOGGER);

                if (aggregatedPayload.isEmpty()) {
                    return Optional.empty();
                }

                MqttMessage aggregated = new MqttMessage(
                    message.getTopic(),
                    aggregatedPayload.get(),
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