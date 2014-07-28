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

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.ParseException;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
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
public class PutCollectionHandler implements HttpHandler
{
    final Charset charset = Charset.forName("utf-8");

    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();

    private static final Logger logger = LoggerFactory.getLogger(PutCollectionHandler.class);

    /**
     * Creates a new instance of EntityResource
     */
    public PutCollectionHandler()
    {
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        RequestContext rc = new RequestContext(exchange);

        DB db = client.getDB(rc.getDBName());
        
        if (rc.getCollectionName().isEmpty() || rc.getCollectionName().startsWith("@"))
        {
            ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_NOT_ACCEPTABLE, new IllegalArgumentException("collection name cannot be empty or start with @"));
            return;
        }
        
        if (db.collectionExists(rc.getCollectionName()))
        {
            // update

            logger.warn("update collection not yet implemented");
        }
        else
        {
            StreamSourceChannel sourceChannel = exchange.getRequestChannel();

            JsonObject content = null;

            try
            {
                Reader reader = Channels.newReader(sourceChannel, charset.toString());

                content = JsonObject.readFrom(reader);
            }
            catch (UnsupportedCharsetException ex)
            {
                ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, ex);
                return;
            }
            catch (IOException | ParseException | UnsupportedOperationException ex)
            {
                ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_NOT_ACCEPTABLE, ex);
                return;
            }
            finally
            {
                if (sourceChannel != null)
                {
                    sourceChannel.close();
                }
            }

            // create
            DBCollection coll = db.createCollection(rc.getCollectionName(), null);

            BasicDBObject indexes = new BasicDBObject();

            indexes.put("@type", 1);

            coll.createIndex(indexes);

            BasicDBObject metadata = new BasicDBObject();

            metadata.put("@type", "metadata");
            metadata.put("@created_on", Instant.now().toString());

            if (content != null && !content.isEmpty())
            {
                metadata.putAll(JSONHelper.convertJsonToDbObj(content));
            }

            coll.insert(metadata);

            ResponseHelper.endExchange(exchange, HttpStatus.SC_CREATED);
        }
    }
}
