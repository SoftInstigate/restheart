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
package com.softinstigate.restart;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 *
 * @author uji
 */
public class RequestContextDecoderHandler implements HttpHandler
{
    private final HttpHandler next;
    
    /**
     * Creates a new instance of EntityResource
     * @param next
     */
    public RequestContextDecoderHandler(HttpHandler next)
    {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        String path = exchange.getRequestPath();
        
        String[] pathTokens = path.split("/");
        
        //    /mgmt/db/collection
        //    /api/db/collection
        
        if (pathTokens.length < 4)
        {
            exchange.setResponseCode(404);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send("not found");
            exchange.endExchange();
            return;
        }
        
        exchange.getRequestHeaders().put(HttpString.tryFromString("db"), pathTokens[2]);
        exchange.getRequestHeaders().put(HttpString.tryFromString("collection"), pathTokens[3]);
        
        if (pathTokens.length > 4)
            exchange.getRequestHeaders().put(HttpString.tryFromString("id"), pathTokens[4]);
        
        next.handleRequest(exchange);
    }
}