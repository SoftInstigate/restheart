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

import com.softinstigate.restheart.handlers.document.*;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;
import com.softinstigate.restheart.db.DocumentDAO;
import com.softinstigate.restheart.db.IndexDAO;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.collection.PutCollectionHandler;
import com.softinstigate.restheart.utils.ChannelReader;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.RequestHelper;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class PutIndexHandler extends PipedHttpHandler
{
    private static final Logger logger = LoggerFactory.getLogger(PutCollectionHandler.class);
    
    private static final BasicDBObject fieldsToReturn;
    
    static
    {
        fieldsToReturn = new BasicDBObject();
        fieldsToReturn.put("_id", 1);
        fieldsToReturn.put("@created_on", 1);
    }
    
    /**
     * Creates a new instance of PutDocumentHandler
     */
    public PutIndexHandler()
    {
        super(null);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        String db = context.getDBName();
        String co = context.getCollectionName();
        String id = context.getIndexId();
        
        if (id.startsWith("@"))
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "index name cannot start with @", null);
            return;
        }
        
        String _content = ChannelReader.read(exchange.getRequestChannel());

        DBObject content;

        try
        {
            content = (DBObject) JSON.parse(_content);
        }
        catch (JSONParseException ex)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "json data is invalid", ex);
            return;
        }
        
        if (content == null)
            content = new BasicDBObject();
        
        // cannot PUT an array
        if (content instanceof BasicDBList)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "json data cannot be an array");
            return;
        }
        
        DBObject keys = (DBObject) content.get("keys");
        DBObject ops = (DBObject) content.get("ops");
        
        if (keys == null)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "wrong request, content must include 'keys' object", null);
            return;
        }
        
        if (ops == null)
        {
            ops = new BasicDBObject();
        }
        
        ops.put("name", id);
        
        try
        {
            IndexDAO.createIndex(db, co, keys, ops);
        }
        catch(Throwable t)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "error creating the index", t);
        }
        
        ResponseHelper.endExchange(exchange, HttpStatus.SC_CREATED);
    }
}