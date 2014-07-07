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
package com.softinstigate.restheart.handlers;

import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;

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
            Logger.getLogger(this.getClass().getName()).log(Level.ERROR, "error handling the request", t);

            ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, t);
        }
    }
}