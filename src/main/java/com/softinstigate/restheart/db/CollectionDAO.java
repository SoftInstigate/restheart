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
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import org.bson.BSONObject;
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

    private static final BasicDBObject PROPS_QUERY = new BasicDBObject("_id", "_properties");
    private static final BasicDBObject DOCUMENTS_QUERY = new BasicDBObject("_id", new BasicDBObject("$ne", "_properties"));

    private static final BasicDBObject fieldsToReturn;

    static
    {
        fieldsToReturn = new BasicDBObject();
        fieldsToReturn.put("_id", 1);
        fieldsToReturn.put("_created_on", 1);
    }

    private static final BasicDBObject fieldsToReturnIndexes;

    static
    {
        fieldsToReturnIndexes = new BasicDBObject();
        fieldsToReturnIndexes.put("key", 1);
        fieldsToReturnIndexes.put("name", 1);
    }

    /**
     * WARNING: slow method. perf tests show this can take up to 35% overall
     * requests processing time when getting data from a collection
     *
     * @deprecated
     * @param exchange
     * @param dbName
     * @param collectionName
     * @return true if the specified collection exits in the db dbName
     */
    public static boolean checkCollectionExists(HttpServerExchange exchange, String dbName, String collectionName)
    {
        if (!doesCollectionExist(dbName, collectionName))
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return false;
        }

        return true;
    }

    /**
     * WARNING: quite method. perf tests show this can take up to 35% overall
     * requests processing time when getting data from a collection
     *
     * @deprecated
     * @param dbName
     * @param collectionName
     * @return true if the specified collection exits in the db dbName
     */
    public static boolean doesCollectionExist(String dbName, String collectionName)
    {
        if (dbName == null || dbName.isEmpty() || dbName.contains(" "))
        {
            return false;
        }

        BasicDBObject query = new BasicDBObject("name", dbName + "." + collectionName);

        return client.getDB(dbName).getCollection("system.namespaces").findOne(query) != null;
    }

    public static DBCollection getCollection(String dbName, String collName)
    {
        return client.getDB(dbName).getCollection(collName);
    }

    public static boolean isCollectionEmpty(DBCollection coll)
    {
        return coll.count(DOCUMENTS_QUERY) == 0;
    }

    public static void dropCollection(DBCollection coll)
    {
        coll.drop();
    }

    public static long getCollectionSize(DBCollection coll, Deque<String> filter)
    {
        final BasicDBObject query = DOCUMENTS_QUERY;

        if (filter != null)
        {
            try
            {
                filter.stream().forEach(f ->
                {
                    query.putAll((BSONObject) JSON.parse(f));  // this can throw JSONParseException for invalid filter parameters
                });
            }
            catch (JSONParseException jpe)
            {
                logger.warn("****** error parsing filter expression {}", filter, jpe);
            }
        }

        return coll.count(query);
    }

    public static ArrayList<DBObject> getCollectionData(DBCollection coll, int page, int pagesize, Deque<String> sortBy, Deque<String> filter) throws JSONParseException
    {
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
                sf = sf.replaceAll("_lastupdated_on", "_etag"); // _lastupdated is not stored and actually generated from @tag

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

        // apply filter
        final BasicDBObject query = new BasicDBObject(DOCUMENTS_QUERY);

        if (filter != null)
        {
            filter.stream().forEach((String f) ->
            {
                BSONObject filterQuery = (BSONObject) JSON.parse(f);
                replaceObjectIds(filterQuery);
                    
                query.putAll(filterQuery);  // this can throw JSONParseException for invalid filter parameters
            });
        }

        ArrayList<DBObject> data = getDataFromCursor(coll.find(query).sort(sort).limit(pagesize).skip(pagesize * (page - 1)));

        data.forEach(row ->
        {
            Object etag = row.get("_etag");

            if (etag != null && ObjectId.isValid("" + etag))
            {
                ObjectId _etag = new ObjectId("" + etag);

                row.put("_lastupdated_on", Instant.ofEpochSecond(_etag.getTimestamp()).toString());
            }
        }
        );

        return data;
    }

    public static DBObject getCollectionProps(String dbName, String collName)
    {
        DBCollection coll = CollectionDAO.getCollection(dbName, collName);

        DBObject properties = coll.findOne(PROPS_QUERY);

        if (properties != null)
        {
            properties.put("_id", collName);
            
            Object etag = properties.get("_etag");

            if (etag != null && ObjectId.isValid("" + etag))
            {
                ObjectId oid = new ObjectId("" + etag);

                properties.put("_lastupdated_on", Instant.ofEpochSecond(oid.getTimestamp()).toString());
            }
        }

        return properties;
    }

    /**
     *
     *
     * @param dbName
     * @param collName
     * @param content
     * @param etag
     * @param updating
     * @param patching
     * @return the HttpStatus code to retrun
     */
    public static int upsertCollection(String dbName, String collName, DBObject content, ObjectId etag, boolean updating, boolean patching)
    {
        DB db = DBDAO.getDB(dbName);

        DBCollection coll = db.getCollection(collName);

        if (patching && !updating)
        {
            return HttpStatus.SC_NOT_FOUND;
        }

        if (updating)
        {
            if (etag == null)
            {
                return HttpStatus.SC_CONFLICT;
            }

            BasicDBObject idAndEtagQuery = new BasicDBObject("_id", "_properties");
            idAndEtagQuery.append("_etag", etag);

            if (coll.count(idAndEtagQuery) < 1)
            {
                return HttpStatus.SC_PRECONDITION_FAILED;
            }
        }

        ObjectId timestamp = new ObjectId();
        Instant now = Instant.ofEpochSecond(timestamp.getTimestamp());

        if (content == null)
        {
            content = new BasicDBObject();
        }

        content.removeField("_id"); // make sure we don't change this field

        if (updating)
        {
            content.removeField("_crated_on"); // don't allow to update this field
            content.put("_etag", timestamp);
        }
        else
        {
            content.put("_id", "_properties");
            content.put("_created_on", now.toString());
            content.put("_etag", timestamp);
        }

        if (patching)
        {
            coll.update(PROPS_QUERY, new BasicDBObject("$set", content), true, false);
            return HttpStatus.SC_OK;
        }
        else
        {
            // we use findAndModify to get the @created_on field value from the existing properties document
            // we need to put this field back using a second update 
            // it is not possible in a single update even using $setOnInsert update operator
            // in this case we need to provide the other data using $set operator and this makes it a partial update (patch semantic) 

            DBObject old = coll.findAndModify(PROPS_QUERY, fieldsToReturn, null, false, content, false, true);

            if (old != null)
            {
                Object oldTimestamp = old.get("_created_on");

                if (oldTimestamp == null)
                {
                    oldTimestamp = now.toString();
                    logger.warn("properties of collection {} had no @created_on field. set to now", coll.getFullName());
                }

                // need to readd the @created_on field 
                BasicDBObject createdContet = new BasicDBObject("_created_on", "" + oldTimestamp);
                createdContet.markAsPartialObject();
                coll.update(PROPS_QUERY, new BasicDBObject("$set", createdContet), true, false);

                return HttpStatus.SC_OK;
            }
            else
            {
                // need to readd the @created_on field 
                BasicDBObject createdContet = new BasicDBObject("_created_on", now.toString());
                createdContet.markAsPartialObject();
                coll.update(PROPS_QUERY, new BasicDBObject("$set", createdContet), true, false);

                initDefaultIndexes(coll);

                return HttpStatus.SC_CREATED;
            }
        }
    }

    public static int deleteCollection(String dbName, String collName, ObjectId requestEtag)
    {
        DBCollection coll = getCollection(dbName, collName);

        BasicDBObject checkEtag = new BasicDBObject("_id", "_properties");
        checkEtag.append("_etag", requestEtag);

        DBObject exists = coll.findOne(checkEtag, fieldsToReturn);

        if (exists == null)
        {
            return HttpStatus.SC_PRECONDITION_FAILED;
        }
        else
        {
            coll.drop();
            return HttpStatus.SC_NO_CONTENT;
        }
    }

    public static ArrayList<DBObject> getDataFromCursor(DBCursor cursor)
    {
        return new ArrayList<>(cursor.toArray());
    }

    private static void initDefaultIndexes(DBCollection coll)
    {
        coll.createIndex(new BasicDBObject("_id", 1).append("_etag", 1), new BasicDBObject("name", "_id_etag_idx"));
        coll.createIndex(new BasicDBObject("_etag", 1), new BasicDBObject("name", "_etag_idx"));
        coll.createIndex(new BasicDBObject("_created_on", 1), new BasicDBObject("name", "_created_on_idx"));
    }
    
    /**
     * this replaces string that are valid ObjectIds with ObjectIds objects
     * @param source
     * @return 
     */
    private static void replaceObjectIds(BSONObject source)
    {
        if (source == null)
            return;
        
        source.keySet().stream().forEach((key) ->
        {
            Object o = source.get(key);
            
            if (o instanceof BSONObject)
            {
                replaceObjectIds((BSONObject)o);
            }
            else if (ObjectId.isValid(o.toString()))
            {
                source.put(key, new ObjectId(o.toString()));
            }
        });
    }
}
