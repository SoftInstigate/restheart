/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.utils;

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import org.restheart.hal.Representation;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Map;
import org.bson.types.ObjectId;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ResponseHelper {

    /**
     *
     * @param exchange
     * @param code
     */
    public static void endExchange(HttpServerExchange exchange, int code) {
        exchange.setResponseCode(code);
        exchange.endExchange();
    }

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
        exchange.setResponseCode(code);

        String httpStatuText = HttpStatus.getStatusText(code);

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, Representation.HAL_JSON_MEDIA_TYPE);
        exchange.getResponseSender().send(getErrorJsonDocument(exchange.getRequestPath(), code, httpStatuText, message, t));
        exchange.endExchange();
    }

    private static String getErrorJsonDocument(String href, int code, String httpStatusText, String message, Throwable t) {
        Representation rep = new Representation(href);

        rep.addProperty("http status code", code);
        rep.addProperty("http status description", httpStatusText);
        if (message != null) {
            rep.addProperty("message", message);
        }

        Representation nrep = new Representation("#");

        if (t != null) {
            nrep.addProperty("exception", t.getClass().getName());

            if (t.getMessage() != null) {
                nrep.addProperty("exception message", t.getMessage());
            }

            BasicDBList stackTrace = getStackTraceJson(t);

            if (stackTrace != null) {
                nrep.addProperty("stack trace", stackTrace);
            }

            rep.addRepresentation("rh:exception", nrep);
        }

        return rep.toString();
    }

    private static BasicDBList getStackTraceJson(Throwable t) {
        if (t == null || t.getStackTrace() == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        String st = sw.toString();
        st = st.replaceAll("\t", "  ");
        String[] lines = st.split("\n");
        BasicDBList list = new BasicDBList();

        list.addAll(Arrays.asList(lines));
        return list;
    }

    /**
     *
     * @param exchange
     * @param properties
     */
    public static void injectEtagHeader(HttpServerExchange exchange, Map<String, Object> properties) {
        if (properties == null) {
            return;
        }

        Object _etag = properties.get("_etag");

        if (ObjectId.isValid("" + _etag)) {
            ObjectId etag = (ObjectId) _etag;

            exchange.getResponseHeaders().put(Headers.ETAG, etag.toString());
        }
    }

    /**
     *
     * @param exchange
     * @param properties
     */
    public static void injectEtagHeader(HttpServerExchange exchange, DBObject properties) {
        if (properties == null) {
            return;
        }

        Object _etag = properties.get("_etag");

        if (ObjectId.isValid("" + _etag)) {
            ObjectId etag = (ObjectId) _etag;

            exchange.getResponseHeaders().put(Headers.ETAG, etag.toString());
        }
    }
}