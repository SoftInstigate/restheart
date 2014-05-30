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
package com.softinstigate.restart.handlers.collections;

import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteConcern;
import com.mongodb.util.JSON;
import com.softinstigate.restart.db.MongoDBClientSingleton;
import com.softinstigate.restart.utils.ChannelReader;
import com.softinstigate.restart.utils.HttpStatus;
import com.softinstigate.restart.utils.RequestContext;
import com.softinstigate.restart.utils.ResponseHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author uji
 */
public class PostCollectionsHandler implements HttpHandler
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();
    
    /**
     * Creates a new instance of POSTHandler
     */
    public PostCollectionsHandler()
    {
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        RequestContext c = new RequestContext(exchange);
        
        DBCollection coll = client.getDB(c.getDBName()).getCollection(c.getCollectionName());

        createDocument(coll, ChannelReader.read(exchange.getRequestChannel()));
        
        ResponseHelper.endExchange(exchange, HttpStatus.SC_OK);
    }

    /**
     * method for creating a document in the collection coll
     *
     * @param coll
     * @param content representation for the resource
     */
    private void createDocument(DBCollection coll, String content)
    {
        DBObject obj = (DBObject) JSON.parse(content);

        coll.insert(obj, WriteConcern.ACKNOWLEDGED);
    }
}