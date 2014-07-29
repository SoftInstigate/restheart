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
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.softinstigate.restheart.handlers.GetHandler;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class GetDocumentHandler extends GetHandler
{
    private static final Logger logger = LoggerFactory.getLogger(GetDocumentHandler.class);

    /**
     * Creates a new instance of EntityResource
     */
    public GetDocumentHandler()
    {
    }

    @Override
    protected String generateContent(HttpServerExchange exchange, MongoClient client, int page, int pagesize, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter)
    {
        RequestContext rc = new RequestContext(exchange);

        ObjectId oid;
        String   sid;
        
        try
        {
            oid = new ObjectId(rc.getDocumentId());
            sid = null;
        }
        catch(IllegalArgumentException ex)
        {
            // the id is not an object id
            sid = rc.getDocumentId();
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

        DBObject document = client.getDB(rc.getDBName()).getCollection(rc.getCollectionName()).findOne(query);

        if (document == null)
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return null;
        }

        Map<String, Object> item = new TreeMap<>();

        document.keySet().stream().forEach((key) ->
        {
            // data value is either a String or a Map. the former case applies with nested json objects

            Object obj = document.get(key);

            if (obj instanceof BasicDBList)
            {
                BasicDBList dblist = (BasicDBList) obj;

                obj = dblist.toMap();
            }

            item.put(key, obj);
        });

        return generateDocumentContent(exchange.getRequestURL(), exchange.getQueryString(), item);
    }
}
