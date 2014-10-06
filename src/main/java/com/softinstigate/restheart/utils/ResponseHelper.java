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

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
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
        exchange.endExchange();
    }

    public static void endExchangeWithMessage(HttpServerExchange exchange, int code, String message)
    {
        endExchangeWithMessage(exchange, code, message, null);
    }
    
    public static void endExchangeWithMessage(HttpServerExchange exchange, int code, String message, Throwable t)
    {
        exchange.setResponseCode(code);

        String httpStatuText = HttpStatus.getStatusText(code);
            
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send(getErrorJsonDocument(code, httpStatuText, message, t));
        exchange.endExchange();
    }
    
    private static String getErrorJsonDocument(int code, String httpStatusText, String message, Throwable t)
    {
        JsonObject root = new JsonObject();
        
        root.add("http status code", code);
        root.add("http status description", httpStatusText);
        if (message != null)
            root.add("message", message);
        
        JsonArray stackTrace = getStackTraceJson(t);
        
        if (t != null && t.getMessage() != null)
            root.add("exception", t.toString());
        
        if (t != null)
            root.add("exception", t.getClass().getName());
        
        if (t != null && t.getMessage() != null)
            root.add("exception message", t.getMessage());
        
        if (stackTrace != null)
            root.add("stack trace", getStackTraceJson(t));
        
        return root.toString();
    }
    
    private static JsonArray getStackTraceJson(Throwable t)
    {
        if (t == null || t.getStackTrace() == null)
            return null;
        
        JsonArray root = new JsonArray();
        
        for (StackTraceElement e: t.getStackTrace())
        {
            root.add(e.toString());
        }
        
        return root;
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
    
    public static void injectEtagHeader(HttpServerExchange exchange, DBObject metadata)
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
