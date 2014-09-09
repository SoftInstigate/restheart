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
package com.softinstigate.restheart.handlers.collection;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.utils.ChannelReader;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.JSONHelper;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.bson.types.ObjectId;

/**
 *
 * @author uji
 */
public class PostCollectionHandler extends PutCollectionHandler implements HttpHandler 
{
    /**
     * Creates a new instance of PostCollectionHandler
     */
    public PostCollectionHandler()
    {
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        RequestContext rc = new RequestContext(exchange);

        DBCollection coll = CollectionDAO.getCollection(rc.getDBName(), rc.getCollectionName());

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
        
        // cannot POST an array
        if (content instanceof BasicDBList)
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_ACCEPTABLE);
            return;
        }

        Object id = content.get("_id");
        
        if (id == null)
        {
            id = new ObjectId();
        }

        WriteResult wr = coll.update(getIdQuery(id), content, true, false);
        
        exchange.getResponseHeaders().add(HttpString.tryFromString("Location"), JSONHelper.getReference(exchange.getRequestURL(), id.toString()).toString());

        if (wr.isUpdateOfExisting())
            ResponseHelper.endExchange(exchange, HttpStatus.SC_OK);
        else
            ResponseHelper.endExchange(exchange, HttpStatus.SC_CREATED);
    }
    
    private Object getIdFromString(String id)
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
    
    private BasicDBObject getIdQuery(Object id)
    {
        if (id instanceof ObjectId)
            return new BasicDBObject("_id", id);
        else
            return  new BasicDBObject("_id", getIdFromString((String) id));
    }
}
