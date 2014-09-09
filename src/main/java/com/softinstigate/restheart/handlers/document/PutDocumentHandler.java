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

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteResult;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.handlers.collection.PutCollectionHandler;
import com.softinstigate.restheart.utils.ChannelReader;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class PutDocumentHandler implements HttpHandler
{
    private static final Logger logger = LoggerFactory.getLogger(PutCollectionHandler.class);
    
    /**
     * Creates a new instance of EntityResource
     */
    public PutDocumentHandler()
    {
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        RequestContext rc = new RequestContext(exchange);

        DBCollection coll = CollectionDAO.getCollection(rc.getDBName(), rc.getDBName());
        
        String _content = ChannelReader.read(exchange.getRequestChannel());

        DBObject content;

        try
        {
            content = (DBObject) JSON.parse(_content);
        }
        catch (JSONParseException ex)
        {
            ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_NOT_ACCEPTABLE, ex);
            return;
        }
        
        if (content == null)
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_ACCEPTABLE);
            return;
        }
        
        // cannot PATCH an array
        if (content instanceof BasicDBList)
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_ACCEPTABLE);
            return;
        }
        
        String id = rc.getDocumentId();
        
        if (content.get("_id") == null)
        {
            content.put("_id", id);
        }
        else if (!content.get("_id").equals(id))
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_ACCEPTABLE);
            logger.warn("PUT not acceptable since _id in content body is different than id in URL");
            return;
        }
        
        WriteResult wr = coll.update(getIdQuery(id), content, true, false);

        if (wr.isUpdateOfExisting())
            ResponseHelper.endExchange(exchange, HttpStatus.SC_OK);
        else
            ResponseHelper.endExchange(exchange, HttpStatus.SC_CREATED);
    }
    
    private Object getId(String id)
    {
        try
        {
            return new ObjectId(id);
        }
        catch(IllegalArgumentException ex)
        {
            // the id is not an object id
            return id;
        }
    }
    
    private BasicDBObject getIdQuery(String id)
    {
        return new BasicDBObject("_id", getId(id));
    }
}