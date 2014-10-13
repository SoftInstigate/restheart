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
package com.softinstigate.restheart.db;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.softinstigate.restheart.handlers.RequestContext;
import java.time.Instant;
import java.util.Map;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class MetadataFixer
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();

    private static final Logger logger = LoggerFactory.getLogger(MetadataFixer.class);

    public static boolean addCollectionMetadata(String dbName, String collName)
    {
        DBObject dbmd = DBDAO.getDbProps(dbName);

        if (dbmd == null)
        {
            // db must exists with properties
            return false;
        }

        DBObject md = CollectionDAO.getCollectionProps(dbName, collName);

        if (md != null) // properties exists
        {
            return false;
        }

        // check if collection has data
        DB db = DBDAO.getDB(dbName);

        if (!db.collectionExists(collName))
        {
            return false;
        }

        // ok, create the properties
        DBObject properties = new BasicDBObject();

        ObjectId timestamp = new ObjectId();
        Instant now = Instant.ofEpochSecond(timestamp.getTimestamp());

        properties.put("_id", "_properties");
        properties.put("_created_on", now.toString());
        properties.put("_etag", timestamp);

        DBCollection coll = CollectionDAO.getCollection(dbName, collName);

        coll.insert(properties);
        logger.info("properties added to {}/{}", dbName, collName);
        return true;
    }

    public static boolean addDbMetadata(String dbName)
    {
        if (!DBDAO.doesDbExists(dbName))
        {
            return false;
        }

        DBObject dbmd = DBDAO.getDbProps(dbName);

        if (dbmd != null) // properties exists
        {
            return false;
        }

        DB db = DBDAO.getDB(dbName);

        // ok, create the properties
        DBObject properties = new BasicDBObject();

        ObjectId timestamp = new ObjectId();
        Instant now = Instant.ofEpochSecond(timestamp.getTimestamp());

        properties.put("_id", "_properties");
        properties.put("_created_on", now.toString());
        properties.put("_etag", timestamp);

        DBCollection coll = CollectionDAO.getCollection(dbName, "_properties");

        coll.insert(properties);
        logger.info("properties added to {}", dbName);
        return true;
    }

    public static void fixMetadata()
    {
        client.getDatabaseNames().stream().filter((dbName) -> (!RequestContext.isReservedResourceDb(dbName))).map((dbName) ->
        {
            try
            {
                addDbMetadata(dbName);
            }
            catch (Throwable t)
            {
                logger.error("error fixing properties of db {}", dbName, t);
            }
            return dbName;
        }).forEach((dbName) ->
        {
            DB db = DBDAO.getDB(dbName);

            DBDAO.getDbCollections(db).stream().filter((collectionName) -> (!RequestContext.isReservedResourceCollection(collectionName))).forEach(
                    (collectionName) ->
                    {
                        try
                        {
                            addCollectionMetadata(dbName, collectionName);
                        }
                        catch (Throwable t)
                        {
                            logger.error("error fixing properties of collection {}/{}", dbName, collectionName, t);
                        }
                    }
            );
        });
    }
}