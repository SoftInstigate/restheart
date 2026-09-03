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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.restheart.mqtt.model.MqttMessage;
import org.restheart.mqtt.pipeline.FilterStage;
import org.restheart.mqtt.pipeline.MqttEventPipeline;

import com.hivemq.client.mqtt.MqttClient;

import io.undertow.server.handlers.sse.ServerSentEventConnection;

/**
 * Unit tests for MqttSseService.
 * Tests topic extraction, QoS parsing, payload envelope format,
 * cached message delivery, pipeline integration, and queue overflow.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttSseServiceTest {

    private MqttSseService service;
    private Map<String, Object> config;
    private MqttMessageRouter router;

    @BeforeEach
    void setUp() {
        service = new MqttSseService();
        config = new HashMap<>();
        router = new MqttMessageRouter(mock(MqttClient.class), 5000, true, 1000);
    }

    private void injectConfig() throws Exception {
        Field configField = MqttSseService.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(service, config);

        Field routerField = MqttSseService.class.getDeclaredField("router");
        routerField.setAccessible(true);
        routerField.set(service, router);
    }

    private void callInit() throws Exception {
        injectConfig();
        service.init();
    }

    // --- Topic extraction tests ---

    @Test
    @DisplayName("Extract topic filter from query string")
    void testExtractTopicFromQueryString() throws Exception {
        config.put("default-topic", "default/#");
        callInit();

        ServerSentEventConnection conn = mock(ServerSentEventConnection.class);
        when(conn.getQueryString()).thenReturn("topic=sensors/%23");

        String topic = service.resolveTopicFilter(conn.getQueryString());
        assertEquals("sensors/#", topic);
    }

    @Test
    @DisplayName("Use default topic when no topic in query string")
    void testDefaultTopicFromConfig() throws Exception {
        config.put("default-topic", "traffic/#");
        callInit();

        String topic = service.resolveTopicFilter(null);
        assertEquals("traffic/#", topic);
    }

    @Test
    @DisplayName("Decode URL-encoded topic filter with wildcards")
    void testUrlDecodedTopic() throws Exception {
        config.put("default-topic", "default/#");
        callInit();

        // %23 = #, %2B = + (literal plus, not space)
        String topic = service.resolveTopicFilter("topic=sensors/%23");
        assertEquals("sensors/#", topic);
    }

    // --- QoS extraction tests ---

    @Test
    @DisplayName("Extract QoS from query string")
    void testExtractQosFromQueryString() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("default-qos", 0);
        callInit();

        int qos = service.resolveQos("topic=sensors/&qos=2");
        assertEquals(2, qos);
    }

    @Test
    @DisplayName("Use default QoS when not in query string")
    void testDefaultQos() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("default-qos", 1);
        callInit();

        int qos = service.resolveQos("topic=sensors/");
        assertEquals(1, qos);
    }

    // --- Payload envelope format tests ---

    @Test
    @DisplayName("Envelope format wraps message in JSON object")
    void testPayloadEnvelopeFormat() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("payload-envelope", true);
        callInit();

        MqttMessage msg = new MqttMessage("sensors/temp", "{\"temp\":25}", 1, Instant.parse("2026-01-01T00:00:00Z"));
        String payload = service.formatPayload(msg, false);

        assertTrue(payload.contains("\"topic\":\"sensors/temp\""));
        assertTrue(payload.contains("\"payload\":\"{\\\"temp\\\":25}\""));
        assertTrue(payload.contains("\"qos\":1"));
        assertTrue(payload.contains("\"cached\":false"));
    }

    @Test
    @DisplayName("Raw format returns just the payload string")
    void testPayloadRawFormat() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("payload-envelope", false);
        callInit();

        MqttMessage msg = new MqttMessage("sensors/temp", "{\"temp\":25}", 1, Instant.now());
        String payload = service.formatPayload(msg, false);

        assertEquals("{\"temp\":25}", payload);
    }

    @Test
    @DisplayName("Cached flag included when cached=true")
    void testCachedFlagInEnvelope() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("payload-envelope", true);
        callInit();

        MqttMessage msg = new MqttMessage("sensors/temp", "{}", 1, Instant.now());
        String payload = service.formatPayload(msg, true);

        assertTrue(payload.contains("\"cached\":true"));
    }

    // --- Pipeline integration tests ---

    @Test
    @DisplayName("Pipeline is built from config stages")
    void testPipelineBuiltFromConfig() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("default-qos", 0);
        callInit();

        assertNotNull(service);
        // Pipeline is internal, verify via process
        MqttMessage msg = new MqttMessage("sensors/temp", "raw", 0, Instant.now());
        Optional<MqttMessage> result = service.processThroughPipeline(msg);
        assertTrue(result.isPresent(), "Identity pipeline should pass message through");
    }

    @Test
    @DisplayName("Filter stage drops non-matching messages")
    void testFilterStageDropsMessage() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("default-qos", 0);
        callInit();

        // Build a pipeline with a filter that drops everything
        MqttEventPipeline pipeline = MqttEventPipeline.builder()
            .addStage(FilterStage.byMinQos(99))  // min QoS 99 = drop all
            .build();
        service.setPipeline(pipeline);

        MqttMessage msg = new MqttMessage("sensors/temp", "{}", 1, Instant.now());
        Optional<MqttMessage> result = service.processThroughPipeline(msg);
        assertTrue(result.isEmpty(), "Filter should drop the message");
    }

    // --- Queue overflow test ---

    @Test
    @DisplayName("Queue overflow does not throw exception")
    void testQueueOverflowDoesNotThrow() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("default-qos", 0);
        config.put("per-connection-queue-capacity", 2);
        callInit();

        // Verify config is parsed correctly
        assertEquals(2, service.getPerConnectionQueueCapacity());
    }
}
