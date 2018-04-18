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
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
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
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.restheart.utils.HttpStatus;
import static org.restheart.utils.RequestHelper.UPDATE_OPERATORS;
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
            .upsert(true);

    public final static FindOneAndUpdateOptions FAU_NOT_UPSERT_OPS = new FindOneAndUpdateOptions()
            .upsert(false);

    public final static FindOneAndUpdateOptions FAU_AFTER_UPSERT_OPS = new FindOneAndUpdateOptions()
            .upsert(true).returnDocument(ReturnDocument.AFTER);

    public final static FindOneAndUpdateOptions FAU_AFTER_NOT_UPSERT_OPS = new FindOneAndUpdateOptions()
            .upsert(false).returnDocument(ReturnDocument.AFTER);

    public final static UpdateOptions U_UPSERT_OPS = new UpdateOptions()
            .upsert(true);

    public final static UpdateOptions U_NOT_UPSERT_OPS = new UpdateOptions()
            .upsert(false);

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
     * @param returnNew
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
            boolean returnNew,
            boolean deepPatching,
            boolean allowUpsert) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(data);

        BsonDocument document = getUpdateDocument(data, deepPatching);

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

        if (replace) {
            // here we cannot use the atomic findOneAndReplace because it does
            // not support update operators.

            BsonDocument oldDocument;

            if (idPresent) {
                oldDocument = coll.findOneAndDelete(query);
            } else {
                oldDocument = null;
            }

            BsonDocument newDocument;

            try {
                newDocument = coll.findOneAndUpdate(
                        query,
                        document,
                        allowUpsert ? FAU_AFTER_UPSERT_OPS : FAU_AFTER_NOT_UPSERT_OPS);
            } catch (MongoCommandException mce) {
                LOGGER.debug("******** {}", mce.getErrorMessage());
                if (mce.getErrorCode() == 11000) {
                    if (allowUpsert
                            && filter != null
                            && !filter.isEmpty()
                            && mce.getErrorMessage().contains("$_id_ dup key")) {
                        // DuplicateKey error
                        // this happens if the filter parameter didn't match
                        // the existing document and so the upserted doc
                        // has an existing _id 
                        return new OperationResult(HttpStatus.SC_NOT_FOUND, oldDocument, oldDocument);
                    } else {
                        return new OperationResult(HttpStatus.SC_EXPECTATION_FAILED);
                    }
                } else {
                    throw mce;
                }
            }

            return new OperationResult(-1, oldDocument, newDocument);
        } else if (returnNew) {
            BsonDocument newDocument;
            try {
                newDocument = coll.findOneAndUpdate(
                        query,
                        document,
                        allowUpsert ? FAU_AFTER_UPSERT_OPS : FAU_AFTER_NOT_UPSERT_OPS);
            } catch (MongoCommandException mce) {
                LOGGER.debug("******** {}", mce.getErrorMessage());
                if (mce.getErrorCode() == 11000) {
                    if (allowUpsert
                            && filter != null
                            && !filter.isEmpty()
                            && mce.getErrorMessage().contains("$_id_ dup key")) {
                        // DuplicateKey error due to filter 
                        // this happens if the filter parameter didn't match
                        // the existing document and so the upserted doc
                        // has an existing _id 
                        return new OperationResult(HttpStatus.SC_NOT_FOUND);
                    } else {
                        return new OperationResult(HttpStatus.SC_EXPECTATION_FAILED);
                    }
                } else {
                    throw mce;
                }
            }

            return new OperationResult(-1, null, newDocument);
        } else {
            BsonDocument oldDocument;

            try {
                oldDocument = coll.findOneAndUpdate(
                        query,
                        document,
                        allowUpsert ? FAU_UPSERT_OPS : FAU_NOT_UPSERT_OPS);
            } catch (MongoCommandException mce) {
                LOGGER.debug("******** {}", mce.getErrorMessage());
                if (mce.getErrorCode() == 11000) {
                    if (allowUpsert
                            && filter != null
                            && !filter.isEmpty()
                            && mce.getErrorMessage().contains("$_id_ dup key")) {
                        // DuplicateKey error
                        // this happens if the filter parameter didn't match
                        // the existing document and so the upserted doc
                        // has an existing _id 
                        return new OperationResult(HttpStatus.SC_NOT_FOUND);
                    } else {
                        return new OperationResult(HttpStatus.SC_EXPECTATION_FAILED);
                    }
                } else {
                    throw mce;
                }
            }

            return new OperationResult(-1, oldDocument, null);
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

        BulkWriteResult result = coll.bulkWrite(wm);

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
     * @param data
     * @param flatten if we should flatten nested documents' values using dot
     * notation
     * @return the document for update operation, with proper update operators
     */
    public static BsonDocument getUpdateDocument(BsonDocument data, boolean flatten) {
        BsonDocument ret = new BsonDocument();

        // add other update operators
        data.keySet().stream().filter((String key)
                -> UPDATE_OPERATORS.contains(key))
                .forEach(key -> {
                    ret.put(key, data.get(key));
                });

        // add properties to $set update operator
        List<String> setKeys;

        setKeys = data.keySet().stream().filter((String key)
                -> !UPDATE_OPERATORS.contains(key))
                .collect(Collectors.toList());

        if (setKeys != null && !setKeys.isEmpty()) {
            BsonDocument set = new BsonDocument();

            setKeys.stream().forEach((String key)
                    -> {
                if (flatten) {
                    flatten(null, key, data, set);
                } else {
                    set.append(key, data.get(key));
                }
            });

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

    /*
     * Recursively flatten BsonDocuments using dot notation so that we only set values on explicit keys
     */
    private static void flatten(String prefix, String key, BsonDocument data, BsonDocument set) {
        final String newPrefix = prefix == null ? key : prefix + "." + key;
        final BsonValue value = data.get(key);
        if (value.isDocument()) {
            ((BsonDocument) value).keySet().forEach(childKey -> {
                flatten(newPrefix, childKey, (BsonDocument) value, set);
            });
        } else {
            set.append(newPrefix, value);
        }
    }

    private DAOUtils() {
    }
}
