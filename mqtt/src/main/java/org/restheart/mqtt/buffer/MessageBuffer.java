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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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

    /** Allowance for the MqttMessage, its Pending wrapper and the queue node holding them. */
    private static final int MESSAGE_OVERHEAD_BYTES = 256;

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

    /**
     * How long {@link Strategy#BLOCKING} waits for room before giving up on a message: not at all
     * by default, it waits forever.
     *
     * <p>Waiting is how backpressure is applied. The writer acknowledges a message to the broker
     * once it is buffered, so a message the buffer refuses is lost - nothing else holds it. A
     * buffer that fills means MongoDB is not keeping up, and the right answer is to stop taking
     * messages from the broker, which keeps them instead, rather than to discard them. Set a
     * positive value only where losing messages is preferable to slowing ingestion down.</p>
     */
    public static final long DEFAULT_MAX_WAIT_MS = 0L;

    /**
     * How much of the heap the buffer may hold, in bytes.
     *
     * <p>A ceiling in messages alone says nothing about memory: ten thousand readings of a hundred
     * bytes are a megabyte, ten thousand images of a hundred kilobytes are a gigabyte. Both limits
     * apply, and whichever is reached first applies backpressure.</p>
     */
    public static final long DEFAULT_MAX_BYTES = 64L * 1024 * 1024;

    private final long maxWaitMs;
    private final long maxBytes;

    /** Permits are bytes: taken when a message is buffered, returned when it leaves. */
    private final Semaphore bytes;

    private final ArrayBlockingQueue<Pending> queue;
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
        this(capacity, strategy, DEFAULT_MAX_WAIT_MS);
    }

    /**
     * @param capacity   how many messages the buffer holds
     * @param strategy   what to do when it is full
     * @param maxWaitMs  for {@link Strategy#BLOCKING} only: how long to wait for room before
     *                   giving up on a message. Zero or less waits forever.
     */
    public MessageBuffer(int capacity, Strategy strategy, long maxWaitMs) {
        this(capacity, strategy, maxWaitMs, DEFAULT_MAX_BYTES);
    }

    /**
     * @param capacity   how many messages the buffer holds
     * @param strategy   what to do when it is full
     * @param maxWaitMs  for {@link Strategy#BLOCKING} only: how long to wait for room before
     *                   giving up on a message. Zero or less waits forever.
     * @param maxBytes   how many bytes of messages it holds, whichever limit is reached first
     */
    public MessageBuffer(int capacity, Strategy strategy, long maxWaitMs, long maxBytes) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("strategy must not be null");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("max-bytes must be positive");
        }
        this.capacity = capacity;
        this.strategy = strategy;
        this.maxWaitMs = maxWaitMs;
        this.maxBytes = Math.min(maxBytes, Integer.MAX_VALUE);
        this.bytes = new Semaphore((int) this.maxBytes);
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * The heap a buffered message is answerable for: its payload, plus its topic, plus a rough
     * allowance for the object graph around them. Exact accounting is neither possible nor needed
     * - what matters is that a buffer of large messages fills long before one of small ones.
     *
     * @param message the message to size
     * @return its weight in bytes, never more than {@link #maxBytes}
     */
    private int weigh(Pending message) {
        var payload = message.message().getPayloadBytes();
        var size = (payload == null ? 0 : payload.length)
            + message.message().getTopic().length() * 2
            + MESSAGE_OVERHEAD_BYTES;
        return (int) Math.min(size, maxBytes);
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
    public boolean offer(Pending message) {
        return switch (strategy) {
            case RING -> offerRing(message);
            case DROP_INCOMING -> offerDropIncoming(message);
            case BLOCKING -> offerBlocking(message);
        };
    }

    private boolean offerRing(Pending message) {
        var weight = weigh(message);
        synchronized (queue) {
            // room for both limits, made by dropping the oldest messages
            while (!bytes.tryAcquire(weight) || queue.remainingCapacity() == 0) {
                Pending dropped = queue.poll();
                if (dropped == null) {
                    // nothing left to evict: the queue is empty and this message still does not
                    // fit, which only happens if it alone is heavier than the whole buffer
                    recordDropped(message);
                    return false;
                }
                bytes.release(weigh(dropped));
                recordDropped(dropped);
            }
            queue.offer(message);
        }
        acceptedCount.incrementAndGet();
        return true;
    }

    private boolean offerDropIncoming(Pending message) {
        var weight = weigh(message);
        if (!bytes.tryAcquire(weight)) {
            recordDropped(message);
            return false;
        }
        boolean accepted = queue.offer(message);
        if (accepted) {
            acceptedCount.incrementAndGet();
        } else {
            bytes.release(weight);
            recordDropped(message);
        }
        return accepted;
    }

    private boolean offerBlocking(Pending message) {
        try {
            // Bounded, not queue.put(). An unbounded wait parks the dispatching thread for as long
            // as the database is away, and those threads are holding messages that are therefore
            // never acknowledged - so the broker's in-flight window fills and it stops delivering
            // ANYTHING to this client, SSE included. A database problem would take the live stream
            // down with it, even though the live stream does not touch the database.
            //
            // Past a configured ceiling the message is dropped and counted. It is lost: the writer
            // has not acknowledged it yet, but it will, because nothing else is holding it. Only a
            // deployment that prefers losing messages to slowing down should set one.
            var weight = weigh(message);

            if (maxWaitMs <= 0) {
                bytes.acquire(weight);
                queue.put(message);
                acceptedCount.incrementAndGet();
                return true;
            }

            var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxWaitMs);
            if (bytes.tryAcquire(weight, maxWaitMs, TimeUnit.MILLISECONDS)) {
                var left = Math.max(0, deadline - System.nanoTime());
                if (queue.offer(message, left, TimeUnit.NANOSECONDS)) {
                    acceptedCount.incrementAndGet();
                    return true;
                }
                bytes.release(weight);
            }
            recordDropped(message);
            return false;
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
    private void recordDropped(Pending message) {
        long count = droppedCount.incrementAndGet();
        if (count % 1000 == 0) {
            LOGGER.warn("Buffer overflow with strategy {}: {} messages dropped so far (last dropped topic: {})",
                strategy, count, message.message().getTopic());
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
    public List<Pending> drain(int batchSize) {
        List<Pending> batch = new ArrayList<>();
        queue.drainTo(batch, batchSize);
        batch.forEach(pending -> bytes.release(weigh(pending)));
        return batch;
    }

    /**
     * @return how many bytes of messages the buffer currently holds
     */
    public long bytes() {
        return maxBytes - bytes.availablePermits();
    }

    /**
     * @return the buffer's ceiling in bytes
     */
    public long maxBytes() {
        return maxBytes;
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
        synchronized (queue) {
            List<Pending> discarded = new ArrayList<>();
            queue.drainTo(discarded);
            discarded.forEach(pending -> bytes.release(weigh(pending)));
        }
    }

    /**
     * A buffered message together with the callback that reports it as taken.
     * <p>
     * {@code MqttMongoWriter} acknowledges a message to the broker as soon as it is buffered -
     * ordered MQTT acknowledgements make anything later stall ingestion for every consumer - so
     * it passes a callback that does nothing. The field is kept for consumers that do want to
     * report a message as taken only once they have stored it.
     * </p>
     *
     * @param message the message
     * @param taken   invoked when responsibility for it has genuinely been taken; may be a no-op
     *                for a message that arrived through a path with no acknowledgement to release
     */
    public record Pending(MqttMessage message, Runnable taken) {
    }

}
