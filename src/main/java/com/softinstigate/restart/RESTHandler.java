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
package com.softinstigate.restart;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteConcern;
import com.mongodb.util.JSON;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import java.util.Deque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author uji
 */
public class RESTHandler implements HttpHandler
{
    private static MongoClient client = MongoDBClientSingleton.getInstance().getClient();

    /**
     * Creates a new instance of EntityResource
     */
    public RESTHandler()
    {
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        HeaderMap headers = exchange.getRequestHeaders();

        headers.put(Headers.CONTENT_TYPE, "application/json");

        String uri = exchange.getRequestURI();

        String[] uriTokens = uri.split("/");

        if (uriTokens.length < 3)
        {
            exchange.setResponseCode(404);
            exchange.endExchange();
        }

        String _db = uriTokens[1];
        String _coll = uriTokens[2];

        DB db = null;
        DBCollection coll = null;

        //Logger.getLogger(this.getClass().getName()).log(Level.INFO, "uri: {0}", uri);
        if (client.getDatabaseNames().contains(_db))
        {
            db = client.getDB(_db);

            if (db.collectionExists(_coll))
            {
                coll = db.getCollection(_coll);
            }
            else
            {
                exchange.setResponseCode(404);
                exchange.endExchange();
                return;
            }
        }
        else
        {
            exchange.setResponseCode(404);
            exchange.endExchange();
            return;
        }

        HttpString method = exchange.getRequestMethod();

        //Logger.getLogger(this.getClass().getName()).log(Level.INFO, "method: {0}", method);
        if (method.equals(Methods.GET))
        {
            try
            {
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

                String ret = getJson(coll, skip, limit);

                exchange.setResponseContentLength(ret.length());
                exchange.setResponseCode(200);

                exchange.getResponseSender().send(ret);
            }
            catch (Throwable t)
            {
                exchange.setResponseCode(500);

                Logger.getLogger(this.getClass().getName()).warning(t.getMessage());
            }
        }
        else if (method.equals(Methods.POST))
        {
            exchange.setResponseCode(500);
        }
        else if (method.equals(Methods.DELETE))
        {
            exchange.setResponseCode(500);
        }
    }

    /**
     * GET method for creating an instance of test document
     *
     * @param coll
     * @param skip
     * @param limit
     * @return
     */
    public String getJson(DBCollection coll, int skip, int limit)
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

    /**
     * POST method for creating an instance of test document
     *
     * @param coll
     * @param content representation for the resource
     */
    public void putJson(DBCollection coll, String content)
    {
        DBObject obj = (DBObject) JSON.parse(content);

        coll.insert(obj, WriteConcern.ACKNOWLEDGED);
    }

    /**
     * DELETE method for deleting documents
     *
     * @param coll
     */
    public void delete(DBCollection coll)
    {
        coll.drop();
    }
}
