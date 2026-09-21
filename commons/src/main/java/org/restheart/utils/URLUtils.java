/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.bson.BsonValue;
import org.restheart.exchange.Request;
import org.restheart.exchange.UnsupportedDocumentIdException;

import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.QueryParameterUtils;

/**
 * Utility class for URL manipulation and processing operations.
 * Provides methods for URL decoding, path manipulation, query string processing,
 * and parameter handling for HTTP server exchanges.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class URLUtils {

    protected URLUtils() {
        // protected constructor to hide the implicit public one
    }

    /**
     * The attached parameter a multi-tenant deployment uses to say, per request, the public base
     * URL of the tenant the request is for — scheme and host as the client wrote them.
     *
     * <p>Named for the MCP server, which was its first reader, and kept that way because the name
     * is a contract with the deployments that attach it. It answers a question broader than MCP:
     * what this tenant is called from outside. See {@link #publicBaseUrl}.
     */
    public static final String PUBLIC_BASE_URL_OVERRIDE = "override-ai-mcp-public-base-url";

    /**
     * The base URL an external client reaches this instance at: {@code configured} when the
     * operator set one, else what the request says it came in on.
     *
     * <p>Order, and each step is there for a deployment that exists: the configured value first,
     * because behind a proxy only the operator knows the public name; then {@code X-Forwarded-Proto}
     * and {@code X-Forwarded-Host}, which is what a proxy that terminates TLS leaves behind; then the
     * exchange's own scheme and {@code Host}. An empty string when even the host is missing, which
     * is not a URL and is meant to be noticed.
     *
     * <p>The two forwarded headers are read one by one, not as a pair. A proxy that preserves the
     * {@code Host} it was called with has no reason to add {@code X-Forwarded-Host}, and several do
     * exactly that; requiring both threw the forwarded scheme away and answered {@code http://} for
     * a site served over {@code https://}. Of a comma-separated {@code X-Forwarded-Proto}, left by a
     * chain of proxies, the first entry is the one the client used.
     *
     * <p>Shared because more than one service has to answer the same question — what to call
     * myself in something I hand out — and two answers that drift produce a document naming a
     * host that another document does not.
     *
     * @param configured the operator's own value, or {@code null}/blank when unset
     * @param exchange the request being served
     */
    public static String externalBaseUrl(String configured, HttpServerExchange exchange) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }

        var headers = exchange.getRequestHeaders();
        var forwardedHost = headers.getFirst("X-Forwarded-Host");
        var host = forwardedHost != null && !forwardedHost.isBlank() ? forwardedHost.strip() : headers.getFirst("Host");

        if (host == null || host.isBlank()) {
            return "";
        }

        var forwardedProto = headers.getFirst("X-Forwarded-Proto");
        var scheme = forwardedProto != null && !forwardedProto.isBlank()
                ? forwardedProto.split(",")[0].strip()
                : exchange.getRequestScheme();

        return scheme + "://" + host;
    }

    /**
     * {@link #externalBaseUrl}, except that a base URL attached to the request as
     * {@link #PUBLIC_BASE_URL_OVERRIDE} wins over everything.
     *
     * <p>On a node that serves many tenants, one per host, a configured value cannot be right for
     * all of them, and the forwarded headers depend on a proxy the node does not control. The
     * deployment knows which tenant a request is for, attaches its public base URL before
     * authentication, and every document that names this tenant — the MCP catalogue, the
     * protected-resource metadata, the challenge that points at it — then names it the same way.
     *
     * @param configured the operator's own value, or {@code null}/blank when unset
     * @param exchange the request being served
     */
    public static String publicBaseUrl(String configured, HttpServerExchange exchange) {
        var attached = exchange.getAttachment(Request.ATTACHED_PARAMS_KEY);

        if (attached != null && attached.get(PUBLIC_BASE_URL_OVERRIDE) instanceof String override && !override.isBlank()) {
            return override;
        }

        return externalBaseUrl(configured, exchange);
    }

    /**
     * Removes trailing slashes from a given string path.
     * For example, given string "/ciao/this/has/trailings/////" returns
     * "/ciao/this/has/trailings".
     *
     * @param s the string to process
     * @return the string without trailing slashes, or null if input is null
     */
    public static String removeTrailingSlashes(String s) {
        if (s == null) {
            return null;
        }

        s = s.strip();

        if (s.length() < 2) {
            return s;
        }

        if (s.charAt(s.length() - 1) == '/') {
            return removeTrailingSlashes(s.substring(0, s.length() - 1));
        } else {
            return s;
        }
    }

    /**
     * Decodes the percent-encoded query string using UTF-8 encoding.
     * This method properly handles the '+' character encoding.
     *
     * @param qs the query string to decode
     * @return the decoded query string
     */
    public static String decodeQueryString(String qs) {
        return decodeQueryString(qs, "UTF-8");
    }

    /**
     * Decodes the percent-encoded query string using the specified encoding.
     * This method properly handles the '+' character encoding and falls back
     * to returning the original string if decoding fails.
     *
     * @param qs the query string to decode
     * @param enc the encoding name to use for decoding
     * @return the decoded query string, or the original string if decoding fails
     */
    public static String decodeQueryString(String qs, String enc) {
        try {
            return URLDecoder.decode(qs.replace("+", "%2B"), enc).replace("%2B", "+");
        } catch (UnsupportedEncodingException | IllegalArgumentException ex) {
            return qs;
        }
    }

    /**
     * Decodes the percent-encoded query string from an HTTP server exchange.
     * Uses the encoding specified in the exchange, or falls back to UTF-8.
     *
     * @param exchange the HTTP server exchange containing the query string
     * @return the decoded query string
     */
    public static String decodeQueryString(HttpServerExchange exchange) {
        var enc = QueryParameterUtils.getQueryParamEncoding(exchange);
        enc = enc == null ? exchange.getConnection().getUndertowOptions().get(UndertowOptions.URL_CHARSET, StandardCharsets.UTF_8.name()) : enc;
        return decodeQueryString(exchange.getQueryString(), enc);
    }

    /**
     * Gets the parent path of the given path by removing the last path segment.
     * For example, "/a/b/c" returns "/a/b", and "/a" returns "/".
     *
     * @param path the path to get the parent of
     * @return the parent path, or the original path if it's null, empty, or "/"
     */
    public static String getParentPath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return path;
        }

        var lastSlashPos = path.lastIndexOf('/');

        if (lastSlashPos > 0) {
            return path.substring(0, lastSlashPos); // strip off the slash
        } else if (lastSlashPos == 0) {
            return "/";
        } else {
            return ""; // we expect people to add + "/somedir on their own
        }
    }

    /**
     * Gets the prefix URL of the HTTP server exchange by removing the relative path
     * from the full request URL.
     *
     * @param exchange the HTTP server exchange
     * @return the prefix URL (scheme, host, port, and context path)
     */
    public static String getPrefixUrl(HttpServerExchange exchange) {
        return exchange.getRequestURL().replaceAll(Pattern.quote(exchange.getRelativePath()), "");
    }

    /**
     * Gets the query string from the exchange with specified parameters removed.
     * This method removes all occurrences of the specified parameter names and
     * their values from the query string.
     *
     * @param exchange the HTTP server exchange containing the query string
     * @param paramsToRemove array of parameter names to remove from the query string
     * @return the query string with specified parameters removed
     */
    public static String getQueryStringRemovingParams(HttpServerExchange exchange, String... paramsToRemove) {
        var ret = exchange.getQueryString();

        if (ret == null || ret.isEmpty() || paramsToRemove == null) {
            return ret;
        }

        for (var key : paramsToRemove) {
            var values = exchange.getQueryParameters().get(key);

            if (values != null) {
                for (String value : values) {
                    ret = ret.replaceAll(Pattern.quote(key + "=" + value + "&"), "");
                    ret = ret.replaceAll(Pattern.quote(key + "=" + value + "$"), "");
                }
            }
        }

        return ret;
    }

    /**
     * Converts a BSON value ID to its string representation for URL usage.
     * String values are wrapped in single quotes, while other types are
     * converted to their JSON representation.
     *
     * @param id the BSON value representing the document ID
     * @return the string representation of the ID suitable for URLs
     * @throws UnsupportedDocumentIdException if the ID type is not supported
     */
    public static String getIdString(BsonValue id) throws UnsupportedDocumentIdException {
        if (id == null) {
            return null;
        } else if (id.isString()) {
            return "'" + id.asString().getValue() + "'";
        } else {
            return BsonUtils.minify(BsonUtils.toJson(id).replace("\"", "'")).toString();
        }
    }
}
