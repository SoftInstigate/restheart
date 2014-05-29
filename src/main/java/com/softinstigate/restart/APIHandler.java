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
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Deque;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSourceChannel;

/**
 *
 * @author uji
 */
public class APIHandler implements HttpHandler
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();
    
    final Charset charset = Charset.forName("utf-8");  

    /**
     * Creates a new instance of EntityResource
     */
    public APIHandler()
    {
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        String _db = exchange.getRequestHeaders().get("db").getFirst();
        String _coll = exchange.getRequestHeaders().get("collection").getFirst();

        DB db;
        DBCollection coll;

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
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                exchange.getResponseSender().send("not found");
                exchange.endExchange();
                return;
            }
        }
        else
        {
            exchange.setResponseCode(404);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send("not found");
            exchange.endExchange();
            return;
        }

        HttpString method = exchange.getRequestMethod();

        //Logger.getLogger(this.getClass().getName()).log(Level.INFO, "method: {0}", method);
        if (method.equals(Methods.GET))
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

            String ret = getDocuments(coll, skip, limit);

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            exchange.setResponseContentLength(ret.length());
            exchange.setResponseCode(200);

            exchange.getResponseSender().send(ret);
        }
        else if (method.equals(Methods.POST))
        {
            StringBuilder content = new StringBuilder();

            ByteBuffer buf = ByteBuffer.allocate(128);
            
            StreamSourceChannel channel = exchange.getRequestChannel();

            while (Channels.readBlocking(channel, buf) != -1) {  
                    buf.flip();  
                    content.append(charset.decode(buf));  
                    buf.clear();  
                }  
            
            createDocument(coll, content.toString());

            exchange.setResponseCode(200);
        }
        else if (method.equals(Methods.DELETE))
        {
            deleteCollection(coll);
            exchange.setResponseCode(200);
        }
        else if (method.equals(Methods.PUT))
        {
            deleteCollection(coll);
            exchange.setResponseCode(200);
        }

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

    /**
     * method for creating a document in the collection coll
     *
     * @param coll
     * @param content representation for the resource
     */
    public void createDocument(DBCollection coll, String content)
    {
        DBObject obj = (DBObject) JSON.parse(content);

        coll.insert(obj, WriteConcern.ACKNOWLEDGED);
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
