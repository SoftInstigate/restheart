/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.db;

import com.mongodb.MongoClient;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.DeleteManyModel;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.WriteModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.restheart.exchange.OperationResult;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DocumentDAO implements DocumentRepository {

    private final Logger LOGGER = LoggerFactory.getLogger(DocumentDAO.class);

    private final MongoClient client;

    /**
     *
     */
    public DocumentDAO() {
        client = MongoClientSingleton.getInstance().getClient();
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param documentId
     * @return
     */
    @Override
    public Document getDocumentEtag(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final Object documentId) {
        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<Document> mcoll = mdb.getCollection(collName);

        var query = eq("_id", documentId);
        FindIterable<Document> documents = cs == null
                ? mcoll.find(query)
                        .projection(new Document("_etag", 1))
                : mcoll.find(cs, query)
                        .projection(new Document("_etag", 1));

        return documents.iterator().tryNext();
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param documentId
     * @param shardKeys
     * @param newContent
     * @param requestEtag
     * @param patching
     * @param checkEtag
     * @return the HttpStatus code
     */
    @Override
    @SuppressWarnings("unchecked")
    public OperationResult upsertDocument(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final Object documentId,
            final BsonDocument filter,
            final BsonDocument shardKeys,
            final BsonDocument newContent,
            final String requestEtag,
            final boolean patching,
            final boolean checkEtag) {
        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<BsonDocument> mcoll
                = mdb.getCollection(collName, BsonDocument.class);

        // genereate new etag
        ObjectId newEtag = new ObjectId();

        final BsonDocument content = DAOUtils.validContent(newContent);

        content.put("_etag", new BsonObjectId(newEtag));

        OperationResult updateResult = DAOUtils.updateDocument(
                cs,
                mcoll,
                documentId,
                filter,
                shardKeys,
                content,
                !patching);
        
        BsonDocument oldDocument = updateResult.getOldData();

        if (patching) {
            if (oldDocument == null) {
                return new OperationResult(
                        updateResult.getHttpCode() > 0
                        ? updateResult.getHttpCode()
                        : HttpStatus.SC_CREATED, newEtag, null, updateResult.getNewData());
            } else if (checkEtag) {
                // check the old etag (in case restore the old document version)
                return optimisticCheckEtag(
                        cs,
                        mcoll,
                        shardKeys,
                        oldDocument,
                        newEtag,
                        requestEtag,
                        HttpStatus.SC_OK,
                        false);
            } else {
                var query = eq("_id", documentId);
                BsonDocument newDocument = cs == null
                        ? mcoll.find(query).first()
                        : mcoll.find(cs, query).first();

                return new OperationResult(updateResult.getHttpCode() > 0
                        ? updateResult.getHttpCode()
                        : HttpStatus.SC_OK, newEtag, oldDocument, newDocument);
            }
        } else if (oldDocument != null && checkEtag) { // upsertDocument
            // check the old etag (in case restore the old document)
            return optimisticCheckEtag(
                    cs,
                    mcoll,
                    shardKeys,
                    oldDocument,
                    newEtag,
                    requestEtag,
                    HttpStatus.SC_OK,
                    false);
        } else if (oldDocument != null) {  // insert
            BsonDocument newDocument = mcoll.find(
                    eq("_id", documentId)).first();

            return new OperationResult(
                    updateResult.getHttpCode() > 0
                    ? updateResult.getHttpCode()
                    : HttpStatus.SC_OK, newEtag, oldDocument, newDocument);
        } else {
            BsonDocument newDocument = mcoll.find(
                    eq("_id", documentId)).first();

            return new OperationResult(
                    updateResult.getHttpCode() > 0
                    ? updateResult.getHttpCode()
                    : HttpStatus.SC_CREATED, newEtag, null, newDocument);
        }
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param shardKeys
     * @param newContent
     * @param requestEtag
     * @param checkEtag
     * @return
     */
    @Override
    @SuppressWarnings("unchecked")
    public OperationResult upsertDocumentPost(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final BsonDocument filter,
            final BsonDocument shardKeys,
            final BsonDocument newContent,
            final String requestEtag,
            final boolean checkEtag) {
        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<BsonDocument> mcoll
                = mdb.getCollection(collName, BsonDocument.class);

        ObjectId newEtag = new ObjectId();

        final BsonDocument content = DAOUtils.validContent(newContent);

        content.put("_etag", new BsonObjectId(newEtag));

        Object documentId;

        if (content.containsKey("_id")) {
            documentId = content.get("_id");
        } else {
            documentId = Optional.empty(); // key _id is not present
        }

        // new document since the id is missing ()
        OperationResult updateResult = DAOUtils.updateDocument(
                cs,
                mcoll,
                documentId,
                filter,
                shardKeys,
                content,
                true);

        BsonDocument oldDocument = updateResult.getOldData();
        BsonDocument newDocument = updateResult.getNewData();

        if (oldDocument == null) {
            return new OperationResult(
                    updateResult.getHttpCode() > 0
                    ? updateResult.getHttpCode()
                    : HttpStatus.SC_CREATED,
                    newEtag,
                    null,
                    newDocument);
        } else if (checkEtag) {  // upsertDocument
            // check the old etag (in case restore the old document version)
            return optimisticCheckEtag(
                    cs,
                    mcoll,
                    shardKeys,
                    oldDocument,
                    newEtag,
                    requestEtag,
                    HttpStatus.SC_OK,
                    false);
        } else {
            return new OperationResult(updateResult.getHttpCode() > 0
                    ? updateResult.getHttpCode()
                    : HttpStatus.SC_OK,
                    newEtag, oldDocument, newDocument);
        }
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param documents
     * @param shardKeys
     * @return
     */
    @Override
    @SuppressWarnings("unchecked")
    public BulkOperationResult bulkUpsertDocumentsPost(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final BsonArray documents,
            final BsonDocument filter,
            final BsonDocument shardKeys) {
        Objects.requireNonNull(documents);

        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<BsonDocument> mcoll
                = mdb.getCollection(collName, BsonDocument.class);

        BsonObjectId newEtag = new BsonObjectId(new ObjectId());

        documents
                .stream()
                .filter(d -> d != null && d.isDocument())
                .forEachOrdered(document -> {
                    document.
                            asDocument()
                            .put("_etag", newEtag);
                });

        return DAOUtils.bulkUpsertDocuments(
                cs,
                mcoll,
                documents,
                filter,
                shardKeys);
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param documentId
     * @param filter
     * @param shardedKeys
     * @param requestEtag
     * @param checkEtag
     * @return
     */
    @Override
    public OperationResult deleteDocument(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final Object documentId,
            final BsonDocument filter,
            final BsonDocument shardedKeys,
            final String requestEtag,
            final boolean checkEtag
    ) {
        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<BsonDocument> mcoll
                = mdb.getCollection(collName, BsonDocument.class);

        BsonDocument oldDocument = cs == null
                ? mcoll.findOneAndDelete(
                        getIdFilter(documentId, filter, shardedKeys))
                : mcoll.findOneAndDelete(cs,
                        getIdFilter(documentId, filter, shardedKeys));

        if (oldDocument == null) {
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        } else if (checkEtag) {
            // check the old etag (in case restore the old document version)
            return optimisticCheckEtag(
                    cs,
                    mcoll,
                    null,
                    oldDocument,
                    null,
                    requestEtag,
                    HttpStatus.SC_NO_CONTENT, true);
        } else {
            return new OperationResult(HttpStatus.SC_NO_CONTENT);
        }
    }

    private Bson getIdFilter(
            final Object documentId,
            final BsonDocument filter,
            final BsonDocument shardedKeys) {
        Bson q = eq("_id", documentId);

        if (shardedKeys != null) {
            q = and(q, shardedKeys);
        }

        if (filter != null && !filter.isEmpty()) {
            q = and(q, filter);
        }

        return q;
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param filter
     * @param shardedKeys
     * @return
     */
    @Override
    public BulkOperationResult bulkDeleteDocuments(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final BsonDocument filter,
            final BsonDocument shardedKeys) {
        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<BsonDocument> mcoll
                = mdb.getCollection(collName, BsonDocument.class);

        List<WriteModel<BsonDocument>> deletes = new ArrayList<>();

        Bson _filter;

        if (shardedKeys != null) {
            _filter = and(filter, shardedKeys);
        } else {
            _filter = filter;
        }

        deletes.add(new DeleteManyModel<>(_filter));

        BulkWriteResult result = cs == null
                ? mcoll.bulkWrite(deletes)
                : mcoll.bulkWrite(cs, deletes);

        return new BulkOperationResult(HttpStatus.SC_OK, null, result);
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param filter
     * @param shardedKeys
     * @param data
     * @return
     */
    @Override
    public BulkOperationResult bulkPatchDocuments(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final BsonDocument filter,
            final BsonDocument shardedKeys,
            final BsonDocument data) {
        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<BsonDocument> mcoll
                = mdb.getCollection(collName, BsonDocument.class);

        List<WriteModel<BsonDocument>> patches = new ArrayList<>();

        Bson _filter;

        if (shardedKeys != null) {
            _filter = and(filter, shardedKeys);
        } else {
            _filter = filter;
        }

        patches.add(new UpdateManyModel<>(
                _filter,
                DAOUtils.getUpdateDocument(data),
                DAOUtils.U_NOT_UPSERT_OPS));

        BulkWriteResult result = cs == null
                ? mcoll.bulkWrite(patches)
                : mcoll.bulkWrite(cs, patches);

        return new BulkOperationResult(HttpStatus.SC_OK, null, result);
    }

    private OperationResult optimisticCheckEtag(
            final ClientSession cs,
            final MongoCollection<BsonDocument> coll,
            final BsonDocument shardKeys,
            final BsonDocument oldDocument,
            final Object newEtag,
            final String requestEtag,
            final int httpStatusIfOk,
            final boolean deleting
    ) {

        BsonValue oldEtag = oldDocument.get("_etag");

        if (oldEtag != null && requestEtag == null) {
            // oopps, we need to restore old document
            // they call it optimistic lock strategy
            if (deleting) {
                DAOUtils.updateDocument(
                        cs,
                        coll,
                        oldDocument.get("_id"),
                        null,
                        shardKeys,
                        oldDocument,
                        true);
            } else {
                DAOUtils.restoreDocument(
                        cs,
                        coll,
                        oldDocument.get("_id"),
                        shardKeys,
                        oldDocument,
                        newEtag,
                        "_etag");
            }

            return new OperationResult(
                    HttpStatus.SC_CONFLICT, oldEtag, oldDocument, null);
        }

        BsonValue _requestEtag;

        if (ObjectId.isValid(requestEtag)) {
            _requestEtag = new BsonObjectId(new ObjectId(requestEtag));
        } else {
            // restheart generates ObjectId etags, but here we support
            // strings as well
            _requestEtag = new BsonString(requestEtag);
        }

        if (Objects.equals(_requestEtag, oldEtag)) {
            var query = eq("_id", oldDocument.get("_id"));

            BsonDocument newDocument = cs == null
                    ? coll.find(query).first()
                    : coll.find(cs, query).first();

            return new OperationResult(
                    httpStatusIfOk, newEtag, oldDocument, newDocument);
        } else {
            // oopps, we need to restore old document
            // they call it optimistic lock strategy
            if (deleting) {
                DAOUtils.updateDocument(
                        cs,
                        coll,
                        oldDocument.get("_id"),
                        shardKeys,
                        null,
                        oldDocument,
                        true);
            } else {
                DAOUtils.restoreDocument(
                        cs,
                        coll,
                        oldDocument.get("_id"),
                        shardKeys,
                        oldDocument,
                        newEtag,
                        "_etag");
            }

            return new OperationResult(
                    HttpStatus.SC_PRECONDITION_FAILED,
                    oldEtag,
                    oldDocument,
                    null);
        }
    }
}
