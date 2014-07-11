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
import com.mongodb.DBCursor;
import com.mongodb.MongoClient;
import com.mongodb.util.JSON;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.utils.RequestContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.nio.charset.Charset;
import java.util.Deque;

/**
 *
 * @author uji
 */
public class GetCollectionHandler implements HttpHandler
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();
    
    final Charset charset = Charset.forName("utf-8");  

    /**
     * Creates a new instance of EntityResource
     */
    public GetCollectionHandler()
    {
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        RequestContext rc = new RequestContext(exchange);
        
        DBCollection coll = client.getDB(rc.getDBName()).getCollection(rc.getCollectionName());

        int skip = 0;
        int limit = 100;

        Deque<String> _skips = exchange.getQueryParameters().get("skip");
        Deque<String> _limits = exchange.getQueryParameters().get("limit");

        if (_skips != null && !_skips.isEmpty())
        {
            try
            {
                skip = Integer.parseInt(_skips.getFirst());
            }
            catch (NumberFormatException nfwe)
            {
                skip = 0;
            }
        }

        if (_limits != null && !_limits.isEmpty())
        {
            try
            {
                limit = Integer.parseInt(_limits.getFirst());
            }
            catch (NumberFormatException nfwe)
            {
                limit = 100;
            }
        }

        String ret = getDocuments(coll, skip, limit);

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.setResponseContentLength(ret.length());
        exchange.setResponseCode(200);

        exchange.getResponseSender().send(ret);
        
        exchange.endExchange();
    }

    /**
     * method for getting documents of collection coll
     *
     * @param coll
     * @param skip
     * @param limit defaule is 100
     * @return
     */
    public String getDocuments(DBCollection coll, int skip, int limit)
    {
        DBCursor cursor = coll.find().limit(limit).skip(skip);

        String ret = null;

        try
        {
            ret = JSON.serialize(cursor);
        }
        finally
        {
            cursor.close();
        }

        return ret;
    }
}