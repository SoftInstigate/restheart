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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.restheart.mqtt.buffer.MessageBuffer.Strategy;
import org.restheart.mqtt.model.MqttMessage;

/**
 * Unit tests for MessageBuffer.
 * Tests both RING and BLOCKING strategies, capacity boundaries,
 * concurrent producers, and drain behavior.
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
        assertThrows(IllegalArgumentException.class, () -> new MessageBuffer(-1, Strategy.BLOCKING));
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
    @DisplayName("BLOCKING: rejects when full")
    void testBlockingRejectsWhenFull() {
        MessageBuffer buffer = new MessageBuffer(2, Strategy.BLOCKING);

        assertTrue(buffer.offer(msg("t", "1")));
        assertTrue(buffer.offer(msg("t", "2")));
        assertFalse(buffer.offer(msg("t", "3"))); // rejected
        assertEquals(2, buffer.size());
    }

    @Test
    @DisplayName("BLOCKING: rejected message is not stored")
    void testBlockingRejectedNotStored() {
        MessageBuffer buffer = new MessageBuffer(1, Strategy.BLOCKING);

        buffer.offer(msg("t", "kept"));
        buffer.offer(msg("t", "rejected"));

        List<MqttMessage> drained = buffer.drain(10);
        assertEquals(1, drained.size());
        assertEquals("kept", drained.get(0).getPayload());
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

    // --- Capacity and strategy accessors ---

    @Test
    @DisplayName("Capacity and strategy are accessible")
    void testAccessors() {
        MessageBuffer ring = new MessageBuffer(42, Strategy.RING);
        assertEquals(42, ring.capacity());
        assertEquals(Strategy.RING, ring.strategy());

        MessageBuffer blocking = new MessageBuffer(7, Strategy.BLOCKING);
        assertEquals(7, blocking.capacity());
        assertEquals(Strategy.BLOCKING, blocking.strategy());
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

        // Drain all and verify no corruption
        List<MqttMessage> all = buffer.drain(capacity + 100);
        assertTrue(all.size() > 0, "Should have messages");
        for (MqttMessage msg : all) {
            assertNotNull(msg.getTopic());
            assertNotNull(msg.getPayload());
        }
    }

    @Test
    @DisplayName("BLOCKING: concurrent producers respect capacity")
    void testConcurrentProducersBlocking() throws Exception {
        int capacity = 100;
        int producers = 4;
        int messagesPerProducer = 100;
        MessageBuffer buffer = new MessageBuffer(capacity, Strategy.BLOCKING);

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
    }
}
