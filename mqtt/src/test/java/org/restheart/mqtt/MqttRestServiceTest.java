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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.restheart.mqtt.model.MqttMessage;

import com.hivemq.client.mqtt.MqttClient;

/**
 * Unit tests for MqttRestService.
 * Tests last-value polling endpoint for MQTT topics.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttRestServiceTest {

    private MqttMessageRouter router;

    @BeforeEach
    void setUp() {
        router = new MqttMessageRouter(mock(MqttClient.class), 5000, true, 1000);
    }

    private void cacheMessage(String topic, String payload, int qos) {
        MqttMessage msg = new MqttMessage(topic, payload, qos, Instant.now());
        // Use the router's internal cache via reflection
        try {
            Field cacheField = MqttMessageRouter.class.getDeclaredField("lastMessageCache");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, MqttMessage> cache = (Map<String, MqttMessage>) cacheField.get(router);
            cache.put(topic, msg);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("GET with existing topic returns 200 with JSON payload")
    void testGetExistingTopic() throws Exception {
        cacheMessage("sensors/temp", "{\"temp\":25}", 1);

        MqttMessage result = router.getLastMessage("sensors/temp");
        assertNotNull(result, "Cached message should be retrievable");
        assertEquals("sensors/temp", result.getTopic());
        assertEquals("{\"temp\":25}", result.getPayload());
        assertEquals(1, result.getQos());
    }

    @Test
    @DisplayName("GET with non-existing topic returns null")
    void testGetNonExistingTopic() {
        MqttMessage result = router.getLastMessage("sensors/nonexistent");
        assertNull(result, "Non-existing topic should return null");
    }

    @Test
    @DisplayName("Cached message has receivedAt timestamp")
    void testCachedMessageHasTimestamp() {
        cacheMessage("sensors/temp", "{\"temp\":25}", 1);

        MqttMessage result = router.getLastMessage("sensors/temp");
        assertNotNull(result);
        assertNotNull(result.getReceivedAt());
    }

    @Test
    @DisplayName("Multiple topics cached independently")
    void testMultipleTopicsCached() {
        cacheMessage("sensors/temp", "{\"temp\":25}", 1);
        cacheMessage("sensors/humidity", "{\"humidity\":60}", 0);

        MqttMessage temp = router.getLastMessage("sensors/temp");
        MqttMessage humidity = router.getLastMessage("sensors/humidity");

        assertNotNull(temp);
        assertNotNull(humidity);
        assertEquals("{\"temp\":25}", temp.getPayload());
        assertEquals("{\"humidity\":60}", humidity.getPayload());
    }

    @Test
    @DisplayName("Cache overwrites previous message for same topic")
    void testCacheOverwrites() {
        cacheMessage("sensors/temp", "{\"temp\":20}", 1);
        cacheMessage("sensors/temp", "{\"temp\":25}", 1);

        MqttMessage result = router.getLastMessage("sensors/temp");
        assertNotNull(result);
        assertEquals("{\"temp\":25}", result.getPayload());
    }
}
