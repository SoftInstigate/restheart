/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.uiam.utils;

import io.undertow.server.HttpServerExchange;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Deque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class URLUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(URLUtils.class);

     /** given string /ciao/this/has/trailings///// returns
     * /ciao/this/has/trailings
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
            return URLDecoder.decode(
                    qs.replace("+", "%2B"), "UTF-8").replace("%2B", "+");
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
            return path.substring(0, lastSlashPos); //strip off the slash
        } else if (lastSlashPos == 0) {
            return "/";
        } else {
            return ""; //we expect people to add  + "/somedir on their own
        }
    }

    /**
     *
     * @param exchange
     * @return
     */
    static public String getPrefixUrl(HttpServerExchange exchange) {
        return exchange.getRequestURL()
                .replaceAll(exchange.getRelativePath(), "");
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
