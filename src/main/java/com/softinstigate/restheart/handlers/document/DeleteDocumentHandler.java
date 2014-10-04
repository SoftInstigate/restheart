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

import com.softinstigate.restheart.db.DocumentDAO;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.RequestHelper;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class DeleteDocumentHandler extends PipedHttpHandler
{
    private static final Logger logger = LoggerFactory.getLogger(DeleteDocumentHandler.class);
    
    /**
     * Creates a new instance of DeleteDocumentHandler
     */
    public DeleteDocumentHandler()
    {
        super(null);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        ObjectId etag = RequestHelper.getUpdateEtag(exchange);
        
        if (etag == null)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_CONFLICT, "the " + Headers.ETAG + " header must be provided");
            logger.warn("the {} header in required", Headers.ETAG);
            return;
        }
        
        int SC = DocumentDAO.deleteDocument(context.getDBName(), context.getCollectionName(), context.getDocumentId(), etag);
        
        ResponseHelper.endExchange(exchange, SC);
    }
}