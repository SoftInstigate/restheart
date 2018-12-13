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

import java.io.PrintWriter;
import java.io.StringWriter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.uiam.handlers.ExchangeHelper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseHelper {

    /**
     *
     * @param exchange
     * @param code
     * @param message
     */
    public static void endExchangeWithMessage(HttpServerExchange exchange, int code, String message) {
        endExchangeWithMessage(exchange, code, message, null);
    }

    /**
     *
     * @param exchange
     * @param code
     * @param message
     * @param t
     */
    public static void endExchangeWithMessage(HttpServerExchange exchange, int code, String message, Throwable t) {
        var hex = new ExchangeHelper(exchange);

        hex.setResponseStatusCode(code);

        String httpStatusText = HttpStatus.getStatusText(code);

        hex.setInError(true);

        exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/json");

        hex.setResponseContent(
                getErrorJsonDocument(exchange.getRequestPath(), code, httpStatusText, message, t, false));
    }

    /**
     *
     * @param exchange
     * @param code
     * @param body
     */
    public static void endExchangeWithRepresentation(HttpServerExchange exchange, int code, JsonObject body) {
        var hex = new ExchangeHelper(exchange);

        hex.setResponseStatusCode(code);

        hex.setInError(true);
        hex.setResponseContent(body);
    }

    public static JsonObject getErrorJsonDocument(String href, int code, String httpStatusText, String message,
            Throwable t, boolean includeStackTrace) {
        JsonObject resp = new JsonObject();

        resp.add("http status code", new JsonPrimitive(code));
        resp.add("http status description", new JsonPrimitive(httpStatusText));
        if (message != null) {
            resp.add("message", new JsonPrimitive(avoidEscapedChars(message)));
        }

        JsonObject nrep = new JsonObject();

        if (t != null) {
            nrep.add("class", new JsonPrimitive(t.getClass().getName()));

            if (includeStackTrace) {
                JsonArray stackTrace = getStackTraceJson(t);

                if (stackTrace != null) {
                    nrep.add("stack trace", stackTrace);
                }
            }

            resp.add("exception", nrep);
        }

        return resp;
    }

    private static JsonArray getStackTraceJson(Throwable t) {
        if (t == null || t.getStackTrace() == null) {
            return null;
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        String st = sw.toString();
        st = avoidEscapedChars(st);
        String[] lines = st.split("\n");

        JsonArray list = new JsonArray();

        for (String line : lines) {
            list.add(new JsonPrimitive(line));
        }

        return list;
    }

    private static String avoidEscapedChars(String s) {
        return s == null ? null : s.replaceAll("\"", "'").replaceAll("\t", "  ");
    }

    private ResponseHelper() {
    }
}
