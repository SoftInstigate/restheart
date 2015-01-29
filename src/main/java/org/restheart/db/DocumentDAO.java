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

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.RequestHelper;
import org.restheart.utils.URLUtils;
import io.undertow.util.HttpString;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import static jdk.nashorn.internal.runtime.Debug.id;
import org.bson.types.ObjectId;
import org.restheart.utils.IllegalDocumentIdException;
import org.restheart.utils.URLUtils.DOC_ID_TYPE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DocumentDAO implements Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentDAO.class);

    public DocumentDAO() {
    }

    /**
     * @param dbName
     * @param collName
     * @param documentId
     * @param content
     * @param requestEtag
     * @param patching
     * @return the HttpStatus code
     */
    @Override
    public int upsertDocument(String dbName, String collName, String documentId, DBObject content, ObjectId requestEtag, boolean patching) {
        DB db = client.getDB(dbName);

        DBCollection coll = db.getCollection(collName);

        ObjectId timestamp = new ObjectId();
        Instant now = Instant.ofEpochSecond(timestamp.getTimestamp());

        if (content == null) {
            content = new BasicDBObject();
        }

        content.put("_etag", timestamp);

        BasicDBObject idQuery = new BasicDBObject("_id", documentId);

        if (patching) {
            content.removeField("_created_on"); // make sure we don't change this field

            DBObject oldDocument = coll.findAndModify(idQuery, null, null, false, new BasicDBObject("$set", content), false, false);

            if (oldDocument == null) {
                return HttpStatus.SC_NOT_FOUND;
            } else {
                // check the old etag (in case restore the old document version)
                return optimisticCheckEtag(coll, oldDocument, requestEtag, HttpStatus.SC_OK);
            }
        } else {
            content.put("_created_on", now.toString()); // let's assume this is an insert. in case we'll set it back with a second update

            // we use findAndModify to get the @created_on field value from the existing document
            // in case this is an update well need to upsertDocument it back using a second update 
            // it is not possible to do it with a single update
            // (even using $setOnInsert update because we'll need to use the $set operator for other data and this would make it a partial update (patch semantic) 
            DBObject oldDocument = coll.findAndModify(idQuery, null, null, false, content, false, true);

            if (oldDocument != null) { // upsertDocument
                Object oldTimestamp = oldDocument.get("_created_on");

                if (oldTimestamp == null) {
                    oldTimestamp = now.toString();
                    LOGGER.warn("properties of document /{}/{}/{} had no @created_on field. set it to current time",
                            dbName, collName, documentId);
                }

                // need to readd the @created_on field 
                BasicDBObject created = new BasicDBObject("_created_on", "" + oldTimestamp);
                created.markAsPartialObject();
                coll.update(idQuery, new BasicDBObject("$set", created), true, false);

                // check the old etag (in case restore the old document version)
                return optimisticCheckEtag(coll, oldDocument, requestEtag, HttpStatus.SC_OK);
            } else {  // insert
                return HttpStatus.SC_CREATED;
            }
        }
    }

    /**
     * @param exchange
     * @param dbName
     * @param collName
     * @param docId
     * @param content
     * @param requestEtag
     * @return
     */
    @Override
    public int upsertDocumentPost(HttpServerExchange exchange, String dbName, String collName, DBObject content, ObjectId requestEtag) {
        DB db = client.getDB(dbName);

        DBCollection coll = db.getCollection(collName);

        ObjectId timestamp = new ObjectId();
        Instant now = Instant.ofEpochSecond(timestamp.getTimestamp());

        if (content == null) {
            content = new BasicDBObject();
        }

        content.put("_etag", timestamp);
        content.put("_created_on", now.toString()); // make sure we don't change this field

        Object _idInContent = content.get("_id");
        content.removeField("_id");
       
        if (_idInContent == null) {
            // new document since the id was just auto-generated
            content.put("_id", docId);

            coll.insert(content);

            exchange.getResponseHeaders()
                    .add(HttpString.tryFromString("Location"),
                            getReferenceLink(exchange.getRequestURL(), docId.toString()).toString());

            return HttpStatus.SC_CREATED;
        } else {
            // might be existing, we will check
            exchange.getResponseHeaders()
                    .add(HttpString.tryFromString("Location"),
                            getReferenceLink(exchange.getRequestURL(), _idInContent.toString()).toString());
        }
        

        BasicDBObject idQuery = new BasicDBObject("_id", docId);

        // we use findAndModify to get the @created_on field value from the existing document
        // we need to upsertDocument this field back using a second update 
        // it is not possible in a single update even using $setOnInsert update operator
        // in this case we need to provide the other data using $set operator and this makes it a partial update (patch semantic) 
        DBObject oldDocument = coll.findAndModify(idQuery, null, null, false, content, false, true);

        if (oldDocument != null) {  // upsertDocument
            Object oldTimestamp = oldDocument.get("_created_on");

            if (oldTimestamp == null) {
                oldTimestamp = now.toString();
                LOGGER.warn("properties of document /{}/{}/{} had no @created_on field. set it to current time",
                        dbName, collName, _idInContent.toString());
            }

            // need to readd the @created_on field 
            BasicDBObject createdContent = new BasicDBObject("_created_on", "" + oldTimestamp);
            createdContent.markAsPartialObject();
            coll.update(idQuery, new BasicDBObject("$set", createdContent), true, false);

            // check the old etag (in case restore the old document version)
            return optimisticCheckEtag(coll, oldDocument, requestEtag, HttpStatus.SC_OK);
        } else { // insert
            return HttpStatus.SC_CREATED;
        }
    }

    /**
     * @param dbName
     * @param collName
     * @param documentId
     * @param requestEtag
     * @return
     */
    @Override
    public int deleteDocument(String dbName, String collName, String documentId, ObjectId requestEtag) {
        DB db = client.getDB(dbName);

        DBCollection coll = db.getCollection(collName);

        BasicDBObject idQuery = new BasicDBObject("_id", documentId);

        DBObject oldDocument = coll.findAndModify(idQuery, null, null, true, null, false, false);

        if (oldDocument == null) {
            return HttpStatus.SC_NOT_FOUND;
        } else {
            // check the old etag (in case restore the old document version)
            return optimisticCheckEtag(coll, oldDocument, requestEtag, HttpStatus.SC_NO_CONTENT);
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
            return new URI(URLUtils.removeTrailingSlashes(parentUrl) + "/" + referencedName);
        } catch (URISyntaxException ex) {
            LOGGER.error("error creating URI from {} + / + {}", parentUrl, referencedName, ex);
        }

        return null;
    }

}
