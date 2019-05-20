/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Deque;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class URLUtils {

    /**
     * given string /ciao/this/has/trailings///// returns /ciao/this/has/trailings
     *
     * @param s
     * @return the string s without the trailing slashes
     */
    static public String removeTrailingSlashes(String s) {
        if (s == null || s.length() < 2) {
            return s;
        }

        if (s.trim().charAt(s.length() - 1) == '/') {
            return removeTrailingSlashes(s.substring(0, s.length() - 1));
        } else {
            return s.trim();
        }
    }

    /**
     * decode the percent encoded query string
     *
     * @param qs
     * @return the undecoded string
     */
    static public String decodeQueryString(String qs) {
        try {
            return URLDecoder.decode(qs.replace("+", "%2B"), "UTF-8").replace("%2B", "+");
        } catch (UnsupportedEncodingException ex) {
            return null;
        }
    }

    /**
     *
     * @param path
     * @return
     */
    static public String getParentPath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return path;
        }

        int lastSlashPos = path.lastIndexOf('/');

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
     * @return
     */
    static public String getPrefixUrl(HttpServerExchange exchange) {
        return exchange.getRequestURL().replaceAll(exchange.getRelativePath(), "");
    }

    /**
     *
     * @param exchange
     * @param paramsToRemove
     * @return
     */
    public static String getQueryStringRemovingParams(HttpServerExchange exchange, String... paramsToRemove) {
        String ret = exchange.getQueryString();

        if (ret == null || ret.isEmpty() || paramsToRemove == null) {
            return ret;
        }

        for (String key : paramsToRemove) {
            Deque<String> values = exchange.getQueryParameters().get(key);

            if (values != null) {
                for (String value : values) {
                    ret = ret.replaceAll(key + "=" + value + "&", "");
                    ret = ret.replaceAll(key + "=" + value + "$", "");
                }
            }
        }

        return ret;
    }

    private URLUtils() {
    }
}
