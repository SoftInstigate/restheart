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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.restheart.metrics.MetricNameAndLabels;
import org.restheart.metrics.Metrics;
import org.restheart.mqtt.buffer.MessageBuffer;
import org.restheart.mqtt.buffer.MessageBuffer.Pending;
import org.restheart.mqtt.buffer.MessageBuffer.Strategy;
import org.restheart.mqtt.model.MqttMessage;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.WriteModel;

/**
 * Unit tests for MqttMongoWriter.
 * Tests plugin registration, document conversion, id strategies, topic matching,
 * the MongoDB write strategy (insertMany vs. upserting bulkWrite), retry/dead-letter
 * behaviour, the drain loop, and the writer's relationship to the injected MongoClient.
 * <p>
 * No MongoDB instance is required: {@link MongoClient}, {@link MongoDatabase} and
 * {@link MongoCollection} are mocked with Mockito.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttMongoWriterTest {

    private MqttMessage msg(String topic, String payload, int qos) {
        return new MqttMessage(topic, payload, qos, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private MqttMessage msg(String topic, String payload, int qos, Instant receivedAt) {
        return new MqttMessage(topic, payload, qos, receivedAt);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = MqttMongoWriter.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field f = MqttMongoWriter.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /**
     * Builds a mocked mclient/db/collection chain and wires the writer's fields required to
     * exercise {@link MqttMongoWriter#flush()} without a real MongoDB.
     */
    @SuppressWarnings("unchecked")
    private MongoCollection<Document> wireWriterForFlush(MqttMongoWriter writer, String idStrategy,
            int batchSize, int maxRetries, long retryDelayMs, String deadLetterFile) throws Exception {
        MongoClient mclient = mock(MongoClient.class);
        MongoDatabase db = mock(MongoDatabase.class);
        MongoCollection<Document> coll = mock(MongoCollection.class);
        when(mclient.getDatabase("db")).thenReturn(db);
        when(db.getCollection("coll")).thenReturn(coll);

        setField(writer, "mclient", mclient);
        setField(writer, "idStrategy", idStrategy);
        setField(writer, "idField", "messageId");
        setField(writer, "batchSize", batchSize);
        setField(writer, "maxRetries", maxRetries);
        setField(writer, "retryDelayMs", retryDelayMs);
        setField(writer, "deadLetterFile", deadLetterFile);
        setField(writer, "sinks", List.of(new MqttMongoWriter.MongoSink("sensors/#", "db", "coll")));
        return coll;
    }

    private MongoBulkWriteException bulkWriteException(BulkWriteError... errors) {
        return new MongoBulkWriteException(
            BulkWriteResult.unacknowledged(),
            Arrays.asList(errors),
            null,
            new ServerAddress(),
            Set.of());
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

    // --- plugin registration (regression test for finding C1: the writer must actually run) ---

    @Test
    @DisplayName("MqttMongoWriter implements Initializer and is registered for AFTER_STARTUP")
    void testImplementsInitializerWithAfterStartup() {
        assertTrue(Initializer.class.isAssignableFrom(MqttMongoWriter.class),
            "MqttMongoWriter must implement Initializer, otherwise PluginsScanner never instantiates it "
                + "and @Inject/@OnInit never fire");

        RegisterPlugin registerPlugin = MqttMongoWriter.class.getAnnotation(RegisterPlugin.class);
        assertNotNull(registerPlugin, "MqttMongoWriter must carry @RegisterPlugin");
        assertEquals(InitPoint.AFTER_STARTUP, registerPlugin.initPoint());
    }

    @Test
    @DisplayName("The @OnInit method is distinct from Initializer.init()")
    void testOnInitMethodDistinctFromInitializerInit() throws Exception {
        List<Method> onInitMethods = Arrays.stream(MqttMongoWriter.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(OnInit.class))
            .toList();

        assertEquals(1, onInitMethods.size(), "Exactly one method should be annotated @OnInit");
        Method onInit = onInitMethods.get(0);
        assertNotEquals("init", onInit.getName(),
            "The @OnInit method must not be named init(), since Initializer already declares init()");

        // init() overrides Initializer.init() and is not itself annotated @OnInit
        Method init = MqttMongoWriter.class.getMethod("init");
        assertFalse(init.isAnnotationPresent(OnInit.class));
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
        setField(writer, "idStrategy", "payload-field");
        setField(writer, "idField", "messageId");

        MqttMessage message = msg("sensors/temp", "{\"messageId\":\"msg-001\",\"temp\":25}", 1);
        Document doc = writer.toDocument(message);

        assertEquals("msg-001", doc.get("_id"));
    }

    @Test
    @DisplayName("ID strategy 'payload-field' falls back when field missing")
    void testIdStrategyPayloadFieldMissing() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        setField(writer, "idStrategy", "payload-field");
        setField(writer, "idField", "messageId");

        MqttMessage message = msg("sensors/temp", "{\"temp\":25}", 1);
        Document doc = writer.toDocument(message);

        assertNull(doc.get("_id"), "Should fall back to auto when field missing");
    }

    @Test
    @DisplayName("ID strategy 'topic-timestamp-hash' generates deterministic hash")
    void testIdStrategyTopicTimestampHash() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        setField(writer, "idStrategy", "topic-timestamp-hash");

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

    // --- id-strategy / id-field validation at onInit (an unknown id-strategy must not silently
    // disable deduplication) ---

    @Test
    @DisplayName("onInit() rejects an unknown id-strategy value, naming the key, the offending value and the accepted set")
    void testOnInitRejectsUnknownIdStrategy() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter(mock(MqttMessageRouter.class));
        Map<String, Object> config = Map.of(
            "id-strategy", "paylod-field", // typo, must not be silently accepted
            "mongo-sink", List.of());
        setField(writer, "config", config);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, writer::onInit);
        assertTrue(ex.getMessage().contains("id-strategy"), "message should name the offending key");
        assertTrue(ex.getMessage().contains("paylod-field"), "message should name the offending value");
        assertTrue(ex.getMessage().contains("payload-field") && ex.getMessage().contains("topic-timestamp-hash")
            && ex.getMessage().contains("auto"), "message should list the accepted values");
    }

    @Test
    @DisplayName("onInit() accepts each of the three documented id-strategy values and completes initialization")
    void testOnInitAcceptsValidIdStrategies() throws Exception {
        for (String value : List.of("auto", "payload-field", "topic-timestamp-hash")) {
            MqttMongoWriter writer = new MqttMongoWriter(mock(MqttMessageRouter.class));
            Map<String, Object> config = Map.of(
                "id-strategy", value,
                "id-field", "messageId",
                "mongo-sink", List.of());
            setField(writer, "config", config);

            writer.onInit();
            try {
                assertEquals(value, getField(writer, "idStrategy"));
            } finally {
                writer.close();
            }
        }
    }

    @Test
    @DisplayName("onInit() registers buffer gauges reflecting the live state of the configured buffer")
    void testOnInitRegistersBufferGauges() throws Exception {
        // Metrics.registerGauge() does not replace an already-registered gauge, so a stale
        // supplier left behind by another test's onInit() call must be cleared first.
        Metrics.removeMetric(MetricNameAndLabels.of("mqtt_buffer_capacity"));
        Metrics.removeMetric(MetricNameAndLabels.of("mqtt_buffer_size"));
        Metrics.removeMetric(MetricNameAndLabels.of("mqtt_buffer_accepted"));
        Metrics.removeMetric(MetricNameAndLabels.of("mqtt_buffer_dropped"));

        MqttMongoWriter writer = new MqttMongoWriter(mock(MqttMessageRouter.class));
        Map<String, Object> config = Map.of(
            "id-strategy", "auto",
            "buffer", Map.of("strategy", "ring-buffer", "capacity", 5),
            "mongo-sink", List.of());
        setField(writer, "config", config);

        writer.onInit();
        try {
            assertEquals(5, Metrics.getGaugeValue(MetricNameAndLabels.of("mqtt_buffer_capacity")));
            assertEquals(0, Metrics.getGaugeValue(MetricNameAndLabels.of("mqtt_buffer_size")));
            assertEquals(0L, Metrics.getGaugeValue(MetricNameAndLabels.of("mqtt_buffer_accepted")));
            assertEquals(0L, Metrics.getGaugeValue(MetricNameAndLabels.of("mqtt_buffer_dropped")));

            MessageBuffer buffer = (MessageBuffer) getField(writer, "buffer");
            buffer.offer(new Pending(msg("sensors/temp", "{\"temp\":1}", 0), () -> { }));

            assertEquals(1, Metrics.getGaugeValue(MetricNameAndLabels.of("mqtt_buffer_size")));
            assertEquals(1L, Metrics.getGaugeValue(MetricNameAndLabels.of("mqtt_buffer_accepted")));
        } finally {
            writer.close();
        }
    }

    @Test
    @DisplayName("onInit() rejects a blank id-field when id-strategy is payload-field")
    void testOnInitRejectsBlankIdFieldForPayloadFieldStrategy() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter(mock(MqttMessageRouter.class));
        Map<String, Object> config = Map.of(
            "id-strategy", "payload-field",
            "id-field", "   ",
            "mongo-sink", List.of());
        setField(writer, "config", config);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, writer::onInit);
        assertTrue(ex.getMessage().contains("id-field"), "message should name the offending key");
        assertTrue(ex.getMessage().contains("payload-field"), "message should explain why it matters");
    }

    @Test
    @DisplayName("A blank id-field is not validated when id-strategy does not use it")
    void testBlankIdFieldAllowedWhenIdStrategyIsNotPayloadField() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter(mock(MqttMessageRouter.class));
        Map<String, Object> config = Map.of(
            "id-strategy", "auto",
            "id-field", "",
            "mongo-sink", List.of());
        setField(writer, "config", config);

        writer.onInit();
        writer.close();
    }

    // --- mongo-sink validation at onInit (Fix 4: an incomplete or malformed entry must not be
    // silently dropped - it must fail fast, naming the missing/invalid key and the offending
    // entry, the same way an unrecognized id-strategy already does) ---

    @Test
    @DisplayName("onInit() rejects a mongo-sink entry missing the collection key, naming the key and the entry")
    void testOnInitRejectsMongoSinkEntryMissingCollection() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter(mock(MqttMessageRouter.class));
        Map<String, Object> config = Map.of(
            "id-strategy", "auto",
            "mongo-sink", List.of(Map.of("topic", "sensors/#", "database", "iot")));
        setField(writer, "config", config);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, writer::onInit);
        assertTrue(ex.getMessage().contains("collection"), "message should name the missing key");
    }

    @Test
    @DisplayName("onInit() rejects a mongo-sink entry missing the topic key")
    void testOnInitRejectsMongoSinkEntryMissingTopic() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter(mock(MqttMessageRouter.class));
        Map<String, Object> config = Map.of(
            "id-strategy", "auto",
            "mongo-sink", List.of(Map.of("database", "iot", "collection", "sensor-events")));
        setField(writer, "config", config);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, writer::onInit);
        assertTrue(ex.getMessage().contains("topic"), "message should name the missing key");
    }

    @Test
    @DisplayName("onInit() rejects a mongo-sink entry whose database is not a String")
    void testOnInitRejectsMongoSinkEntryWithNonStringDatabase() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter(mock(MqttMessageRouter.class));
        Map<String, Object> config = Map.of(
            "id-strategy", "auto",
            "mongo-sink", List.of(Map.of("topic", "sensors/#", "database", 42, "collection", "events")));
        setField(writer, "config", config);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, writer::onInit);
        assertTrue(ex.getMessage().contains("database"), "message should name the offending key");
    }

    @Test
    @DisplayName("onInit() builds a sink for each complete, well-typed mongo-sink entry")
    void testOnInitBuildsSinksFromValidMongoSinkEntries() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter(mock(MqttMessageRouter.class));
        Map<String, Object> config = Map.of(
            "id-strategy", "auto",
            "mongo-sink", List.of(
                Map.of("topic", "sensors/#", "database", "iot", "collection", "sensor-events"),
                Map.of("topic", "traffic/#", "database", "iot", "collection", "traffic-events")));
        setField(writer, "config", config);

        writer.onInit();
        try {
            @SuppressWarnings("unchecked")
            List<MqttMongoWriter.MongoSink> sinks = (List<MqttMongoWriter.MongoSink>) getField(writer, "sinks");
            assertEquals(2, sinks.size());
            assertEquals(new MqttMongoWriter.MongoSink("sensors/#", "iot", "sensor-events"), sinks.get(0));
            assertEquals(new MqttMongoWriter.MongoSink("traffic/#", "iot", "traffic-events"), sinks.get(1));
        } finally {
            writer.close();
        }
    }

    @Test
    @DisplayName("An absent mongo-sink key stays legal: no sinks are built and onInit completes")
    void testOnInitAllowsAbsentMongoSinkKey() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter(mock(MqttMessageRouter.class));
        Map<String, Object> config = Map.of("id-strategy", "auto");
        setField(writer, "config", config);

        writer.onInit();
        try {
            @SuppressWarnings("unchecked")
            List<MqttMongoWriter.MongoSink> sinks = (List<MqttMongoWriter.MongoSink>) getField(writer, "sinks");
            assertTrue(sinks.isEmpty());
        } finally {
            writer.close();
        }
    }

    // --- topic matching ---
    //
    // MqttMongoWriter no longer has its own topic-matching logic: it delegates to the shared
    // MqttTopicMatcher (see MqttTopicMatcherTest for full coverage of the matching semantics).
    // These tests only confirm that MqttMongoWriter.flush()'s sink routing exercises that shared
    // matcher correctly for the sink-filter shapes used in this module's configuration.

    @Test
    @DisplayName("topic matching: exact match")
    void testTopicMatchesExact() {
        assertTrue(MqttTopicMatcher.matches("sensors/temp", "sensors/temp"));
        assertFalse(MqttTopicMatcher.matches("sensors/humidity", "sensors/temp"));
    }

    @Test
    @DisplayName("topic matching: # wildcard matches all")
    void testTopicMatchesHash() {
        assertTrue(MqttTopicMatcher.matches("any/topic", "#"));
    }

    @Test
    @DisplayName("topic matching: prefix/# wildcard")
    void testTopicMatchesPrefixHash() {
        assertTrue(MqttTopicMatcher.matches("sensors/temp", "sensors/#"));
        assertTrue(MqttTopicMatcher.matches("sensors/room1/temp", "sensors/#"));
        assertFalse(MqttTopicMatcher.matches("traffic/flow", "sensors/#"));
    }

    @Test
    @DisplayName("topic matching: prefix/+ wildcard")
    void testTopicMatchesPrefixPlus() {
        assertTrue(MqttTopicMatcher.matches("sensors/temp", "sensors/+"));
        assertFalse(MqttTopicMatcher.matches("sensors/room1/temp", "sensors/+"));
        assertFalse(MqttTopicMatcher.matches("traffic/flow", "sensors/+"));
    }

    // --- write strategy: insertMany (auto) vs. upserting bulkWrite (dedup strategies) ---

    @Test
    @DisplayName("id-strategy 'auto' writes with insertMany, not bulkWrite")
    void testAutoStrategyUsesInsertMany() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        MongoCollection<Document> coll = wireWriterForFlush(writer, "auto", 10, 3, 1L, "unused.log");

        MessageBuffer buffer = new MessageBuffer(10, Strategy.RING);
        buffer.offer(new Pending(msg("sensors/temp", "{\"temp\":1}", 0), () -> { }));
        buffer.offer(new Pending(msg("sensors/temp", "{\"temp\":2}", 0), () -> { }));
        setField(writer, "buffer", buffer);

        writer.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(coll).insertMany(captor.capture(), any(InsertManyOptions.class));
        assertEquals(2, captor.getValue().size());
        verify(coll, never()).bulkWrite(anyList(), any(BulkWriteOptions.class));
    }

    @Test
    @DisplayName("id-strategy 'topic-timestamp-hash' writes with bulkWrite using upserting ReplaceOneModels")
    void testDedupStrategyUsesUpsertingBulkWrite() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        MongoCollection<Document> coll = wireWriterForFlush(writer, "topic-timestamp-hash", 10, 3, 1L, "unused.log");
        when(coll.bulkWrite(anyList(), any(BulkWriteOptions.class))).thenReturn(mock(BulkWriteResult.class));

        MessageBuffer buffer = new MessageBuffer(10, Strategy.RING);
        buffer.offer(new Pending(msg("sensors/temp", "{\"temp\":1}", 0, Instant.parse("2026-01-01T00:00:00Z")), () -> { }));
        buffer.offer(new Pending(msg("sensors/temp", "{\"temp\":2}", 0, Instant.parse("2026-01-01T00:00:01Z")), () -> { }));
        setField(writer, "buffer", buffer);

        writer.flush();

        verify(coll, never()).insertMany(anyList(), any(InsertManyOptions.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WriteModel<Document>>> modelsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<BulkWriteOptions> optionsCaptor = ArgumentCaptor.forClass(BulkWriteOptions.class);
        verify(coll).bulkWrite(modelsCaptor.capture(), optionsCaptor.capture());

        assertFalse(optionsCaptor.getValue().isOrdered(), "bulkWrite must be unordered");
        List<WriteModel<Document>> models = modelsCaptor.getValue();
        assertEquals(2, models.size());
        for (WriteModel<Document> model : models) {
            assertTrue(model instanceof ReplaceOneModel, "expected an upserting ReplaceOneModel, got " + model.getClass());
            ReplaceOneModel<Document> replaceModel = (ReplaceOneModel<Document>) model;
            assertTrue(replaceModel.getReplaceOptions().isUpsert(), "ReplaceOneModel must upsert");
        }
    }

    // --- duplicate key handling (finding C5) ---

    @Test
    @DisplayName("A duplicate-key (11000) bulk error under a dedup strategy is treated as success: no retry, no dead-letter, counted")
    void testDuplicateKeyTreatedAsSuccess() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        Path deadLetter = Files.createTempFile("mqtt-dead-letter", ".log");
        MongoCollection<Document> coll = wireWriterForFlush(writer, "topic-timestamp-hash", 10, 3, 1L,
            deadLetter.toString());

        // 2 documents; only index 0 is reported as an error (duplicate key) — index 1 succeeded silently
        when(coll.bulkWrite(anyList(), any(BulkWriteOptions.class)))
            .thenThrow(bulkWriteException(new BulkWriteError(11000, "E11000 duplicate key error", new BsonDocument(), 0)));

        MessageBuffer buffer = new MessageBuffer(10, Strategy.RING);
        buffer.offer(new Pending(msg("sensors/temp", "{\"temp\":1}", 0, Instant.parse("2026-01-01T00:00:00Z")), () -> { }));
        buffer.offer(new Pending(msg("sensors/temp", "{\"temp\":2}", 0, Instant.parse("2026-01-01T00:00:01Z")), () -> { }));
        setField(writer, "buffer", buffer);

        writer.flush();

        verify(coll, times(1)).bulkWrite(anyList(), any(BulkWriteOptions.class));
        assertEquals(1, writer.getDuplicateCount());
        assertEquals(0, Files.size(deadLetter), "duplicate-key documents must not be dead-lettered");

        Files.deleteIfExists(deadLetter);
    }

    // --- partial failure retries only the failed documents (finding M10) ---

    @Test
    @DisplayName("A genuine bulk-write error retries only the failed indices, not the whole batch")
    void testPartialFailureRetriesOnlyFailedDocuments() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        MongoCollection<Document> coll = wireWriterForFlush(writer, "topic-timestamp-hash", 10, 3, 1L, "unused.log");

        // First attempt: 2 documents submitted, index 0 fails with a genuine (non-duplicate-key) error
        when(coll.bulkWrite(anyList(), any(BulkWriteOptions.class)))
            .thenThrow(bulkWriteException(new BulkWriteError(11600, "interrupted", new BsonDocument(), 0)))
            .thenReturn(mock(BulkWriteResult.class));

        MessageBuffer buffer = new MessageBuffer(10, Strategy.RING);
        buffer.offer(new Pending(msg("sensors/temp", "{\"temp\":1}", 0, Instant.parse("2026-01-01T00:00:00Z")), () -> { }));
        buffer.offer(new Pending(msg("sensors/temp", "{\"temp\":2}", 0, Instant.parse("2026-01-01T00:00:01Z")), () -> { }));
        setField(writer, "buffer", buffer);

        writer.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WriteModel<Document>>> captor = ArgumentCaptor.forClass(List.class);
        verify(coll, times(2)).bulkWrite(captor.capture(), any(BulkWriteOptions.class));

        List<List<WriteModel<Document>>> attempts = captor.getAllValues();
        assertEquals(2, attempts.get(0).size(), "first attempt should submit the whole batch");
        assertEquals(1, attempts.get(1).size(), "retry should submit only the previously-failed document");
    }

    // --- dead-letter on exhausted retries ---

    @Test
    @DisplayName("Exhausted retries append the still-failing documents to the dead-letter file, one JSON document per line")
    void testExhaustedRetriesDeadLetter(@TempDir Path tempDir) throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        Path deadLetterPath = tempDir.resolve("dead-letter.log");
        // maxRetries=1: the initial attempt plus exactly one retry, both failing, then dead-letter
        MongoCollection<Document> coll = wireWriterForFlush(writer, "topic-timestamp-hash", 10, 1, 1L,
            deadLetterPath.toString());

        when(coll.bulkWrite(anyList(), any(BulkWriteOptions.class)))
            .thenThrow(bulkWriteException(new BulkWriteError(11600, "interrupted", new BsonDocument(), 0)));

        MessageBuffer buffer = new MessageBuffer(10, Strategy.RING);
        buffer.offer(new Pending(msg("sensors/temp", "{\"temp\":42}", 0, Instant.parse("2026-01-01T00:00:00Z")), () -> { }));
        setField(writer, "buffer", buffer);

        writer.flush();

        assertTrue(Files.exists(deadLetterPath), "dead-letter file should have been created");
        List<String> lines = Files.readAllLines(deadLetterPath);
        assertEquals(1, lines.size(), "one JSON document per line");
        Document deadLettered = Document.parse(lines.get(0));
        assertEquals("sensors/temp", deadLettered.getString("topic"));
        assertEquals("{\"temp\":42}", deadLettered.getString("payload"));
    }

    @Test
    @DisplayName("A failure to write the dead-letter file is logged, not thrown")
    void testDeadLetterWriteFailureDoesNotThrow() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        // A directory cannot be opened as a FileWriter target, so writing the dead-letter will fail
        MongoCollection<Document> coll = wireWriterForFlush(writer, "topic-timestamp-hash", 10, 0, 1L,
            Files.createTempDirectory("mqtt-dead-letter-dir").toString());

        when(coll.bulkWrite(anyList(), any(BulkWriteOptions.class)))
            .thenThrow(bulkWriteException(new BulkWriteError(11600, "interrupted", new BsonDocument(), 0)));

        MessageBuffer buffer = new MessageBuffer(10, Strategy.RING);
        buffer.offer(new Pending(msg("sensors/temp", "{\"temp\":1}", 0), () -> { }));
        setField(writer, "buffer", buffer);

        // must not throw despite the dead-letter write failing
        writer.flush();
    }

    // --- drain loop throughput (finding A5) ---

    @Test
    @DisplayName("drainUntilEmptyOrDeadline empties a buffer holding several batches within one wake-up")
    void testDrainLoopEmptiesMultipleBatchesInOneInterval() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        MongoCollection<Document> coll = wireWriterForFlush(writer, "auto", 2, 3, 1L, "unused.log");

        MessageBuffer buffer = new MessageBuffer(20, Strategy.RING);
        for (int i = 0; i < 7; i++) {
            buffer.offer(new Pending(msg("sensors/temp", "{\"temp\":" + i + "}", 0), () -> { }));
        }
        setField(writer, "buffer", buffer);
        setField(writer, "running", true);

        assertEquals(7, buffer.size());

        writer.drainUntilEmptyOrDeadline(System.currentTimeMillis() + 5000);

        assertEquals(0, buffer.size(), "the whole backlog should drain within a single wake-up, not just one batch");
        // batchSize=2 over 7 messages: 4 flush()/insertMany calls (2,2,2,1)
        verify(coll, times(4)).insertMany(anyList(), any(InsertManyOptions.class));
    }

    @Test
    @DisplayName("drainUntilEmptyOrDeadline stops once the deadline has elapsed even if the buffer is not empty")
    void testDrainLoopRespectsDeadline() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        wireWriterForFlush(writer, "auto", 1, 3, 1L, "unused.log");

        MessageBuffer buffer = new MessageBuffer(20, Strategy.RING);
        for (int i = 0; i < 5; i++) {
            buffer.offer(new Pending(msg("sensors/temp", "{\"temp\":" + i + "}", 0), () -> { }));
        }
        setField(writer, "buffer", buffer);
        setField(writer, "running", true);

        // deadline already in the past: exactly one flush (the do-while's mandatory first pass), then stop
        writer.drainUntilEmptyOrDeadline(System.currentTimeMillis() - 1);

        assertEquals(4, buffer.size(), "only the do-while's first mandatory pass should have run");
    }

    // --- close() must not close the injected MongoClient (finding A6) ---

    @Test
    @DisplayName("close() stops the drain loop but does not close the injected MongoClient")
    void testCloseDoesNotCloseInjectedClient() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        MongoClient mclient = mock(MongoClient.class);
        setField(writer, "mclient", mclient);
        setField(writer, "buffer", new MessageBuffer(10, Strategy.RING));
        setField(writer, "sinks", List.of());
        setField(writer, "running", true);

        writer.close();

        assertFalse((boolean) getField(writer, "running"));
        verify(mclient, never()).close();
    }
    // --- shutdown must not throw away the buffer ---
    //
    // close() used to be `running = false; flush();` - and flush() drains exactly one batch. So a
    // clean shutdown with a full buffer wrote batchSize messages and discarded the rest. That is
    // the only point in the whole path where messages disappeared rather than reaching disk:
    // everywhere else a failure ends in the dead-letter file. The two failures also arrive
    // together in practice, since RESTHeart gets restarted during a MongoDB incident, which is
    // exactly when the buffer is full.

    @Test
    @DisplayName("close() drains the whole buffer, not just one batch")
    void testCloseDrainsMoreThanOneBatch() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        MongoCollection<Document> coll = wireWriterForFlush(writer, "auto", 200, 1, 1L, deadLetterPath());
        setField(writer, "shutdownTimeoutMs", 10_000L);

        MessageBuffer buffer = new MessageBuffer(2000, Strategy.RING);
        for (int i = 0; i < 1000; i++) {
            buffer.offer(new Pending(msg("sensors/temp", "{\"n\":" + i + "}", 0), () -> { }));
        }
        setField(writer, "buffer", buffer);
        setField(writer, "running", true);

        writer.close();

        assertEquals(0, buffer.size(), "the buffer must be empty after a clean shutdown");
        // 1000 messages at a batch size of 200 is five insertMany calls. One would be the old
        // behaviour, and 800 messages on the floor.
        verify(coll, times(5)).insertMany(anyList(), any(InsertManyOptions.class));
    }

    @Test
    @DisplayName("close() dead-letters whatever the shutdown deadline did not cover")
    void testCloseDeadLettersWhatTheDeadlineLeaves() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        String deadLetter = deadLetterPath();
        wireWriterForFlush(writer, "auto", 200, 1, 1L, deadLetter);
        // Zero budget: drainUntilEmptyOrDeadline runs its body exactly once (it is a do/while),
        // so one batch reaches MongoDB and the remaining 800 are past the deadline. Deterministic,
        // where a real timeout would be a race.
        setField(writer, "shutdownTimeoutMs", 0L);

        MessageBuffer buffer = new MessageBuffer(2000, Strategy.RING);
        for (int i = 0; i < 1000; i++) {
            buffer.offer(new Pending(msg("sensors/temp", "{\"n\":" + i + "}", 0), () -> { }));
        }
        setField(writer, "buffer", buffer);
        setField(writer, "running", true);

        writer.close();

        assertEquals(0, buffer.size(), "nothing may be left holding a reference in the buffer");
        var lines = Files.readAllLines(Path.of(deadLetter));
        assertEquals(800, lines.size(),
            "everything the deadline did not cover must reach the dead-letter file rather than "
                + "being dropped; got " + lines.size() + " lines");
    }

    @Test
    @DisplayName("the dead-letter file rotates once it passes dead-letter-max-bytes")
    void testDeadLetterFileRotates() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter();
        String deadLetter = deadLetterPath();
        wireWriterForFlush(writer, "auto", 200, 1, 1L, deadLetter);
        setField(writer, "deadLetterMaxBytes", 64L);
        setField(writer, "shutdownTimeoutMs", 0L);

        // Two rounds, each past the 64-byte cap: the first fills the file, the second finds it
        // oversized and rotates before writing.
        for (int round = 0; round < 2; round++) {
            MessageBuffer buffer = new MessageBuffer(10, Strategy.RING);
            for (int i = 0; i < 5; i++) {
                buffer.offer(new Pending(msg("sensors/temp", "{\"round\":" + round + ",\"n\":" + i + "}", 0), () -> { }));
            }
            setField(writer, "buffer", buffer);
            setField(writer, "running", true);
            setField(writer, "batchSize", 0); // nothing reaches MongoDB; everything is stranded
            writer.close();
        }

        assertTrue(Files.exists(Path.of(deadLetter + ".1")),
            "the oversized file must be rotated aside rather than growing without bound");
        assertTrue(Files.exists(Path.of(deadLetter)),
            "and a fresh file must carry the newest failures");
    }

    /**
     * @return a dead-letter path under the module's target directory, unique per call, so tests
     *         neither collide with one another nor leave files in the working directory
     */
    private static String deadLetterPath() throws Exception {
        var dir = Path.of("target", "dead-letter-tests");
        Files.createDirectories(dir);
        return dir.resolve("dl-" + System.nanoTime() + ".log").toAbsolutePath().toString();
    }

    // --- configuration reading must survive YAML's choice of numeric type ---

    @Test
    @DisplayName("a long-valued setting written as a plain integer in YAML is read, not fatal")
    void testIntegerValuedLongSettingIsCoerced() throws Exception {
        // "retry-delay-ms: 2000" in YAML arrives as an Integer, while the default is 1000L. The
        // reader used to cast blind; because the cast is erased, the ClassCastException surfaced
        // at the assignment inside onInit and took the whole server down at startup with
        // "Integer cannot be cast to Long". Every long-valued key here was affected -
        // flush-interval-ms, retry-delay-ms, shutdown-timeout-ms, dead-letter-max-bytes.
        MqttMongoWriter writer = new MqttMongoWriter(mock(MqttMessageRouter.class));
        setField(writer, "config", Map.of(
            "id-strategy", "auto",
            "mongo-sink", List.of(),
            "drain", Map.of(
                "retry-delay-ms", 2000,        // Integer where a Long is expected
                "flush-interval-ms", 250,
                "shutdown-timeout-ms", 7000)));

        writer.onInit();
        try {
            assertEquals(2000L, getField(writer, "retryDelayMs"));
            assertEquals(250L, getField(writer, "flushIntervalMs"));
            assertEquals(7000L, getField(writer, "shutdownTimeoutMs"));
        } finally {
            writer.close();
        }
    }

    @Test
    @DisplayName("a setting of an unusable type falls back to the default and says so")
    void testUnusableSettingTypeFallsBackToDefault() throws Exception {
        MqttMongoWriter writer = new MqttMongoWriter(mock(MqttMessageRouter.class));
        setField(writer, "config", Map.of(
            "id-strategy", "auto",
            "mongo-sink", List.of(),
            "drain", Map.of("batch-size", "not a number")));

        writer.onInit();
        try {
            // Silently running on a default is the "present but inert" trap the module has a
            // sentinel for; the reader logs a warning naming the key rather than pretending.
            assertEquals(200, getField(writer, "batchSize"));
        } finally {
            writer.close();
        }
    }

    @Test
    @DisplayName("a message the buffer refuses is acknowledged anyway, so the connection keeps breathing")
    void testRefusedMessageIsStillAcknowledged() throws Exception {
        // The wiring the whole live/durable separation rests on. When the buffer refuses a message
        // - full, and the wait ceiling elapsed - the message is lost either way. Leaving it
        // unacknowledged would be strictly worse than dropping it: it would hold a slot in the
        // broker's in-flight window, and once that fills the broker stops delivering to this client
        // entirely, SSE included. A MongoDB outage would take the live stream down with it.
        var router = mock(MqttMessageRouter.class);
        MqttMongoWriter writer = new MqttMongoWriter(router);
        setField(writer, "config", Map.of(
            "id-strategy", "auto",
            "buffer", Map.of("strategy", "drop-incoming", "capacity", 1),
            "mongo-sink", List.of(Map.of(
                "topic", "sensors/#", "database", "db", "collection", "coll"))));

        // The drain loop starts inside onInit and will reach for MongoDB; a deep-stub keeps it from
        // tripping over a null client. What is written there is not what this test is about.
        setField(writer, "mclient", mock(MongoClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS));

        writer.onInit();
        try {
            var listener = ArgumentCaptor.forClass(MqttMessageRouter.DurableListener.class);
            verify(router).subscribeDurable(eq("sensors/#"), any(), listener.capture());

            var acknowledged = new java.util.concurrent.atomic.AtomicInteger();
            Runnable ack = acknowledged::incrementAndGet;

            // First fills the single slot and is held - not acknowledged until it is written.
            listener.getValue().onMessage(msg("sensors/temp", "{\"n\":1}", 1), ack);
            assertEquals(0, acknowledged.get(), "a buffered message must not be acknowledged yet");

            // Second is refused, and must be acknowledged on the spot.
            listener.getValue().onMessage(msg("sensors/temp", "{\"n\":2}", 1), ack);
            assertEquals(1, acknowledged.get(),
                "a refused message must be acknowledged rather than left holding an in-flight slot");
        } finally {
            writer.close();
        }
    }

}
