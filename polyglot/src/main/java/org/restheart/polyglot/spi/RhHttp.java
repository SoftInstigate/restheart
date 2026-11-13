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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.stream.Collectors;

import org.graalvm.polyglot.Value;

/**
 * The {@code http} capability of the SDK: a client over the shared, configured
 * {@link HttpClient} of the {@code http-client} provider.
 *
 * <p>Every call blocks and returns a {@link RhHttpResponse}. A 4xx or a 5xx does not throw, it
 * comes back with {@code ok === false}: throwing on a valid HTTP response is what trips
 * everybody up. A connection failure or a timeout throws.</p>
 *
 * <p>The body of a {@code post}, {@code put} or {@code patch} is sent as JSON when it is an
 * object or an array, as text when it is a string, as bytes when it is bytes. The options,
 * all optional: {@code headers}, {@code timeout} in milliseconds, {@code auth} as
 * {@code { bearer }} or {@code { basic: { user, password } }}, {@code form} for an
 * {@code application/x-www-form-urlencoded} body, {@code json} for an explicit JSON body,
 * {@code contentType}.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RhHttp {
    private final HttpClient client;
    private final Duration defaultTimeout;

    /**
     * @param client the shared client
     * @param defaultTimeout the request timeout of a call that sets none
     */
    public RhHttp(HttpClient client, Duration defaultTimeout) {
        this.client = client;
        this.defaultTimeout = defaultTimeout;
    }

    public RhHttpResponse get(String url) {
        return send("GET", url, null, null);
    }

    public RhHttpResponse get(String url, Value options) {
        return send("GET", url, null, options);
    }

    public RhHttpResponse delete(String url) {
        return send("DELETE", url, null, null);
    }

    public RhHttpResponse delete(String url, Value options) {
        return send("DELETE", url, null, options);
    }

    public RhHttpResponse post(String url, Value body) {
        return send("POST", url, body, null);
    }

    public RhHttpResponse post(String url, Value body, Value options) {
        return send("POST", url, body, options);
    }

    public RhHttpResponse put(String url, Value body) {
        return send("PUT", url, body, null);
    }

    public RhHttpResponse put(String url, Value body, Value options) {
        return send("PUT", url, body, options);
    }

    public RhHttpResponse patch(String url, Value body) {
        return send("PATCH", url, body, null);
    }

    public RhHttpResponse patch(String url, Value body, Value options) {
        return send("PATCH", url, body, options);
    }

    /**
     * Any request: {@code { method, url, body?, headers?, timeout?, auth?, form?, json?, contentType? }}.
     *
     * @param spec the request
     * @return the response
     */
    public RhHttpResponse request(Value spec) {
        if (spec == null || !spec.hasMembers()) {
            throw new IllegalArgumentException("http.request() needs an object with method and url");
        }

        var method = spec.hasMember("method") ? spec.getMember("method").asString().toUpperCase(Locale.ROOT) : "GET";
        var url = GuestValues.string(spec.getMember("url"), "url");

        if (url == null) {
            throw new IllegalArgumentException("http.request() needs an url");
        }

        return send(method, url, spec.hasMember("body") ? spec.getMember("body") : null, spec);
    }

    private RhHttpResponse send(String method, String url, Value body, Value options) {
        var builder = HttpRequest.newBuilder(URI.create(url));
        var timeout = defaultTimeout;
        String contentType = null;
        HttpRequest.BodyPublisher publisher = null;

        if (options != null && options.hasMembers()) {
            if (options.hasMember("timeout") && options.getMember("timeout").isNumber()) {
                timeout = Duration.ofMillis(options.getMember("timeout").asLong());
            }

            if (options.hasMember("headers") && options.getMember("headers").hasMembers()) {
                var headers = options.getMember("headers");
                for (var name : headers.getMemberKeys()) {
                    var value = headers.getMember(name);
                    if (value != null && !value.isNull()) {
                        builder.header(name, GuestValues.string(value, "header " + name));
                        if (name.equalsIgnoreCase("content-type")) {
                            contentType = value.asString();
                        }
                    }
                }
            }

            if (options.hasMember("contentType") && options.getMember("contentType").isString()) {
                contentType = options.getMember("contentType").asString();
            }

            if (options.hasMember("auth") && options.getMember("auth").hasMembers()) {
                builder.header("Authorization", authorization(options.getMember("auth")));
            }

            if (options.hasMember("form") && options.getMember("form").hasMembers()) {
                var form = options.getMember("form");
                var encoded = form.getMemberKeys().stream()
                        .map(k -> URLEncoder.encode(k, StandardCharsets.UTF_8) + "="
                                + URLEncoder.encode(GuestValues.string(form.getMember(k), k), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&"));
                publisher = BodyPublishers.ofString(encoded);
                contentType = contentType == null ? "application/x-www-form-urlencoded" : contentType;
            } else if (options.hasMember("json")) {
                publisher = BodyPublishers.ofString(GuestValues.toJson(options.getMember("json")).toString());
                contentType = contentType == null ? "application/json" : contentType;
            }
        }

        if (publisher == null) {
            if (body == null || body.isNull()) {
                publisher = BodyPublishers.noBody();
            } else if (body.isString()) {
                publisher = BodyPublishers.ofString(body.asString());
                contentType = contentType == null ? "text/plain; charset=utf-8" : contentType;
            } else if (body.hasBufferElements() || (body.isHostObject() && body.asHostObject() instanceof byte[])) {
                publisher = BodyPublishers.ofByteArray(GuestValues.toBytes(body));
                contentType = contentType == null ? "application/octet-stream" : contentType;
            } else {
                publisher = BodyPublishers.ofString(GuestValues.toJson(body).toString());
                contentType = contentType == null ? "application/json" : contentType;
            }
        }

        if (contentType != null && !hasHeader(options, "content-type")) {
            builder.header("Content-Type", contentType);
        }

        builder.timeout(timeout).method(method, publisher);

        try {
            var response = client.send(builder.build(), BodyHandlers.ofByteArray());
            return new RhHttpResponse(response.statusCode(), response.headers(), response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(method + " " + url + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(method + " " + url + " interrupted", e);
        }
    }

    private static boolean hasHeader(Value options, String name) {
        if (options == null || !options.hasMembers() || !options.hasMember("headers")) {
            return false;
        }

        var headers = options.getMember("headers");

        return headers.hasMembers() && headers.getMemberKeys().stream().anyMatch(k -> k.equalsIgnoreCase(name));
    }

    private static String authorization(Value auth) {
        if (auth.hasMember("bearer")) {
            return "Bearer " + GuestValues.string(auth.getMember("bearer"), "auth.bearer");
        }

        var basic = auth.hasMember("basic") ? auth.getMember("basic") : auth;

        if (basic.hasMembers() && basic.hasMember("user")) {
            var user = GuestValues.string(basic.getMember("user"), "auth.user");
            var password = basic.hasMember("password") ? GuestValues.string(basic.getMember("password"), "auth.password") : "";
            return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        }

        throw new IllegalArgumentException("auth must be { bearer } or { basic: { user, password } }");
    }
}
