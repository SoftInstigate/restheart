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
        exchange.getResponseSender().send(getErrorJsonDocument(exchange.getRequestPath(), code, httpStatuText, message, t));
        exchange.endExchange();
    }
    
    private static String getErrorJsonDocument(String href, int code, String httpStatusText, String message, Throwable t)
    {
        Representation rep = new Representation(href);
        
        rep.addProperty("http status code", code);
        rep.addProperty("http status description", httpStatusText);
        if (message != null)
            rep.addProperty("message", message);

        if (t != null)
            rep.addProperty("exception", t.getClass().getName());
        
        if (t!= null && t.getMessage() != null)
        {
            rep.addProperty("exception message", t.getMessage());
        }
            
        BasicDBList stackTrace = getStackTraceJson(t);
        
        if (stackTrace != null)
            rep.addProperty("stack trace", stackTrace);
        
        return rep.toString();
    }
    
    private static BasicDBList getStackTraceJson(Throwable t)
    {
        if (t == null || t.getStackTrace() == null)
            return null;
        
        BasicDBList list = new BasicDBList();
        
        for (StackTraceElement e: t.getStackTrace())
        {
            list.add(e.toString());
        }
        
        return list;
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
