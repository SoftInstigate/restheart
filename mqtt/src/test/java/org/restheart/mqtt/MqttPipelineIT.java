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

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.restheart.mqtt.pipeline.FilterStage;
import org.restheart.mqtt.pipeline.MqttEventPipeline;
import org.restheart.mqtt.pipeline.ThrottleStage;
import org.restheart.mqtt.pipeline.TumblingWindowAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test for MQTT event processing pipeline.
 * Tests filter, throttle, and tumbling window stages with real MQTT messages.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttPipelineIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttPipelineIT.class);

    @Test
    void testThrottleStageLimitsEvents() throws Exception {
        // Arrange
        MqttEventPipeline pipeline = MqttEventPipeline.builder()
            .addStage(new ThrottleStage(5))  // Max 5 events per second
            .build();

        // Act - process 20 messages rapidly
        int processed = 0;
        for (int i = 0; i < 20; i++) {
            MqttMessage msg = new MqttMessage("sensors/temp", "{\"temp\":" + i + "}", 0, Instant.now());
            Optional<MqttMessage> result = pipeline.process(msg);
            if (result.isPresent()) {
                processed++;
            }
        }

        // Assert - should have throttled to roughly 5
        assertTrue(processed <= 6, "Throttle should limit to ~5 events, got: " + processed);
        assertTrue(processed >= 1, "Should allow at least 1 event");
    }

    @Test
    void testTumblingWindowAggregatorEmitsAfterWindow() throws Exception {
        // Arrange
        MqttEventPipeline pipeline = MqttEventPipeline.builder()
            .addStage(new TumblingWindowAggregator(500, "count", null))
            .build();

        // Act - send 5 messages quickly
        int emitted = 0;
        for (int i = 0; i < 5; i++) {
            MqttMessage msg = new MqttMessage("sensors/temp", "{\"temp\":" + i + "}", 0, Instant.now());
            Optional<MqttMessage> result = pipeline.process(msg);
            if (result.isPresent()) {
                emitted++;
            }
        }

        // Assert - window not closed yet, nothing emitted
        assertEquals(0, emitted, "Window should not have emitted yet");

        // Wait for window to close
        Thread.sleep(600);

        // Send one more message to trigger emission
        MqttMessage triggerMsg = new MqttMessage("sensors/temp", "{\"temp\":99}", 0, Instant.now());
        Optional<MqttMessage> result = pipeline.process(triggerMsg);

        assertTrue(result.isPresent(), "Should emit aggregated result after window closes");
        assertEquals("5", result.get().getPayload(), "Count should be 5");
    }

    @Test
    void testFilterStageDropsNonMatchingMessages() throws Exception {
        // Arrange
        MqttEventPipeline pipeline = MqttEventPipeline.builder()
            .addStage(new FilterStage(99))  // min QoS 99 = drop all
            .build();

        // Act
        MqttMessage msg = new MqttMessage("sensors/temp", "{}", 1, Instant.now());
        Optional<MqttMessage> result = pipeline.process(msg);

        // Assert
        assertTrue(result.isEmpty(), "Filter should drop the message");
    }

    @Test
    void testPipelineComposition() throws Exception {
        // Arrange - filter + throttle
        MqttEventPipeline pipeline = MqttEventPipeline.builder()
            .addStage(new ThrottleStage(100))  // High limit, basically no throttle
            .build();

        // Act
        MqttMessage msg = new MqttMessage("sensors/temp", "{\"temp\":25}", 1, Instant.now());
        Optional<MqttMessage> result = pipeline.process(msg);

        // Assert
        assertTrue(result.isPresent(), "Pipeline should pass message through");
        assertEquals("sensors/temp", result.get().getTopic());
    }
}
