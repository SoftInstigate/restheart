package org.restheart.mqtt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    // --- An MQTT payload is arbitrary bytes. The module used to do new String(bytes, UTF_8) on
    // reception, which substitutes U+FFFD for anything it cannot decode - silently, irreversibly,
    // and before any consumer or the database could see the original. Protobuf, CBOR, an image,
    // anything compressed: destroyed at the door. ---

    /** 0x80 is a continuation byte with no lead byte: never valid UTF-8. */
    private static final byte[] NOT_UTF8 = new byte[] { (byte) 0x80, (byte) 0xFF, 0x00, (byte) 0xFE };

    @Test
    @DisplayName("a payload that is not valid UTF-8 is kept byte for byte")
    void testBinaryPayloadIsPreservedExactly() {
        var msg = new MqttMessage("sensors/raw", NOT_UTF8, 1, Instant.now(), false);

        assertFalse(msg.isTextPayload(), "these bytes are not valid UTF-8");
        assertNull(msg.getPayload(), "there is no text to return, and a lossy guess would be worse");
        assertArrayEquals(NOT_UTF8, msg.getPayloadBytes(), "the bytes must survive untouched");
    }

    @Test
    @DisplayName("a non-text payload is offered as base64 for the surfaces that only carry text")
    void testBinaryPayloadAsBase64() {
        var msg = new MqttMessage("sensors/raw", NOT_UTF8, 1, Instant.now(), false);

        assertEquals(java.util.Base64.getEncoder().encodeToString(NOT_UTF8), msg.getPayloadAsBase64());
        assertArrayEquals(NOT_UTF8, java.util.Base64.getDecoder().decode(msg.getPayloadAsBase64()),
            "base64 must round-trip, or JSON and SSE consumers cannot recover the payload");
    }

    @Test
    @DisplayName("a UTF-8 payload still reads as text, including non-ASCII")
    void testTextPayloadStillWorks() {
        var msg = new MqttMessage("sensors/temp", "{\"città\":\"Milano ✓\"}", 1, Instant.now(), false);

        assertTrue(msg.isTextPayload());
        assertEquals("{\"città\":\"Milano ✓\"}", msg.getPayload());
    }

    @Test
    @DisplayName("the payload array is copied in and out, so the message stays immutable")
    void testPayloadIsDefensivelyCopied() {
        byte[] mutable = { 1, 2, 3 };
        var msg = new MqttMessage("sensors/raw", mutable, 1, Instant.now(), false);

        mutable[0] = 99;
        assertEquals(1, msg.getPayloadBytes()[0], "mutating the caller's array must not change the message");

        msg.getPayloadBytes()[0] = 42;
        assertEquals(1, msg.getPayloadBytes()[0], "mutating a returned array must not change the message");
    }

    @Test
    @DisplayName("equality compares payload contents, not array identity")
    void testEqualityOnPayloadContents() {
        var a = new MqttMessage("t", new byte[] { 1, 2 }, 1, Instant.EPOCH, false);
        var b = new MqttMessage("t", new byte[] { 1, 2 }, 1, Instant.EPOCH, false);
        var c = new MqttMessage("t", new byte[] { 1, 3 }, 1, Instant.EPOCH, false);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    @DisplayName("toString does not dump binary payloads as mojibake")
    void testToStringOnBinaryPayload() {
        var msg = new MqttMessage("sensors/raw", NOT_UTF8, 1, Instant.now(), false);
        assertTrue(msg.toString().contains("4 bytes"), msg.toString());
    }
}
