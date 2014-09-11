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
import com.mongodb.DBCollection;
import com.mongodb.WriteResult;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import org.bson.types.ObjectId;

/**
 *
 * @author uji
 */
public class DeleteDocumentHandler extends PipedHttpHandler
{
    /**
     * Creates a new instance of EntityResource
     */
    public DeleteDocumentHandler()
    {
        super(null);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        DBCollection coll = CollectionDAO.getCollection(context.getDBName(), context.getCollectionName());
        
        ObjectId oid;
        String   sid;
        
        try
        {
            oid = new ObjectId(context.getDocumentId());
            sid = null;
        }
        catch(IllegalArgumentException ex)
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
        
        WriteResult wr = coll.remove(query);
        
        if (wr.getN() <1)
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
        }
        else
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_GONE);
        }
    }
}