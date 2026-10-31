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

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonString;
import org.bson.BsonValue;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.restheart.exchange.BsonResponse;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.exchange.JsonResponse;
import org.restheart.exchange.Response;
import org.restheart.exchange.ServiceResponse;
import org.restheart.exchange.StringResponse;
import org.restheart.polyglot.spi.proxy.BsonProxy;
import org.restheart.polyglot.spi.proxy.HeadersProxy;
import org.restheart.polyglot.spi.proxy.JsonProxy;
import org.restheart.polyglot.spi.proxy.Native;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * The response as a plugin sees it.
 *
 * <ul>
 *   <li>{@code res.statusCode}: read and set</li>
 *   <li>{@code res.headers["x-request-id"] = id}: case-insensitive, writable</li>
 *   <li>{@code res.content}: typed by the content kind, read and set; {@code res.contentType}</li>
 *   <li>{@code res.json(x)}: content, content type and 200, in one call</li>
 *   <li>{@code res.text(s)}, {@code res.bytes(b)}: the same for text and bytes</li>
 *   <li>{@code res.setInError(code, message)}: the error body RESTHeart sends everywhere</li>
 *   <li>{@code res.raw}: the Java {@link Response}, the escape hatch</li>
 * </ul>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RhResponse implements ProxyObject {
    private static final List<String> KEYS = List.of("statusCode", "headers", "content", "contentType", "raw",
            "json", "text", "bytes", "setInError", "header");

    private final Response<?> response;
    private final ContentKind kind;

    /**
     * @param response the Java response
     * @param kind the content kind of the plugin
     */
    public RhResponse(Response<?> response, ContentKind kind) {
        this.response = response;
        this.kind = kind;
    }

    /**
     * @return the Java response
     */
    public Response<?> raw() {
        return response;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "statusCode" -> response.getStatusCode();
            case "headers" -> new HeadersProxy(response.getHeaders(), true);
            case "content" -> content();
            case "contentType" -> response.getContentType();
            case "raw" -> response;
            case "json" -> (ProxyExecutable) a -> {
                json(a.length > 0 ? a[0] : null);
                return null;
            };
            case "text" -> (ProxyExecutable) a -> {
                text(a.length > 0 ? a[0] : null);
                return null;
            };
            case "bytes" -> (ProxyExecutable) a -> {
                bytes(a.length > 0 ? a[0] : null);
                return null;
            };
            case "setInError" -> (ProxyExecutable) a -> {
                if (a.length == 0 || !a[0].isNumber()) {
                    throw new IllegalArgumentException("setInError needs the status code and a message");
                }
                response.setInError(a[0].asInt(), a.length > 1 ? GuestValues.toText(a[1]) : null);
                return null;
            };
            case "header" -> (ProxyExecutable) a -> {
                if (a.length < 2 || !a[0].isString()) {
                    throw new IllegalArgumentException("header needs a name and a value");
                }
                response.setHeader(a[0].asString(), GuestValues.string(a[1], "header " + a[0].asString()));
                return null;
            };
            default -> null;
        };
    }

    @Override
    public Object getMemberKeys() {
        return ProxyArray.fromList(new ArrayList<Object>(KEYS));
    }

    @Override
    public boolean hasMember(String key) {
        return KEYS.contains(key);
    }

    @Override
    public void putMember(String key, Value value) {
        switch (key) {
            case "statusCode" -> {
                if (value == null || !value.isNumber()) {
                    throw new IllegalArgumentException("statusCode must be a number");
                }
                response.setStatusCode(value.asInt());
            }
            case "content" -> setContent(value);
            case "contentType" -> response.setContentType(GuestValues.string(value, "contentType"));
            default -> throw new UnsupportedOperationException("res." + key + " is read-only");
        }
    }

    @Override
    public boolean removeMember(String key) {
        throw new UnsupportedOperationException("res." + key + " cannot be removed");
    }

    /**
     * Sets the content as JSON, the content type and, when none is set yet, status 200.
     *
     * @param value the value, converted according to the content kind
     */
    public void json(Value value) {
        if (response instanceof StringResponse r) {
            r.setContent(value == null || value.isNull() ? "null" : GuestValues.toJson(value).toString());
        } else if (response instanceof JsonResponse r) {
            r.setContent(value == null || value.isNull() ? null : GuestValues.toJson(value));
        } else if (response instanceof BsonResponse r) {
            r.setContent(value == null || value.isNull() ? null : GuestValues.toBson(value));
        } else if (response instanceof ByteArrayResponse r) {
            r.setContent(GuestValues.toJson(value).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } else {
            setContent(value);
        }

        response.setContentTypeAsJson();
        ok();
    }

    /**
     * Sets the content as text, {@code text/plain} and, when none is set yet, status 200.
     *
     * @param value the text
     */
    public void text(Value value) {
        var text = GuestValues.toText(value);

        if (response instanceof StringResponse r) {
            r.setContent(text);
        } else if (response instanceof JsonResponse r) {
            r.setContent(text == null ? null : new JsonPrimitive(text));
        } else if (response instanceof BsonResponse r) {
            r.setContent(text == null ? null : new BsonString(text));
        } else if (response instanceof ByteArrayResponse r) {
            r.setContent(text == null ? null : text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } else {
            setContent(value);
        }

        response.setContentType("text/plain; charset=utf-8");
        ok();
    }

    /**
     * Sets the content as bytes, {@code application/octet-stream} and, when none is set yet,
     * status 200. For a {@code bytes} plugin; a string plugin gets the bytes as UTF-8 text.
     *
     * @param value the bytes
     */
    public void bytes(Value value) {
        var bytes = GuestValues.toBytes(value);

        if (response instanceof ByteArrayResponse r) {
            r.setContent(bytes);
        } else if (response instanceof StringResponse r) {
            r.setContent(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        } else {
            throw new UnsupportedOperationException("res.bytes() needs a bytes or string plugin, this one is " + kind.key());
        }

        response.setContentType("application/octet-stream");
        ok();
    }

    private void ok() {
        if (response.getStatusCode() < 200) {
            response.setStatusCode(200);
        }
    }

    private Object content() {
        if (!(response instanceof ServiceResponse<?> sr)) {
            return null;
        }

        var content = sr.getContent();

        if (content == null) {
            return null;
        }

        return switch (kind) {
            case STRING -> content instanceof String s ? s : String.valueOf(content);
            case JSON -> content instanceof JsonElement j ? JsonProxy.toGuest(j) : Native.wrap(content);
            case BSON, MONGO, CSV -> content instanceof BsonValue b ? BsonProxy.toGuest(b) : Native.wrap(content);
            case BYTES -> content;
        };
    }

    @SuppressWarnings("unchecked")
    private void setContent(Value value) {
        var isNull = value == null || value.isNull();

        if (response instanceof StringResponse r) {
            r.setContent(isNull ? null : GuestValues.toText(value));
        } else if (response instanceof JsonResponse r) {
            r.setContent(isNull ? null : GuestValues.toJson(value));
        } else if (response instanceof BsonResponse r) {
            r.setContent(isNull ? null : GuestValues.toBson(value));
        } else if (response instanceof ByteArrayResponse r) {
            r.setContent(isNull ? null : GuestValues.toBytes(value));
        } else if (response instanceof ServiceResponse<?> r) {
            ((ServiceResponse<Object>) r).setContent(isNull ? null : value.isHostObject() ? value.asHostObject() : GuestValues.toBson(value));
        } else {
            throw new UnsupportedOperationException("res.content is read-only for " + response.getClass().getSimpleName());
        }
    }
}
