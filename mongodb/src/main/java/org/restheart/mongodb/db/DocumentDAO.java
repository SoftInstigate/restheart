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

import com.mongodb.MongoBulkWriteException;
import com.mongodb.assertions.Assertions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.DeleteManyModel;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.WriteModel;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.ExchangeKeys.WRITE_MODE;

import static org.restheart.mongodb.db.DAOUtils.BAD_VALUE_KEY_ERROR;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DocumentDAO implements DocumentRepository {
    private final CollectionDAO collectionDAO;

    /**
     *
     */
    public DocumentDAO() {
        collectionDAO = new CollectionDAO(MongoClientSingleton.getInstance().getClient());
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
    public BsonDocument getDocumentEtag(
        final Optional<ClientSession> cs,
        final String dbName,
        final String collName,
        final Object documentId) {
        var mcoll = collectionDAO.getCollection(dbName,collName);

        var query = eq("_id", documentId);
        var documents = cs.isPresent()
            ? mcoll.find(cs.get(), query).projection(new BsonDocument("_etag", new BsonInt32(1)))
            : mcoll.find(query).projection(new BsonDocument("_etag", new BsonInt32(1)));

        return documents.iterator().tryNext();
    }

    /**
     *
     * @param cs the client session
     * @param method the request method
     * @param writeMode the write mode
     * @param dbName
     * @param collName
     * @param documentId
     * @param shardKeys
     * @param newContent
     * @param requestEtag
     * @param checkEtag
     * @return the OperationResult
     */
    @Override
    public OperationResult writeDocument(
        final Optional<ClientSession> cs,
        final METHOD method,
        final WRITE_MODE writeMode,
        final String dbName,
        final String collName,
        final Optional<BsonValue> documentId,
        final Optional<BsonDocument> filter,
        final Optional<BsonDocument> shardKeys,
        final BsonDocument newContent,
        final String requestEtag,
        final boolean checkEtag) {
        var mcoll = collectionDAO.getCollection(dbName, collName);

        // genereate new etag
        var newEtag = new BsonObjectId();

        final var content = DAOUtils.validContent(newContent);

        content.put("_etag", newEtag);

        var writeResult = DAOUtils.writeDocument(
            cs,
            method,
            writeMode,
            mcoll,
            documentId,
            filter,
            shardKeys,
            content);

        var oldDocument = writeResult.getOldData();

        return switch(method) {
            case PATCH -> {
                if (oldDocument == null) {
                    yield new OperationResult(writeResult.getHttpCode() > 0 ? writeResult.getHttpCode()  : HttpStatus.SC_CREATED, newEtag, null, writeResult.getNewData(), writeResult.getCause());
                } else if (checkEtag) {
                    // check the old etag (in case restore the old document version)
                    yield optimisticCheckEtag(
                        cs,
                        mcoll,
                        shardKeys,
                        oldDocument,
                        newEtag,
                        requestEtag,
                        HttpStatus.SC_OK,
                        false);
                } else {
                    var query = eq("_id", documentId.get());
                    var newDocument = cs.isPresent() ? mcoll.find(cs.get(), query).first() : mcoll.find(query).first();
                    yield new OperationResult(writeResult.getHttpCode() > 0 ? writeResult.getHttpCode(): HttpStatus.SC_OK, newEtag, oldDocument, newDocument, writeResult.getCause());
                }

            }

            case PUT, POST -> {
                if (oldDocument != null && checkEtag) { // upsertDocument
                    // check the old etag (in case restore the old document)
                    yield optimisticCheckEtag(
                        cs,
                        mcoll,
                        shardKeys,
                        oldDocument,
                        newEtag,
                        requestEtag,
                        HttpStatus.SC_OK,
                        false);
                } else if (oldDocument != null) {  // insert
                    var newDocument = cs.isPresent() ? mcoll.find(cs.get(), eq("_id", oldDocument.get("_id"))).first() : mcoll.find(eq("_id", oldDocument.get("_id"))).first();
                    yield new OperationResult(writeResult.getHttpCode() > 0 ? writeResult.getHttpCode() : HttpStatus.SC_OK, newEtag, oldDocument, newDocument, writeResult.getCause());
                } else {
                    var newDocument = cs.isPresent() ? mcoll.find(cs.get(), eq("_id", documentId.isPresent() ? documentId.get() : writeResult.getNewId() )).first() : mcoll.find(eq("_id", documentId.isPresent() ? documentId.get() : writeResult.getNewId() )).first();
                    yield new OperationResult(writeResult.getHttpCode() > 0 ? writeResult.getHttpCode() : HttpStatus.SC_CREATED, newEtag, null, newDocument, writeResult.getCause());
                }
            }

            default -> throw new UnsupportedOperationException("unsupported method: " + method);
        };
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param shardKeys
     * @param content
     * @param writeMode
     * @param requestEtag
     * @param checkEtag
     * @return the OperationResult
     */
    // public OperationResult writeDocumentPost(
    //     final Optional<ClientSession> cs,
    //     final String dbName,
    //     final String collName,
    //     final Optional<BsonDocument> filter,
    //     final Optional<BsonDocument> shardKeys,
    //     final BsonDocument content,
    //     final WRITE_MODE writeMode,
    //     final String requestEtag,
    //     final boolean checkEtag) {
    //     var mcoll = collectionDAO.getCollection(dbName, collName);

    //     var newEtag = new BsonObjectId();

    //     final var _content = DAOUtils.validContent(content);

    //     _content.put("_etag", newEtag);

    //     Optional<BsonValue> documentId;

    //     if (_content.containsKey("_id")) {
    //         documentId = Optional.of(_content.get("_id"));
    //     } else {
    //         // new document since the id is missing
    //         // if update => error
    //         // if a filter is specified => ok, the first document matching the filter will be updated
    //         if (writeMode == WRITE_MODE.UPDATE && (filter == null || filter.isEmpty())) {
    //             return new OperationResult(HttpStatus.SC_BAD_REQUEST, null, null, null);
    //         } else {
    //             documentId = Optional.empty(); // key _id is not present
    //         }
    //     }

    //     var updateResult = DAOUtils.writeDocument(
    //         cs,
    //         mcoll,
    //         METHOD.POST,
    //         writeMode,
    //         documentId,
    //         filter,
    //         shardKeys,
    //         _content);

    //     var oldDocument = updateResult.getOldData();
    //     var newDocument = updateResult.getNewData();

    //     if (oldDocument == null) {
    //         return new OperationResult(updateResult.getHttpCode() > 0
    //             ? updateResult.getHttpCode()
    //             : HttpStatus.SC_CREATED,
    //             newEtag,
    //             null,
    //             newDocument,
    //             updateResult.getCause());
    //     } else if (checkEtag) {  // upsertDocument
    //         // check the old etag (in case restore the old document version)
    //         return optimisticCheckEtag(
    //             cs,
    //             mcoll,
    //             shardKeys,
    //             oldDocument,
    //             newEtag,
    //             requestEtag,
    //             HttpStatus.SC_OK,
    //             false);
    //     } else {
    //         return new OperationResult(updateResult.getHttpCode() > 0 ? updateResult.getHttpCode() : HttpStatus.SC_OK,
    //             newEtag, oldDocument, newDocument, updateResult.getCause());
    //     }
    // }

    /**
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param documents
     * @param shardKeys
     * @param writeMode
     * @return the BulkOperationResult
     */
    @Override
    public BulkOperationResult bulkPostDocuments(
        final Optional<ClientSession> cs,
        final String dbName,
        final String collName,
        final BsonArray documents,
        final Optional<BsonDocument> filter,
        final Optional<BsonDocument> shardKeys,
        final WRITE_MODE writeMode) {
        Objects.requireNonNull(documents);

        var mcoll = collectionDAO.getCollection(dbName, collName);

        var newEtag = new BsonObjectId(new ObjectId());

        documents
            .stream()
            .filter(d -> d != null && d.isDocument())
            .forEachOrdered(document -> document.asDocument().put("_etag", newEtag));

        return DAOUtils.bulkWriteDocuments(
            cs,
            mcoll,
            documents,
            filter,
            shardKeys,
            writeMode);
    }

    /**
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param filter
     * @param shardedKeys
     * @param data
     * @return the BulkOperationResult
     */
    @Override
    public BulkOperationResult bulkPatchDocuments(
        final Optional<ClientSession> cs,
        final String dbName,
        final String collName,
        final BsonDocument filter,
        final Optional<BsonDocument> shardKeys,
        final BsonDocument data) {
        Assertions.assertNotNull(filter);
        Assertions.assertFalse(filter.isEmpty());

        var mcoll = collectionDAO.getCollection(dbName, collName);

        var patches = new ArrayList<WriteModel<BsonDocument>>();

        Bson _filter;

        if (shardKeys.isPresent() && !shardKeys.get().isEmpty()) {
            _filter = and(filter, shardKeys.get());
        } else {
            _filter = filter;
        }

        patches.add(new UpdateManyModel<>(_filter, DAOUtils.getUpdateDocument(data), DAOUtils.U_NOT_UPSERT_OPS));

        try {
            var result = cs.isPresent() ? mcoll.bulkWrite(cs.get(), patches) : mcoll.bulkWrite(patches);
            return new BulkOperationResult(HttpStatus.SC_OK, null, result);
        } catch (MongoBulkWriteException mce) {
            return switch (mce.getCode()) {
                case BAD_VALUE_KEY_ERROR -> new BulkOperationResult(ResponseHelper.getHttpStatusFromErrorCode(mce.getCode()), null, null);
                default -> throw mce;
            };
        }
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
     * @return the OperationResult
     */
    @Override
    public OperationResult deleteDocument(
        final Optional<ClientSession> cs,
        final String dbName,
        final String collName,
        final Optional<BsonValue> documentId,
        final Optional<BsonDocument> filter,
        final Optional<BsonDocument> shardKeys,
        final String requestEtag,
        final boolean checkEtag
    ) {
        var mcoll = collectionDAO.getCollection(dbName, collName);

        var oldDocument = cs.isPresent()
                ? mcoll.findOneAndDelete(cs.get(), getIdFilter(documentId, filter, shardKeys))
                : mcoll.findOneAndDelete(getIdFilter(documentId, filter, shardKeys));

        if (oldDocument == null) {
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        } else if (checkEtag) {
            // check the old etag (in case restore the old document version)
            return optimisticCheckEtag(
                cs,
                mcoll,
                Optional.empty(),
                oldDocument,
                null,
                requestEtag,
                HttpStatus.SC_NO_CONTENT, true);
        } else {
            return new OperationResult(HttpStatus.SC_NO_CONTENT, oldDocument);
        }
    }

    private Bson getIdFilter(
        final Optional<BsonValue> documentId,
        final Optional<BsonDocument> filter,
        final Optional<BsonDocument> shardedKeys) {
        Assertions.assertTrue(documentId.isPresent() || filter.isPresent());

        Bson q = null;
        var flag = false;

        if (documentId.isPresent()) {
            q = eq("_id", documentId.get());
            flag = true;
        }

        if (shardedKeys.isPresent() && !shardedKeys.get().isEmpty()) {
            q = flag ? and(q, shardedKeys.get()) : eq("_id", shardedKeys.get());
            flag = true;
        }

        if (filter.isPresent() && !filter.get().isEmpty()) {
            q = flag ? and(q, filter.get()) : eq("_id", filter.get());
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
     * @return the BulkOperationResult
     */
    @Override
    public BulkOperationResult bulkDeleteDocuments(
        final Optional<ClientSession> cs,
        final String dbName,
        final String collName,
        final BsonDocument filter,
        final Optional<BsonDocument> shardedKeys) {
        Assertions.assertNotNull(filter);
        Assertions.assertFalse(filter.isEmpty());

        var mcoll = collectionDAO.getCollection(dbName, collName);

        var deletes = new ArrayList<WriteModel<BsonDocument>>();

        Bson _filter;

        if (shardedKeys.isPresent()) {
            _filter = and(filter, shardedKeys.get());
        } else {
            _filter = filter;
        }

        deletes.add(new DeleteManyModel<>(_filter));

        var result = cs.isPresent() ? mcoll.bulkWrite(cs.get(), deletes) : mcoll.bulkWrite(deletes);

        return new BulkOperationResult(HttpStatus.SC_OK, null, result);
    }

    private OperationResult optimisticCheckEtag(
        final Optional<ClientSession> cs,
        final MongoCollection<BsonDocument> coll,
        final Optional<BsonDocument> shardKeys,
        final BsonDocument oldDocument,
        final BsonObjectId newEtag,
        final String requestEtag,
        final int httpStatusIfOk,
        final boolean deleting
    ) {
        var oldEtag = oldDocument.get("_etag");

        if (oldEtag != null && requestEtag == null) {
            // oopps, we need to restore old document
            // they call it optimistic lock strategy
            if (deleting) {
                DAOUtils.writeDocument(
                    cs,
                    METHOD.PUT,
                    WRITE_MODE.UPSERT,
                    coll,
                    Optional.of(oldDocument.get("_id")),
                    Optional.empty(),
                    shardKeys,
                    oldDocument);
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

            return new OperationResult(HttpStatus.SC_CONFLICT, oldEtag, oldDocument, null);
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

            var newDocument = cs.isPresent() ? coll.find(cs.get(), query).first() : coll.find(query).first();

            return new OperationResult(httpStatusIfOk, newEtag, oldDocument, newDocument);
        } else {
            // oopps, we need to restore old document
            // they call it optimistic lock strategy
            if (deleting) {
                DAOUtils.writeDocument(
                    cs,
                    METHOD.PUT,
                    WRITE_MODE.UPSERT,
                    coll,
                    Optional.of(oldDocument.get("_id")),
                    shardKeys,
                    Optional.empty(),
                    oldDocument);
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
