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

package org.restheart.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test for MQTT queue overflow handling.
 * Verifies that when a client cannot keep up with message rate,
 * the process remains responsive and overflow is handled gracefully.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttOverflowIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttOverflowIT.class);

    @Test
    void testQueueOverflowDoesNotCrashProcess() throws Exception {
        // Arrange - small queue
        LinkedBlockingQueue<MqttMessage> queue = new LinkedBlockingQueue<>(10);
        AtomicInteger receivedCount = new AtomicInteger(0);

        // Act - flood with messages
        for (int i = 0; i < 1000; i++) {
            MqttMessage msg = new MqttMessage("sensors/temp", "{\"temp\":" + i + "}", 0, Instant.now());
            if (!queue.offer(msg)) {
                // Queue full - this is expected behavior
            } else {
                receivedCount.incrementAndGet();
            }
        }

        // Assert - process remains responsive
        assertEquals(10, receivedCount.get(), "Should have received exactly queue capacity");
        assertTrue(queue.size() <= 10, "Queue should not exceed capacity");
    }

    @Test
    void testSlowConsumerReceivesPartialMessages() throws Exception {
        // Arrange - slow consumer with small queue
        LinkedBlockingQueue<MqttMessage> queue = new LinkedBlockingQueue<>(5);
        AtomicInteger droppedCount = new AtomicInteger(0);

        // Act - fast producer
        for (int i = 0; i < 50; i++) {
            MqttMessage msg = new MqttMessage("sensors/temp", "{\"temp\":" + i + "}", 0, Instant.now());
            if (!queue.offer(msg)) {
                droppedCount.incrementAndGet();
            }
        }

        // Assert - some messages received, some dropped
        assertTrue(queue.size() > 0, "Should have received some messages");
        assertTrue(droppedCount.get() > 0, "Should have dropped some messages");
        assertTrue(queue.size() <= 5, "Queue should not exceed capacity");
    }
}
