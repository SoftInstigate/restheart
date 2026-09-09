package org.restheart.mqtt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Qos}.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class QosTest {

    @Test
    @DisplayName("code() returns the wire value each constant is named after")
    void testCodeMatchesWireValue() {
        assertEquals(0, Qos.AT_MOST_ONCE.code());
        assertEquals(1, Qos.AT_LEAST_ONCE.code());
        assertEquals(2, Qos.EXACTLY_ONCE.code());
    }

    @Test
    @DisplayName("fromCode() resolves each of the three valid wire values")
    void testFromCodeResolvesValidValues() {
        assertSame(Qos.AT_MOST_ONCE, Qos.fromCode(0));
        assertSame(Qos.AT_LEAST_ONCE, Qos.fromCode(1));
        assertSame(Qos.EXACTLY_ONCE, Qos.fromCode(2));
    }

    @Test
    @DisplayName("fromCode() returns null, not an exception, for a negative code")
    void testFromCodeReturnsNullForNegativeCode() {
        assertNull(Qos.fromCode(-1));
    }

    @Test
    @DisplayName("fromCode() returns null, not an exception, for a code above the valid range")
    void testFromCodeReturnsNullForOutOfRangeCode() {
        assertNull(Qos.fromCode(3));
        assertNull(Qos.fromCode(99));
    }

    @Test
    @DisplayName("fromCode() round-trips with code() for every constant")
    void testFromCodeRoundTripsWithCode() {
        for (Qos qos : Qos.values()) {
            assertSame(qos, Qos.fromCode(qos.code()));
        }
    }
}
