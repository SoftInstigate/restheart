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
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.JSONHelper;
import com.softinstigate.restheart.utils.RequestHelper;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
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
     * @param requestEtag
     * @param patching
     * @return the HttpStatus code to retrun
     */
    public static int upsertDocument(String dbName, String collName, String documentId, DBObject content, ObjectId requestEtag, boolean patching)
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

        BasicDBObject idQuery = new BasicDBObject("_id", getId(documentId));

        if (patching)
        {
            content.removeField("@created_on"); // make sure we don't change this field
            
            DBObject oldDocument = coll.findAndModify(idQuery, null, null, false, new BasicDBObject("$set", content), false, false);

            if (oldDocument == null)
            {
                return HttpStatus.SC_NOT_FOUND;
            }
            else
            {
                // check the old etag (in case restore the old document version)
                return optimisticCheckEtag(coll, oldDocument, requestEtag, HttpStatus.SC_NO_CONTENT);
            }
        }
        else
        {
            // we use findAndModify to get the @created_on field value from the existing document
            // we need to put this field back using a second update 
            // it is not possible in a single update even using $setOnInsert update operator
            // in this case we need to provide the other data using $set operator and this makes it a partial update (patch semantic) 
            DBObject oldDocument = coll.findAndModify(idQuery, null, null, false, content, false, true);

            content.put("@created_on", now.toString()); // let's optimistically assume this is an insert. in case we'll set it back by a second update
            
            if (oldDocument != null) // upsert
            {
                Object oldTimestamp = oldDocument.get("@created_on");

                if (oldTimestamp == null)
                {
                    oldTimestamp = now.toString();
                    logger.warn("metadata of document /{}/{}/{} had no @created_on field. set to now", dbName, collName, documentId);
                }

                // need to readd the @created_on field 
                BasicDBObject created = new BasicDBObject("@created_on", "" + oldTimestamp);
                created.markAsPartialObject();
                coll.update(idQuery, new BasicDBObject("$set", created), true, false);

                // check the old etag (in case restore the old document version)
                return optimisticCheckEtag(coll, oldDocument, requestEtag, HttpStatus.SC_NO_CONTENT);
            }
            else // insert
            {
                return HttpStatus.SC_CREATED;
            }
        }
    }

    /**
     *
     *
     * @param exchange
     * @param dbName
     * @param collName
     * @param content
     * @param requestEtag
     * @return the HttpStatus code to retrun
     */
    public static int upsertDocumentPost(HttpServerExchange exchange, String dbName, String collName, DBObject content, ObjectId requestEtag)
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
        content.put("@created_on", now.toString()); // make sure we don't change this field

        Object _id = content.get("_id");
        content.removeField("_id");
        
        if (_id == null)
        {
            ObjectId id = new ObjectId();
            content.put("_id", id);
            
            coll.insert(content);
            
            exchange.getResponseHeaders().add(HttpString.tryFromString("Location"), JSONHelper.getReference(exchange.getRequestURL(), id.toString()).toString());
        
            return HttpStatus.SC_CREATED;
        }
        
        BasicDBObject idQuery = new BasicDBObject("_id", getId("" + _id));

        // we use findAndModify to get the @created_on field value from the existing document
        // we need to put this field back using a second update 
        // it is not possible in a single update even using $setOnInsert update operator
        // in this case we need to provide the other data using $set operator and this makes it a partial update (patch semantic) 
        DBObject oldDocument = coll.findAndModify(idQuery, null, null, false, content, false, true);

        if (oldDocument != null) // upsert
        {
            Object oldTimestamp = oldDocument.get("@created_on");

            if (oldTimestamp == null)
            {
                oldTimestamp = now.toString();
                logger.warn("metadata of document /{}/{}/{} had no @created_on field. set to now", dbName, collName, _id.toString());
            }

            // need to readd the @created_on field 
            BasicDBObject createdContet = new BasicDBObject("@created_on", "" + oldTimestamp);
            createdContet.markAsPartialObject();
            coll.update(idQuery, new BasicDBObject("$set", createdContet), true, false);

            // check the old etag (in case restore the old document version)
            return optimisticCheckEtag(coll, oldDocument, requestEtag, HttpStatus.SC_NO_CONTENT);
        }
        else // insert
        {
            return HttpStatus.SC_CREATED;
        }
    }

    public static int deleteDocument(String dbName, String collName, String documentId, ObjectId requestEtag)
    {
        DB db = DBDAO.getDB(dbName);

        DBCollection coll = db.getCollection(collName);

        BasicDBObject idQuery = new BasicDBObject("_id", getId(documentId));

        DBObject oldDocument = coll.findAndModify(idQuery, null, null, true, null, false, false);

        if (oldDocument == null)
        {
            return HttpStatus.SC_NOT_FOUND;
        }
        else
        {
            // check the old etag (in case restore the old document version)
            return optimisticCheckEtag(coll, oldDocument, requestEtag, HttpStatus.SC_GONE);
        }
    }

    public static ArrayList<DBObject> getDataFromCursor(DBCursor cursor)
    {
        return new ArrayList<>(cursor.toArray());
    }

    private static Object getId(String id)
    {
        if (id == null)
            return new ObjectId();
        
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

    private static int optimisticCheckEtag(DBCollection coll, DBObject oldDocument, ObjectId requestEtag, int httpStatusIfOk)
    {
        if (requestEtag == null)
        {
            logger.warn("the {} header in required", Headers.ETAG);
            coll.save(oldDocument);
            return HttpStatus.SC_CONFLICT;
        }
        
        Object oldEtag = RequestHelper.getEtagAsObjectId(oldDocument.get("@etag"));

        if (oldEtag == null) // well we don't had an etag there so fine
        {
            return HttpStatus.SC_NO_CONTENT;
        }
        else
        {
            if (oldEtag.equals(requestEtag))
            {
                return httpStatusIfOk; // ok they match
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
