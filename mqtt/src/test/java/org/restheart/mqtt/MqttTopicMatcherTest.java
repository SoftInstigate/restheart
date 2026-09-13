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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MqttTopicMatcher}, covering MQTT 3.1.1/5.0 topic matching semantics.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttTopicMatcherTest {

    @Test
    @DisplayName("exact topics match, different topics do not")
    void testExactMatch() {
        assertTrue(MqttTopicMatcher.matches("sensors/temp", "sensors/temp"));
        assertFalse(MqttTopicMatcher.matches("sensors/temp", "sensors/humidity"));
    }

    @Test
    @DisplayName("# alone matches every (non-$-prefixed) topic")
    void testHashAloneMatchesEverything() {
        assertTrue(MqttTopicMatcher.matches("sensors/temp", "#"));
        assertTrue(MqttTopicMatcher.matches("a", "#"));
        assertTrue(MqttTopicMatcher.matches("a/b/c/d", "#"));
    }

    @Test
    @DisplayName("sport/# matches sport, sport/x and sport/x/y")
    void testTrailingHashMatchesParentLevelAndDescendants() {
        assertTrue(MqttTopicMatcher.matches("sport", "sport/#"));
        assertTrue(MqttTopicMatcher.matches("sport/x", "sport/#"));
        assertTrue(MqttTopicMatcher.matches("sport/x/y", "sport/#"));
    }

    @Test
    @DisplayName("sensors/# does not match a longer sibling level like sensorsPrivate")
    void testHashDoesNotMatchLongerSiblingLevel() {
        assertFalse(MqttTopicMatcher.matches("sensorsPrivate/secret", "sensors/#"));
        assertFalse(MqttTopicMatcher.matches("sensorsPrivate", "sensors/#"));
    }

    @Test
    @DisplayName("+ matches exactly one level")
    void testPlusMatchesExactlyOneLevel() {
        assertTrue(MqttTopicMatcher.matches("sensors/temp", "sensors/+"));
        assertTrue(MqttTopicMatcher.matches("sensors/humidity", "sensors/+"));
        assertFalse(MqttTopicMatcher.matches("sensors/temp/1", "sensors/+"));
        assertFalse(MqttTopicMatcher.matches("sensors", "sensors/+"));
        assertTrue(MqttTopicMatcher.matches("a/b", "+/+"));
    }

    @Test
    @DisplayName("+ matches an empty level")
    void testPlusMatchesEmptyLevel() {
        assertTrue(MqttTopicMatcher.matches("sensors/", "sensors/+"));
        assertTrue(MqttTopicMatcher.matches("a//b", "a/+/b"));
    }

    @Test
    @DisplayName("level counts must match exactly outside of a trailing #")
    void testLevelCountsMustMatchExactly() {
        assertFalse(MqttTopicMatcher.matches("sensors/temp/extra", "sensors/temp"));
        assertFalse(MqttTopicMatcher.matches("sensors", "sensors/temp"));
        assertFalse(MqttTopicMatcher.matches("sensors/temp", "sensors/temp/extra"));
    }

    @Test
    @DisplayName("# and +/x must not match topics whose first level begins with $")
    void testDollarPrefixedTopicsExcludedFromWildcardMatchAtFirstLevel() {
        assertFalse(MqttTopicMatcher.matches("$SYS/broker/uptime", "#"));
        assertFalse(MqttTopicMatcher.matches("$SYS/broker/uptime", "+/broker/uptime"));
        assertFalse(MqttTopicMatcher.matches("$SYS/broker/uptime", "+/+/+"));
    }

    @Test
    @DisplayName("a literal first level matching $SYS is still allowed to use wildcards further in the filter")
    void testDollarPrefixedTopicMatchesNonWildcardFirstLevel() {
        assertTrue(MqttTopicMatcher.matches("$SYS/broker/uptime", "$SYS/broker/uptime"));
        assertTrue(MqttTopicMatcher.matches("$SYS/broker/uptime", "$SYS/+/uptime"));
        assertTrue(MqttTopicMatcher.matches("$SYS/broker/uptime", "$SYS/#"));
    }

    @Test
    @DisplayName("null topic or filter never match")
    void testNullsDoNotMatch() {
        assertFalse(MqttTopicMatcher.matches(null, "sensors/#"));
        assertFalse(MqttTopicMatcher.matches("sensors/temp", null));
    }

    // --- subsumes: the filter-to-filter relation the router uses to pick a minimal covering set
    // of broker subscriptions. Overlapping subscriptions are not harmless - MQTT 3.1.1 lets a
    // broker send one copy per matching subscription, and Mosquitto does. ---

    @Test
    @DisplayName("subsumes: a wildcard filter covers what it matches")
    void testSubsumesBasicCovering() {
        assertTrue(MqttTopicMatcher.subsumes("sensors/#", "sensors/temp"));
        assertTrue(MqttTopicMatcher.subsumes("sensors/#", "sensors/temp/inner"));
        assertTrue(MqttTopicMatcher.subsumes("sensors/#", "sensors/+"));
        assertTrue(MqttTopicMatcher.subsumes("sensors/#", "sensors/#"));
        assertTrue(MqttTopicMatcher.subsumes("sensors/+", "sensors/temp"));
        assertTrue(MqttTopicMatcher.subsumes("#", "sensors/temp"));
        assertTrue(MqttTopicMatcher.subsumes("+/+", "a/b"));
    }

    @Test
    @DisplayName("subsumes: the narrower filter never covers the broader one")
    void testSubsumesIsDirectional() {
        assertFalse(MqttTopicMatcher.subsumes("sensors/temp", "sensors/#"));
        assertFalse(MqttTopicMatcher.subsumes("sensors/temp", "sensors/+"));
        assertFalse(MqttTopicMatcher.subsumes("sensors/+", "sensors/#"));
        assertFalse(MqttTopicMatcher.subsumes("sensors/+", "sensors/temp/inner"));
    }

    @Test
    @DisplayName("subsumes: unrelated filters cover nothing of each other")
    void testSubsumesUnrelated() {
        assertFalse(MqttTopicMatcher.subsumes("sensors/#", "traffic/x"));
        assertFalse(MqttTopicMatcher.subsumes("sensors/#", "sensorsPrivate/x"));
        assertFalse(MqttTopicMatcher.subsumes("a/b/c", "a/b"));
        assertFalse(MqttTopicMatcher.subsumes("a/b", "a/b/c"));
    }

    @Test
    @DisplayName("subsumes: a wildcard does not cover a filter that names a $ topic")
    void testSubsumesExcludesSystemTopics() {
        // $SYS/x matches "$SYS/#" but not "#", so dropping the $SYS subscription in favour of the
        // wildcard would silently stop delivering those messages.
        assertFalse(MqttTopicMatcher.subsumes("#", "$SYS/#"));
        assertFalse(MqttTopicMatcher.subsumes("+/#", "$SYS/broker/uptime"));
        assertTrue(MqttTopicMatcher.subsumes("$SYS/#", "$SYS/broker/uptime"));
    }

    @Test
    @DisplayName("subsumes: a trailing # covers the parent level itself")
    void testSubsumesParentLevel() {
        assertTrue(MqttTopicMatcher.subsumes("sport/#", "sport"));
        assertTrue(MqttTopicMatcher.matches("sport", "sport/#"));
    }

    @Test
    @DisplayName("subsumes: null is never a relation")
    void testSubsumesNulls() {
        assertFalse(MqttTopicMatcher.subsumes(null, "a"));
        assertFalse(MqttTopicMatcher.subsumes("a", null));
        assertFalse(MqttTopicMatcher.subsumes(null, null));
    }

    @Test
    @DisplayName("subsumes agrees with matches: anything the narrower filter matches, the broader one matches too")
    void testSubsumesAgreesWithMatches() {
        var filters = java.util.List.of("#", "+", "+/#", "a", "a/#", "a/+", "a/b", "a/b/c", "a/+/c", "$SYS/#");
        var topics = java.util.List.of("a", "b", "a/b", "a/c", "a/b/c", "a/x/c", "a/b/c/d", "$SYS/x", "$SYS/x/y");

        for (String broader : filters) {
            for (String narrower : filters) {
                if (!MqttTopicMatcher.subsumes(broader, narrower)) {
                    continue;
                }
                for (String topic : topics) {
                    if (MqttTopicMatcher.matches(topic, narrower)) {
                        assertTrue(MqttTopicMatcher.matches(topic, broader),
                            "'" + broader + "' claims to subsume '" + narrower + "' but does not match '"
                                + topic + "', which '" + narrower + "' does");
                    }
                }
            }
        }
    }
}
