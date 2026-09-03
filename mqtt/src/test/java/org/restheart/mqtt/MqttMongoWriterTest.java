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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.time.Instant;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.restheart.mqtt.model.MqttMessage;

/**
 * Unit tests for MqttMongoWriter.
 * Tests document conversion, ID strategies, and topic matching.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttMongoWriterTest {

    private MqttMessage msg(String topic, String payload, int qos) {
        return new MqttMessage(topic, payload, qos, Instant.parse("2026-01-01T00:00:00Z"));
    }

    // --- constructor injection ---

    @Test
    @DisplayName("Package-private constructor injects the given router")
    void testConstructorInjectsRouter() throws Exception {
        MqttMessageRouter mockRouter = mock(MqttMessageRouter.class);
        MqttMongoWriter writer = new MqttMongoWriter(mockRouter);

        Field routerField = MqttMongoWriter.class.getDeclaredField("router");
        routerField.setAccessible(true);
        assertSame(mockRouter, routerField.get(writer));
    }

    // --- toDocument ---

    @Test
    @DisplayName("toDocument creates correct document structure")
    void testToDocumentStructure() {
        MqttMongoWriter writer = new MqttMongoWriter();
        MqttMessage message = msg("sensors/temp", "{\"temp\":25}", 1);

        Document doc = writer.toDocument(message);

        assertEquals("sensors/temp", doc.getString("topic"));
        assertEquals("{\"temp\":25}", doc.getString("payload"));
        assertEquals("2026-01-01T00:00:00Z", doc.getString("receivedAt"));
        assertEquals(1, doc.getInteger("qos"));
    }

    @Test
    @DisplayName("ID strategy 'auto' does not set _id")
    void testIdStrategyAuto() {
        MqttMongoWriter writer = new MqttMongoWriter();
        MqttMessage message = msg("sensors/temp", "{\"temp\":25}", 1);

        Document doc = writer.toDocument(message);

        assertNull(doc.get("_id"), "auto strategy should not set _id");
    }

    @Test
    @DisplayName("ID strategy 'payload-field' extracts field from JSON payload")
    void testIdStrategyPayloadField() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        // Set idStrategy via reflection
        var field = MqttMongoWriter.class.getDeclaredField("idStrategy");
        field.setAccessible(true);
        field.set(writer, "payload-field");

        var idFieldField = MqttMongoWriter.class.getDeclaredField("idField");
        idFieldField.setAccessible(true);
        idFieldField.set(writer, "messageId");

        MqttMessage message = msg("sensors/temp", "{\"messageId\":\"msg-001\",\"temp\":25}", 1);
        Document doc = writer.toDocument(message);

        assertEquals("msg-001", doc.get("_id"));
    }

    @Test
    @DisplayName("ID strategy 'payload-field' falls back when field missing")
    void testIdStrategyPayloadFieldMissing() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        var field = MqttMongoWriter.class.getDeclaredField("idStrategy");
        field.setAccessible(true);
        field.set(writer, "payload-field");

        var idFieldField = MqttMongoWriter.class.getDeclaredField("idField");
        idFieldField.setAccessible(true);
        idFieldField.set(writer, "messageId");

        MqttMessage message = msg("sensors/temp", "{\"temp\":25}", 1);
        Document doc = writer.toDocument(message);

        assertNull(doc.get("_id"), "Should fall back to auto when field missing");
    }

    @Test
    @DisplayName("ID strategy 'topic-timestamp-hash' generates deterministic hash")
    void testIdStrategyTopicTimestampHash() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        var field = MqttMongoWriter.class.getDeclaredField("idStrategy");
        field.setAccessible(true);
        field.set(writer, "topic-timestamp-hash");

        MqttMessage message = msg("sensors/temp", "{\"temp\":25}", 1);
        Document doc = writer.toDocument(message);

        String id = doc.getString("_id");
        assertNotNull(id);
        assertEquals(24, id.length(), "Hash should be 24 hex chars (12 bytes)");

        // Same topic + timestamp = same hash
        MqttMessage message2 = msg("sensors/temp", "{\"temp\":30}", 1);
        Document doc2 = writer.toDocument(message2);
        assertEquals(id, doc2.getString("_id"), "Same topic+timestamp should produce same hash");
    }

    @Test
    @DisplayName("Different topics produce different hashes")
    void testDifferentTopicsDifferentHashes() {
        String hash1 = MqttMongoWriter.computeHash("sensors/temp", "2026-01-01T00:00:00Z");
        String hash2 = MqttMongoWriter.computeHash("sensors/humidity", "2026-01-01T00:00:00Z");

        assertFalse(hash1.equals(hash2), "Different topics should produce different hashes");
    }

    // --- topicMatches ---

    @Test
    @DisplayName("topicMatches: exact match")
    void testTopicMatchesExact() {
        assertTrue(MqttMongoWriter.topicMatches("sensors/temp", "sensors/temp"));
        assertFalse(MqttMongoWriter.topicMatches("sensors/humidity", "sensors/temp"));
    }

    @Test
    @DisplayName("topicMatches: # wildcard matches all")
    void testTopicMatchesHash() {
        assertTrue(MqttMongoWriter.topicMatches("any/topic", "#"));
    }

    @Test
    @DisplayName("topicMatches: prefix/# wildcard")
    void testTopicMatchesPrefixHash() {
        assertTrue(MqttMongoWriter.topicMatches("sensors/temp", "sensors/#"));
        assertTrue(MqttMongoWriter.topicMatches("sensors/room1/temp", "sensors/#"));
        assertFalse(MqttMongoWriter.topicMatches("traffic/flow", "sensors/#"));
    }

    @Test
    @DisplayName("topicMatches: prefix/+ wildcard")
    void testTopicMatchesPrefixPlus() {
        assertTrue(MqttMongoWriter.topicMatches("sensors/temp", "sensors/+"));
        assertFalse(MqttMongoWriter.topicMatches("sensors/room1/temp", "sensors/+"));
        assertFalse(MqttMongoWriter.topicMatches("traffic/flow", "sensors/+"));
    }
}
