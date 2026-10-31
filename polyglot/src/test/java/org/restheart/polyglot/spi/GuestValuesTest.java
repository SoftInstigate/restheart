/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.polyglot.spi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.types.ObjectId;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.restheart.polyglot.spi.proxy.BsonProxy;
import org.restheart.polyglot.spi.proxy.JsonProxy;
import org.restheart.polyglot.spi.proxy.MapProxy;

import com.google.gson.JsonParser;

/**
 * The conversions between guest values and BSON, JSON, text and bytes, and the rules they
 * follow: canonical extended JSON in plain object literals, int32 or double unless overridden,
 * native dates accepted.
 */
class GuestValuesTest {
    private static Context ctx;

    @BeforeAll
    static void open() {
        ctx = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).allowHostClassLookup(c -> true).allowExperimentalOptions(true).build();
        ctx.enter();
    }

    @AfterAll
    static void close() {
        ctx.leave();
        ctx.close();
    }

    private static Value js(String expression) {
        return ctx.eval("js", expression);
    }

    // ---- to BSON

    @Test
    void numbersAreInt32OrDouble() {
        var doc = GuestValues.toBson(js("({ i: 1, neg: -5, d: 2.5, big: 3000000000, max: 2147483647, over: 2147483648 })")).asDocument();
        assertTrue(doc.get("i").isInt32());
        assertTrue(doc.get("neg").isInt32());
        assertTrue(doc.get("d").isDouble());
        assertTrue(doc.get("big").isDouble(), "an integer beyond int32 is a double unless $numberLong says otherwise");
        assertTrue(doc.get("max").isInt32());
        assertTrue(doc.get("over").isDouble());
    }

    @Test
    void extendedJsonWrappersOverrideTheNumberRule() {
        var doc = GuestValues.toBson(js("({ l: { $numberLong: '3000000000' }, i: { $numberInt: '7' }, d: { $numberDouble: '1' }, dec: { $numberDecimal: '1.50' } })")).asDocument();
        assertEquals(new BsonInt64(3000000000L), doc.get("l"));
        assertEquals(7, doc.get("i").asInt32().getValue());
        assertTrue(doc.get("d").isDouble());
        assertEquals("1.50", doc.get("dec").asDecimal128().getValue().toString());
    }

    @Test
    void oidAndDateWrappersBecomeTheirTypes() {
        var doc = GuestValues.toBson(js("({ _id: { $oid: '507f1f77bcf86cd799439011' }, when: { $date: '2026-01-01T00:00:00Z' }, millis: { $date: 0 } })")).asDocument();
        assertEquals(new ObjectId("507f1f77bcf86cd799439011"), doc.getObjectId("_id").getValue());
        assertEquals(1767225600000L, doc.getDateTime("when").getValue());
        assertEquals(0L, doc.getDateTime("millis").getValue());
    }

    @Test
    void aNativeDateIsABsonDate() {
        var doc = GuestValues.toBson(js("({ when: new Date(1000) })")).asDocument();
        assertEquals(1000L, doc.getDateTime("when").getValue());
    }

    @Test
    void aWrongOidIsAnError() {
        var e = assertThrows(IllegalArgumentException.class, () -> GuestValues.toBson(js("({ $oid: 'nope' })")));
        assertTrue(e.getMessage().contains("$oid"), e.getMessage());
    }

    @Test
    void arraysNullsBooleansStringsAndFunctions() {
        var doc = GuestValues.toBson(js("({ a: [1, 'x', null, [true]], n: null, u: undefined, s: 'str', f: () => 1 })")).asDocument();
        var a = doc.getArray("a");
        assertEquals(4, a.size());
        assertTrue(a.get(0).isInt32());
        assertEquals("x", a.get(1).asString().getValue());
        assertTrue(a.get(2).isNull());
        assertTrue(a.get(3).asArray().get(0).asBoolean().getValue());
        assertTrue(doc.get("n").isNull());
        assertTrue(doc.get("u").isNull());
        assertEquals(new BsonString("str"), doc.get("s"));
        assertFalse(doc.containsKey("f"), "a function has no BSON form and is dropped");
    }

    @Test
    void bytesBecomeBinary() {
        var doc = GuestValues.toBson(js("({ b: new Uint8Array([1, 2, 3]) })")).asDocument();
        assertArrayEquals(new byte[] {1, 2, 3}, doc.getBinary("b").getData());
    }

    @Test
    void aProxyOverADocumentIsUnwrappedNotCopied() {
        var original = BsonDocument.parse("{ \"_id\": { \"$oid\": \"507f1f77bcf86cd799439011\" }, \"n\": { \"$numberLong\": \"9007199254740993\" } }");
        var echo = js("(x) => x").execute(new BsonProxy(original));
        var back = GuestValues.toBson(echo);
        assertTrue(back.isDocument());
        assertEquals(original, back.asDocument());
    }

    @Test
    void anExtendedJsonWrapperReadFromADocumentIsWrittenBackAsItsType() {
        // what BsonProxy hands out for an ObjectId is { $oid }, a MapProxy: written back, it is an ObjectId again
        var oid = new ObjectId();
        var wrapper = BsonProxy.toGuest(new org.bson.BsonObjectId(oid));
        var doc = GuestValues.toBson(js("(id) => ({ _id: id })").execute(wrapper)).asDocument();
        assertEquals(oid, doc.getObjectId("_id").getValue());
    }

    // ---- to JSON

    @Test
    void jsonKeepsNumbersStringsAndNesting() {
        var json = GuestValues.toJson(js("({ a: 1, b: 2.5, c: 'x', d: null, e: [1, 2], f: { g: true } })"));
        assertEquals(JsonParser.parseString("{\"a\":1,\"b\":2.5,\"c\":\"x\",\"d\":null,\"e\":[1,2],\"f\":{\"g\":true}}"), json);
    }

    @Test
    void jsonDatesAreIsoStringsAndBytesBase64() {
        var json = GuestValues.toJson(js("({ when: new Date(0), b: new Uint8Array([104, 105]) })")).getAsJsonObject();
        assertEquals("1970-01-01T00:00:00Z", json.get("when").getAsString());
        assertEquals("aGk=", json.get("b").getAsString());
    }

    @Test
    void jsonKeepsExtendedJsonWrappersAsPlainObjects() {
        // a { $numberLong } read from a document and sent back as JSON keeps its 64 bits
        var wrapper = BsonProxy.toGuest(new BsonInt64(9007199254740993L));
        var json = GuestValues.toJson(js("(v) => ({ n: v })").execute(wrapper)).getAsJsonObject();
        assertEquals("9007199254740993", json.getAsJsonObject("n").get("$numberLong").getAsString());
    }

    @Test
    void jsonOfABsonDocumentIsRelaxedExtendedJson() {
        var doc = BsonDocument.parse("{ \"_id\": { \"$oid\": \"507f1f77bcf86cd799439011\" }, \"n\": 1 }");
        var json = GuestValues.toJson(js("(x) => x").execute(new BsonProxy(doc))).getAsJsonObject();
        assertEquals("507f1f77bcf86cd799439011", json.getAsJsonObject("_id").get("$oid").getAsString());
        assertEquals(1, json.get("n").getAsInt());
    }

    @Test
    void jsonOfAJsonProxyIsTheElementItself() {
        var element = JsonParser.parseString("{\"a\":[1,{\"b\":2}]}").getAsJsonObject();
        var json = GuestValues.toJson(js("(x) => x").execute(new JsonProxy(element)));
        assertEquals(element, json);
    }

    @Test
    void jsonOfAMapProxyIsPlain() {
        var json = GuestValues.toJson(js("(x) => x").execute(new MapProxy(Map.of("a", List.of(1, 2), "b", Map.of("c", "d")))));
        assertEquals(JsonParser.parseString("{\"a\":[1,2],\"b\":{\"c\":\"d\"}}"), json);
    }

    // ---- text and bytes

    @Test
    void textIsTheStringOrTheJson() {
        assertEquals("hello", GuestValues.toText(js("'hello'")));
        assertEquals("{\"a\":1}", GuestValues.toText(js("({ a: 1 })")));
        assertNull(GuestValues.toText(js("null")));
        assertEquals("1.5", GuestValues.toText(js("1.5")));
    }

    @Test
    void bytesFromStringTypedArrayAndNumbers() {
        assertArrayEquals("ciao".getBytes(StandardCharsets.UTF_8), GuestValues.toBytes(js("'ciao'")));
        assertArrayEquals(new byte[] {1, 2, 3}, GuestValues.toBytes(js("new Uint8Array([1, 2, 3])")));
        assertArrayEquals(new byte[] {1, 2, 3}, GuestValues.toBytes(js("new Uint8Array([1, 2, 3]).buffer")));
        assertArrayEquals(new byte[] {7, 8}, GuestValues.toBytes(js("[7, 8]")));
        assertArrayEquals(new byte[0], GuestValues.toBytes(js("undefined")));
    }

    @Test
    void stringRefusesAnObject() {
        assertEquals("x", GuestValues.string(js("'x'"), "what"));
        assertEquals("1", GuestValues.string(js("1"), "what"));
        assertNull(GuestValues.string(js("null"), "what"));
        var e = assertThrows(IllegalArgumentException.class, () -> GuestValues.string(js("({})"), "to"));
        assertTrue(e.getMessage().startsWith("to must be a string"), e.getMessage());
    }
}
