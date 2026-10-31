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

import java.math.BigDecimal;
import java.util.ArrayList;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * A Gson {@link JsonObject} seen by the guest as a native object, lazily: a nested object or
 * array is wrapped when the plugin reads it, the document is never copied.
 *
 * <p>The content of a {@code json} request is handed over this way, so that
 * {@code req.content.user.name} is a property chain and not a parse.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonProxy extends ReadOnlyObject {
    private final JsonObject object;

    /**
     * @param object the object, null is an empty object
     */
    public JsonProxy(JsonObject object) {
        this.object = object == null ? new JsonObject() : object;
    }

    /**
     * @return the wrapped object
     */
    public JsonObject object() {
        return object;
    }

    /**
     * The guest view of a Gson element: a proxy for objects and arrays, a primitive otherwise.
     *
     * @param e the element
     * @return the guest value
     */
    public static Object toGuest(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        } else if (e.isJsonObject()) {
            return new JsonProxy(e.getAsJsonObject());
        } else if (e.isJsonArray()) {
            return new Array(e.getAsJsonArray());
        } else {
            return primitive(e.getAsJsonPrimitive());
        }
    }

    private static Object primitive(JsonPrimitive p) {
        if (p.isBoolean()) {
            return p.getAsBoolean();
        } else if (p.isString()) {
            return p.getAsString();
        } else {
            var n = p.getAsNumber();
            // Gson keeps numbers as LazilyParsedNumber: pick the narrowest guest number
            var bd = n instanceof BigDecimal b ? b : new BigDecimal(n.toString());
            if (bd.scale() <= 0 && bd.stripTrailingZeros().scale() <= 0) {
                try {
                    return bd.intValueExact();
                } catch (ArithmeticException notInt) {
                    try {
                        return bd.longValueExact();
                    } catch (ArithmeticException notLong) {
                        return bd.doubleValue();
                    }
                }
            }
            return bd.doubleValue();
        }
    }

    @Override
    public Object getMember(String key) {
        return toGuest(object.get(key));
    }

    @Override
    public Object getMemberKeys() {
        return ProxyArray.fromList(new ArrayList<Object>(object.keySet()));
    }

    @Override
    public boolean hasMember(String key) {
        return object.has(key);
    }

    @Override
    public String toString() {
        return object.toString();
    }

    /**
     * A Gson {@link JsonArray} seen by the guest as a native array, read-only.
     */
    public static class Array implements ProxyArray {
        private final JsonArray array;

        public Array(JsonArray array) {
            this.array = array == null ? new JsonArray() : array;
        }

        public JsonArray array() {
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
}
