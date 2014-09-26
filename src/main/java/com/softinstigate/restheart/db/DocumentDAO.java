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
import com.mongodb.WriteResult;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import java.util.ArrayList;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class DocumentDAO
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();

    private static final Logger logger = LoggerFactory.getLogger(DocumentDAO.class);

    private static final BasicDBObject fieldsToReturn;

    static
    {
        fieldsToReturn = new BasicDBObject();
        fieldsToReturn.put("_id", 1);
        fieldsToReturn.put("@created_on", 1);
    }

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

    /**
     *
     *
     * @param dbName
     * @param collName
     * @param documentId
     * @param content
     * @param patching
     * @return the HttpStatus code to retrun
     */
    public static int upsertDocument(String dbName, String collName, String documentId, DBObject content, boolean patching)
    {
        DB db = DBDAO.getDB(dbName);

        DBCollection coll = db.getCollection(collName);

        ObjectId timestamp = new ObjectId();
        Instant now = Instant.ofEpochSecond(timestamp.getTimestamp());

        if (content == null)
        {
            content = new BasicDBObject();
        }

        content.put("@etag", timestamp);
        content.removeField("@created_on"); // make sure we don't change this field

        BasicDBObject idQuery = new BasicDBObject("_id", getId(documentId));
        
        if (patching)
        {
            WriteResult wr = coll.update(idQuery, new BasicDBObject("$set", content), false, false);
            
            if (wr.getN() == 0)
                return HttpStatus.SC_NOT_FOUND;
            else
                return HttpStatus.SC_OK;
        }
        else
        {
            // we use findAndModify to get the @created_on field value from the existing document
            // we need to put this field back using a second update 
            // it is not possible in a single update even using $setOnInsert update operator
            // in this case we need to provide the other data using $set operator and this makes it a partial update (patch semantic) 
            DBObject old = coll.findAndModify(idQuery, fieldsToReturn, null, false, content, false, true);

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
                coll.update(idQuery, new BasicDBObject("$set", createdContet), true, false);
                
                return HttpStatus.SC_OK;
            }
            else
            {
                // need to readd the @created_on field 
                BasicDBObject createdContet = new BasicDBObject("@created_on", now.toString());
                createdContet.markAsPartialObject();
                coll.update(idQuery, new BasicDBObject("$set", createdContet), true, false);
                
                return HttpStatus.SC_CREATED;
            }
        }
    }

    public static ArrayList<DBObject> getDataFromCursor(DBCursor cursor)
    {
        return new ArrayList<>(cursor.toArray());
    }

    private static Object getId(String id)
    {
        if (ObjectId.isValid(id))
        {
            return new ObjectId(id);
        }
        else
        {
            // the id is not an object id
            return id;
        }
    }
}
