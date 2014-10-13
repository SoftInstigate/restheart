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
package com.softinstigate.restheart.handlers.properties;

import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.handlers.RequestContext.METHOD;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;

/**
 *
 * @author uji
 */
public class MetadataEnforcerHandler extends PipedHttpHandler
{
    private final static String JSON_MEDIA_TYPE = "application/json";
    /**
     * Creates a new instance of MetadataEnforcerHandler
     *
     * @param next
     */
    public MetadataEnforcerHandler(PipedHttpHandler next)
    {
        super(next);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        if (context.getDbProps() == null
                && !(context.getType() == RequestContext.TYPE.DB && context.getMethod() == METHOD.PUT)
                && (context.getType() != RequestContext.TYPE.ROOT))
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, "db " + context.getDBName() + " does not exist");
            return;
        }

        if (context.getCollectionProps() == null
                && !(context.getType() == RequestContext.TYPE.COLLECTION && context.getMethod() == METHOD.PUT)
                && (context.getType() != RequestContext.TYPE.ROOT)
                && (context.getType() != RequestContext.TYPE.DB))
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, "collection " + context.getDBName() + "/" + context.getCollectionName() + " does not exist");
            return;
        }

        next.handleRequest(exchange, context);
    }
}
