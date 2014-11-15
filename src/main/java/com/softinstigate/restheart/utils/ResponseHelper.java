/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart.utils;

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import com.softinstigate.restheart.hal.Representation;
import com.softinstigate.restheart.handlers.RequestContext;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Map;
import org.bson.types.ObjectId;

/**
 *
 * @author Andrea Di Cesare
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
        }

        if (t != null && t.getMessage() != null) {
            nrep.addProperty("exception message", t.getMessage());
        }

        BasicDBList stackTrace = getStackTraceJson(t);

        if (stackTrace != null) {
            nrep.addProperty("stack trace", stackTrace);
        }

        rep.addRepresentation("rh:exception", nrep);

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

    /**
     *
     * @param rep
     * @param exchange
     * @param context
     */
    public static void injectWarnings(Representation rep, HttpServerExchange exchange, RequestContext context) {
        if (context.getWarnings() != null && !context.getWarnings().isEmpty()) {
            context.getWarnings().stream().map((warning) -> {
                Representation nrep = new Representation("#warnings");
                nrep.addProperty("message", warning);
                return nrep;
            }).forEach((nrep) -> {
                rep.addRepresentation("rh:warnings", nrep);
            });
        }
    }
}
