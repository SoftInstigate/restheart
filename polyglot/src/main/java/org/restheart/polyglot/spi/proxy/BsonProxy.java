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
package org.restheart.polyglot.spi.proxy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

/**
 * A {@link BsonDocument} seen by the guest as a native object, lazily, with the BSON types that
 * have no guest equivalent in canonical extended JSON: {@code { $oid }}, {@code { $date }},
 * {@code { $numberDecimal }} and the others.
 *
 * <p>Reading is symmetric with writing: what a plugin reads from a {@code bson} request, a
 * {@code mongo} request or a {@code mongo} capability query is what it writes in a filter or an
 * update. There is one representation to learn, and it is the one RESTHeart users write in
 * query strings and request bodies.</p>
 *
 * <p>Numbers: int32 and double become guest numbers; an int64 becomes a guest number when it
 * fits in the 53 bits a JavaScript number keeps exact, {@code { $numberLong: "..." }} otherwise;
 * a Decimal128 is always {@code { $numberDecimal: "..." }}.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BsonProxy extends ReadOnlyObject {
    /** The largest integer a JavaScript number represents exactly, 2^53 - 1. */
    static final long MAX_SAFE_INTEGER = 9007199254740991L;

    private final BsonDocument document;

    /**
     * @param document the document, null is an empty document
     */
    public BsonProxy(BsonDocument document) {
        this.document = document == null ? new BsonDocument() : document;
    }

    /**
     * @return the wrapped document
     */
    public BsonDocument document() {
        return document;
    }

    /**
     * The guest view of a BSON value.
     *
     * @param v the value
     * @return a proxy for documents, arrays and the extended JSON wrappers, a primitive otherwise
     */
    public static Object toGuest(BsonValue v) {
        if (v == null) {
            return null;
        }

        return switch (v.getBsonType()) {
            case DOCUMENT -> new BsonProxy(v.asDocument());
            case ARRAY -> new Array(v.asArray());
            case STRING -> v.asString().getValue();
            case BOOLEAN -> v.asBoolean().getValue();
            case INT32 -> v.asInt32().getValue();
            case INT64 -> {
                var l = v.asInt64().getValue();
                yield Math.abs(l) <= MAX_SAFE_INTEGER ? (Object) l : extended("$numberLong", Long.toString(l));
            }
            case DOUBLE -> v.asDouble().getValue();
            case DECIMAL128 -> extended("$numberDecimal", v.asDecimal128().getValue().toString());
            case OBJECT_ID -> extended("$oid", v.asObjectId().getValue().toHexString());
            case DATE_TIME -> extended("$date", Instant.ofEpochMilli(v.asDateTime().getValue()).toString());
            case NULL, UNDEFINED -> null;
            case BINARY -> {
                var b = v.asBinary();
                var inner = new LinkedHashMap<String, Object>();
                inner.put("base64", Base64.getEncoder().encodeToString(b.getData()));
                inner.put("subType", String.format("%02x", b.getType()));
                yield extended("$binary", inner);
            }
            case REGULAR_EXPRESSION -> {
                var r = v.asRegularExpression();
                var inner = new LinkedHashMap<String, Object>();
                inner.put("pattern", r.getPattern());
                inner.put("options", r.getOptions());
                yield extended("$regularExpression", inner);
            }
            case TIMESTAMP -> {
                var t = v.asTimestamp();
                var inner = new LinkedHashMap<String, Object>();
                inner.put("t", t.getTime());
                inner.put("i", t.getInc());
                yield extended("$timestamp", inner);
            }
            case MIN_KEY -> extended("$minKey", 1);
            case MAX_KEY -> extended("$maxKey", 1);
            case JAVASCRIPT -> extended("$code", v.asJavaScript().getCode());
            case SYMBOL -> extended("$symbol", v.asSymbol().getSymbol());
            default -> v.toString();
        };
    }

    private static Object extended(String key, Object value) {
        var m = new LinkedHashMap<String, Object>(1);
        m.put(key, value);
        return new MapProxy(m);
    }

    @Override
    public Object getMember(String key) {
        return toGuest(document.get(key));
    }

    @Override
    public Object getMemberKeys() {
        return ProxyArray.fromList(new ArrayList<Object>(document.keySet()));
    }

    @Override
    public boolean hasMember(String key) {
        return document.containsKey(key);
    }

    @Override
    public String toString() {
        return document.toJson();
    }

    /**
     * A {@link BsonArray} seen by the guest as a native array, read-only.
     */
    public static class Array implements ProxyArray {
        private final BsonArray array;

        public Array(BsonArray array) {
            this.array = array == null ? new BsonArray() : array;
        }

        public BsonArray array() {
            return array;
        }

        @Override
        public Object get(long index) {
            if (index < 0 || index >= array.size()) {
                throw new ArrayIndexOutOfBoundsException((int) index);
            }
            return toGuest(array.get((int) index));
        }

        @Override
        public void set(long index, Value value) {
            throw new UnsupportedOperationException("read-only array");
        }

        @Override
        public long getSize() {
            return array.size();
        }

        @Override
        public String toString() {
            return array.toString();
        }
    }

    /**
     * The map behind a {@link MapProxy} built by {@link #toGuest}, for the converters that read
     * an extended JSON wrapper back.
     *
     * @param proxy a proxy
     * @return its map, empty when the proxy is not a {@link MapProxy}
     */
    static Map<String, ?> mapOf(Object proxy) {
        return proxy instanceof MapProxy mp ? mp.map() : Map.of();
    }
}
