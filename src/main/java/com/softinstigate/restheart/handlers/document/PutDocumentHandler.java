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
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.collection.PutCollectionHandler;
import com.softinstigate.restheart.utils.ChannelReader;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class PutDocumentHandler extends PipedHttpHandler
{
    private static final Logger logger = LoggerFactory.getLogger(PutCollectionHandler.class);
    
    private static final BasicDBList fieldsToReturn;
    
    static
    {
        fieldsToReturn = new BasicDBList();
        fieldsToReturn.add("_id");
        fieldsToReturn.add("@created_on");
    }
    
    /**
     * Creates a new instance of EntityResource
     */
    public PutDocumentHandler()
    {
        super(null);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        DBCollection coll = CollectionDAO.getCollection(context.getDBName(), context.getCollectionName());
        
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
            content = new BasicDBObject();
        
        // cannot PUT an array
        if (content instanceof BasicDBList)
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_ACCEPTABLE);
            return;
        }
        
        String id = context.getDocumentId();
        
        if (content.get("_id") == null) 
        {
            content.put("_id", getId(id));
        }
        else if (!content.get("_id").equals(id))
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_ACCEPTABLE);
            logger.warn("PUT not acceptable: _id in content body is different than id in URL");
            return;
        }
        
        ObjectId timestamp = new ObjectId();
        
        content.put("@etag", timestamp);
        
        DBObject old = coll.findAndModify(getIdQuery(id), null, null, false, content, false, true);

        if (old != null)
        {
            // need to add @created_on taken from the old record since id is not an objectid
            if (!ObjectId.isValid(id))
            {
                Object oldTimestamp = old.get("@created_on");
                
                if (ObjectId.isValid("" + oldTimestamp))
                {
                    BasicDBObject createdContet = new BasicDBObject("@created_on", old.get("@created_on"));
                    createdContet.markAsPartialObject();
                    coll.update(getIdQuery(id), new BasicDBObject("$set", createdContet), true, false);
                }
                else
                {
                    logger.warn("could not reset the @create_on field on document with id {} (which is not an ObjectId)" + id);
                }
            }
            
            ResponseHelper.endExchange(exchange, HttpStatus.SC_OK);
        }
        else
        {
            // need to add @created_on since id is not an objectid
            if (!ObjectId.isValid(id))
            {
                BasicDBObject createdContet = new BasicDBObject("@created_on", timestamp);
                createdContet.markAsPartialObject();
                coll.update(getIdQuery(id), new BasicDBObject("$set", createdContet), true, false);
            }
            
            ResponseHelper.endExchange(exchange, HttpStatus.SC_CREATED);
        }
        
        
        
        
    }
    
    private Object getId(String id)
    {
        if (ObjectId.isValid(id))
        {
            return new ObjectId(id);
        }
        else
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