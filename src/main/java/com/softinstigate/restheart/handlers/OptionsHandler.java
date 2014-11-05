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
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 *
 * @author uji
 */
public class OptionsHandler extends PipedHttpHandler
{
    /**
     * Creates a new instance of OptionsHandler
     * 
     * OPTIONS is used in CORS preflight phase and needs to be outside the security zone (i.e. not Authorization header required)
     * It is important that OPTIONS responds to any resource URL, regardless its existance:
     * This is because OPTIONS http://restheart/employees/tofire/andrea shall not give any information
     * 
     * @param next
     */
    public OptionsHandler(PipedHttpHandler next)
    {
        super(next);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        if (!(context.getMethod() == RequestContext.METHOD.OPTIONS))
        {
            next.handleRequest(exchange, context);
            return;
        }

        if (context.getType() == RequestContext.TYPE.ROOT)
        {
            exchange.getResponseHeaders()
                .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent");

        }
        else if (context.getType() == RequestContext.TYPE.DB)
        {
            exchange.getResponseHeaders()
                    .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, PATCH, DELETE, OPTIONS")
                    .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent");
        }
        else if (context.getType() == RequestContext.TYPE.COLLECTION)
        {
            exchange.getResponseHeaders()
                    .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, POST, PATCH, DELETE, OPTIONS")
                    .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent");
        }
        else if (context.getType() == RequestContext.TYPE.DOCUMENT)
        {
            exchange.getResponseHeaders()
                    .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, PATCH, DELETE, OPTIONS")
                    .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, If-None-Match, Origin, X-Requested-With, User-Agent");
        }
        else if (context.getType() == RequestContext.TYPE.INDEX)
        {
            exchange.getResponseHeaders()
                    .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "PUT")
                    .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent");
        }
        else if (context.getType() == RequestContext.TYPE.COLLECTION_INDEXES)
        {
            exchange.getResponseHeaders()
                    .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                    .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent");
        }

        exchange.setResponseCode(HttpStatus.SC_OK);
        exchange.endExchange();
    }
}
