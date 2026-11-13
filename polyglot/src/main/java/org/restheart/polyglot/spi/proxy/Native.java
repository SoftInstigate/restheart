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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.BsonValue;

import com.google.gson.JsonElement;

/**
 * Turns a Java value into what the guest sees as native data: maps, lists, Gson and BSON
 * values become proxies, everything else crosses as it is.
 *
 * <p>This is the rule the whole facade follows: data is exposed as data, and methods are kept
 * for behaviour. A plugin writes {@code req.query.limit}, not {@code req.query.get("limit")}.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public final class Native {
    private Native() {
    }

    /**
     * @param v a Java value
     * @return its guest view
     */
    @SuppressWarnings("unchecked")
    public static Object wrap(Object v) {
        if (v == null) {
            return null;
        } else if (v instanceof Optional<?> o) {
            return o.map(Native::wrap).orElse(null);
        } else if (v instanceof Map<?, ?> m) {
            return new MapProxy((Map<String, ?>) m);
        } else if (v instanceof List<?> l) {
            return new ListProxy(l);
        } else if (v instanceof JsonElement j) {
            return JsonProxy.toGuest(j);
        } else if (v instanceof BsonValue b) {
            return BsonProxy.toGuest(b);
        } else {
            return v;
        }
    }
}
