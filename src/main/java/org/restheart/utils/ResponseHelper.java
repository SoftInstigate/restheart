/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart.utils;

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import com.mongodb.util.JSONParseException;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Map;
import org.bson.BsonObjectId;
import org.bson.types.ObjectId;
import org.restheart.hal.Representation;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseHelper {

    /**
     *
     * @param exchange
     * @param code
     */
    public static void endExchange(HttpServerExchange exchange, int code) {
        exchange.setStatusCode(code);
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
    public static void endExchangeWithMessage(HttpServerExchange exchange,
            int code,
            String message,
            Throwable t) {
        exchange.setStatusCode(code);

        String httpStatuText = HttpStatus.getStatusText(code);

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, Representation.HAL_JSON_MEDIA_TYPE);

        exchange.getResponseSender().send(
                getErrorJsonDocument(exchange.getRequestPath(),
                        code,
                        httpStatuText,
                        message,
                        t, false));
        exchange.endExchange();
    }

    private static String getErrorJsonDocument(String href,
            int code,
            String httpStatusText,
            String message,
            Throwable t,
            boolean includeStackTrace) {
        Representation rep = new Representation(href);

        rep.addProperty("http status code", code);
        rep.addProperty("http status description", httpStatusText);
        if (message != null) {
            rep.addProperty("message", avoidEscapedChars(message));
        }

        Representation nrep = new Representation();

        if (t != null) {
            nrep.addProperty("exception", t.getClass().getName());

            if (t.getMessage() != null) {
                if (t instanceof JSONParseException) {
                    nrep.addProperty("exception message", "invalid json");
                } else {
                    nrep.addProperty("exception message", avoidEscapedChars(t.getMessage()));
                }

            }

            if (includeStackTrace) {
                BasicDBList stackTrace = getStackTraceJson(t);

                if (stackTrace != null) {
                    nrep.addProperty("stack trace", stackTrace);
                }
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
        st = avoidEscapedChars(st);
        String[] lines = st.split("\n");

        BasicDBList list = new BasicDBList();

        list.addAll(Arrays.asList(lines));
        return list;
    }

    private static String avoidEscapedChars(String s) {
        return s == null
                ? null
                : s
                .replaceAll("\"", "'")
                .replaceAll("\t", "  ");
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

        if (_etag == null || !(_etag instanceof ObjectId)) {
            return;
        }

        exchange.getResponseHeaders().put(Headers.ETAG, _etag.toString());
    }

    /**
     *
     * @param exchange
     * @param etag
     */
    public static void injectEtagHeader(HttpServerExchange exchange, Object etag) {
        if (etag == null) {
        } else if (etag instanceof ObjectId) {
            exchange.getResponseHeaders().put(
                    Headers.ETAG, etag.toString());
        } else if (etag instanceof BsonObjectId) {
            exchange.getResponseHeaders().put(
                    Headers.ETAG, ((BsonObjectId) etag)
                    .getValue()
                    .toString());
        }
    }

    /**
     *
     * @param code mongodb error code from MongoException.getCode()
     * @return
     */
    public static int getHttpStatusFromErrorCode(int code) {
        switch (code) {
            case 13:
                // The MongoDB user does not have enough permissions to execute this operation.
                return HttpStatus.SC_FORBIDDEN;
            case 18:
                // Wrong MongoDB user credentials
                return HttpStatus.SC_FORBIDDEN;
            case 61:
                // Write request for sharded collection must specify the shardkey.
                return HttpStatus.SC_BAD_REQUEST;
            case 66:
                // Update tried to change the immutable shardkey
                return HttpStatus.SC_FORBIDDEN;
            case 121:
                //Document failed validation
                return HttpStatus.SC_BAD_REQUEST;
            default:
                // Other
                return HttpStatus.SC_INTERNAL_SERVER_ERROR;
        }
    }

    /**
     *
     * @param code mongodb error code from MongoException.getCode()
     * @return
     */
    public static String getMessageFromErrorCode(int code) {
        switch (code) {
            case 13:
                return "The MongoDB user does not have enough permissions to execute this operation.";
            case 18:
                return "Wrong MongoDB user credentials (wrong password or need to specify the authentication dababase with 'authSource=<db>' option in mongo-uri).";
            case 61:
                return "Write request for sharded collection must specify the shardkey. Use the 'shardkey' query parameter.";
            case 66:
                return "Update tried to change the immutable shardkey.";
            case 121:
                //Document failed validation
                return "Document failed collection validation.";
            default:
                return "Error handling the request, see log for more information";
        }
    }

    private ResponseHelper() {
    }
}
