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

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.ExchangeKeys.WRITE_MODE;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DbUtils {

    /**
     *
     */
    public final static Logger LOGGER = LoggerFactory.getLogger(DbUtils.class);

    /**
     *
     */
    public static final int DUPLICATE_KEY_ERROR = 11000;

    /**
     *
     */
    public static final int BAD_VALUE_KEY_ERROR = 2;

    /**
     *
     */
    public final static FindOneAndUpdateOptions FAU_UPSERT_OPS = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER);

    /**
     *
     */
    public final static FindOneAndUpdateOptions FAU_NOT_UPSERT_OPS = new FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER);

    /**
     *
     */
    public final static FindOneAndUpdateOptions FOU_AFTER_UPSERT_OPS = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER);

    /**
     *
     */
    public final static FindOneAndReplaceOptions FOR_AFTER_UPSERT_OPS = new FindOneAndReplaceOptions().upsert(true).returnDocument(ReturnDocument.AFTER);

    /**
     *
     */
    public final static FindOneAndUpdateOptions FOU_AFTER_NOT_UPSERT_OPS = new FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER);

    /**
     *
     */
    public final static FindOneAndReplaceOptions FOR_AFTER_NOT_UPSERT_OPS = new FindOneAndReplaceOptions().upsert(false).returnDocument(ReturnDocument.AFTER);

    /**
     *
     */
    public final static UpdateOptions U_UPSERT_OPS = new UpdateOptions().upsert(true);

    /**
     *
     */
    public final static UpdateOptions U_NOT_UPSERT_OPS = new UpdateOptions().upsert(false);

    /**
     *
     */
    public final static ReplaceOptions R_NOT_UPSERT_OPS = new ReplaceOptions().upsert(false);

    /**
     *
     */
    public final static BulkWriteOptions BWO_NOT_ORDERED = new BulkWriteOptions().ordered(false);

    private static final Bson IMPOSSIBLE_CONDITION = eq("_etag", new ObjectId());

    /**
     *
     * @param cs the client session
     * @param coll
     * @param method the request method
     * @param documentId use Optional.empty() to specify no documentId (null is _id: null)
     * @param filter
     * @param shardKeys
     * @param data
     * @return the old document
     */
    public static OperationResult updateFileMetadata(
        final Optional<ClientSession> cs,
        final MongoCollection<BsonDocument> coll,
        final METHOD method,
        final Optional<BsonValue> documentId,
        final Optional<BsonDocument> filter,
        final Optional<BsonDocument> shardKeys,
        final BsonDocument data) {
        return writeDocument(
            cs,
            method,
            WRITE_MODE.UPSERT,
            coll,
            documentId,
            filter,
            shardKeys,
            data);
    }

    /**
     * Writes a mongo document
     *
     * The MongoDB write operation depends on the request method and on the write mode as follows:
     * --------------------------------------------------------------------------------------------
     * | wm     | method | URI         | write operation                 |  wrop argument         |
     * --------------------------------------------------------------------------------------------
     * | insert | POST   | /coll       | insertOne                       | document               |
     * | insert | PUT    | /coll/docid | insertOne                       | document               |
     * | insert | PATCH  | /coll/docid | findOneAndUpdate(upsert:true)(1)| update operator expr   |
     * --------------------------------------------------------------------------------------------
     * | update | POST   | /coll       | findOneAndReplace(upsert:false) | document               |
     * | update | PUT    | /coll/docid | findOneAndReplace(upsert:false) | document               |
     * | update | PATCH  | /coll/docid | findOneAndUpdate(upsert:false)  | update operator expr   |
     * --------------------------------------------------------------------------------------------
     * | upsert | POST   | /coll       | findOneAndReplace(upsert:true)  | document               |
     * | upsert | PUT    | /coll/docid | findOneAndReplace(upsert:true)  | document               |
     * | upsert | PATCH  | /coll/docid | findOneAndUpdate(upsert:true)   | update operator expr   |
     * --------------------------------------------------------------------------------------------
     * (1) uses a find condition that won't match any existing document, making sure the operation is an insert
     *
     *
     * @param cs the client session
     * @param method the request method
     * @param writeMode the write mode
     * @param coll the collection
     * @param documentId use Optional.empty() to specify no documentId
     * @param filter
     * @param shardKeys
     * @param data
<<<<<<< HEAD
<<<<<<< HEAD
     * @param replace
     * @param upsert if true then we will flatten any nested BsonDocuments
     * into dot notation to ensure only the requested fields are updated.
     * @param allowUpsert whether or not to allow upsert mode
=======
>>>>>>> e0b1d620... :recycle: Refactoring of MongoDB write operation methods
     * @return the new or old document depending on returnNew
=======
     * @return the OperationResult
>>>>>>> a580d2a8... :sparkles: PATCH document accept update operators expr also with ?writeMode=insert
     */
    public static OperationResult writeDocument(
        final Optional<ClientSession> cs,
        final METHOD method,
        final WRITE_MODE writeMode,
        final MongoCollection<BsonDocument> coll,
        final Optional<BsonValue> documentId,
        final Optional<BsonDocument> filter,
        final Optional<BsonDocument> shardKeys,
        final BsonDocument data) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(data);
        Objects.requireNonNull(writeMode);

        var query = documentId.isPresent() ? eq("_id", documentId.get()) : IMPOSSIBLE_CONDITION;

        if (shardKeys.isPresent() && !shardKeys.get().isEmpty()) {
            query = and(query, shardKeys.get());
        }

        if (filter.isPresent() && !filter.get().isEmpty()) {
            query = and(query, filter.get());
        }

        BsonDocument oldDocument;

        if (documentId.isPresent()) {
            // TODO can we remove this?
            oldDocument = cs.isPresent() ? coll.find(cs.get(), query).first() : coll.find(query).first() ;

            // if document not exits and update request => fail request with 404
            if (writeMode == WRITE_MODE.UPDATE && oldDocument == null) {
                return new OperationResult(HttpStatus.SC_NOT_FOUND);
            }
        } else {
            // if update, docId is mandatory
            if (writeMode == WRITE_MODE.UPDATE) {
                LOGGER.debug("write request with writeMode=update missing document id");
                return new OperationResult(HttpStatus.SC_BAD_REQUEST);
            }

            oldDocument = null;
        }

        return switch(writeMode) {
            case INSERT -> switch(method) {
                case PATCH -> {
                    try {
                        var newDocument = cs.isPresent()
                            ? coll.findOneAndUpdate(cs.get(), IMPOSSIBLE_CONDITION, getUpdateDocument(data, false), FAU_NOT_UPSERT_OPS)
                            : coll.findOneAndUpdate(IMPOSSIBLE_CONDITION, getUpdateDocument(data, false), FAU_UPSERT_OPS);
                        yield new OperationResult(-1, oldDocument, newDocument);
                    } catch (IllegalArgumentException iae) {
                        yield new OperationResult(HttpStatus.SC_BAD_REQUEST, oldDocument, iae);
                    }
                }

                case POST, PUT -> {
                    try {
                        resolveCurrentDateOperator(data);

                        var insertedId = cs.isPresent()
                            ? coll.insertOne(cs.get(), data).getInsertedId()
                            : coll.insertOne(data).getInsertedId();

                        var insertedQuery = eq("_id", insertedId);

                        if (shardKeys.isPresent() && !shardKeys.get().isEmpty()) {
                            insertedQuery = and(insertedQuery, shardKeys.get());
                        }

                        var newDocument = cs.isPresent()? coll.find(cs.get(), insertedQuery).first(): coll.find(insertedQuery).first();
                        yield new OperationResult(-1, oldDocument, newDocument);
                    } catch (IllegalArgumentException iae) {
                        yield new OperationResult(HttpStatus.SC_BAD_REQUEST, oldDocument, iae);
                    }
                }

                default -> throw new UnsupportedOperationException("unsupported method " + method);
            };

            case UPDATE, UPSERT -> switch(method) {
                case PATCH -> {
                    try {
                        final var ops = writeMode == WRITE_MODE.UPSERT ? FAU_UPSERT_OPS : FAU_NOT_UPSERT_OPS;
                        var newDocument = cs.isPresent()
                            ? coll.findOneAndUpdate(cs.get(), query, getUpdateDocument(data, false), ops)
                            : coll.findOneAndUpdate(query, getUpdateDocument(data, false), ops);
                        yield new OperationResult(-1, oldDocument, newDocument);
                    } catch (IllegalArgumentException iae) {
                        yield new OperationResult(HttpStatus.SC_BAD_REQUEST, oldDocument, iae);
                    }
                }

                case PUT, POST -> {
                    try {
                        if (filter.isPresent() && !filter.get().isEmpty()) {
                            query = and(query, filter.get());
                        }

                        final var ops = writeMode == WRITE_MODE.UPSERT ? FOR_AFTER_UPSERT_OPS : FOR_AFTER_NOT_UPSERT_OPS;
                        var newDocument = cs.isPresent()
                            ? coll.findOneAndReplace(cs.get(), query, getReplaceDocument(data), ops)
                            : coll.findOneAndReplace(query, getReplaceDocument(data), ops);
                        yield new OperationResult(-1, oldDocument, newDocument);
                    } catch (IllegalArgumentException iae) {
                        yield new OperationResult(HttpStatus.SC_BAD_REQUEST, oldDocument, iae);
                    }
                }

                default -> throw new UnsupportedOperationException("unsupported method " + method);
            };
        };
    }

    /**
     *
     * @param cs the client session
     * @param coll
     * @param documentId
     * @param shardKeys
     * @param data
     * @param etag
     * @param etagLocation
     * @return
     */
    public static boolean restoreDocument(
        final Optional<ClientSession> cs,
        final MongoCollection<BsonDocument> coll,
        final BsonValue documentId,
        final Optional<BsonDocument> shardKeys,
        final BsonDocument data,
        final Object etag,
        final String etagLocation) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(data);

        Bson query;

        if (etag == null) {
            query = eq("_id", documentId);
        } else {
            query = and(eq("_id", documentId), eq(etagLocation != null && !etagLocation.isEmpty() ? etagLocation : "_etag", etag));
        }

        if (shardKeys.isPresent() && !shardKeys.get().isEmpty()) {
            query = and(query, shardKeys.get());
        }

        var result = cs.isPresent()
            ? coll.replaceOne(cs.get(), query, data, R_NOT_UPSERT_OPS)
            : coll.replaceOne(query, data, R_NOT_UPSERT_OPS);

        return result.getModifiedCount() == 1;
    }

    /**
     *
     * @param cs the client session
     * @param coll
     * @param documents
     * @param filter
     * @param shardKeys
     * @param writeMode
     * @return
     */
    public static BulkOperationResult bulkWriteDocuments(
        final Optional<ClientSession> cs,
        final MongoCollection<BsonDocument> coll,
        final BsonArray documents,
        final Optional<BsonDocument> filter,
        final Optional<BsonDocument> shardKeys,
        final WRITE_MODE writeMode) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(documents);

        var newEtag = new ObjectId();

        var wm = getBulkWriteModel(
            coll,
            documents,
            filter,
            shardKeys,
            newEtag,
            writeMode);

        var result = cs.isPresent()
            ? coll.bulkWrite(cs.get(), wm, BWO_NOT_ORDERED)
            : coll.bulkWrite(wm, BWO_NOT_ORDERED) ;

        return new BulkOperationResult(HttpStatus.SC_OK, newEtag, result);
    }

    /**
     *
     * @param newContent the value of newContent
     * @return a not null BsonDocument
     */
    static BsonDocument validContent(final BsonDocument newContent) {
        return (newContent == null) ? new BsonDocument() : newContent;
    }

    /**
     *
     * @param mcoll
     * @param documents
     * @param filter
     * @param shardKeys
     * @param etag
     * @return
     */
    static List<WriteModel<BsonDocument>> getBulkWriteModel(
        final MongoCollection<BsonDocument> mcoll,
        final BsonArray documents,
        final Optional<BsonDocument> filter,
        final Optional<BsonDocument> shardKeys,
        final ObjectId etag,
        final WRITE_MODE writeMode) {
        Objects.requireNonNull(mcoll);
        Objects.requireNonNull(documents);

        var updates = new ArrayList<WriteModel<BsonDocument>>();

        documents.stream().filter(d -> d.isDocument())
            .map(d -> d.asDocument())
            .forEach(document -> {
                // generate new id if missing, will be an insert
                if (!document.containsKey("_id")) {
                    document.put("_id", new BsonObjectId(new ObjectId()));
                }

                // add the _etag
                document.put("_etag", new BsonObjectId(etag));

                var _filter = eq("_id", document.get("_id"));

                if (shardKeys.isPresent() && !shardKeys.get().isEmpty()) {
                    _filter = and(_filter, shardKeys.get());
                }

                if (filter.isPresent() && !filter.get().isEmpty()) {
                    _filter = and(_filter, filter.get());
                }

                switch(writeMode) {
                    case UPSERT -> updates.add(new UpdateOneModel<>(_filter, getUpdateDocument(document), new UpdateOptions().upsert(true)));
                    case UPDATE -> updates.add(new UpdateOneModel<>(_filter, getUpdateDocument(document), new UpdateOptions().upsert(false)));
                    case INSERT -> updates.add(new InsertOneModel<>(document));
                }
            });

        return updates;
    }

    /**
     *
     * @param data
     * @return the document for update operation, with proper update operators
     */
    static BsonDocument getUpdateDocument(final BsonDocument data) {
        return getUpdateDocument(data, false);
    }

    /**
     *
     * @param data
     * @param flatten if we should flatten nested documents' values using dot
     * notation
     * @return the document for update operation, with proper update operators
     */
    static BsonDocument getUpdateDocument(final BsonDocument data, final boolean flatten) {
        var ret = new BsonDocument();

        // add other update operators
        data.keySet()
            .stream()
            .filter(key -> BsonUtils.isUpdateOperator(key))
            .forEach(key -> ret.put(key, data.get(key)));

        // add properties to $set update operator
        List<String> keys;

        keys = data.keySet()
            .stream()
            .filter(key -> !BsonUtils.isUpdateOperator(key))
            .collect(Collectors.toList());

        if (keys != null && !keys.isEmpty()) {
            BsonDocument set;

            if (flatten) {
                set = BsonUtils.flatten(ret, false);
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

    /**
     *
     * @param doc
     * @return the document for replace operation, without dot notation and
     * replacing $currentDate operator
     */
    static BsonDocument getReplaceDocument(final BsonDocument doc) {
        if (BsonUtils.containsUpdateOperators(doc, false)) {
            var ret = new BsonDocument();
            ret.putAll(doc);

            resolveCurrentDateOperator(ret);

            return BsonUtils.unflatten(ret).asDocument();
        } else {
            return doc;
        }
    }

    /**
     * Replace { "$currentDate": {"key": true}} with { "key": { "$date": 12345... }}
     *
     * @param doc
     * @throws IllegalArgumentException
     */
    static void resolveCurrentDateOperator(BsonDocument doc) throws IllegalArgumentException {
        var cd = doc.remove("$currentDate");

        if (cd == null) {
            return;
        }

        if (!cd.isDocument()) {
            throw new IllegalArgumentException("wrong $currentDate operator");
        }

        long currentTimeMillis = System.currentTimeMillis();

        cd.asDocument()
            .entrySet()
            .stream()
            .forEach(entry -> {
                if (BsonBoolean.TRUE.equals(entry.getValue())) {
                    doc.put(entry.getKey(), new BsonDateTime(currentTimeMillis));
                } else if (entry.getValue().isDocument() && entry.getValue().asDocument().containsKey("$type")) {
                    if (new BsonString("date").equals(entry.getValue().asDocument().get("$type"))) {
                        doc.put(entry.getKey(), new BsonDateTime(currentTimeMillis));
                    } else if (new BsonString("timestamp").equals(entry.getValue().asDocument().get("$type"))) {
                        doc.put(entry.getKey(), new BsonTimestamp(currentTimeMillis));
                    } else {
                        throw new IllegalArgumentException("wrong $currentDate operator");
                    }
                } else {
                    throw new IllegalArgumentException("wrong $currentDate operator");
                }
            });
    }
}
