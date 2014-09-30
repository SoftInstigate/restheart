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
import static com.softinstigate.restheart.db.CollectionDAO.doesCollectionExist;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.RequestHelper;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
    
    public static boolean checkDbExists(HttpServerExchange exchange, String dbName)
    {
        if (!doesDbExists(dbName))
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return false;
        }

        return true;
    }

    public static boolean doesDbExists(String dbName)
    {
        BasicDBObject query = new BasicDBObject("name", new BasicDBObject("$regex", "^"+ dbName + "\\..*"));
        
        return client.getDB(dbName).getCollection("system.namespaces").findOne(query) != null;
        /*
        TODO check this!!!!! 
        check removed. too slow!
        if (!client.getDatabaseNames().contains(dbName))
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return false;
        }
        */

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
        // filter out reserved resourced
        List<String> _colls = colls.stream().filter(coll -> !RequestContext.isReservedResourceCollection(coll)).collect(Collectors.toList());
        
        return _colls.size();
    }

    /**
     * @param dbName
     * @param colls the collections list as got from getDbCollections()
     * @return the db metadata
    *
     */
    public static Map<String, Object> getDbMetaData(String dbName, List<String> colls)
    {
        Map<String, Object> metadata = null;

        // get metadata collection if exists
        if (colls.contains("@metadata"))
        {
            DBCollection metadatacoll = CollectionDAO.getCollection(dbName, "@metadata");

            DBObject metadatarow = metadatacoll.findOne(METADATA_QUERY);

            metadata = DAOUtils.getDataFromRow(metadatarow, "_id");

            if (metadata == null)
            {
                metadata = new HashMap<>();
            }
            
            Object etag = metadata.get("@etag");

            if (etag != null && ObjectId.isValid("" + etag))
            {
                ObjectId oid = new ObjectId("" + etag);

                metadata.put("@lastupdated_on", Instant.ofEpochSecond(oid.getTimestamp()).toString());
            }
        }

        return metadata;
    }

    /**
     * @param dbName
     * @param colls the collections list as got from getDbCollections()
     * @param page
     * @param pagesize
     * @param sortBy
     * @param filterBy
     * @param filter
     * @return the db data
    *
     */
    public static List<Map<String, Object>> getData(String dbName, List<String> colls, int page, int pagesize, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter)
    {
        // filter out reserved resourced
        List<String> _colls = colls.stream().filter(coll -> !RequestContext.isReservedResourceCollection(coll)).collect(Collectors.toList());
        
        // apply page and pagesize
        _colls = _colls.subList((page - 1) * pagesize, (page - 1) * pagesize + pagesize > _colls.size() ? _colls.size() : (page - 1) * pagesize + pagesize);

        // apply sort_by
        logger.debug("sort_by not yet implemented");

        // apply filter_by and filter
        logger.debug("filter not yet implemented");

        List<Map<String, Object>> data = new ArrayList<>();

        _colls.stream().map(
                (coll) ->
                {
                    TreeMap<String, Object> properties = new TreeMap<>();

                    properties.put("_id", coll);
                    
                    Map<String, Object> metadata = CollectionDAO.getCollectionMetadata((CollectionDAO.getCollection(dbName, coll)));
                    
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

        DBCollection coll = db.getCollection("@metadata");
        
        boolean existing = db.getCollectionNames().size() > 0;
        
        if (patching && !existing)
        {
            return HttpStatus.SC_NOT_FOUND;
        }
        
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
            content = new BasicDBObject();
        
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