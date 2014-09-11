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
package com.softinstigate.restheart.handlers.root;

import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author uji
 */
public class PutRootHandler extends PipedHttpHandler
{
    /**
     * Creates a new instance of PutRootHandler
     */
    public PutRootHandler()
    {
        super(null);
    }

    /**
     * updating the root resource via API is not supported by desing
     * @param exchange
     * @param context
     * @throws java.lang.Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_IMPLEMENTED);
    }
}