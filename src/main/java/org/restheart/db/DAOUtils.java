/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
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

import com.mongodb.MongoCommandException;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonTimestamp;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Nath Papadacis {@literal <nath@thirststudios.co.uk>}
 */
public class DAOUtils {

    public final static Logger LOGGER = LoggerFactory.getLogger(DAOUtils.class);

    public final static FindOneAndUpdateOptions FAU_UPSERT_OPS = new FindOneAndUpdateOptions()
            .upsert(true).returnDocument(ReturnDocument.AFTER);

    public final static FindOneAndUpdateOptions FAU_NOT_UPSERT_OPS = new FindOneAndUpdateOptions()
            .upsert(false).returnDocument(ReturnDocument.AFTER);

    public final static FindOneAndUpdateOptions FOU_AFTER_UPSERT_OPS = new FindOneAndUpdateOptions()
            .upsert(true).returnDocument(ReturnDocument.AFTER);

    public final static FindOneAndReplaceOptions FOR_AFTER_UPSERT_OPS = new FindOneAndReplaceOptions()
            .upsert(true).returnDocument(ReturnDocument.AFTER);

    public final static FindOneAndUpdateOptions FOU_AFTER_NOT_UPSERT_OPS = new FindOneAndUpdateOptions()
            .upsert(false).returnDocument(ReturnDocument.AFTER);

    public final static FindOneAndReplaceOptions FOR_AFTER_NOT_UPSERT_OPS = new FindOneAndReplaceOptions()
            .upsert(false).returnDocument(ReturnDocument.AFTER);

    public final static UpdateOptions U_UPSERT_OPS = new UpdateOptions()
            .upsert(true);

    public final static UpdateOptions U_NOT_UPSERT_OPS = new UpdateOptions()
            .upsert(false);
    
    public final static  BulkWriteOptions BWO_NOT_ORDERED = new BulkWriteOptions()
            .ordered(false);

    private static final Bson IMPOSSIBLE_CONDITION = eq("_etag", new ObjectId());

    /**
     *
     * @param newContent the value of newContent
     * @return a not null BsonDocument
     */
    protected static BsonDocument validContent(final BsonDocument newContent) {
        return (newContent == null) ? new BsonDocument() : newContent;
    }

    /**
     *
     * @param coll
     * @param documentId use Optional.empty() to specify no documentId (null is
     * _id: null)
     * @param shardKeys
     * @param data
     * @param patching Whether we want to patch the metadata or replace it
     * entirely.
     * @return the old document
     */
    public static OperationResult updateMetadata(
            MongoCollection<BsonDocument> coll,
            Object documentId,
            BsonDocument filter,
            BsonDocument shardKeys,
            BsonDocument data,
            boolean patching) {
        return updateDocument(
                coll,
                documentId,
                filter,
                shardKeys,
                data,
                false,
                patching,
                false);
    }

    /**
     *
     * @param coll
     * @param documentId use Optional.empty() to specify no documentId (null is
     * _id: null)
     * @param shardKeys
     * @param data
     * @param replace
     * @return the old document
     */
    public static OperationResult updateDocument(
            MongoCollection<BsonDocument> coll,
            Object documentId,
            BsonDocument filter,
            BsonDocument shardKeys,
            BsonDocument data,
            boolean replace) {
        return updateDocument(
                coll,
                documentId,
                filter,
                shardKeys,
                data,
                replace,
                false,
                true);
    }

    /**
     * Update a mongo document<br>
     * <strong>TODO</strong> - Think about changing the numerous arguments into
     * a context
     *
     * @param coll
     * @param documentId use Optional.empty() to specify no documentId (null is
     * _id: null)
     * @param shardKeys
     * @param data
     * @param replace
     * @param deepPatching if true then we will flatten any nested BsonDocuments
     * into dot notation to ensure only the requested fields are updated.
     * @param allowUpsert whether or not to allow upsert mode
     * @return the new or old document depending on returnNew
     */
    @SuppressWarnings("rawtypes")
    public static OperationResult updateDocument(
            MongoCollection<BsonDocument> coll,
            Object documentId,
            BsonDocument filter,
            BsonDocument shardKeys,
            BsonDocument data,
            boolean replace,
            boolean deepPatching,
            boolean allowUpsert) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(data);

        Bson query;

        boolean idPresent = true;

        if (documentId instanceof Optional
                && !((Optional) documentId).isPresent()) {
            query = IMPOSSIBLE_CONDITION;
            idPresent = false;
        } else {
            query = eq("_id", documentId);
        }

        if (shardKeys != null) {
            query = and(query, shardKeys);
        }

        if (filter != null && !filter.isEmpty()) {
            query = and(query, filter);
        }

        BsonDocument oldDocument;

        if (idPresent) {
            oldDocument = coll.find(query).first();

            if (!allowUpsert && oldDocument == null) {
                return new OperationResult(HttpStatus.SC_NOT_FOUND);
            }
        } else {
            oldDocument = null;
        }

        if (replace) {
            BsonDocument newDocument;

            try {
                newDocument = coll.findOneAndReplace(query,
                        getReplaceDocument(data),
                        allowUpsert ? FOR_AFTER_UPSERT_OPS : FOR_AFTER_NOT_UPSERT_OPS);
            } catch (IllegalArgumentException iae) {
                return new OperationResult(HttpStatus.SC_BAD_REQUEST, oldDocument, null);
            } catch (MongoCommandException mce) {
                LOGGER.debug("document {} not updated, "
                        + "might be due to a duplicate key error. "
                        + "errorCode: {}, errorMessage: {}", 
                        documentId, 
                        mce.getErrorCode(),
                        mce.getErrorMessage());
                if (mce.getErrorCode() == 11000) {
                    if (allowUpsert
                            && filter != null
                            && !filter.isEmpty()
                            && mce.getErrorMessage().contains("$_id_ dup key")) {
                        // DuplicateKey error
                        // this happens if the filter parameter didn't match
                        // the existing document and so the upserted doc
                        // has an existing _id 
                        return new OperationResult(HttpStatus.SC_NOT_FOUND, oldDocument, null);
                    } else {
                        return new OperationResult(HttpStatus.SC_EXPECTATION_FAILED, oldDocument, null);
                    }
                } else {
                    throw mce;
                }
            }

            return new OperationResult(-1, oldDocument, newDocument);
        } else {
            BsonDocument newDocument;

            try {
                newDocument = coll.findOneAndUpdate(query,
                        getUpdateDocument(data, deepPatching),
                        allowUpsert ? FAU_UPSERT_OPS : FAU_NOT_UPSERT_OPS);
            } catch (MongoCommandException mce) {
                if (mce.getErrorCode() == 11000) {
                    if (allowUpsert
                            && filter != null
                            && !filter.isEmpty()
                            && mce.getErrorMessage().contains("$_id_ dup key")) {
                        // DuplicateKey error
                        // this happens if the filter parameter didn't match
                        // the existing document and so the upserted doc
                        // has an existing _id 
                        return new OperationResult(HttpStatus.SC_NOT_FOUND,
                                oldDocument,
                                null);
                    } else {
                        return new OperationResult(HttpStatus.SC_EXPECTATION_FAILED,
                                oldDocument,
                                null);
                    }
                } else {
                    throw mce;
                }
            }

            return new OperationResult(-1, oldDocument, newDocument);
        }
    }

    public static boolean restoreDocument(
            MongoCollection<BsonDocument> coll,
            Object documentId,
            BsonDocument shardKeys,
            BsonDocument data,
            Object etag,
            String etagLocation) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(data);

        Bson query;

        if (etag == null) {
            query = eq("_id", documentId);
        } else {
            query = and(eq("_id", documentId), eq(
                    etagLocation != null && !etagLocation.isEmpty()
                    ? etagLocation : "_etag", etag));
        }

        if (shardKeys != null) {
            query = and(query, shardKeys);
        }

        UpdateResult result = coll.replaceOne(query, data, U_NOT_UPSERT_OPS);

        if (result.isModifiedCountAvailable()) {
            return result.getModifiedCount() == 1;
        } else {
            return true;
        }
    }

    public static BulkOperationResult bulkUpsertDocuments(
            final MongoCollection<BsonDocument> coll,
            final BsonArray documents,
            final BsonDocument filter,
            final BsonDocument shardKeys) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(documents);

        ObjectId newEtag = new ObjectId();

        List<WriteModel<BsonDocument>> wm = getBulkWriteModel(
                coll,
                documents,
                filter,
                shardKeys,
                newEtag);

        
        BulkWriteResult result = coll.bulkWrite(wm, BWO_NOT_ORDERED);

        return new BulkOperationResult(HttpStatus.SC_OK, newEtag, result);
    }

    private static List<WriteModel<BsonDocument>> getBulkWriteModel(
            final MongoCollection<BsonDocument> mcoll,
            final BsonArray documents,
            final BsonDocument filter,
            final BsonDocument shardKeys,
            final ObjectId etag) {
        Objects.requireNonNull(mcoll);
        Objects.requireNonNull(documents);

        List<WriteModel<BsonDocument>> updates = new ArrayList<>();

        documents.stream().filter(_document -> _document.isDocument())
                .forEach(new Consumer<BsonValue>() {
                    @Override
                    public void accept(BsonValue _document) {
                        BsonDocument document = _document.asDocument();

                        // generate new id if missing, will be an insert
                        if (!document.containsKey("_id")) {
                            document
                                    .put("_id", new BsonObjectId(new ObjectId()));
                        }

                        // add the _etag
                        document.put("_etag", new BsonObjectId(etag));

                        Bson _filter = eq("_id", document.get("_id"));

                        if (shardKeys != null) {
                            _filter = and(_filter, shardKeys);
                        }

                        if (filter != null && !filter.isEmpty()) {
                            _filter = and(_filter, filter);
                        }

                        updates.add(new UpdateOneModel<>(
                                _filter,
                                getUpdateDocument(document),
                                new UpdateOptions().upsert(true)
                        ));
                    }
                });

        return updates;
    }

    /**
     *
     * @param data
     * @return the document for update operation, with proper update operators
     */
    public static BsonDocument getUpdateDocument(BsonDocument data) {
        return getUpdateDocument(data, false);
    }

    /**
     *
     * @param doc
     * @return the document for replace operation, without dot notation and
     * replacing $currentDate operator
     */
    public static BsonDocument getReplaceDocument(final BsonDocument doc) {
        if (JsonUtils.containsUpdateOperators(doc, false)) {
            BsonDocument ret = new BsonDocument();
            ret.putAll(doc);

            BsonValue cd = ret.remove("$currentDate");

            if (cd != null) {
                long currentTimeMillis = System.currentTimeMillis();

                if (cd.isDocument()) {
                    cd.asDocument()
                            .entrySet()
                            .stream()
                            .forEach(entry -> {
                                if (BsonBoolean.TRUE.equals(entry.getValue())) {
                                    ret.put(entry.getKey(),
                                            new BsonDateTime(currentTimeMillis));
                                } else if (entry.getValue().isDocument()
                                        && entry.getValue().asDocument().
                                                containsKey("$type")) {
                                    if (new BsonString("date").equals(
                                            entry.getValue().asDocument().get("$type"))) {
                                        ret.put(entry.getKey(),
                                                new BsonDateTime(currentTimeMillis));

                                    } else if (new BsonString("timestamp").equals(
                                            entry.getValue().asDocument().get("$type"))) {
                                        ret.put(entry.getKey(),
                                                new BsonTimestamp(currentTimeMillis));
                                    } else {
                                        throw new IllegalArgumentException("wrong $currentDate operator");
                                    }

                                } else {
                                    throw new IllegalArgumentException("wrong $currentDate operator");
                                }
                            });
                }
            }

            return JsonUtils.unflatten(ret).asDocument();
        } else {
            return doc;
        }
    }

    /**
     *
     * @param data
     * @param flatten if we should flatten nested documents' values using dot
     * notation
     * @return the document for update operation, with proper update operators
     */
    public static BsonDocument getUpdateDocument(BsonDocument data, boolean flatten) {
        BsonDocument ret = new BsonDocument();

        // add other update operators
        data.keySet()
                .stream()
                .filter(key -> JsonUtils.isUpdateOperator(key))
                .forEach(key -> ret.put(key, data.get(key)));

        // add properties to $set update operator
        List<String> keys;

        keys = data.keySet()
                .stream()
                .filter(key -> !JsonUtils.isUpdateOperator(key))
                .collect(Collectors.toList());

        if (keys != null && !keys.isEmpty()) {
            BsonDocument set;

            if (flatten) {
                set = JsonUtils.flatten(ret, false);
            } else {
                set = new BsonDocument();
                keys.stream().forEach(key -> set.append(key, data.get(key)));
            }
            if (!set.isEmpty()) {
                if (ret.get("$set") == null) {
                    ret.put("$set", set);
                } else if (ret.get("$set").isDocument()) {
                    ret.get("$set").asDocument().putAll(set);
                } else {
                    // update is going to fail on mongodb
                    // error 9, Modifiers operate on fields but we found a String instead
                    LOGGER.debug("$set is not an object: {}", ret.get("$set"));
                }
            }
        }

        return ret;
    }

    private DAOUtils() {
    }
}
