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

import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.utils.RequestContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author uji
 */
public class DeleteCollectionHandler implements HttpHandler
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();
    
    /**
     * Creates a new instance of EntityResource
     */
    public DeleteCollectionHandler()
    {
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        RequestContext c = new RequestContext(exchange);
        
        DBCollection coll = client.getDB(c.getDBName()).getCollection(c.getCollectionName());

        deleteCollection(coll);
        exchange.setResponseCode(200);

        exchange.endExchange();
    }

    /**
     * method for deleting the collection coll
     *
     * @param coll
     */
    public void deleteCollection(DBCollection coll)
    {
        coll.drop();
    }
}