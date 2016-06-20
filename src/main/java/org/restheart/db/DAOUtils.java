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

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCollection;
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
import java.util.stream.Collectors;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.restheart.utils.HttpStatus;
import static org.restheart.utils.RequestHelper.UPDATE_OPERATORS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.mongodb.client.model.Filters.and;
import java.util.Optional;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DAOUtils {

    public final static Logger LOGGER = LoggerFactory.getLogger(DAOUtils.class);

    public final static FindOneAndUpdateOptions FAU_UPSERT_OPS
            = new FindOneAndUpdateOptions()
            .upsert(true);

    public final static FindOneAndUpdateOptions FAU_AFTER_UPSERT_OPS
            = new FindOneAndUpdateOptions()
            .upsert(true).returnDocument(ReturnDocument.AFTER);

    public final static UpdateOptions U_UPSERT_OPS
            = new UpdateOptions()
            .upsert(true);

    public final static UpdateOptions U_NOT_UPSERT_OPS
            = new UpdateOptions()
            .upsert(false);

    private DAOUtils() {
    }

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
     * @param replace
     * @return the old document
     */
    public static OperationResult updateDocument(
            MongoCollection<BsonDocument> coll,
            Object documentId,
            BsonDocument shardKeys,
            BsonDocument data,
            boolean replace) {
        return updateDocument(
                coll, 
                documentId, 
                shardKeys, 
                data, 
                replace, 
                false);
    }

    private static final Bson IMPOSSIBLE_CONDITION 
            = eq("_etag", new ObjectId());

    /**
     *
     * @param coll
     * @param documentId use Optional.empty() to specify no documentId (null is
     * _id: null)
     * @param shardKeys
     * @param data
     * @param replace
     * @param returnNew
     * @return the new or old document depending on returnNew
     */
    public static OperationResult updateDocument(
            MongoCollection<BsonDocument> coll,
            Object documentId,
            BsonDocument shardKeys,
            BsonDocument data,
            boolean replace,
            boolean returnNew) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(data);

        BsonDocument document = getUpdateDocument(data);

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

        if (replace) {
            // here we cannot use the atomic findOneAndReplace because it does
            // not support update operators.

            BsonDocument oldDocument;

            if (idPresent) {
                oldDocument = coll.findOneAndDelete(query);
            } else {
                oldDocument = null;
            }

            BsonDocument newDocument = coll.findOneAndUpdate(
                    query, 
                    document, 
                    FAU_AFTER_UPSERT_OPS);

            return new OperationResult(-1, oldDocument, newDocument);
        } else if (returnNew) {
            BsonDocument newDocument = coll.findOneAndUpdate(
                    query, 
                    document, 
                    FAU_AFTER_UPSERT_OPS);

            return new OperationResult(-1, null, newDocument);
        } else {
            BsonDocument oldDocument = coll.findOneAndUpdate(
                    query, 
                    document, 
                    FAU_UPSERT_OPS);

            return new OperationResult(-1, oldDocument, null);
        }
    }

    public static boolean restoreDocument(
            MongoCollection<BsonDocument> coll,
            Object documentId,
            BsonDocument shardKeys,
            BsonDocument data,
            Object etag) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(data);

        Bson query;

        if (etag == null) {
            query = eq("_id", documentId);
        } else {
            query = and(eq("_id", documentId), eq("_etag", etag));
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
            MongoCollection<BsonDocument> coll,
            final BsonArray documents,
            BsonDocument shardKeys) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(documents);

        ObjectId newEtag = new ObjectId();

        List<WriteModel<BsonDocument>> wm = getBulkWriteModel(
                coll,
                documents,
                shardKeys,
                newEtag);

        BulkWriteResult result = coll.bulkWrite(wm);

        return new BulkOperationResult(HttpStatus.SC_OK, newEtag, result);
    }

    private static List<WriteModel<BsonDocument>> getBulkWriteModel(
            final MongoCollection<BsonDocument> mcoll,
            final BsonArray documents,
            BsonDocument shardKeys,
            final ObjectId etag) {
        Objects.requireNonNull(mcoll);
        Objects.requireNonNull(documents);

        List<WriteModel<BsonDocument>> updates = new ArrayList<>();

        documents.stream().filter(_document -> _document.isDocument())
                .forEach(_document -> {
                    BsonDocument document = _document.asDocument();

                    // generate new id if missing, will be an insert
                    if (!document.containsKey("_id")) {
                        document
                                .put("_id", new BsonObjectId(new ObjectId()));
                    }

                    // add the _etag
                    document.put("_etag", new BsonObjectId(etag));

                    Bson filter = eq("_id", document.get("_id"));

                    if (shardKeys != null) {
                        filter = and(filter, shardKeys);
                    }

                    updates.add(new UpdateOneModel<>(
                            filter,
                            getUpdateDocument(document),
                            new UpdateOptions().upsert(true)
                    ));
                });

        return updates;
    }

    /**
     *
     * @param data
     * @return the document for update operation, with proper update operators
     */
    public static BsonDocument getUpdateDocument(BsonDocument data) {
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
                set.append(key, data.get(key));
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
}
