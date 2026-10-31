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
import java.util.LinkedHashMap;
import java.util.List;

import org.bson.BsonValue;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.restheart.exchange.BadRequestException;
import org.restheart.exchange.BsonFromCsvRequest;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.Request;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.StringRequest;
import org.restheart.polyglot.spi.proxy.BsonProxy;
import org.restheart.polyglot.spi.proxy.HeadersProxy;
import org.restheart.polyglot.spi.proxy.JsonProxy;
import org.restheart.polyglot.spi.proxy.MapProxy;
import org.restheart.polyglot.spi.proxy.Native;
import org.restheart.polyglot.spi.proxy.QueryProxy;

import com.google.gson.JsonElement;

/**
 * The request as a plugin sees it: data as properties, behaviour as methods, every Java idiom
 * translated here, once.
 *
 * <ul>
 *   <li>{@code req.path}, {@code req.method}, {@code req.url}, {@code req.queryString}</li>
 *   <li>{@code req.query.limit}: the first value, a string, {@code undefined} when absent;
 *       {@code req.queryAll.tag}: every value, an array</li>
 *   <li>{@code req.headers["content-type"]}: case-insensitive</li>
 *   <li>{@code req.account}: {@code { name, roles }}, null when not authenticated</li>
 *   <li>{@code req.content}: typed by the content kind, a string, native JSON, bytes;
 *       assignable, for the interceptors that change a request</li>
 *   <li>{@code req.mongo}: the MongoDB specific part, for the {@code mongo} kind, see {@link RhMongoRequest}</li>
 *   <li>{@code req.isGet()} and the other method predicates, {@code req.isAuthenticated()},
 *       {@code req.isHandledBy(name)}, {@code req.pathParams(template)}, {@code req.cookie(name)}</li>
 *   <li>{@code req.raw}: the Java {@link Request}, the escape hatch</li>
 * </ul>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RhRequest implements ProxyObject {
    private static final List<String> KEYS = List.of("path", "method", "url", "queryString", "query", "queryAll",
            "headers", "account", "content", "contentType", "mongo", "raw",
            "isGet", "isPost", "isPut", "isPatch", "isDelete", "isOptions", "isHead",
            "isAuthenticated", "isHandledBy", "pathParams", "cookie", "header", "param", "attach");

    private final Request<?> request;
    private final ContentKind kind;
    private RhMongoRequest mongo;

    /**
     * @param request the Java request
     * @param kind the content kind of the plugin
     */
    public RhRequest(Request<?> request, ContentKind kind) {
        this.request = request;
        this.kind = kind;
    }

    /**
     * @return the Java request
     */
    public Request<?> raw() {
        return request;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "path" -> request.getPath();
            case "method" -> request.getMethod().name();
            case "url" -> request.getURL();
            case "queryString" -> request.getQueryString();
            case "query" -> new QueryProxy(request.getQueryParameters(), false);
            case "queryAll" -> new QueryProxy(request.getQueryParameters(), true);
            case "headers" -> new HeadersProxy(request.getHeaders(), true);
            case "account" -> account();
            case "content" -> content();
            case "contentType" -> request.getContentType();
            case "mongo" -> mongo();
            case "raw" -> request;
            case "isGet" -> (ProxyExecutable) a -> request.isGet();
            case "isPost" -> (ProxyExecutable) a -> request.isPost();
            case "isPut" -> (ProxyExecutable) a -> request.isPut();
            case "isPatch" -> (ProxyExecutable) a -> request.isPatch();
            case "isDelete" -> (ProxyExecutable) a -> request.isDelete();
            case "isOptions" -> (ProxyExecutable) a -> request.isOptions();
            case "isHead" -> (ProxyExecutable) a -> request.isHead();
            case "isAuthenticated" -> (ProxyExecutable) a -> request.isAuthenticated();
            case "isHandledBy" -> (ProxyExecutable) a -> request instanceof ServiceRequest<?> sr
                    && a.length > 0 && a[0].isString() && sr.isHandledBy(a[0].asString());
            case "pathParams" -> (ProxyExecutable) a -> {
                if (a.length == 0 || !a[0].isString()) {
                    throw new IllegalArgumentException("pathParams needs the path template, e.g. '/users/{id}'");
                }
                var params = request.getPathParams(a[0].asString());
                return new MapProxy(params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params));
            };
            case "cookie" -> (ProxyExecutable) a -> {
                if (a.length == 0 || !a[0].isString()) {
                    throw new IllegalArgumentException("cookie needs the cookie name");
                }
                var cookie = request.getCookie(a[0].asString());
                return cookie == null ? null : cookie.getValue();
            };
            case "header" -> (ProxyExecutable) a -> a.length > 0 && a[0].isString() ? request.getHeader(a[0].asString()) : null;
            case "param" -> (ProxyExecutable) a -> a.length > 0 && a[0].isString() ? Native.wrap(request.attachedParam(a[0].asString())) : null;
            case "attach" -> (ProxyExecutable) a -> {
                if (a.length < 2 || !a[0].isString()) {
                    throw new IllegalArgumentException("attach needs a name and a value");
                }
                request.attachParam(a[0].asString(), a[1].isHostObject() ? a[1].asHostObject() : GuestValues.toBson(a[1]));
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
            case "content" -> setContent(value);
            case "contentType" -> request.setContentType(GuestValues.string(value, "contentType"));
            default -> throw new UnsupportedOperationException("req." + key + " is read-only");
        }
    }

    @Override
    public boolean removeMember(String key) {
        throw new UnsupportedOperationException("req." + key + " cannot be removed");
    }

    private Object account() {
        var account = request.getAuthenticatedAccount();

        if (account == null) {
            return null;
        }

        var m = new LinkedHashMap<String, Object>();
        m.put("name", account.getPrincipal() == null ? null : account.getPrincipal().getName());
        m.put("roles", account.getRoles() == null ? List.of() : new ArrayList<>(account.getRoles()));
        return new MapProxy(m);
    }

    /**
     * The content, typed by the content kind. A body that does not parse is a
     * {@link BadRequestException}, which {@link GuestErrors} answers with 400.
     */
    private Object content() {
        if (!(request instanceof ServiceRequest<?> sr)) {
            return null;
        }

        Object content;

        try {
            content = sr.getContent();
        } catch (BadRequestException bre) {
            throw new IllegalArgumentException(bre.getMessage(), bre);
        }

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
        if (request instanceof StringRequest r) {
            r.setContent(GuestValues.toText(value));
        } else if (request instanceof JsonRequest r) {
            r.setContent(value == null || value.isNull() ? null : GuestValues.toJson(value));
        } else if (request instanceof BsonFromCsvRequest r) {
            var b = GuestValues.toBson(value);
            if (!b.isArray()) {
                throw new IllegalArgumentException("the content of a csv request is an array of documents");
            }
            r.setContent(b.asArray());
        } else if (request instanceof BsonRequest r) {
            r.setContent(value == null || value.isNull() ? null : GuestValues.toBson(value));
        } else if (request instanceof ByteArrayRequest r) {
            r.setContent(GuestValues.toBytes(value));
        } else if (request instanceof ServiceRequest<?> r) {
            ((ServiceRequest<Object>) r).setContent(value == null || value.isNull() ? null : value.isHostObject() ? value.asHostObject() : GuestValues.toBson(value));
        } else {
            throw new UnsupportedOperationException("req.content is read-only for " + request.getClass().getSimpleName());
        }
    }

    private RhMongoRequest mongo() {
        if (mongo == null && request instanceof MongoRequest mr) {
            mongo = new RhMongoRequest(mr);
        }
        return mongo;
    }
}
