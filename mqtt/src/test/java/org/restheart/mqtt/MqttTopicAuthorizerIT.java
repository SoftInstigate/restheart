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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test for MqttTopicAuthorizer.
 * Tests topic matching with realistic IoT topic patterns and ACL configurations.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttTopicAuthorizerIT {

    // --- Realistic IoT scenarios ---

    @Test
    @DisplayName("IoT: sensor reader can read sensor topics")
    void testSensorReaderCanReadSensors() {
        List<String> readerAcl = List.of("sensors/#", "devices/+/status");

        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/temperature", readerAcl));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/room1/humidity", readerAcl));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("devices/sensor42/status", readerAcl));
    }

    @Test
    @DisplayName("IoT: sensor reader cannot access admin topics")
    void testSensorReaderCannotAccessAdmin() {
        List<String> readerAcl = List.of("sensors/#", "devices/+/status");

        assertFalse(MqttTopicAuthorizer.isTopicAllowed("admin/config", readerAcl));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("system/shutdown", readerAcl));
    }

    @Test
    @DisplayName("IoT: admin can access everything")
    void testAdminCanAccessEverything() {
        List<String> adminAcl = List.of("#");

        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", adminAcl));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("admin/config", adminAcl));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("any/topic/at/all", adminAcl));
    }

    @Test
    @DisplayName("IoT: device can only publish to its own command topic")
    void testDeviceRestrictedToOwnTopic() {
        List<String> deviceAcl = List.of("devices/sensor42/commands", "devices/sensor42/status");

        assertTrue(MqttTopicAuthorizer.isTopicAllowed("devices/sensor42/commands", deviceAcl));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("devices/sensor42/status", deviceAcl));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("devices/sensor43/commands", deviceAcl));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", deviceAcl));
    }

    @Test
    @DisplayName("IoT: traffic monitor can read traffic and sensors")
    void testTrafficMonitorMultiTopic() {
        List<String> monitorAcl = List.of("traffic/#", "sensors/+/temperature", "sensors/+/humidity");

        assertTrue(MqttTopicAuthorizer.isTopicAllowed("traffic/flow/junction42", monitorAcl));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/room1/temperature", monitorAcl));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/room2/humidity", monitorAcl));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("sensors/room1/pressure", monitorAcl));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("admin/debug", monitorAcl));
    }

    @Test
    @DisplayName("IoT: building automation with hierarchical topics")
    void testBuildingAutomationHierarchy() {
        List<String> hvacAcl = List.of("building/+/hvac/#", "building/+/temperature");

        assertTrue(MqttTopicAuthorizer.isTopicAllowed("building/floor1/hvac/zone1", hvacAcl));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("building/floor1/hvac/zone2/setpoint", hvacAcl));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("building/floor2/temperature", hvacAcl));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("building/floor1/lighting", hvacAcl));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", hvacAcl));
    }

    @Test
    @DisplayName("Empty ACL denies all topics")
    void testEmptyAclDeniesAll() {
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", List.of()));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", null));
    }

    @Test
    @DisplayName("Topic matching handles edge cases")
    void testEdgeCases() {
        // Empty topic
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("", List.of("sensors/#")));

        // Topic with special characters
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("sensors/temp-001", "sensors/temp-001"));
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("sensors/temp_001", "sensors/+"));

        // Deep nesting
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("a/b/c/d/e/f/g", "a/b/c/d/e/f/g"));
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("a/b/c/d/e/f/g", "a/#"));
    }
}
