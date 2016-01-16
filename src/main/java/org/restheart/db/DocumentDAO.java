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

import com.google.common.base.Objects;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.Document;
import org.restheart.utils.HttpStatus;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DocumentDAO implements Repository {
    
    private final Logger LOGGER = LoggerFactory.getLogger(DocumentDAO.class);
    
    private final MongoClient client;

    private final static FindOneAndReplaceOptions FAR_UPSERT_OPS
            = new FindOneAndReplaceOptions().upsert(true);

    private final static UpdateOptions UPDATE_UPSERT_OPS
            = new UpdateOptions().upsert(true);

    public DocumentDAO() {
        client = MongoDBClientSingleton.getInstance().getClient();
    }

    /**
     * @param dbName
     * @param collName
     * @param documentId
     * @param newContent
     * @param requestEtag
     * @param patching
     * @param checkEtag
     * @return the HttpStatus code
     */
    @Override
    public OperationResult upsertDocument(
            final String dbName,
            final String collName,
            final Object documentId,
            final DBObject newContent,
            final String requestEtag,
            final boolean patching,
            final boolean checkEtag) {
        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<Document> mcoll = mdb.getCollection(collName);

        // genereate new etag
        ObjectId newEtag = new ObjectId();

        final DBObject content = DAOUtils.validContent(newContent);

        content.put("_etag", newEtag);

        //TODO remove this after migration to mongodb driver 3.2 completes
        Document dcontent = new Document(content.toMap());

        if (patching) {
            List<String> keys;
            keys = dcontent.keySet().stream().filter((String key)
                    -> !"$inc".equals(key)
                    && !"$mul".equals(key)
                    && !"$rename".equals(key)
                    && !"$setOnInsert".equals(key)
                    && !"$set".equals(key)
                    && !"$unset".equals(key)
                    && !"$min".equals(key)
                    && !"$max".equals(key)
                    && !"$currentDate".equals(key))
                    .collect(Collectors.toList());

            if (keys != null && !keys.isEmpty()) {

                Document set = new Document();

                keys.stream().forEach((String key)
                        -> {
                    Object o = dcontent.remove(key);

                    set.append(key, o);
                });

                if (dcontent.get("$set") == null) {
                    dcontent.put("$set", set);
                } else if (dcontent.get("$set") instanceof Document) {
                    ((Document) dcontent.get("$set"))
                            .putAll(set);
                } else if (dcontent.get("$set") instanceof DBObject) { //TODO remove this after migration to mongodb driver 3.2 completes
                    ((DBObject) dcontent.get("$set"))
                            .putAll(set);
                } else {
                    LOGGER.warn("count not add properties to $set since request data contains $set property which is not an object: {}", dcontent.get("$set"));
                }
            }

            Document oldDocument = mcoll.findOneAndUpdate(
                    eq("_id", documentId),
                    dcontent);

            if (oldDocument == null) {
                return new OperationResult(HttpStatus.SC_NOT_FOUND);
            } else if (checkEtag) {
                // check the old etag (in case restore the old document version)
                return optimisticCheckEtag(mcoll, oldDocument, newEtag,
                        requestEtag, HttpStatus.SC_OK);
            } else {
                return new OperationResult(HttpStatus.SC_OK, newEtag);
            }
        } else {
            Document oldDocument = mcoll.findOneAndReplace(
                    eq("_id", documentId),
                    dcontent,
                    FAR_UPSERT_OPS);

            if (oldDocument != null && checkEtag) { // upsertDocument
                // check the old etag (in case restore the old document)
                return optimisticCheckEtag(mcoll, oldDocument, newEtag,
                        requestEtag, HttpStatus.SC_OK);
            } else if (oldDocument != null) {  // insert
                return new OperationResult(HttpStatus.SC_OK, newEtag);
            } else {
                return new OperationResult(HttpStatus.SC_CREATED, newEtag);
            }
        }
    }

    @Override
    public Document getDocumentEtag(final String dbName, final String collName, final Object documentId) {
        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<Document> mcoll = mdb.getCollection(collName);

        FindIterable<Document> documents = mcoll
                .find(eq("_id", documentId))
                .projection(new Document("_etag", 1));

        return documents == null ? null
                : documents.iterator().tryNext();
    }

    /**
     * @param dbName
     * @param collName
     * @param documentId
     * @param newContent
     * @param requestEtag
     * @param checkEtag
     * @return
     */
    @Override
    public OperationResult upsertDocumentPost(
            final String dbName,
            final String collName,
            final Object documentId,
            final DBObject newContent,
            final String requestEtag,
            final boolean checkEtag) {
        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<Document> mcoll = mdb.getCollection(collName);

        ObjectId newEtag = new ObjectId();

        final DBObject content = (newContent == null) ? new BasicDBObject() : newContent;

        content.put("_etag", newEtag);

        Object _idInContent = content.get("_id");

        content.removeField("_id");

        //TODO remove this after migration to mongodb driver 3.2 completes
        Document dcontent = new Document(content.toMap());

        if (_idInContent == null) {
            // new document since the id was just auto-generated
            dcontent.put("_id", documentId);

            mcoll.insertOne(dcontent);

            return new OperationResult(HttpStatus.SC_CREATED, newEtag);
        } else {
            Document oldDocument = mcoll.findOneAndReplace(eq("_id", documentId), dcontent, FAR_UPSERT_OPS);

            if (oldDocument == null) {
                return new OperationResult(HttpStatus.SC_CREATED, newEtag);
            } else if (checkEtag) {  // upsertDocument
                // check the old etag (in case restore the old document version)
                return optimisticCheckEtag(mcoll, oldDocument, newEtag, requestEtag, HttpStatus.SC_OK);
            } else {
                return new OperationResult(HttpStatus.SC_OK, newEtag);

            }
        }
    }

    /**
     * @param dbName
     * @param collName
     * @param documentId
     * @param requestEtag
     * @param checkEtag
     * @return
     */
    @Override
    public OperationResult deleteDocument(
            final String dbName,
            final String collName,
            final Object documentId,
            final String requestEtag,
            final boolean checkEtag
    ) {
        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<Document> mcoll = mdb.getCollection(collName);

        Document oldDocument = mcoll.findOneAndDelete(eq("_id", documentId));

        if (oldDocument == null) {
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        } else if (checkEtag) {
            // check the old etag (in case restore the old document version)
            return optimisticCheckEtag(mcoll, oldDocument, null, requestEtag, HttpStatus.SC_NO_CONTENT);
        } else {
            return new OperationResult(HttpStatus.SC_NO_CONTENT);
        }
    }

    private OperationResult optimisticCheckEtag(
            final MongoCollection<Document> coll,
            final Document oldDocument,
            final Object newEtag,
            final String requestEtag,
            final int httpStatusIfOk) {

        Object oldEtag = oldDocument.get("_etag");

        if (oldEtag != null && requestEtag == null) {
            coll.updateOne(eq("_id", oldDocument.get("_id")),
                    new Document("$set", oldDocument),
                    UPDATE_UPSERT_OPS);
            return new OperationResult(HttpStatus.SC_CONFLICT, oldEtag);
        }

        String _oldEtag;

        if (oldEtag != null) {
            _oldEtag = oldEtag.toString();
        } else {
            _oldEtag = null;
        }

        if (Objects.equal(requestEtag, _oldEtag)) {
            return new OperationResult(httpStatusIfOk, newEtag);
        } else {
            // oopps, we need to restore old document
            // they call it optimistic lock strategy
            coll.updateOne(eq("_id", oldDocument.get("_id")),
                    new Document("$set", oldDocument),
                    UPDATE_UPSERT_OPS);
            return new OperationResult(HttpStatus.SC_PRECONDITION_FAILED, oldEtag);
        }
    }
}
