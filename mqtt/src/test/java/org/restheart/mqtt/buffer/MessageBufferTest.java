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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.restheart.mqtt.buffer.MessageBuffer.Strategy;
import org.restheart.mqtt.model.MqttMessage;

/**
 * Unit tests for MessageBuffer.
 * Tests RING, DROP_INCOMING and BLOCKING strategies, capacity boundaries,
 * cumulative counters, concurrent producers, and drain behavior.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MessageBufferTest {

    private MqttMessage msg(String topic, String payload) {
        return new MqttMessage(topic, payload, 1, Instant.now());
    }

    // --- Construction ---

    @Test
    @DisplayName("Constructor rejects non-positive capacity")
    void testInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new MessageBuffer(0, Strategy.RING));
        assertThrows(IllegalArgumentException.class, () -> new MessageBuffer(-1, Strategy.DROP_INCOMING));
    }

    @Test
    @DisplayName("Constructor rejects null strategy")
    void testNullStrategy() {
        assertThrows(IllegalArgumentException.class, () -> new MessageBuffer(10, null));
    }

    // --- RING strategy ---

    @Test
    @DisplayName("RING: accepts messages up to capacity")
    void testRingAcceptsUpToCapacity() {
        MessageBuffer buffer = new MessageBuffer(3, Strategy.RING);

        assertTrue(buffer.offer(msg("t", "1")));
        assertTrue(buffer.offer(msg("t", "2")));
        assertTrue(buffer.offer(msg("t", "3")));
        assertEquals(3, buffer.size());
    }

    @Test
    @DisplayName("RING: drops oldest on overflow")
    void testRingDropOldest() {
        MessageBuffer buffer = new MessageBuffer(2, Strategy.RING);

        buffer.offer(msg("t", "old"));
        buffer.offer(msg("t", "mid"));
        buffer.offer(msg("t", "new")); // should drop "old"

        assertEquals(2, buffer.size());
        List<MqttMessage> drained = buffer.drain(10);
        assertEquals("mid", drained.get(0).getPayload());
        assertEquals("new", drained.get(1).getPayload());
    }

    @Test
    @DisplayName("RING: always returns true")
    void testRingAlwaysAccepts() {
        MessageBuffer buffer = new MessageBuffer(1, Strategy.RING);

        assertTrue(buffer.offer(msg("t", "1")));
        assertTrue(buffer.offer(msg("t", "2"))); // drops "1"
        assertTrue(buffer.offer(msg("t", "3"))); // drops "2"
        assertEquals(1, buffer.size());
    }

    @Test
    @DisplayName("RING: dropping the oldest message really removes it and counts it")
    void testRingDropOldestIsCounted() {
        MessageBuffer buffer = new MessageBuffer(2, Strategy.RING);

        buffer.offer(msg("t", "old"));
        buffer.offer(msg("t", "mid"));
        buffer.offer(msg("t", "new")); // "old" is the one dropped

        assertEquals(1, buffer.droppedCount());
        assertEquals(3, buffer.acceptedCount());

        List<MqttMessage> drained = buffer.drain(10);
        assertTrue(drained.stream().noneMatch(m -> "old".equals(m.getPayload())),
            "the dropped message must not still be in the buffer");
    }

    // --- DROP_INCOMING strategy ---

    @Test
    @DisplayName("DROP_INCOMING: accepts messages up to capacity")
    void testDropIncomingAcceptsUpToCapacity() {
        MessageBuffer buffer = new MessageBuffer(3, Strategy.DROP_INCOMING);

        assertTrue(buffer.offer(msg("t", "1")));
        assertTrue(buffer.offer(msg("t", "2")));
        assertTrue(buffer.offer(msg("t", "3")));
        assertEquals(3, buffer.size());
    }

    @Test
    @DisplayName("DROP_INCOMING: rejects when full")
    void testDropIncomingRejectsWhenFull() {
        MessageBuffer buffer = new MessageBuffer(2, Strategy.DROP_INCOMING);

        assertTrue(buffer.offer(msg("t", "1")));
        assertTrue(buffer.offer(msg("t", "2")));
        assertFalse(buffer.offer(msg("t", "3"))); // rejected
        assertEquals(2, buffer.size());
    }

    @Test
    @DisplayName("DROP_INCOMING: rejected message is not stored and is counted")
    void testDropIncomingRejectedNotStoredAndCounted() {
        MessageBuffer buffer = new MessageBuffer(1, Strategy.DROP_INCOMING);

        buffer.offer(msg("t", "kept"));
        boolean second = buffer.offer(msg("t", "rejected"));

        assertFalse(second);
        List<MqttMessage> drained = buffer.drain(10);
        assertEquals(1, drained.size());
        assertEquals("kept", drained.get(0).getPayload());

        assertEquals(1, buffer.acceptedCount());
        assertEquals(1, buffer.droppedCount());
    }

    // --- BLOCKING strategy ---

    @Test
    @DisplayName("BLOCKING: accepts messages up to capacity")
    void testBlockingAcceptsUpToCapacity() {
        MessageBuffer buffer = new MessageBuffer(3, Strategy.BLOCKING);

        assertTrue(buffer.offer(msg("t", "1")));
        assertTrue(buffer.offer(msg("t", "2")));
        assertTrue(buffer.offer(msg("t", "3")));
        assertEquals(3, buffer.size());
    }

    @Test
    @DisplayName("BLOCKING: producer blocks when full and proceeds once a consumer drains")
    void testBlockingBlocksUntilSpaceAvailable() throws Exception {
        MessageBuffer buffer = new MessageBuffer(1, Strategy.BLOCKING);
        buffer.offer(msg("t", "1")); // fill the buffer

        CountDownLatch aboutToBlock = new CountDownLatch(1);
        CountDownLatch enqueued = new CountDownLatch(1);
        AtomicBoolean completedBeforeDrain = new AtomicBoolean(false);

        Thread producer = new Thread(() -> {
            aboutToBlock.countDown();
            buffer.offer(msg("t", "2"));
            enqueued.countDown();
        });
        producer.start();

        assertTrue(aboutToBlock.await(1, TimeUnit.SECONDS));
        // Give the producer thread a short chance to run; it must still be blocked
        // because the buffer is full and nothing has been drained yet.
        boolean finishedTooEarly = enqueued.await(200, TimeUnit.MILLISECONDS);
        completedBeforeDrain.set(finishedTooEarly);
        assertFalse(completedBeforeDrain.get(), "producer must not have enqueued before the buffer was drained");

        // Drain one slot to unblock the producer.
        buffer.drain(1);

        assertTrue(enqueued.await(1, TimeUnit.SECONDS), "producer should complete once space is available");
        producer.join(1000);
        assertEquals(1, buffer.size());
    }

    @Test
    @DisplayName("BLOCKING: interrupting a blocked producer returns false and preserves the interrupt flag")
    void testBlockingInterruptReturnsFalseAndSetsInterruptFlag() throws Exception {
        MessageBuffer buffer = new MessageBuffer(1, Strategy.BLOCKING);
        buffer.offer(msg("t", "1")); // fill the buffer

        CountDownLatch aboutToBlock = new CountDownLatch(1);
        AtomicBoolean offerResult = new AtomicBoolean(true);
        AtomicBoolean interruptFlagSet = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);

        Thread producer = new Thread(() -> {
            aboutToBlock.countDown();
            boolean result = buffer.offer(msg("t", "2"));
            offerResult.set(result);
            interruptFlagSet.set(Thread.currentThread().isInterrupted());
            done.countDown();
        });
        producer.start();

        assertTrue(aboutToBlock.await(1, TimeUnit.SECONDS));
        Thread.sleep(100); // let the producer reach queue.put(..) and block
        producer.interrupt();

        assertTrue(done.await(1, TimeUnit.SECONDS), "producer should return promptly after interruption");
        producer.join(1000);

        assertFalse(offerResult.get(), "interrupted offer must return false");
        assertTrue(interruptFlagSet.get(), "interrupt flag must be restored on the thread");
    }

    // --- Drain ---

    @Test
    @DisplayName("Drain returns up to batchSize messages")
    void testDrainBatchSize() {
        MessageBuffer buffer = new MessageBuffer(10, Strategy.RING);

        for (int i = 0; i < 5; i++) {
            buffer.offer(msg("t", String.valueOf(i)));
        }

        List<MqttMessage> batch = buffer.drain(3);
        assertEquals(3, batch.size());
        assertEquals(2, buffer.size()); // 5 - 3 = 2
    }

    @Test
    @DisplayName("Drain on empty buffer returns empty list")
    void testDrainEmpty() {
        MessageBuffer buffer = new MessageBuffer(10, Strategy.RING);

        List<MqttMessage> batch = buffer.drain(10);
        assertNotNull(batch);
        assertTrue(batch.isEmpty());
    }

    @Test
    @DisplayName("Drain returns all remaining messages if fewer than batchSize")
    void testDrainLessThanBatch() {
        MessageBuffer buffer = new MessageBuffer(10, Strategy.RING);

        buffer.offer(msg("t", "1"));
        buffer.offer(msg("t", "2"));

        List<MqttMessage> batch = buffer.drain(100);
        assertEquals(2, batch.size());
        assertEquals(0, buffer.size());
    }

    // --- Clear ---

    @Test
    @DisplayName("Clear empties the buffer")
    void testClear() {
        MessageBuffer buffer = new MessageBuffer(10, Strategy.RING);

        buffer.offer(msg("t", "1"));
        buffer.offer(msg("t", "2"));
        assertEquals(2, buffer.size());

        buffer.clear();
        assertEquals(0, buffer.size());
    }

    @Test
    @DisplayName("Clear does not reset the cumulative accepted/dropped counters")
    void testClearDoesNotResetCounters() {
        MessageBuffer buffer = new MessageBuffer(1, Strategy.DROP_INCOMING);

        buffer.offer(msg("t", "1"));
        buffer.offer(msg("t", "2")); // rejected, counted as dropped
        assertEquals(1, buffer.acceptedCount());
        assertEquals(1, buffer.droppedCount());

        buffer.clear();

        assertEquals(0, buffer.size());
        assertEquals(1, buffer.acceptedCount());
        assertEquals(1, buffer.droppedCount());
    }

    // --- Capacity and strategy accessors ---

    @Test
    @DisplayName("Capacity and strategy are accessible")
    void testAccessors() {
        MessageBuffer ring = new MessageBuffer(42, Strategy.RING);
        assertEquals(42, ring.capacity());
        assertEquals(Strategy.RING, ring.strategy());

        MessageBuffer dropIncoming = new MessageBuffer(7, Strategy.DROP_INCOMING);
        assertEquals(7, dropIncoming.capacity());
        assertEquals(Strategy.DROP_INCOMING, dropIncoming.strategy());
    }

    // --- fromConfigValue ---

    @Test
    @DisplayName("fromConfigValue maps all documented spellings")
    void testFromConfigValueMapsDocumentedSpellings() {
        assertEquals(Strategy.RING, Strategy.fromConfigValue("ring-buffer"));
        assertEquals(Strategy.DROP_INCOMING, Strategy.fromConfigValue("drop-incoming"));
        assertEquals(Strategy.BLOCKING, Strategy.fromConfigValue("blocking-queue"));
    }

    @Test
    @DisplayName("fromConfigValue fails fast on an unknown value, naming key, value and accepted set")
    void testFromConfigValueRejectsUnknownValue() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> Strategy.fromConfigValue("overflow-action"));

        String message = ex.getMessage();
        assertTrue(message.contains("buffer.strategy"), "message should name the config key");
        assertTrue(message.contains("overflow-action"), "message should name the offending value");
        assertTrue(message.contains("ring-buffer"), "message should list the accepted values");
        assertTrue(message.contains("drop-incoming"), "message should list the accepted values");
        assertTrue(message.contains("blocking-queue"), "message should list the accepted values");
    }

    // --- Concurrent producers ---

    @Test
    @DisplayName("RING: concurrent producers do not lose messages")
    void testConcurrentProducersRing() throws Exception {
        int capacity = 1000;
        int producers = 4;
        int messagesPerProducer = 500;
        MessageBuffer buffer = new MessageBuffer(capacity, Strategy.RING);

        ExecutorService executor = Executors.newFixedThreadPool(producers);
        CountDownLatch latch = new CountDownLatch(producers);

        for (int p = 0; p < producers; p++) {
            final int producerId = p;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < messagesPerProducer; i++) {
                        buffer.offer(msg("t/" + producerId, String.valueOf(i)));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Buffer should be at capacity (ring drops oldest)
        assertTrue(buffer.size() <= capacity, "Buffer should not exceed capacity");

        int totalOffered = producers * messagesPerProducer;
        assertEquals(totalOffered, buffer.acceptedCount(), "RING always accepts");
        // Every insert either fits or evicts exactly one message, so
        // accepted - dropped must equal the final buffer size.
        assertEquals(buffer.size(), buffer.acceptedCount() - buffer.droppedCount(),
            "accepted minus dropped must equal the final buffer size");

        // Drain all and verify no corruption
        List<MqttMessage> all = buffer.drain(capacity + 100);
        assertTrue(all.size() > 0, "Should have messages");
        for (MqttMessage msg : all) {
            assertNotNull(msg.getTopic());
            assertNotNull(msg.getPayload());
        }
    }

    @Test
    @DisplayName("DROP_INCOMING: concurrent producers respect capacity")
    void testConcurrentProducersDropIncoming() throws Exception {
        int capacity = 100;
        int producers = 4;
        int messagesPerProducer = 100;
        MessageBuffer buffer = new MessageBuffer(capacity, Strategy.DROP_INCOMING);

        ExecutorService executor = Executors.newFixedThreadPool(producers);
        CountDownLatch latch = new CountDownLatch(producers);
        List<Boolean> results = Collections.synchronizedList(new ArrayList<>());

        for (int p = 0; p < producers; p++) {
            final int producerId = p;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < messagesPerProducer; i++) {
                        results.add(buffer.offer(msg("t/" + producerId, String.valueOf(i))));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Some messages should have been rejected
        long accepted = results.stream().filter(r -> r).count();
        long rejected = results.stream().filter(r -> !r).count();

        assertEquals(capacity, accepted, "Only capacity messages should be accepted");
        assertTrue(rejected > 0, "Some messages should have been rejected");
        assertEquals(capacity, buffer.size());

        int totalOffered = producers * messagesPerProducer;
        assertEquals(accepted, buffer.acceptedCount());
        assertEquals(rejected, buffer.droppedCount());
        assertEquals(totalOffered, buffer.acceptedCount() + buffer.droppedCount());
    }

    @Test
    @DisplayName("BLOCKING: concurrent producers never lose messages and stay within capacity")
    void testConcurrentProducersBlocking() throws Exception {
        int capacity = 50;
        int producers = 4;
        int messagesPerProducer = 50;
        int totalOffered = producers * messagesPerProducer;
        MessageBuffer buffer = new MessageBuffer(capacity, Strategy.BLOCKING);

        ExecutorService executor = Executors.newFixedThreadPool(producers);
        CountDownLatch latch = new CountDownLatch(producers);

        // A consumer draining concurrently so producers eventually unblock.
        AtomicBoolean keepDraining = new AtomicBoolean(true);
        Thread consumer = new Thread(() -> {
            while (keepDraining.get() || buffer.size() > 0) {
                if (buffer.drain(10).isEmpty()) {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
        consumer.start();

        for (int p = 0; p < producers; p++) {
            final int producerId = p;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < messagesPerProducer; i++) {
                        buffer.offer(msg("t/" + producerId, String.valueOf(i)));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "all producers should complete without deadlock");
        executor.shutdown();
        keepDraining.set(false);
        consumer.join(2000);

        assertTrue(buffer.size() <= capacity, "Buffer should not exceed capacity");
        assertEquals(totalOffered, buffer.acceptedCount(), "BLOCKING never drops under normal operation");
        assertEquals(0, buffer.droppedCount());
    }
}
