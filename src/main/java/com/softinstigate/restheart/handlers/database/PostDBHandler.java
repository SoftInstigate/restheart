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
package com.softinstigate.restheart.handlers.database;

import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author uji
 */
public class PostDBHandler implements HttpHandler
{
    /**
     * Creates a new instance of POSTHandler
     */
    public PostDBHandler()
    {
    }

    /**
     * creating collections via post is not supported by design
     */
    @Override
    public void handleRequest(HttpServerExchange exchange)
    {
        ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_IMPLEMENTED);
    }
}