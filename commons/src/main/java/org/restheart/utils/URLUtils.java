/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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
