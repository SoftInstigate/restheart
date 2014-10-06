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

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.json.hal.HALDocumentSender;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.utils.RequestHelper;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class GetDocumentHandler extends PipedHttpHandler
{
    private static final Logger logger = LoggerFactory.getLogger(GetDocumentHandler.class);

    /**
     * Creates a new instance of GetDocumentHandler
     */
    public GetDocumentHandler()
    {
        super(null);
    }


    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        ObjectId oid;
        String   sid;
        
        if (ObjectId.isValid(context.getDocumentId()))
        {
            sid = null;
            oid = new ObjectId(context.getDocumentId());
        }
        else
        {
            // the id is not an object id
            sid = context.getDocumentId();
            oid = null;
        }
        
        BasicDBObject query;
        
        if (oid != null)
        {
            query = new BasicDBObject("_id", oid);
        }
        else
        {
            query = new BasicDBObject("_id", sid);
        }
        
        DBObject document = CollectionDAO.getCollection(context.getDBName(), context.getCollectionName()).findOne(query);

        if (document == null)
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return;
        }
        
        Object etag = document.get("@etag");
        
        if (etag != null && ObjectId.isValid("" + etag))
        {
            ObjectId _etag = new ObjectId("" + etag);
            
            document.put("@lastupdated_on", Instant.ofEpochSecond(_etag.getTimestamp()).toString());
            
            // in case the request contains the IF_NONE_MATCH header with the current etag value, just return 304 NOT_MODIFIED code
            if (false && RequestHelper.checkReadEtag(exchange, etag.toString()))
            {
                ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_MODIFIED);
                return;
            }
        }

        ResponseHelper.injectEtagHeader(exchange, document);
        
        HALDocumentSender.sendDocument(exchange, context, document);
        exchange.setResponseCode(HttpStatus.SC_OK);
        exchange.endExchange();
    }
}