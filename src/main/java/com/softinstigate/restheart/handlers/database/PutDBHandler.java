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
package com.softinstigate.restheart.handlers.database;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.ParseException;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.utils.ChannelReader;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.JSONHelper;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.channels.StreamSourceChannel;

/**
 *
 * @author uji
 */
public class PutDBHandler implements HttpHandler
{
    final Charset charset = Charset.forName("utf-8");

    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();

    private static final Logger logger = LoggerFactory.getLogger(PutDBHandler.class);

    /**
     * Creates a new instance of EntityResource
     */
    public PutDBHandler()
    {
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        RequestContext rc = new RequestContext(exchange);

        if (rc.getDBName().isEmpty() || rc.getDBName().startsWith("@"))
        {
            ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_NOT_ACCEPTABLE, new IllegalArgumentException("db name cannot be empty or start with @"));
            return;
        }

        String _content = ChannelReader.read(exchange.getRequestChannel());

        DBObject content = null;

        try
        {
            content = (DBObject) JSON.parse(_content);
        }
        catch (JSONParseException ex)
        {
            ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_NOT_ACCEPTABLE, ex);
            return;
        }

        boolean updating = client.getDatabaseNames().contains(rc.getDBName());

        DB db = client.getDB(rc.getDBName());

        DBCollection coll = null;
        
        BasicDBObject metadata = new BasicDBObject();

        if (updating)
        {
            coll = db.getCollection("@metadata");
            
            BasicDBObject metadataQuery = new BasicDBObject("_id", "@metadata");
            
            DBObject oldmetadata = coll.findOne(metadataQuery);
            
            if (oldmetadata != null)
            {
                metadata.put("@created_on", oldmetadata.get("@created_on"));
                metadata.put("@lastupdated_on", Instant.now().toString());
            }
            
            coll.remove(metadataQuery);
        }
        else
        {
            coll = db.createCollection("@metadata", null);

            metadata.put("@created_on", Instant.now().toString());
        }
        
        metadata.put("_id", "@metadata");
        metadata.put("@type", "metadata");
        
        if (content != null)
        {
            metadata.putAll(content);
        }

        coll.insert(metadata);

        if (updating)
            ResponseHelper.endExchange(exchange, HttpStatus.SC_OK);
        else
            ResponseHelper.endExchange(exchange, HttpStatus.SC_CREATED);
    }
}
