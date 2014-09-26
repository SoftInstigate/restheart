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
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.handlers.GetHandler;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.RequestHelper;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import java.time.Instant;
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
        super(null);
    }

    @Override
    protected String generateContent(HttpServerExchange exchange, RequestContext context, int page, int pagesize, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter)
    {
        ObjectId oid;
        String   sid;
        
        if (ObjectId.isValid(context.getDocumentId()))
        {
            sid = null;
            oid = new ObjectId(context.getDocumentId());
        }
        else
        {
            // the id is not an object id
            sid = context.getDocumentId();
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
        
        DBObject document = CollectionDAO.getCollection(context.getDBName(), context.getCollectionName()).findOne(query);

        if (document == null)
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return null;
        }
        
        Object etag = document.get("@etag");
        
        if (etag != null && ObjectId.isValid("" + etag))
        {
            ObjectId _etag = new ObjectId("" + etag);
            
            document.put("@lastupdated_on", Instant.ofEpochSecond(_etag.getTimestamp()).toString());
            
            // in case the request contains the IF_NONE_MATCH header with the current etag value, just return 304 NOT_MODIFIED code
            if (RequestHelper.checkReadEtag(exchange, etag.toString()))
            {
                ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_MODIFIED);
                return null;
            }
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

        return generateDocumentContent(exchange, item);
    }
}
