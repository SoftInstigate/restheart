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
package com.softinstigate.restheart.security.handlers;

import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.security.AccessManager;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author uji
 */
public class AccessManagerHandler extends PipedHttpHandler
{
    private final AccessManager accessManager;

    /**
     * Creates a new instance of AclHandler
     *
     * @param accessManager
     * @param next
     */
    public AccessManagerHandler(AccessManager accessManager, PipedHttpHandler next)
    {
        super(next);
        this.accessManager = accessManager;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        if (accessManager.isAllowed(exchange, context))
        {
            if (next != null)
                next.handleRequest(exchange, context);
        }
        else
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_UNAUTHORIZED);
        }
    }
}
