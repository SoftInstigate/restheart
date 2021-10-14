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
import com.mongodb.bulk.BulkWriteResult;
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
            final ClientSession cs,
            final String dbName,
            final String collName,
            final Object documentId) {
        var mcoll = collectionDAO.getCollection(dbName,collName);

        var query = eq("_id", documentId);
        var documents = cs == null
                ? mcoll.find(query)
                        .projection(new BsonDocument("_etag", new BsonInt32(1)))
                : mcoll.find(cs, query)
                        .projection(new BsonDocument("_etag", new BsonInt32(1)));

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
     * @param writeMode
     * @param checkEtag
     * @return the HttpStatus code
     */
    @Override
    public OperationResult writeDocument(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final Object documentId,
            final BsonDocument filter,
            final BsonDocument shardKeys,
            final BsonDocument newContent,
            final String requestEtag,
            final boolean patching,
            final WRITE_MODE writeMode,
            final boolean checkEtag) {
        var mcoll = collectionDAO.getCollection(dbName, collName);

        // genereate new etag
        ObjectId newEtag = new ObjectId();

        final var content = DAOUtils.validContent(newContent);

        content.put("_etag", new BsonObjectId(newEtag));

        var updateResult = DAOUtils.writeDocument(
            cs,
            mcoll,
            documentId,
            filter,
            shardKeys,
            content,
            !patching,
            writeMode);

        var oldDocument = updateResult.getOldData();

        if (patching) {
            if (oldDocument == null) {
                return new OperationResult(updateResult.getHttpCode() > 0 
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
                var newDocument = cs == null ? mcoll.find(query).first() : mcoll.find(cs, query).first();

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
            var newDocument = mcoll.find(eq("_id", documentId)).first();

            return new OperationResult(updateResult.getHttpCode() > 0
                ? updateResult.getHttpCode()
                : HttpStatus.SC_OK, newEtag, oldDocument, newDocument);
        } else {
            var newDocument = mcoll.find(eq("_id", documentId)).first();

            return new OperationResult(updateResult.getHttpCode() > 0
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
     * @param content
     * @param writeMode
     * @param requestEtag
     * @param checkEtag
     * @return the OperationResult
     */
    @Override
    public OperationResult writeDocumentPost(
        final ClientSession cs,
        final String dbName,
        final String collName,
        final BsonDocument filter,
        final BsonDocument shardKeys,
        final BsonDocument content,
        final WRITE_MODE writeMode,
        final String requestEtag,
        final boolean checkEtag) {
        var mcoll = collectionDAO.getCollection(dbName, collName);

        var newEtag = new ObjectId();

        final var _content = DAOUtils.validContent(content);

        _content.put("_etag", new BsonObjectId(newEtag));

        Object documentId;

        if (_content.containsKey("_id")) {
            documentId = _content.get("_id");
        } else {
            // new document since the id is missing
            // if update => error
            if (writeMode == WRITE_MODE.UPDATE) {
                return new OperationResult(HttpStatus.SC_BAD_REQUEST, null, null, null);
            } else {
                documentId = Optional.empty(); // key _id is not present
            }
        }

        var updateResult = DAOUtils.writeDocument(
            cs,
            mcoll,
            documentId,
            filter,
            shardKeys,
            _content,
            true,
            writeMode);

        var oldDocument = updateResult.getOldData();
        var newDocument = updateResult.getNewData();

        if (oldDocument == null) {
            return new OperationResult(updateResult.getHttpCode() > 0
                ? updateResult.getHttpCode()
                : HttpStatus.SC_CREATED,
                newEtag,
                null,
                newDocument,
                updateResult.getCause());
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
            return new OperationResult(updateResult.getHttpCode() > 0 ? updateResult.getHttpCode() : HttpStatus.SC_OK,
                newEtag, oldDocument, newDocument, updateResult.getCause());
        }
    }

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
        final ClientSession cs,
        final String dbName,
        final String collName,
        final BsonArray documents,
        final BsonDocument filter,
        final BsonDocument shardKeys,
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
        final ClientSession cs,
        final String dbName,
        final String collName,
        final BsonDocument filter,
        final BsonDocument shardedKeys,
        final BsonDocument data) {
        var mcoll = collectionDAO.getCollection(dbName, collName);

        var patches = new ArrayList<WriteModel<BsonDocument>>();

        Bson _filter;

        if (shardedKeys != null) {
            _filter = and(filter, shardedKeys);
        } else {
            _filter = filter;
        }

        patches.add(new UpdateManyModel<>(_filter, DAOUtils.getUpdateDocument(data), DAOUtils.U_NOT_UPSERT_OPS));

        try {
            var result = cs == null ? mcoll.bulkWrite(patches) : mcoll.bulkWrite(cs, patches);
            return new BulkOperationResult(HttpStatus.SC_OK, null, result);
        } catch (MongoBulkWriteException mce) {
            switch (mce.getCode()) {
                case BAD_VALUE_KEY_ERROR:
                    return new BulkOperationResult(ResponseHelper.getHttpStatusFromErrorCode(mce.getCode()), null, null);
                default:
                    throw mce;
            }
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
        final ClientSession cs,
        final String dbName,
        final String collName,
        final Object documentId,
        final BsonDocument filter,
        final BsonDocument shardedKeys,
        final String requestEtag,
        final boolean checkEtag
    ) {
        var mcoll = collectionDAO.getCollection(dbName, collName);

        var oldDocument = cs == null
                ? mcoll.findOneAndDelete(getIdFilter(documentId, filter, shardedKeys))
                : mcoll.findOneAndDelete(cs,getIdFilter(documentId, filter, shardedKeys));

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
            return new OperationResult(HttpStatus.SC_NO_CONTENT, oldDocument);
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
     * @return the BulkOperationResult
     */
    @Override
    public BulkOperationResult bulkDeleteDocuments(
        final ClientSession cs,
        final String dbName,
        final String collName,
        final BsonDocument filter,
        final BsonDocument shardedKeys) {
        var mcoll = collectionDAO.getCollection(dbName, collName);

        var deletes = new ArrayList<WriteModel<BsonDocument>>();

        Bson _filter;

        if (shardedKeys != null) {
            _filter = and(filter, shardedKeys);
        } else {
            _filter = filter;
        }

        deletes.add(new DeleteManyModel<>(_filter));

        BulkWriteResult result = cs == null ? mcoll.bulkWrite(deletes) : mcoll.bulkWrite(cs, deletes);

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
        var oldEtag = oldDocument.get("_etag");

        if (oldEtag != null && requestEtag == null) {
            // oopps, we need to restore old document
            // they call it optimistic lock strategy
            if (deleting) {
                DAOUtils.writeDocument(
                    cs,
                    coll,
                    oldDocument.get("_id"),
                    null,
                    shardKeys,
                    oldDocument,
                    true,
                    WRITE_MODE.UPSERT);
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

            BsonDocument newDocument = cs == null ? coll.find(query).first() : coll.find(cs, query).first();

            return new OperationResult(httpStatusIfOk, newEtag, oldDocument, newDocument);
        } else {
            // oopps, we need to restore old document
            // they call it optimistic lock strategy
            if (deleting) {
                DAOUtils.writeDocument(
                    cs,
                    coll,
                    oldDocument.get("_id"),
                    shardKeys,
                    null,
                    oldDocument,
                    true,
                    WRITE_MODE.UPSERT);
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
