package org.restheart.mongodb.handlers.changestreams;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NotifyWhenEvaluator}.
 */
class NotifyWhenEvaluatorTest {

    // -----------------------------------------------------------------------
    // from() — parsing
    // -----------------------------------------------------------------------

    @Test
    void fromNullReturnsNull() {
        assertNull(NotifyWhenEvaluator.from(null));
    }

    @Test
    void fromValidDocumentParsesCorrectly() {
        var nw = BsonDocument.parse("{ \"fullDocument::tenantId\": { \"$var\": \"tid\" } }");
        var ev = NotifyWhenEvaluator.from(nw);
        assertNotNull(ev);
        assertEquals("tid", ev.getVarName());
    }

    @Test
    void fromMultipleEntriesThrows() {
        var nw = BsonDocument.parse(
            "{ \"fullDocument::a\": { \"$var\": \"x\" }, \"fullDocument::b\": { \"$var\": \"y\" } }");
        assertThrows(IllegalArgumentException.class, () -> NotifyWhenEvaluator.from(nw));
    }

    @Test
    void fromMissingSeparatorThrows() {
        var nw = BsonDocument.parse("{ \"fullDocumenttenantId\": { \"$var\": \"tid\" } }");
        assertThrows(IllegalArgumentException.class, () -> NotifyWhenEvaluator.from(nw));
    }

    @Test
    void fromMissingVarKeyThrows() {
        var nw = BsonDocument.parse("{ \"fullDocument::tenantId\": { \"notVar\": \"tid\" } }");
        assertThrows(IllegalArgumentException.class, () -> NotifyWhenEvaluator.from(nw));
    }

    // -----------------------------------------------------------------------
    // matches() — scalar equality
    // -----------------------------------------------------------------------

    @Nested
    class ScalarEquality {

        private final NotifyWhenEvaluator ev = NotifyWhenEvaluator.from(
            BsonDocument.parse("{ \"fullDocument::tenantId\": { \"$var\": \"tid\" } }"));

        private BsonDocument event(String tenantId) {
            return new BsonDocument("fullDocument",
                new BsonDocument("tenantId", new BsonString(tenantId)));
        }

        @Test
        void matchesWhenEqual() {
            assertTrue(ev.matches(event("t1"), Map.of("tid", "t1")));
        }

        @Test
        void noMatchWhenDifferent() {
            assertFalse(ev.matches(event("t2"), Map.of("tid", "t1")));
        }

        @Test
        void passThroughWhenVarAbsent() {
            // bound vars don't contain the required variable → pass-through
            assertTrue(ev.matches(event("t1"), Map.of()));
        }

        @Test
        void passThroughWhenBoundVarsNull() {
            assertTrue(ev.matches(event("t1"), null));
        }

        @Test
        void noMatchWhenFieldAbsent() {
            var e = new BsonDocument("fullDocument", new BsonDocument("otherId", new BsonString("t1")));
            assertFalse(ev.matches(e, Map.of("tid", "t1")));
        }

        @Test
        void noMatchWhenTopLevelFieldAbsent() {
            var e = new BsonDocument("documentKey", new BsonDocument("tenantId", new BsonString("t1")));
            assertFalse(ev.matches(e, Map.of("tid", "t1")));
        }
    }

    // -----------------------------------------------------------------------
    // matches() — array membership
    // -----------------------------------------------------------------------

    @Nested
    class ArrayMembership {

        private final NotifyWhenEvaluator ev = NotifyWhenEvaluator.from(
            BsonDocument.parse("{ \"fullDocument::recipients\": { \"$var\": \"userId\" } }"));

        private BsonDocument event(String... recipients) {
            var arr = new BsonArray();
            for (var r : recipients) arr.add(new BsonString(r));
            return new BsonDocument("fullDocument",
                new BsonDocument("recipients", arr));
        }

        @Test
        void matchesWhenValueInArray() {
            assertTrue(ev.matches(event("A", "B", "C"), Map.of("userId", "B")));
        }

        @Test
        void noMatchWhenValueNotInArray() {
            assertFalse(ev.matches(event("A", "B"), Map.of("userId", "C")));
        }

        @Test
        void noMatchOnEmptyArray() {
            assertFalse(ev.matches(event(), Map.of("userId", "A")));
        }

        @Test
        void passThroughWhenVarAbsent() {
            assertTrue(ev.matches(event("A", "B"), Map.of()));
        }
    }

    // -----------------------------------------------------------------------
    // matches() — nested dot-notation path
    // -----------------------------------------------------------------------

    @Test
    void nestedPathIsResolved() {
        var ev = NotifyWhenEvaluator.from(
            BsonDocument.parse("{ \"fullDocument::org.id\": { \"$var\": \"oid\" } }"));
        var e = new BsonDocument("fullDocument",
            new BsonDocument("org", new BsonDocument("id", new BsonString("org-42"))));
        assertTrue(ev.matches(e, Map.of("oid", "org-42")));
        assertFalse(ev.matches(e, Map.of("oid", "org-99")));
    }

    // -----------------------------------------------------------------------
    // Non-string scalar (non-array, non-string field value)
    // -----------------------------------------------------------------------

    @Test
    void nonStringScalarFieldReturnsNoMatch() {
        var ev = NotifyWhenEvaluator.from(
            BsonDocument.parse("{ \"fullDocument::count\": { \"$var\": \"n\" } }"));
        var e = new BsonDocument("fullDocument",
            new BsonDocument("count", new BsonInt32(5)));
        // non-string, non-array field → no match regardless of bound var
        assertFalse(ev.matches(e, Map.of("n", "5")));
    }
}
