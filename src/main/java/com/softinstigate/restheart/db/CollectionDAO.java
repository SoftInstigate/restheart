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
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class CollectionDAO
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();

    private static final Logger logger = LoggerFactory.getLogger(CollectionDAO.class);

    private static final BasicDBObject METADATA_QUERY = new BasicDBObject("_id", "@metadata");
    private static final BasicDBObject DATA_QUERY = new BasicDBObject("_id", new BasicDBObject("$ne", "@metadata"));
    private static final BasicDBObject ALL_FIELDS_BUT_ID = new BasicDBObject("_id", "0");

    public static boolean checkCollectionExists(HttpServerExchange exchange, String dbName, String collectionName)
    {
        if (!doesCollectionExist(dbName, collectionName))
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return false;
        }

        return true;
    }

    public static boolean doesCollectionExist(String dbName, String collectionName)
    {
        if (dbName == null || dbName.isEmpty() || dbName.contains(" "))
        {
            return false;
        }

        return client.getDB(dbName).collectionExists(collectionName);
    }

    public static DBCollection getCollection(String dbName, String collName)
    {
        return client.getDB(dbName).getCollection(collName);
    }

    public static boolean isCollectionEmpty(DBCollection coll)
    {
        return coll.count(DATA_QUERY) == 0;
    }

    public static void dropCollection(DBCollection coll)
    {
        coll.drop();
    }

    public static long getCollectionSize(DBCollection coll)
    {
        return coll.count() - 1; // -1 for the metadata document
    }

    public static List<Map<String, Object>> getCollectionData(DBCollection coll, int page, int pagesize, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter)
    {
        DAOUtils.getDataFromRow(coll.findOne(METADATA_QUERY, ALL_FIELDS_BUT_ID), "_id");

        // apply sort_by
        DBObject sort = new BasicDBObject();

        if (sortBy == null || sortBy.isEmpty())
        {
            sort.put("_id", 1);
        }
        else
        {
            sortBy.stream().forEach((sf) ->
            {
                if (sf.startsWith("-"))
                {
                    sort.put(sf.substring(1), -1);
                }
                else if (sf.startsWith("+"))
                {
                    sort.put(sf.substring(1), -1);
                }
                else
                {
                    sort.put(sf, 1);
                }
            });
        }

        // apply filter_by and filter
        logger.debug("filter not yet implemented");

        List<Map<String, Object>> data = DAOUtils.getDataFromRows(getDataFromCursor(coll.find(DATA_QUERY).sort(sort).limit(pagesize).skip(pagesize * (page - 1))));

        data.forEach(row ->
        {
            Object createdOn = row.get("@created_on");
            Object id = row.get("_id");

            if (createdOn != null && ObjectId.isValid("" + createdOn))
            {
                ObjectId _createdOn = new ObjectId("" + createdOn);
                
                row.put("@created_on", Instant.ofEpochSecond(_createdOn.getTimestamp()).toString());
            }
            else if (id != null && ObjectId.isValid("" + id))
            {
                ObjectId _createdOn = new ObjectId("" + id);

                row.put("@created_on", Instant.ofEpochSecond(_createdOn.getTimestamp()).toString());
            }
                
            Object etag = row.get("@etag");

            if (etag != null && ObjectId.isValid("" + etag))
            {
                ObjectId _etag = new ObjectId("" + etag);

                row.put("@lastupdated_on", Instant.ofEpochSecond(_etag.getTimestamp()).toString());
            }
        }
        );

        return data;
    }

    public static Map<String, Object> getCollectionMetadata(DBCollection coll)
    {
        Map<String, Object> metadata = DAOUtils.getDataFromRow(coll.findOne(METADATA_QUERY), "_id");

        Object createdOn = metadata.get("@created_on");
        Object etag = metadata.get("@etag");

        if (createdOn != null && ObjectId.isValid("" + createdOn))
        {
            ObjectId oid = new ObjectId("" + createdOn);

            metadata.put("@created_on", Instant.ofEpochSecond(oid.getTimestamp()).toString());
        }

        if (etag != null && ObjectId.isValid("" + etag))
        {
            ObjectId oid = new ObjectId("" + etag);

            metadata.put("@lastupdated_on", Instant.ofEpochSecond(oid.getTimestamp()).toString());
        }

        return metadata;
    }

    public static void upsertCollection(DBCollection coll, DBObject content, boolean updating)
    {
        ObjectId timestamp = new ObjectId();

        if (content == null)
        {
            content = new BasicDBObject();
        }

        if (updating)
        {
            content.put("@etag", timestamp);
        }
        else
        {
            content.put("_id", "@metadata");
            content.put("@created_on", timestamp);
            content.put("@etag", timestamp);
        }

        coll.update(METADATA_QUERY, new BasicDBObject("$set", content), true, false);
    }

    public static ArrayList<DBObject> getDataFromCursor(DBCursor cursor)
    {
        return new ArrayList<>(cursor.toArray());
    }
}
