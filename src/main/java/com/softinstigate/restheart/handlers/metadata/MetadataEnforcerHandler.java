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
package com.softinstigate.restheart.handlers.metadata;

import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.json.hal.HALDocumentGenerator;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.RequestContext.METHOD;
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
    /**
     * Creates a new instance of SchemaEnforcerHandler
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
        // check content type
        if (context.getMethod() == RequestContext.METHOD.POST || context.getMethod() == RequestContext.METHOD.PUT || context.getMethod() == RequestContext.METHOD.PATCH)
        {
            HeaderValues contentTypes = exchange.getRequestHeaders().get(Headers.CONTENT_TYPE);

            if (contentTypes == null || contentTypes.isEmpty() || !contentTypes.contains(HALDocumentGenerator.JSON_MEDIA_TYPE) )
            {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, "Contet-Type must be " + HALDocumentGenerator.JSON_MEDIA_TYPE);
                return;
            }
        }
        
        if (context.getDBName() != null)
        {
            if (context.getDbMetadata() == null)
            {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, "db " + context.getDBName() + " does not exist");
                return;
            }
        }
        
        if (context.getCollectionName()!= null && context.getMethod() != METHOD.PUT)
        {
            if (context.getCollectionMetadata() == null)
            {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, "collection " + context.getDBName() + "/" + context.getCollectionName() + " does not exist");
                return;
            }
        }
        
        next.handleRequest(exchange, context);
    }
}