package org.restheart.mqtt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MqttMessage#equals(Object)} and {@link MqttMessage#hashCode()}.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttMessageTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T00:00:00Z");

    @Test
    @DisplayName("Two messages with identical field values are equal and share a hash code")
    void testEqualContentsAreEqual() {
        MqttMessage a = new MqttMessage("sensors/temp", "{\"temp\":25}", 1, T1);
        MqttMessage b = new MqttMessage("sensors/temp", "{\"temp\":25}", 1, T1);

        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(a.hashCode(), b.hashCode(), "equal instances must have equal hash codes");
    }

    @Test
    @DisplayName("A message is equal to itself")
    void testReflexive() {
        MqttMessage a = new MqttMessage("sensors/temp", "{\"temp\":25}", 1, T1);
        assertEquals(a, a);
    }

    @Test
    @DisplayName("Differing topic makes messages unequal")
    void testDifferentTopicNotEqual() {
        MqttMessage a = new MqttMessage("sensors/temp", "{\"temp\":25}", 1, T1);
        MqttMessage b = new MqttMessage("sensors/humidity", "{\"temp\":25}", 1, T1);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("Differing payload makes messages unequal")
    void testDifferentPayloadNotEqual() {
        MqttMessage a = new MqttMessage("sensors/temp", "{\"temp\":25}", 1, T1);
        MqttMessage b = new MqttMessage("sensors/temp", "{\"temp\":26}", 1, T1);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("Differing qos makes messages unequal")
    void testDifferentQosNotEqual() {
        MqttMessage a = new MqttMessage("sensors/temp", "{\"temp\":25}", 1, T1);
        MqttMessage b = new MqttMessage("sensors/temp", "{\"temp\":25}", 2, T1);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("Differing receivedAt makes messages unequal")
    void testDifferentReceivedAtNotEqual() {
        MqttMessage a = new MqttMessage("sensors/temp", "{\"temp\":25}", 1, T1);
        MqttMessage b = new MqttMessage("sensors/temp", "{\"temp\":25}", 1, T2);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("A message is never equal to null or an instance of a different type")
    void testNotEqualToNullOrOtherType() {
        MqttMessage a = new MqttMessage("sensors/temp", "{\"temp\":25}", 1, T1);
        assertNotEquals(a, null);
        assertFalse(a.equals("sensors/temp"));
    }
}
