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
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.handlers.IllegalQueryParamenterException;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.utils.RequestHelper;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class DBDAO
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();

    private static final Logger logger = LoggerFactory.getLogger(DBDAO.class);

    public static final BasicDBObject METADATA_QUERY = new BasicDBObject("_id", "@metadata");

    private static final BasicDBObject fieldsToReturn;

    static
    {
        fieldsToReturn = new BasicDBObject();
        fieldsToReturn.put("_id", 1);
        fieldsToReturn.put("@created_on", 1);
    }

    private static final BasicDBObject fieldsToReturnIndexes;

    static
    {
        fieldsToReturnIndexes = new BasicDBObject();
        fieldsToReturnIndexes.put("key", 1);
        fieldsToReturnIndexes.put("name", 1);
    }

    /**
     * 
     * WARNING: slow method. 
    * @deprecated
    **/
    public static boolean checkDbExists(HttpServerExchange exchange, String dbName)
    {
        if (!doesDbExists(dbName))
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return false;
        }

        return true;
    }

    /**
     * WARNING: slow method. 
     * @param dbName
     * @return 
    **/
    public static boolean doesDbExists(String dbName)
    {

        return client.getDatabaseNames().contains(dbName);
    }

    public static DB getDB(String dbName)
    {
        return client.getDB(dbName);
    }

    public static List<String> getDbCollections(DB db)
    {
        List<String> _colls = new ArrayList(db.getCollectionNames());

        Collections.sort(_colls);

        return _colls;
    }

    /**
     * @param colls the collections list got from getDbCollections()
     * @return the number of collections in this db
     *
     */
    public static long getDBSize(List<String> colls)
    {
        // filter out reserved resources
        List<String> _colls = colls.stream().filter(coll -> !RequestContext.isReservedResourceCollection(coll)).collect(Collectors.toList());

        return _colls.size();
    }

    /**
     * @param dbName
     * @return the db props
     *
     */
    public static DBObject getDbProps(String dbName)
    {
        DBCollection propscoll = CollectionDAO.getCollection(dbName, "@metadata");

        DBObject row = propscoll.findOne(METADATA_QUERY);

        if (row != null)
        {
            row.removeField("_id");
            
            Object etag = row.get("@etag");

            if (etag != null && ObjectId.isValid("" + etag))
            {
                ObjectId oid = new ObjectId("" + etag);

                row.put("@lastupdated_on", Instant.ofEpochSecond(oid.getTimestamp()).toString());
            }
        }

        return row;
    }

    /**
     * @param dbName
     * @param colls the collections list as got from getDbCollections()
     * @param page
     * @param pagesize
     * @return the db data
     * @throws com.softinstigate.restheart.handlers.IllegalQueryParamenterException
     *
     */
    public static List<DBObject> getData(String dbName, List<String> colls, int page, int pagesize)
            throws IllegalQueryParamenterException
    {
        // filter out reserved resources
        List<String> _colls = colls.stream().filter(coll -> !RequestContext.isReservedResourceCollection(coll)).collect(Collectors.toList());

        int size = _colls.size();

        // *** arguments check
        long total_pages = -1;

        if (size > 0)
        {
            float _size = size + 0f;
            float _pagesize = pagesize + 0f;

            total_pages = Math.max(1, Math.round(Math.nextUp(_size / _pagesize)));

            if (page > total_pages)
            {
                throw new IllegalQueryParamenterException("illegal query paramenter, page is bigger that total pages which is " + total_pages);
            }
        }

        // apply page and pagesize
        _colls = _colls.subList((page - 1) * pagesize, (page - 1) * pagesize + pagesize > _colls.size() ? _colls.size() : (page - 1) * pagesize + pagesize);

        List<DBObject> data = new ArrayList<>();

        _colls.stream().map(
                (coll) ->
                {
                    BasicDBObject properties = new BasicDBObject();

                    properties.put("_id", coll);

                    DBObject metadata = CollectionDAO.getCollectionProps(dbName, coll);

                    if (metadata != null)
                        properties.putAll(metadata);

                    return properties;
                }
        ).forEach((item) ->
        {
            data.add(item);
        });

        return data;
    }

    public static int upsertDB(String dbName, DBObject content, ObjectId etag, boolean patching)
    {
        DB db = client.getDB(dbName);

        boolean existing = db.getCollectionNames().size() > 0;

        if (patching && !existing)
        {
            return HttpStatus.SC_NOT_FOUND;
        }

        DBCollection coll = db.getCollection("@metadata");
        
        // check the etag
        if (db.collectionExists("@metadata"))
        {
            if (etag == null)
            {
                logger.warn("the {} header in required", Headers.ETAG);
                return HttpStatus.SC_PRECONDITION_FAILED;
            }

            BasicDBObject idAndEtagQuery = new BasicDBObject("_id", "@metadata");
            idAndEtagQuery.append("@etag", etag);
            
            if (coll.count(idAndEtagQuery) < 1)
            {
                return HttpStatus.SC_PRECONDITION_FAILED;
            }
        }

        // apply new values
        ObjectId timestamp = new ObjectId();
        Instant now = Instant.ofEpochSecond(timestamp.getTimestamp());

        if (content == null)
        {
            content = new BasicDBObject();
        }

        content.put("@etag", timestamp);
        content.removeField("@created_on"); // make sure we don't change this field
        content.removeField("_id"); // make sure we don't change this field

        if (patching)
        {
            coll.update(METADATA_QUERY, new BasicDBObject("$set", content), true, false);

            return HttpStatus.SC_OK;
        }
        else
        {
            // we use findAndModify to get the @created_on field value from the existing document
            // we need to put this field back using a second update 
            // it is not possible in a single update even using $setOnInsert update operator
            // in this case we need to provide the other data using $set operator and this makes it a partial update (patch semantic) 
            DBObject old = coll.findAndModify(METADATA_QUERY, fieldsToReturn, null, false, content, false, true);

            if (old != null)
            {
                Object oldTimestamp = old.get("@created_on");

                if (oldTimestamp == null)
                {
                    oldTimestamp = now.toString();
                    logger.warn("metadata of collection {} had no @created_on field. set to now", coll.getFullName());
                }

                // need to readd the @created_on field 
                BasicDBObject createdContet = new BasicDBObject("@created_on", "" + oldTimestamp);
                createdContet.markAsPartialObject();
                coll.update(METADATA_QUERY, new BasicDBObject("$set", createdContet), true, false);

                return HttpStatus.SC_OK;
            }
            else
            {
                // need to readd the @created_on field 
                BasicDBObject createdContet = new BasicDBObject("@created_on", now.toString());
                createdContet.markAsPartialObject();
                coll.update(METADATA_QUERY, new BasicDBObject("$set", createdContet), true, false);

                return HttpStatus.SC_CREATED;
            }
        }
    }

    public static int deleteDB(String dbName, ObjectId requestEtag)
    {
        DB db = DBDAO.getDB(dbName);

        DBCollection coll = db.getCollection("@metadata");

        BasicDBObject checkEtag = new BasicDBObject("_id", "@metadata");
        checkEtag.append("@etag", requestEtag);

        DBObject exists = coll.findOne(checkEtag, fieldsToReturn);

        if (exists == null)
        {
            return HttpStatus.SC_PRECONDITION_FAILED;
        }
        else
        {
            db.dropDatabase();
            return HttpStatus.SC_GONE;
        }
    }

    private static int optimisticCheckEtag(DB db, DBCollection coll, DBObject documentIdQuery, DBObject oldDocument, ObjectId requestEtag)
    {
        Object oldEtag = RequestHelper.getEtagAsObjectId(oldDocument.get("@etag"));

        if (oldEtag == null) // well we don't had an etag there so fine
        {
            db.dropDatabase();
            return HttpStatus.SC_OK;
        }
        else
        {
            if (oldEtag.equals(requestEtag))
            {
                db.dropDatabase();
                return HttpStatus.SC_GONE; // ok they match

            }
            else
            {
                // oopps, we need to restore old document
                // they call it optimistic lock strategy
                coll.save(oldDocument);
                return HttpStatus.SC_PRECONDITION_FAILED;
            }
        }
    }
}
