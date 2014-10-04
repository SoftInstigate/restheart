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
package com.softinstigate.restheart.handlers.indexes;

import com.softinstigate.restheart.db.IndexDAO;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class DeleteIndexHandler extends PipedHttpHandler
{
    private static final Logger logger = LoggerFactory.getLogger(DeleteIndexHandler.class);
    
    /**
     * Creates a new instance of DeleteDocumentHandler
     */
    public DeleteIndexHandler()
    {
        super(null);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        String db = context.getDBName();
        String co = context.getCollectionName();
        
        String id = context.getIndexId();
        
        if (id.startsWith("@") || id.equals("_id_"))
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_UNAUTHORIZED, id + " is a default index and cannot be deleted", null);
            return;
        }
        
        IndexDAO.deleteIndex(db, co, id);
        
        ResponseHelper.endExchange(exchange, HttpStatus.SC_GONE);
    }
}