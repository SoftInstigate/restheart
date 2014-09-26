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

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.bson.types.ObjectId;

/**
 *
 * @author uji
 */
public class ResponseHelper
{
    public static void endExchange(HttpServerExchange exchange, int code)
    {
        exchange.setResponseCode(code);

        if (Methods.GET.equals(exchange.getRequestMethod()))
        {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");

            String message = HttpStatus.getStatusText(code);

            if (message != null)
            {
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, message.length());
                exchange.getResponseSender().send(message);
            }
        }

        exchange.endExchange();
    }

    public static void endExchangeWithError(HttpServerExchange exchange, int code, Throwable t)
    {
        exchange.setResponseCode(code);

        if (t.getStackTrace() != null)
        {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);

            String message = HttpStatus.getStatusText(code);
            
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send(message + "\n" + t.getMessage() + "\n" + sw.toString());
            exchange.endExchange();
        }

        exchange.endExchange();
    }
    
    public static void injectEtagHeader(HttpServerExchange exchange, Map<String, Object> metadata)
    {
        if (metadata == null)
            return;
        
        Object _etag = metadata.get("@etag");
        
        if (ObjectId.isValid("" + _etag))
        {
            ObjectId etag = (ObjectId) _etag;
            
            exchange.getResponseHeaders().put(Headers.ETAG, etag.toString());
        }
    }
}
