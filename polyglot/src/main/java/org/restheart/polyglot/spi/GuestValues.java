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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDecimal128;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonJavaScript;
import org.bson.BsonMaxKey;
import org.bson.BsonMinKey;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonRegularExpression;
import org.bson.BsonString;
import org.bson.BsonSymbol;
import org.bson.BsonTimestamp;
import org.bson.BsonValue;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.graalvm.polyglot.Value;
import org.restheart.polyglot.spi.proxy.BsonProxy;
import org.restheart.polyglot.spi.proxy.JsonProxy;
import org.restheart.polyglot.spi.proxy.ListProxy;
import org.restheart.polyglot.spi.proxy.MapProxy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Converts the values a plugin hands to the runtime, as polyglot {@link Value}s, into the Java
 * types the exchange and the MongoDB driver work with.
 *
 * <p>BSON is written in canonical extended JSON, in ordinary object literals:
 * {@code { $oid: id }}, {@code { $date: "2026-01-01T00:00:00Z" }}, {@code { $numberLong: "1" }}.
 * A native {@code Date} is accepted too. Numbers follow one rule, stated once: an integer that
 * fits in int32 is an int32, everything else is a double, and {@code $numberInt},
 * {@code $numberLong}, {@code $numberDouble} and {@code $numberDecimal} override it.</p>
 *
 * <p>Values that came from the runtime itself, the proxies over documents and arrays, are
 * unwrapped rather than copied: a document read with the {@code mongo} capability and written
 * back goes through untouched.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public final class GuestValues {
    private static final Set<String> EXTENDED_KEYS = Set.of(
            "$oid", "$date", "$numberInt", "$numberLong", "$numberDouble", "$numberDecimal",
            "$binary", "$regularExpression", "$timestamp", "$minKey", "$maxKey", "$code", "$symbol");

    private GuestValues() {
    }

    // ---- BSON ----

    /**
     * A guest value as BSON.
     *
     * @param v the value
     * @return the BSON value, {@link BsonNull} for null and undefined
     */
    public static BsonValue toBson(Value v) {
        if (v == null || v.isNull()) {
            return BsonNull.VALUE;
        }

        if (v.isHostObject()) {
            var host = v.asHostObject();
            if (host instanceof BsonValue b) {
                return b;
            } else if (host instanceof byte[] bytes) {
                return new BsonBinary(bytes);
            } else if (host instanceof JsonElement j) {
                return jsonToBson(j);
            } else if (host instanceof Map<?, ?> m) {
                return mapToBson(m);
            } else if (host instanceof List<?> l) {
                var a = new BsonArray();
                l.forEach(e -> a.add(javaToBson(e)));
                return a;
            } else if (host instanceof Instant i) {
                return new BsonDateTime(i.toEpochMilli());
            } else if (host instanceof ObjectId oid) {
                return new BsonObjectId(oid);
            }
            return new BsonString(String.valueOf(host));
        }

        if (v.isProxyObject()) {
            var p = v.asProxyObject();
            if (p instanceof BsonProxy bp) {
                return bp.document();
            } else if (p instanceof BsonProxy.Array ba) {
                return ba.array();
            } else if (p instanceof JsonProxy jp) {
                return jsonToBson(jp.object());
            } else if (p instanceof JsonProxy.Array ja) {
                return jsonToBson(ja.array());
            } else if (p instanceof MapProxy mp) {
                return mapToBson(mp.map());
            } else if (p instanceof ListProxy lp) {
                var a = new BsonArray();
                lp.list().forEach(e -> a.add(javaToBson(e)));
                return a;
            }
        }

        if (v.isBoolean()) {
            return BsonBoolean.valueOf(v.asBoolean());
        } else if (v.isString()) {
            return new BsonString(v.asString());
        } else if (v.isNumber()) {
            return number(v);
        } else if (v.isDate() && v.isTime()) {
            return new BsonDateTime(v.asInstant().toEpochMilli());
        } else if (v.hasBufferElements()) {
            return new BsonBinary(bufferBytes(v));
        } else if (isTypedByteArray(v)) {
            return new BsonBinary(typedBytes(v));
        } else if (v.hasArrayElements()) {
            var a = new BsonArray();
            for (var i = 0L;i < v.getArraySize();i++) {
                a.add(toBson(v.getArrayElement(i)));
            }
            return a;
        } else if (v.canExecute()) {
            // a function has no BSON form: dropped, as JSON.stringify does
            return BsonNull.VALUE;
        } else if (v.hasMembers()) {
            var keys = v.getMemberKeys();
            if (keys.size() == 1) {
                var key = keys.iterator().next();
                if (EXTENDED_KEYS.contains(key)) {
                    return extended(key, v.getMember(key));
                }
            }
            var doc = new BsonDocument();
            for (var key : keys) {
                var member = v.getMember(key);
                if (member != null && !member.canExecute()) {
                    doc.put(key, toBson(member));
                }
            }
            return doc;
        }

        return new BsonString(v.toString());
    }

    private static BsonValue number(Value v) {
        if (v.fitsInInt()) {
            return new BsonInt32(v.asInt());
        }
        return new BsonDouble(v.asDouble());
    }

    private static BsonValue extended(String key, Value inner) {
        try {
            return switch (key) {
                case "$oid" -> new BsonObjectId(new ObjectId(inner.asString()));
                case "$date" -> {
                    if (inner.isNumber()) {
                        yield new BsonDateTime(inner.asLong());
                    } else if (inner.isDate() && inner.isTime()) {
                        yield new BsonDateTime(inner.asInstant().toEpochMilli());
                    } else if (inner.hasMembers() && inner.hasMember("$numberLong")) {
                        yield new BsonDateTime(Long.parseLong(inner.getMember("$numberLong").asString()));
                    }
                    yield new BsonDateTime(Instant.parse(inner.asString()).toEpochMilli());
                }
                case "$numberInt" -> new BsonInt32(inner.isNumber() ? inner.asInt() : Integer.parseInt(inner.asString()));
                case "$numberLong" -> new BsonInt64(inner.isNumber() ? inner.asLong() : Long.parseLong(inner.asString()));
                case "$numberDouble" -> new BsonDouble(inner.isNumber() ? inner.asDouble() : parseDouble(inner.asString()));
                case "$numberDecimal" -> new BsonDecimal128(Decimal128.parse(inner.isNumber() ? inner.toString() : inner.asString()));
                case "$binary" -> {
                    var base64 = inner.getMember("base64").asString();
                    var subType = inner.hasMember("subType") ? inner.getMember("subType").asString() : "00";
                    yield new BsonBinary((byte) Integer.parseInt(subType, 16), Base64.getDecoder().decode(base64));
                }
                case "$regularExpression" -> new BsonRegularExpression(inner.getMember("pattern").asString(),
                        inner.hasMember("options") ? inner.getMember("options").asString() : "");
                case "$timestamp" -> new BsonTimestamp(inner.getMember("t").asInt(), inner.getMember("i").asInt());
                case "$minKey" -> new BsonMinKey();
                case "$maxKey" -> new BsonMaxKey();
                case "$code" -> new BsonJavaScript(inner.asString());
                case "$symbol" -> new BsonSymbol(inner.asString());
                default -> throw new IllegalArgumentException(key);
            };
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("wrong extended JSON value for " + key + ": " + inner + " (" + e.getMessage() + ")", e);
        }
    }

    private static double parseDouble(String s) {
        return switch (s) {
            case "Infinity" -> Double.POSITIVE_INFINITY;
            case "-Infinity" -> Double.NEGATIVE_INFINITY;
            case "NaN" -> Double.NaN;
            default -> Double.parseDouble(s);
        };
    }

    private static BsonValue mapToBson(Map<?, ?> m) {
        if (m.size() == 1) {
            var key = String.valueOf(m.keySet().iterator().next());
            if (EXTENDED_KEYS.contains(key)) {
                // an extended JSON wrapper read back from a BsonProxy: parsed as the driver does
                var wrapper = new BsonDocument(key, javaToBson(m.get(key)));
                try {
                    return parseExtended(wrapper);
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException("wrong extended JSON value for " + key + ": " + m.get(key) + " (" + e.getMessage() + ")", e);
                }
            }
        }

        var doc = new BsonDocument();
        m.forEach((k, val) -> doc.put(String.valueOf(k), javaToBson(val)));
        return doc;
    }

    private static BsonValue parseExtended(BsonDocument wrapper) {
        // the driver's parser turns { "x": { "$oid": "..." } } into an ObjectId at "x"
        return BsonDocument.parse("{\"x\": " + wrapper.toJson() + "}").get("x");
    }

    private static BsonValue javaToBson(Object o) {
        if (o == null) {
            return BsonNull.VALUE;
        } else if (o instanceof BsonValue b) {
            return b;
        } else if (o instanceof String s) {
            return new BsonString(s);
        } else if (o instanceof Boolean b) {
            return BsonBoolean.valueOf(b);
        } else if (o instanceof Integer i) {
            return new BsonInt32(i);
        } else if (o instanceof Long l) {
            return new BsonInt64(l);
        } else if (o instanceof Number n) {
            return new BsonDouble(n.doubleValue());
        } else if (o instanceof Map<?, ?> m) {
            return mapToBson(m);
        } else if (o instanceof List<?> l) {
            var a = new BsonArray();
            l.forEach(e -> a.add(javaToBson(e)));
            return a;
        } else if (o instanceof byte[] bytes) {
            return new BsonBinary(bytes);
        } else if (o instanceof Instant i) {
            return new BsonDateTime(i.toEpochMilli());
        } else if (o instanceof JsonElement j) {
            return jsonToBson(j);
        } else if (o instanceof Value v) {
            return toBson(v);
        }
        return new BsonString(String.valueOf(o));
    }

    /**
     * A Gson element as BSON, with extended JSON wrappers honoured.
     *
     * @param j the element
     * @return the BSON value
     */
    public static BsonValue jsonToBson(JsonElement j) {
        if (j == null || j.isJsonNull()) {
            return BsonNull.VALUE;
        }
        // the driver parses canonical extended JSON; wrapping keeps arrays and scalars valid
        return BsonDocument.parse("{\"x\": " + j + "}").get("x");
    }

    // ---- JSON ----

    /**
     * A guest value as a Gson element: what {@code JSON.stringify} would produce, parsed, with
     * dates as ISO strings and bytes as base64.
     *
     * @param v the value
     * @return the element, {@link JsonNull} for null and undefined
     */
    public static JsonElement toJson(Value v) {
        if (v == null || v.isNull()) {
            return JsonNull.INSTANCE;
        }

        if (v.isHostObject()) {
            var host = v.asHostObject();
            if (host instanceof JsonElement j) {
                return j;
            } else if (host instanceof BsonValue b) {
                return bsonToJson(b);
            } else if (host instanceof byte[] bytes) {
                return new JsonPrimitive(Base64.getEncoder().encodeToString(bytes));
            } else if (host instanceof Map<?, ?> m) {
                return mapToJson(m);
            } else if (host instanceof List<?> l) {
                return listToJson(l);
            }
            return new JsonPrimitive(String.valueOf(host));
        }

        if (v.isProxyObject()) {
            var p = v.asProxyObject();
            if (p instanceof JsonProxy jp) {
                return jp.object();
            } else if (p instanceof JsonProxy.Array ja) {
                return ja.array();
            } else if (p instanceof BsonProxy bp) {
                return bsonToJson(bp.document());
            } else if (p instanceof BsonProxy.Array ba) {
                return bsonToJson(ba.array());
            } else if (p instanceof MapProxy mp) {
                // an extended JSON wrapper read from a document stays as it is: { $numberLong: "..." }
                // keeps the 64 bits that a JSON number would lose
                return mapToJson(mp.map());
            } else if (p instanceof ListProxy lp) {
                return listToJson(lp.list());
            }
        }

        if (v.isBoolean()) {
            return new JsonPrimitive(v.asBoolean());
        } else if (v.isString()) {
            return new JsonPrimitive(v.asString());
        } else if (v.isNumber()) {
            if (v.fitsInInt()) {
                return new JsonPrimitive(v.asInt());
            } else if (v.fitsInLong()) {
                return new JsonPrimitive(v.asLong());
            }
            var d = v.asDouble();
            return Double.isFinite(d) ? new JsonPrimitive(d) : JsonNull.INSTANCE;
        } else if (v.isDate() && v.isTime()) {
            return new JsonPrimitive(v.asInstant().toString());
        } else if (v.hasBufferElements()) {
            return new JsonPrimitive(Base64.getEncoder().encodeToString(bufferBytes(v)));
        } else if (isTypedByteArray(v)) {
            return new JsonPrimitive(Base64.getEncoder().encodeToString(typedBytes(v)));
        } else if (v.hasArrayElements()) {
            var a = new JsonArray();
            for (var i = 0L;i < v.getArraySize();i++) {
                a.add(toJson(v.getArrayElement(i)));
            }
            return a;
        } else if (v.canExecute()) {
            return JsonNull.INSTANCE;
        } else if (v.hasMembers()) {
            var o = new JsonObject();
            for (var key : v.getMemberKeys()) {
                var member = v.getMember(key);
                if (member != null && !member.canExecute()) {
                    o.add(key, toJson(member));
                }
            }
            return o;
        }

        return new JsonPrimitive(v.toString());
    }

    /**
     * A BSON value as a Gson element, in relaxed extended JSON, the form RESTHeart answers
     * with: {@code { $oid }} and {@code { $date }} kept, numbers plain.
     *
     * @param b the value
     * @return the element
     */
    public static JsonElement bsonToJson(BsonValue b) {
        if (b == null || b.isNull()) {
            return JsonNull.INSTANCE;
        }
        return JsonParser.parseString(new BsonDocument("x", b).toJson()).getAsJsonObject().get("x");
    }

    private static JsonElement mapToJson(Map<?, ?> m) {
        var o = new JsonObject();
        m.forEach((k, val) -> o.add(String.valueOf(k), javaToJson(val)));
        return o;
    }

    private static JsonElement listToJson(List<?> l) {
        var a = new JsonArray();
        l.forEach(e -> a.add(javaToJson(e)));
        return a;
    }

    private static JsonElement javaToJson(Object o) {
        if (o == null) {
            return JsonNull.INSTANCE;
        } else if (o instanceof JsonElement j) {
            return j;
        } else if (o instanceof String s) {
            return new JsonPrimitive(s);
        } else if (o instanceof Boolean b) {
            return new JsonPrimitive(b);
        } else if (o instanceof Number n) {
            return new JsonPrimitive(n);
        } else if (o instanceof Map<?, ?> m) {
            return mapToJson(m);
        } else if (o instanceof List<?> l) {
            return listToJson(l);
        } else if (o instanceof byte[] bytes) {
            return new JsonPrimitive(Base64.getEncoder().encodeToString(bytes));
        } else if (o instanceof Instant i) {
            return new JsonPrimitive(i.toString());
        } else if (o instanceof BsonValue b) {
            return bsonToJson(b);
        } else if (o instanceof Value v) {
            return toJson(v);
        } else if (o instanceof MapProxy mp) {
            return mapToJson(mp.map());
        } else if (o instanceof ListProxy lp) {
            return listToJson(lp.list());
        } else if (o instanceof BsonProxy bp) {
            return bsonToJson(bp.document());
        } else if (o instanceof BsonProxy.Array ba) {
            return bsonToJson(ba.array());
        } else if (o instanceof JsonProxy jp) {
            return jp.object();
        } else if (o instanceof JsonProxy.Array ja) {
            return ja.array();
        } else if (o instanceof org.graalvm.polyglot.proxy.ProxyArray pa) {
            var a = new JsonArray();
            for (var i = 0L;i < pa.getSize();i++) {
                a.add(javaToJson(pa.get(i)));
            }
            return a;
        }
        return new JsonPrimitive(String.valueOf(o));
    }

    // ---- text and bytes ----

    /**
     * A guest value as text: a string as it is, anything else as JSON.
     *
     * @param v the value
     * @return the text, null for null and undefined
     */
    public static String toText(Value v) {
        if (v == null || v.isNull()) {
            return null;
        } else if (v.isString()) {
            return v.asString();
        } else if (v.isHostObject() && v.asHostObject() instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        } else if (v.hasBufferElements()) {
            return new String(bufferBytes(v), StandardCharsets.UTF_8);
        } else if (isTypedByteArray(v)) {
            return new String(typedBytes(v), StandardCharsets.UTF_8);
        }
        return toJson(v).toString();
    }

    /**
     * A guest value as bytes: a string in UTF-8, a {@code Uint8Array} or {@code ArrayBuffer}
     * as it is, an array of numbers as its bytes, anything else as the bytes of its JSON.
     *
     * @param v the value
     * @return the bytes, an empty array for null and undefined
     */
    public static byte[] toBytes(Value v) {
        if (v == null || v.isNull()) {
            return new byte[0];
        } else if (v.isString()) {
            return v.asString().getBytes(StandardCharsets.UTF_8);
        } else if (v.isHostObject() && v.asHostObject() instanceof byte[] bytes) {
            return bytes;
        } else if (v.hasBufferElements()) {
            return bufferBytes(v);
        } else if (v.hasArrayElements()) {
            var size = (int) v.getArraySize();
            var bytes = new byte[size];
            for (var i = 0;i < size;i++) {
                bytes[i] = (byte) v.getArrayElement(i).asInt();
            }
            return bytes;
        }
        return toJson(v).toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A {@code Uint8Array}: GraalJS exposes a typed array through array elements, the buffer
     * elements belong to its {@code ArrayBuffer}.
     */
    private static boolean isTypedByteArray(Value v) {
        return v.hasArrayElements() && v.hasMember("BYTES_PER_ELEMENT") && v.hasMember("buffer")
                && v.getMember("BYTES_PER_ELEMENT").isNumber() && v.getMember("BYTES_PER_ELEMENT").asInt() == 1
                && v.getMember("buffer").hasBufferElements();
    }

    private static byte[] typedBytes(Value v) {
        var size = (int) v.getArraySize();
        var bytes = new byte[size];
        for (var i = 0;i < size;i++) {
            bytes[i] = (byte) v.getArrayElement(i).asInt();
        }
        return bytes;
    }

    private static byte[] bufferBytes(Value v) {
        var size = (int) v.getBufferSize();
        var bytes = new byte[size];
        for (var i = 0;i < size;i++) {
            bytes[i] = v.readBufferByte(i);
        }
        return bytes;
    }

    /**
     * A string from a guest value that should be one, with a clear error when it is not.
     *
     * @param v the value
     * @param what the name of the value, for the error
     * @return the string, null for null and undefined
     */
    public static String string(Value v, String what) {
        if (v == null || v.isNull()) {
            return null;
        } else if (v.isString()) {
            return v.asString();
        } else if (v.isNumber() || v.isBoolean()) {
            return v.toString();
        }
        throw new IllegalArgumentException(what + " must be a string, got " + v);
    }

    /**
     * The {@link BigDecimal} value of a guest number, for callers that need it exact.
     *
     * @param v a number
     * @return its decimal value
     */
    public static BigDecimal decimal(Value v) {
        return v.fitsInLong() ? BigDecimal.valueOf(v.asLong()) : BigDecimal.valueOf(v.asDouble());
    }
}
