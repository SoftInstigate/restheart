/*
 * RESTHeart - the data Repository API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.db;

import org.restheart.db.entity.PutDocumentEntity;
import org.restheart.db.entity.PostDocumentEntity;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.RequestHelper;
import org.restheart.utils.URLUtilis;
import io.undertow.util.HttpString;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare
 */
public class DocumentDAO implements Repository<PutDocumentEntity, PostDocumentEntity> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentDAO.class);

    private final MongoClient client;
    
    public DocumentDAO() {
        client = MongoDBClientSingleton.getInstance().getClient();
    }

    /**
     *
     * @param dbName
     * @param collName
     * @return
     */
    public DBCollection getCollection(String dbName, String collName) {
        return client.getDB(dbName).getCollection(collName);
    }

    /**
     * @param document
     * @return the HttpStatus code
     */
    @Override
    public int put(PutDocumentEntity document) {
        DB db = DBDAO.getDB(document.dbName);

        DBCollection coll = db.getCollection(document.collName);

        ObjectId timestamp = new ObjectId();
        Instant now = Instant.ofEpochSecond(timestamp.getTimestamp());

        if (document.content == null) {
            throw new IllegalArgumentException("document.content == null");
            // document.content = new BasicDBObject();
        }

        document.content.put("_etag", timestamp);

        BasicDBObject idQuery = new BasicDBObject("_id", getId(document.documentId));

        if (document.patching) {
            document.content.removeField("_created_on"); // make sure we don't change this field

            DBObject oldDocument = coll.findAndModify(idQuery, null, null, false, new BasicDBObject("$set", document.content), false, false);

            if (oldDocument == null) {
                return HttpStatus.SC_NOT_FOUND;
            } else {
                // check the old etag (in case restore the old document version)
                return optimisticCheckEtag(coll, oldDocument, document.requestEtag, HttpStatus.SC_OK);
            }
        } else {
            document.content.put("_created_on", now.toString()); // let's assume this is an insert. in case we'll set it back with a second update

            // we use findAndModify to get the @created_on field value from the existing document
            // in case this is an update well need to put it back using a second update 
            // it is not possible to do it with a single update
            // (even using $setOnInsert update because we'll need to use the $set operator for other data and this would make it a partial update (patch semantic) 
            DBObject oldDocument = coll.findAndModify(idQuery, null, null, false, document.content, false, true);

            if (oldDocument != null) { // put
                Object oldTimestamp = oldDocument.get("_created_on");

                if (oldTimestamp == null) {
                    oldTimestamp = now.toString();
                    LOGGER.warn("properties of document /{}/{}/{} had no @created_on field. set to now",
                            document.dbName, document.collName, document.documentId);
                }

                // need to readd the @created_on field 
                BasicDBObject created = new BasicDBObject("_created_on", "" + oldTimestamp);
                created.markAsPartialObject();
                coll.update(idQuery, new BasicDBObject("$set", created), true, false);

                // check the old etag (in case restore the old document version)
                return optimisticCheckEtag(coll, oldDocument, document.requestEtag, HttpStatus.SC_OK);
            } else {  // insert
                return HttpStatus.SC_CREATED;
            }
        }
    }

    /**
     *
     * @param document
     * @return
     */
    @Override
    public int post(PostDocumentEntity document) {
        DB db = DBDAO.getDB(document.dbName);

        DBCollection coll = db.getCollection(document.collName);

        ObjectId timestamp = new ObjectId();
        Instant now = Instant.ofEpochSecond(timestamp.getTimestamp());

        if (document.content == null) {
            //document.content = new BasicDBObject();
        }

        document.content.put("_etag", timestamp);
        document.content.put("_created_on", now.toString()); // make sure we don't change this field

        Object _id = document.content.get("_id");
        document.content.removeField("_id");

        if (_id == null) {
            ObjectId id = new ObjectId();
            document.content.put("_id", id);

            coll.insert(document.content);

            document.exchange.getResponseHeaders()
                    .add(HttpString.tryFromString("Location"), 
                            getReferenceLink(document.exchange.getRequestURL(), id.toString()).toString());

            return HttpStatus.SC_CREATED;
        } else {
            document.exchange.getResponseHeaders()
                    .add(HttpString.tryFromString("Location"),
                            getReferenceLink(document.exchange.getRequestURL(), _id.toString()).toString());
        }

        BasicDBObject idQuery = new BasicDBObject("_id", getId("" + _id));

        // we use findAndModify to get the @created_on field value from the existing document
        // we need to put this field back using a second update 
        // it is not possible in a single update even using $setOnInsert update operator
        // in this case we need to provide the other data using $set operator and this makes it a partial update (patch semantic) 
        DBObject oldDocument = coll.findAndModify(idQuery, null, null, false, document.content, false, true);

        if (oldDocument != null) {  // put
            Object oldTimestamp = oldDocument.get("_created_on");

            if (oldTimestamp == null) {
                oldTimestamp = now.toString();
                LOGGER.warn("properties of document /{}/{}/{} had no @created_on field. set to now", 
                        document.dbName, document.collName, _id.toString());
            }

            // need to readd the @created_on field 
            BasicDBObject createdContet = new BasicDBObject("_created_on", "" + oldTimestamp);
            createdContet.markAsPartialObject();
            coll.update(idQuery, new BasicDBObject("$set", createdContet), true, false);

            // check the old etag (in case restore the old document version)
            return optimisticCheckEtag(coll, oldDocument, document.requestEtag, HttpStatus.SC_OK);
        } else { // insert
            return HttpStatus.SC_CREATED;
        }
    }

    /**
     *
     * @param dbName
     * @param collName
     * @param documentId
     * @param requestEtag
     * @return
     */
    public int deleteDocument(String dbName, String collName, String documentId, ObjectId requestEtag) {
        DB db = DBDAO.getDB(dbName);

        DBCollection coll = db.getCollection(collName);

        BasicDBObject idQuery = new BasicDBObject("_id", getId(documentId));

        DBObject oldDocument = coll.findAndModify(idQuery, null, null, true, null, false, false);

        if (oldDocument == null) {
            return HttpStatus.SC_NOT_FOUND;
        } else {
            // check the old etag (in case restore the old document version)
            return optimisticCheckEtag(coll, oldDocument, requestEtag, HttpStatus.SC_NO_CONTENT);
        }
    }

    /**
     *
     * @param cursor
     * @return
     */
//    public ArrayList<DBObject> getDataFromCursor(DBCursor cursor) {
//        return new ArrayList<>(cursor.toArray());
//    }

    private Object getId(String id) {
        if (id == null) {
            return new ObjectId();
        }

        if (ObjectId.isValid(id)) {
            return new ObjectId(id);
        } else {
            // the id is not an object id
            return id;
        }
    }

    private int optimisticCheckEtag(DBCollection coll, DBObject oldDocument, ObjectId requestEtag, int httpStatusIfOk) {
        if (requestEtag == null) {
            coll.save(oldDocument);
            return HttpStatus.SC_CONFLICT;
        }

        Object oldEtag = RequestHelper.getEtagAsObjectId(oldDocument.get("_etag"));

        if (oldEtag == null) {  // well we don't had an etag there so fine
            return HttpStatus.SC_NO_CONTENT;
        } else {
            if (oldEtag.equals(requestEtag)) {
                return httpStatusIfOk; // ok they match
            } else {
                // oopps, we need to restore old document
                // they call it optimistic lock strategy
                coll.save(oldDocument);
                return HttpStatus.SC_PRECONDITION_FAILED;
            }
        }
    }

    private URI getReferenceLink(String parentUrl, String referencedName) {
        try {
            return new URI(URLUtilis.removeTrailingSlashes(parentUrl) + "/" + referencedName);
        } catch (URISyntaxException ex) {
            LOGGER.error("error creating URI from {} + / + {}", parentUrl, referencedName, ex);
        }

        return null;
    }

}
