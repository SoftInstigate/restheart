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
package com.softinstigate.restheart.handlers.document;

import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.RequestContext;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 *
 * @author uji
 */
public class OptionsDocumentHandler extends PipedHttpHandler
{
    /**
     * Creates a new instance of GetRootHandler
     */
    public OptionsDocumentHandler()
    {
        super(null);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        exchange.getResponseHeaders()
                .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, PATCH, DELETE, OPTIONS")
                .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Content-Length, Content-Type, Host, If-Match, If-None-Match, Origin, X-Requested-With, User-Agent");

        exchange.setResponseCode(HttpStatus.SC_OK);
        exchange.endExchange();
    }
}
