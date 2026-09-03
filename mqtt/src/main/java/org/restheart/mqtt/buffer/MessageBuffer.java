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
import java.util.concurrent.atomic.AtomicLong;

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
 * Three overflow strategies are supported:
 * <ul>
 *   <li>{@link Strategy#RING} — drop the oldest message when full (for high-frequency sensors)</li>
 *   <li>{@link Strategy#DROP_INCOMING} — reject the new message when full</li>
 *   <li>{@link Strategy#BLOCKING} — block the caller until space is available (true backpressure)</li>
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
         * Drop the oldest message when the buffer is full, to make room for the
         * new one. {@code offer} always returns {@code true}. Suitable for
         * high-frequency sensor data where the latest reading supersedes older ones.
         */
        RING,

        /**
         * Reject the new message when the buffer is full. {@code offer} returns
         * {@code false} in that case, the message is not stored.
         */
        DROP_INCOMING,

        /**
         * Block the calling thread when the buffer is full, until space becomes
         * available, then enqueue the message. {@code offer} returns {@code true}
         * once the message is enqueued, or {@code false} if the thread is
         * interrupted while waiting. This is the only strategy that provides real
         * backpressure: it slows the producer down instead of losing data.
         */
        BLOCKING;

        /**
         * Maps a configuration value to the corresponding strategy.
         *
         * @param value the configured value, one of {@code "ring-buffer"},
         *              {@code "drop-incoming"}, {@code "blocking-queue"}
         * @return the corresponding {@link Strategy}
         * @throws IllegalArgumentException if {@code value} is not one of the accepted values
         */
        public static Strategy fromConfigValue(String value) {
            return switch (value) {
                case "ring-buffer" -> RING;
                case "drop-incoming" -> DROP_INCOMING;
                case "blocking-queue" -> BLOCKING;
                default -> throw new IllegalArgumentException(
                    "Invalid value for buffer.strategy: \"" + value
                        + "\". Accepted values are: ring-buffer, drop-incoming, blocking-queue");
            };
        }
    }

    private final ArrayBlockingQueue<MqttMessage> queue;
    private final Strategy strategy;
    private final int capacity;
    private final AtomicLong acceptedCount = new AtomicLong();
    private final AtomicLong droppedCount = new AtomicLong();

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
     * dropped to make room, and the new message is always accepted. With
     * {@link Strategy#DROP_INCOMING}, the new message is rejected if the buffer
     * is full. With {@link Strategy#BLOCKING}, the calling thread blocks until
     * space is available, unless it is interrupted while waiting.
     * </p>
     *
     * @param message the message to buffer
     * @return {@code true} if the message was accepted, {@code false} if rejected
     *         (with {@link Strategy#DROP_INCOMING}) or if the calling thread was
     *         interrupted while waiting for space (with {@link Strategy#BLOCKING})
     */
    public boolean offer(MqttMessage message) {
        return switch (strategy) {
            case RING -> offerRing(message);
            case DROP_INCOMING -> offerDropIncoming(message);
            case BLOCKING -> offerBlocking(message);
        };
    }

    private boolean offerRing(MqttMessage message) {
        synchronized (queue) {
            while (!queue.offer(message)) {
                // Buffer full — drop oldest to make room
                MqttMessage dropped = queue.poll();
                if (dropped != null) {
                    recordDropped(dropped);
                }
            }
        }
        acceptedCount.incrementAndGet();
        return true;
    }

    private boolean offerDropIncoming(MqttMessage message) {
        boolean accepted = queue.offer(message);
        if (accepted) {
            acceptedCount.incrementAndGet();
        } else {
            recordDropped(message);
        }
        return accepted;
    }

    private boolean offerBlocking(MqttMessage message) {
        try {
            queue.put(message);
            acceptedCount.incrementAndGet();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Records a dropped message: increments the cumulative dropped counter and,
     * every 1000 drops, logs a rate-limited warning so a saturated buffer cannot
     * flood the log.
     *
     * @param message the message that was dropped
     */
    private void recordDropped(MqttMessage message) {
        long count = droppedCount.incrementAndGet();
        if (count % 1000 == 0) {
            LOGGER.warn("Buffer overflow with strategy {}: {} messages dropped so far (last dropped topic: {})",
                strategy, count, message.getTopic());
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
     * Returns the cumulative number of messages accepted into the buffer since
     * creation. Not affected by {@link #clear()}.
     *
     * @return lifetime count of accepted messages
     */
    public long acceptedCount() {
        return acceptedCount.get();
    }

    /**
     * Returns the cumulative number of messages dropped since creation, either
     * evicted by {@link Strategy#RING} or rejected by {@link Strategy#DROP_INCOMING}.
     * Not affected by {@link #clear()}.
     *
     * @return lifetime count of dropped messages
     */
    public long droppedCount() {
        return droppedCount.get();
    }

    /**
     * Removes all messages from the buffer. Does not reset the cumulative
     * {@link #acceptedCount()} and {@link #droppedCount()} counters, which are
     * lifetime totals.
     */
    public void clear() {
        queue.clear();
    }
}
