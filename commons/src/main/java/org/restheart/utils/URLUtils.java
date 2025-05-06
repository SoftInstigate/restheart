/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class URLUtils {

    protected URLUtils() {
        // protected constructor to hide the implicit public one
    }

    /**
     * given string /ciao/this/has/trailings///// returns
     * /ciao/this/has/trailings
     *
     * @param s
     * @return the string s without the trailing slashes
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
     * decode the percent encoded query string
     *
     * @param qs
     * @return the decoded query string
     */
    public static String decodeQueryString(String qs) {
        return decodeQueryString(qs, "UTF-8");
    }

    /**
     * decode the percent encoded query string
     *
     * @param qs
     * @param enc encoding name
     * @return the decoded query string
     */
    public static String decodeQueryString(String qs, String enc) {
        try {
            return URLDecoder.decode(qs.replace("+", "%2B"), enc).replace("%2B", "+");
        } catch (UnsupportedEncodingException | IllegalArgumentException ex) {
            return qs;
        }
    }

    /**
     * decode the percent encoded query string
     *
     * @param exchange
     * @return the decoded query string
     */
    public static String decodeQueryString(HttpServerExchange exchange) {
        var enc = QueryParameterUtils.getQueryParamEncoding(exchange);
        enc = enc == null ? exchange.getConnection().getUndertowOptions().get(UndertowOptions.URL_CHARSET, StandardCharsets.UTF_8.name()) : enc;
        return decodeQueryString(exchange.getQueryString(), enc);
    }

    /**
     *
     * @param path
     * @return the parent path of path
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
     *
     * @param exchange
     * @return the prefix url of the exchange
     */
    public static String getPrefixUrl(HttpServerExchange exchange) {
        return exchange.getRequestURL().replaceAll(Pattern.quote(exchange.getRelativePath()), "");
    }

    /**
     *
     * @param exchange
     * @param paramsToRemove
     * @return
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
     *
     * @param id
     * @return
     * @throws UnsupportedDocumentIdException
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
