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

import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.restheart.polyglot.spi.proxy.JsonProxy;
import org.restheart.polyglot.spi.proxy.ReadOnlyObject;

import com.google.gson.JsonParser;

/**
 * The response of an {@link RhHttp} call: {@code { status, ok, headers, body, json(), bytes() }}.
 *
 * <p>The body is kept as bytes and decoded on demand: {@code body} is the UTF-8 text,
 * {@code json()} the parsed JSON as native data, {@code bytes()} the raw bytes.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RhHttpResponse extends ReadOnlyObject {
    private final int status;
    private final HttpHeaders headers;
    private final byte[] body;

    RhHttpResponse(int status, HttpHeaders headers, byte[] body) {
        this.status = status;
        this.headers = headers;
        this.body = body == null ? new byte[0] : body;
    }

    public int status() {
        return status;
    }

    public boolean ok() {
        return status >= 200 && status < 300;
    }

    public String body() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public byte[] bytes() {
        return body;
    }

    public Object json() {
        var text = body();
        return text.isBlank() ? null : JsonProxy.toGuest(JsonParser.parseString(text));
    }

    public String contentType() {
        return headers.firstValue("content-type").orElse(null);
    }

    public Object headers() {
        return new Headers(headers);
    }

    // A Proxy shows the guest its members only, never its Java methods: json() and bytes() are
    // members too, as executables.
    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "status" -> status;
            case "ok" -> ok();
            case "body" -> body();
            case "headers" -> headers();
            case "contentType" -> contentType();
            case "json" -> (ProxyExecutable) args -> json();
            case "bytes" -> (ProxyExecutable) args -> bytes();
            default -> null;
        };
    }

    @Override
    public Object getMemberKeys() {
        return ProxyArray.fromArray("status", "ok", "body", "headers", "contentType", "json", "bytes");
    }

    @Override
    public boolean hasMember(String key) {
        return switch (key) {
            case "status", "ok", "body", "headers", "contentType", "json", "bytes" -> true;
            default -> false;
        };
    }

    /**
     * The response headers as a native object, case-insensitive, each header as its first value.
     */
    static final class Headers extends ReadOnlyObject {
        private final HttpHeaders headers;

        Headers(HttpHeaders headers) {
            this.headers = headers;
        }

        @Override
        public Object getMember(String key) {
            return headers.firstValue(key).orElse(null);
        }

        @Override
        public Object getMemberKeys() {
            var keys = new ArrayList<Object>();
            headers.map().keySet().forEach(k -> keys.add(k.toLowerCase(Locale.ROOT)));
            return ProxyArray.fromList(keys);
        }

        @Override
        public boolean hasMember(String key) {
            return headers.firstValue(key).isPresent();
        }
    }
}
