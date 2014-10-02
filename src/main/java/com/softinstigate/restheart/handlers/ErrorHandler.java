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

import com.mongodb.CommandFailureException;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class ErrorHandler implements HttpHandler
{
    private final HttpHandler next;
    
    private Logger logger = LoggerFactory.getLogger(ErrorHandler.class);

    /**
     * Creates a new instance of ErrorHandler
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
        catch(CommandFailureException cfe)
        {
            logger.error("mongodb command failure handling the request", cfe);
            
            Object errmsg = cfe.getCommandResult().get("errmsg");
            
            if ("unauthorized".equals(errmsg))
                ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "mongodb db user is not allowed to execute the command. give it more permissions or hide this resource via mongo-mounts", cfe);
            else
                ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "error handling the request", cfe);
                
        }
        catch (Throwable t)
        {
            logger.error("error handling the request", t);

            ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "error handling the request", t);
        }
    }
}