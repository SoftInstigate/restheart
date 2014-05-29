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
import io.undertow.util.Methods;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author uji
 */
public class ErrorHandler implements HttpHandler
{
    private final HttpHandler next;

    /**
     * Creates a new instance of EntityResource
     *
     * @param next
     */
    public ErrorHandler(HttpHandler next)
    {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        try
        {
            next.handleRequest(exchange);
        }
        catch (Throwable t)
        {
            HttpString method = exchange.getRequestMethod();

            exchange.setResponseCode(500);

            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, t.getMessage());

            if (t.getStackTrace() != null)
            {
                Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, Arrays.toString(t.getStackTrace()));

                if (method.equals(Methods.GET))
                {
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                    exchange.getResponseSender().send(t.getMessage());
                    exchange.getResponseSender().send(Arrays.toString(t.getStackTrace()));
                    exchange.endExchange();
                }
            }
        }
    }
}