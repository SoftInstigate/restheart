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

package org.restheart.mqtt.buffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import org.restheart.mqtt.model.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded in-memory buffer for MQTT messages with configurable overflow strategy.
 * <p>
 * Decouples MQTT ingestion rate from MongoDB write throughput. Messages are
 * accumulated via {@link #offer(MqttMessage)} and drained in batches via
 * {@link #drain(int)} for bulk insertion.
 * </p>
 * <p>
 * Two overflow strategies are supported:
 * <ul>
 *   <li>{@link Strategy#RING} — drop oldest message when full (for high-frequency sensors)</li>
 *   <li>{@link Strategy#BLOCKING} — reject new messages when full (backpressure)</li>
 * </ul>
 * </p>
 * <p>
 * This class is thread-safe: multiple producers may call {@code offer()} concurrently,
 * and a single consumer thread calls {@code drain()}.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MessageBuffer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageBuffer.class);

    /**
     * Overflow strategy for the buffer.
     */
    public enum Strategy {
        /**
         * Drop the oldest message when the buffer is full. Suitable for
         * high-frequency sensor data where the latest reading supersedes older ones.
         */
        RING,

        /**
         * Reject new messages when the buffer is full (backpressure). Suitable for
         * loss-intolerant data where every message must be persisted.
         */
        BLOCKING
    }

    private final ArrayBlockingQueue<MqttMessage> queue;
    private final Strategy strategy;
    private final int capacity;

    /**
     * Creates a new message buffer with the specified capacity and strategy.
     *
     * @param capacity maximum number of messages the buffer can hold
     * @param strategy overflow strategy
     * @throws IllegalArgumentException if capacity is not positive
     */
    public MessageBuffer(int capacity, Strategy strategy) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("strategy must not be null");
        }
        this.capacity = capacity;
        this.strategy = strategy;
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * Offers a message to the buffer.
     * <p>
     * With {@link Strategy#RING}, if the buffer is full the oldest message is
     * dropped to make room. With {@link Strategy#BLOCKING}, the message is
     * rejected if the buffer is full.
     * </p>
     *
     * @param message the message to buffer
     * @return {@code true} if the message was accepted, {@code false} if rejected
     *         (only possible with {@link Strategy#BLOCKING})
     */
    public boolean offer(MqttMessage message) {
        if (strategy == Strategy.RING) {
            while (!queue.offer(message)) {
                // Buffer full — drop oldest to make room
                MqttMessage dropped = queue.poll();
                if (dropped != null) {
                    LOGGER.debug("Ring buffer full, dropped oldest message for topic {}", dropped.getTopic());
                }
            }
            return true;
        } else {
            // BLOCKING strategy — reject if full
            boolean accepted = queue.offer(message);
            if (!accepted) {
                LOGGER.debug("Blocking buffer full, rejected message for topic {}", message.getTopic());
            }
            return accepted;
        }
    }

    /**
     * Drains up to {@code batchSize} messages from the buffer into a list.
     * <p>
     * This method is intended to be called by a single consumer thread.
     * The returned list may contain fewer messages than requested if the
     * buffer does not have enough messages available.
     * </p>
     *
     * @param batchSize maximum number of messages to drain
     * @return a list of drained messages (may be empty, never null)
     */
    public List<MqttMessage> drain(int batchSize) {
        List<MqttMessage> batch = new ArrayList<>();
        queue.drainTo(batch, batchSize);
        return batch;
    }

    /**
     * Returns the current number of messages in the buffer.
     *
     * @return current buffer size
     */
    public int size() {
        return queue.size();
    }

    /**
     * Returns the maximum capacity of the buffer.
     *
     * @return buffer capacity
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Returns the overflow strategy.
     *
     * @return the strategy
     */
    public Strategy strategy() {
        return strategy;
    }

    /**
     * Removes all messages from the buffer.
     */
    public void clear() {
        queue.clear();
    }
}
