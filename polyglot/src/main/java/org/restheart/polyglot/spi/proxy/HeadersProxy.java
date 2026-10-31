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

import java.util.ArrayList;
import java.util.Locale;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

/**
 * An Undertow {@link HeaderMap} seen by the guest as a native object with case-insensitive
 * keys: {@code req.headers["content-type"]} and {@code req.headers["Content-Type"]} are the
 * same header, as HTTP says. A header is read as its first value; {@code undefined} when absent.
 *
 * <p>The request headers are read-only. The response headers are writable:
 * {@code res.headers["x-request-id"] = id} sets the header, {@code delete res.headers.etag}
 * removes it.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class HeadersProxy implements ProxyObject {
    private final HeaderMap headers;
    private final boolean writable;

    /**
     * @param headers the header map
     * @param writable whether the guest can set and remove headers
     */
    public HeadersProxy(HeaderMap headers, boolean writable) {
        this.headers = headers;
        this.writable = writable;
    }

    @Override
    public Object getMember(String key) {
        return headers.getFirst(HttpString.tryFromString(key));
    }

    @Override
    public Object getMemberKeys() {
        var keys = new ArrayList<Object>();
        for (var name : headers.getHeaderNames()) {
            keys.add(name.toString().toLowerCase(Locale.ROOT));
        }
        return ProxyArray.fromList(keys);
    }

    @Override
    public boolean hasMember(String key) {
        return headers.contains(HttpString.tryFromString(key));
    }

    @Override
    public void putMember(String key, Value value) {
        if (!writable) {
            throw new UnsupportedOperationException("request headers are read-only, cannot set '" + key + "'");
        }

        var name = HttpString.tryFromString(key);

        if (value == null || value.isNull()) {
            headers.remove(name);
        } else {
            headers.put(name, value.isString() ? value.asString() : value.toString());
        }
    }

    @Override
    public boolean removeMember(String key) {
        if (!writable) {
            throw new UnsupportedOperationException("request headers are read-only, cannot remove '" + key + "'");
        }

        return headers.remove(HttpString.tryFromString(key)) != null;
    }

    /**
     * Every value of a header, for the few headers that repeat.
     *
     * @param key the header name, case-insensitive
     * @return the values, an empty array when the header is absent
     */
    public Object all(String key) {
        var values = headers.get(HttpString.tryFromString(key));
        return ProxyArray.fromList(values == null ? new ArrayList<>() : new ArrayList<Object>(values));
    }
}
