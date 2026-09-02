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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for MqttTopicAuthorizer topic matching logic.
 * Tests MQTT wildcard matching against ACL patterns.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttTopicAuthorizerTest {

    // --- isTopicAllowed ---

    @Test
    @DisplayName("Empty allowed list denies all topics")
    void testEmptyAllowedList() {
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", List.of()));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", null));
    }

    @Test
    @DisplayName("Exact match allows topic")
    void testExactMatch() {
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", List.of("sensors/temp")));
    }

    @Test
    @DisplayName("Exact match denies non-matching topic")
    void testExactMatchDenied() {
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("sensors/humidity", List.of("sensors/temp")));
    }

    @Test
    @DisplayName("Multi-level wildcard (#) allows all topics")
    void testMultiLevelWildcardAll() {
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", List.of("#")));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("anything/at/all", List.of("#")));
    }

    @Test
    @DisplayName("Multi-level wildcard (prefix/#) allows matching topics")
    void testMultiLevelWildcardPrefix() {
        List<String> allowed = List.of("sensors/#");

        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", allowed));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/room1/temp", allowed));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/a/b/c", allowed));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("traffic/flow", allowed));
    }

    @Test
    @DisplayName("Single-level wildcard (+) matches one level")
    void testSingleLevelWildcard() {
        List<String> allowed = List.of("sensors/+/temp");

        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/room1/temp", allowed));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/room2/temp", allowed));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("sensors/room1/humidity", allowed));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("sensors/a/b/temp", allowed)); // too many levels
    }

    @Test
    @DisplayName("Multiple ACL entries: any match allows")
    void testMultipleAclEntries() {
        List<String> allowed = List.of("sensors/#", "traffic/+");

        assertTrue(MqttTopicAuthorizer.isTopicAllowed("sensors/temp", allowed));
        assertTrue(MqttTopicAuthorizer.isTopicAllowed("traffic/flow", allowed));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("admin/config", allowed));
    }

    @Test
    @DisplayName("Denied topic returns false")
    void testDeniedTopic() {
        List<String> allowed = List.of("sensors/#");

        assertFalse(MqttTopicAuthorizer.isTopicAllowed("admin/config", allowed));
        assertFalse(MqttTopicAuthorizer.isTopicAllowed("internal/debug", allowed));
    }

    // --- topicMatchesPattern ---

    @Test
    @DisplayName("Pattern # matches everything")
    void testPatternHashMatchesAll() {
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("any/topic", "#"));
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("a", "#"));
    }

    @Test
    @DisplayName("Pattern sensors/# matches sensors prefix")
    void testPatternPrefixHash() {
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("sensors/temp", "sensors/#"));
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("sensors/room1/temp", "sensors/#"));
        assertFalse(MqttTopicAuthorizer.topicMatchesPattern("traffic/flow", "sensors/#"));
    }

    @Test
    @DisplayName("Pattern sensors/+/temp matches single level")
    void testPatternSingleLevel() {
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("sensors/room1/temp", "sensors/+/temp"));
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("sensors/room2/temp", "sensors/+/temp"));
        assertFalse(MqttTopicAuthorizer.topicMatchesPattern("sensors/room1/humidity", "sensors/+/temp"));
        assertFalse(MqttTopicAuthorizer.topicMatchesPattern("sensors/a/b/temp", "sensors/+/temp"));
    }

    @Test
    @DisplayName("Exact pattern match")
    void testPatternExactMatch() {
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("sensors/temp", "sensors/temp"));
        assertFalse(MqttTopicAuthorizer.topicMatchesPattern("sensors/humidity", "sensors/temp"));
    }

    @Test
    @DisplayName("Pattern with multiple wildcards")
    void testPatternMultipleWildcards() {
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("sensors/room1/temp", "sensors/+/+"));
        assertTrue(MqttTopicAuthorizer.topicMatchesPattern("a/b/c", "+/+/+"));
        assertFalse(MqttTopicAuthorizer.topicMatchesPattern("a/b", "+/+/+"));
    }
}
