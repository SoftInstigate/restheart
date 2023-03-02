/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
import static org.restheart.mongodb.db.DbUtils.BAD_VALUE_KEY_ERROR;
import static org.restheart.utils.BsonUtils.document;
import org.restheart.mongodb.RSOps;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Documents {
    private final Collections collections = Collections.get();

    private Documents() {
    }

    private static Documents INSTANCE = new Documents();

    public static Documents get() {
        return INSTANCE;
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName
     * @param documentId
     * @return
     */
    public BsonDocument getDocumentEtag(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName,
        final Object documentId) {
        var mcoll = collections.collection(rsOps, dbName, collName);

        var query = eq("_id", documentId);
        var documents = cs.isPresent()
            ? mcoll.find(cs.get(), query).projection(new BsonDocument("_etag", new BsonInt32(1)))
            : mcoll.find(query).projection(new BsonDocument("_etag", new BsonInt32(1)));

        return documents.iterator().tryNext();
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param method the request method
     * @param writeMode the write mode
     * @param collName
     * @param documentId
     * @param shardKeys
     * @param newContent
     * @param requestEtag
     * @param checkEtag
     * @return the OperationResult
     */
    public OperationResult writeDocument(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName,
        final METHOD method,
        final WRITE_MODE writeMode,
        final Optional<BsonValue> documentId,
        final Optional<BsonDocument> filter,
        final Optional<BsonDocument> shardKeys,
        final BsonValue newContent,
        final String requestEtag,
        final boolean checkEtag) {
        var mcoll = collections.collection(rsOps, dbName, collName);

        // genereate new etag
        var newEtag = new BsonObjectId();

        final OperationResult writeResult;

        if (newContent.isDocument()) {
            // the content is a document or an update operator expression
            var newContentDoc = newContent.asDocument();

            final var content = DbUtils.validContent(newContentDoc);

            content.put("_etag", newEtag);

            writeResult = DbUtils.writeDocument(
                cs,
                method,
                writeMode,
                mcoll,
                documentId,
                filter,
                shardKeys,
                content);
        } else {
            // the content is an aggregation update array
            var newContentPipeline = newContent.asArray();

            newContentPipeline.add(document().put("$set", document().put("_etag", newEtag)).get());

            writeResult = DbUtils.writeDocument(
                cs,
                method,
                writeMode,
                mcoll,
                documentId,
                filter,
                shardKeys,
                newContentPipeline);
        }

        var oldDocument = writeResult.getOldData();
        var newDocument = writeResult.getNewData();

        if (oldDocument != null && checkEtag) {
            // check the old etag (if not match then restore the old document version)
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
            var httpCode = writeResult.getHttpCode() > 0 ? writeResult.getHttpCode() : oldDocument == null ? HttpStatus.SC_CREATED : HttpStatus.SC_OK;

            // invalidate the cache entris of this collection
            GetCollectionCache.getInstance().invalidateAll(dbName, collName);
            return new OperationResult(httpCode, newEtag, oldDocument, newDocument, writeResult.getCause());
        }
    }

    /**
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName
     * @param documents
     * @param shardKeys
     * @param writeMode
     * @return the BulkOperationResult
     */
    public BulkOperationResult bulkPostDocuments(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName,
        final BsonArray documents,
        final Optional<BsonDocument> filter,
        final Optional<BsonDocument> shardKeys,
        final WRITE_MODE writeMode) {
        Objects.requireNonNull(documents);

        var mcoll = collections.collection(rsOps, dbName, collName);

        var newEtag = new BsonObjectId(new ObjectId());

        documents
            .stream()
            .filter(d -> d != null && d.isDocument())
            .forEachOrdered(document -> document.asDocument().put("_etag", newEtag));

        var ret = DbUtils.bulkWriteDocuments(
            cs,
            mcoll,
            documents,
            filter,
            shardKeys,
            writeMode);

        // invalidate the cache entris of this collection
        GetCollectionCache.getInstance().invalidateAll(dbName, collName);

        return ret;
    }

    /**
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName
     * @param filter
     * @param shardedKeys
     * @param data
     * @return the BulkOperationResult
     */
    public BulkOperationResult bulkPatchDocuments(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName,
        final BsonDocument filter,
        final Optional<BsonDocument> shardKeys,
        final BsonDocument data) {
        Objects.requireNonNull(filter);
        Assertions.assertFalse(filter.isEmpty());

        var mcoll = collections.collection(rsOps, dbName, collName);

        var patches = new ArrayList<WriteModel<BsonDocument>>();

        Bson _filter;

        if (shardKeys.isPresent() && !shardKeys.get().isEmpty()) {
            _filter = and(filter, shardKeys.get());
        } else {
            _filter = filter;
        }

        patches.add(new UpdateManyModel<>(_filter, DbUtils.getUpdateDocument(data), DbUtils.U_NOT_UPSERT_OPS));

        try {
            var result = cs.isPresent() ? mcoll.bulkWrite(cs.get(), patches) : mcoll.bulkWrite(patches);
            var ret = new BulkOperationResult(HttpStatus.SC_OK, null, result);

            // invalidate the cache entris of this collection
            GetCollectionCache.getInstance().invalidateAll(dbName, collName);
            return ret;
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
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName
     * @param documentId
     * @param filter
     * @param shardedKeys
     * @param requestEtag
     * @param checkEtag
     * @return the OperationResult
     */
    public OperationResult deleteDocument(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName,
        final Optional<BsonValue> documentId,
        final Optional<BsonDocument> filter,
        final Optional<BsonDocument> shardKeys,
        final String requestEtag,
        final boolean checkEtag) {
        var mcoll = collections.collection(rsOps, dbName, collName);

        var oldDocument = cs.isPresent()
                ? mcoll.findOneAndDelete(cs.get(), idFilter(documentId, filter, shardKeys))
                : mcoll.findOneAndDelete(idFilter(documentId, filter, shardKeys));

        if (oldDocument == null) {
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        } else if (checkEtag) {
            // check the old etag (in not match restore the old document version)
            return optimisticCheckEtag(
                cs,
                mcoll,
                Optional.empty(),
                oldDocument,
                null,
                requestEtag,
                HttpStatus.SC_NO_CONTENT, true);
        } else {
            // invalidate the cache entris of this collection
            GetCollectionCache.getInstance().invalidateAll(dbName, collName);
            return new OperationResult(HttpStatus.SC_NO_CONTENT, oldDocument);
        }
    }

    private Bson idFilter(
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
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName
     * @param filter
     * @param shardedKeys
     * @return the BulkOperationResult
     */
    public BulkOperationResult bulkDeleteDocuments(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName,
        final BsonDocument filter,
        final Optional<BsonDocument> shardedKeys) {
        Objects.requireNonNull(filter);
        Assertions.assertFalse(filter.isEmpty());

        var mcoll = collections.collection(rsOps, dbName, collName);

        var deletes = new ArrayList<WriteModel<BsonDocument>>();

        Bson _filter;

        if (shardedKeys.isPresent()) {
            _filter = and(filter, shardedKeys.get());
        } else {
            _filter = filter;
        }

        deletes.add(new DeleteManyModel<>(_filter));

        var result = cs.isPresent() ? mcoll.bulkWrite(cs.get(), deletes) : mcoll.bulkWrite(deletes);

        // invalidate the cache entris of this collection
        GetCollectionCache.getInstance().invalidateAll(dbName, collName);

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
                DbUtils.writeDocument(
                    cs,
                    METHOD.PUT,
                    WRITE_MODE.UPSERT,
                    coll,
                    Optional.of(oldDocument.get("_id")),
                    Optional.empty(),
                    shardKeys,
                    oldDocument);
            } else {
                DbUtils.restoreDocument(
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

            // invalidate the cache entris of this collection
            GetCollectionCache.getInstance().invalidateAll(coll);

            return new OperationResult(httpStatusIfOk, newEtag, oldDocument, newDocument);
        } else {
            // oopps, we need to restore old document
            // they call it optimistic lock strategy
            if (deleting) {
                DbUtils.writeDocument(
                    cs,
                    METHOD.PUT,
                    WRITE_MODE.UPSERT,
                    coll,
                    Optional.of(oldDocument.get("_id")),
                    shardKeys,
                    Optional.empty(),
                    oldDocument);
            } else {
                DbUtils.restoreDocument(
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
