/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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

import com.google.common.collect.Lists;
import com.mongodb.MongoCommandException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.internal.MongoBatchCursorAdapter;
import static com.mongodb.client.model.Filters.eq;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;
import static org.restheart.exchange.ExchangeKeys.COLL_META_DOCID_PREFIX;
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.ExchangeKeys.WRITE_MODE;
import static org.restheart.exchange.ExchangeKeys.META_COLLNAME;
import static org.fusesource.jansi.Ansi.Color.YELLOW;
import static org.fusesource.jansi.Ansi.ansi;
import org.restheart.mongodb.RHMongoClients;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.mongodb.RSOps;

import static org.restheart.mongodb.MongoServiceConfigurationKeys.DEFAULT_CURSOR_BATCH_SIZE;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Data Access Object for the mongodb Collection resource. NOTE: this class
 * is package-private and only meant to be used as a delagate within the DbsDAO
 * class.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
class Collections {

    private static final int BATCH_SIZE = MongoServiceConfiguration.get() != null
        ? MongoServiceConfiguration.get().getCursorBatchSize()
        : DEFAULT_CURSOR_BATCH_SIZE;

    private static final Logger LOGGER = LoggerFactory.getLogger(Collections.class);
    private static final BsonDocument FIELDS_TO_RETURN;

    static {
        FIELDS_TO_RETURN = new BsonDocument();
        FIELDS_TO_RETURN.put("_id", new BsonInt32(1));
        FIELDS_TO_RETURN.put("_etag", new BsonInt32(1));
    }

    private final MongoClient client;

    private Collections() {
        this.client = RHMongoClients.mclient();
    }

    private static Collections INSTANCE = new Collections();

    public static Collections get() {
        return INSTANCE;
    }

    /**
     * Returns the MongoCollection object for the collection in db dbName.
     *
     * @param rsOps the ReplicaSet connection options
     * @param dbName the name of the db
     * @param collName the collection name
     *
     * @return the MongoCollection for the collection in db dbName
     */
    MongoCollection<BsonDocument> collection(final Optional<RSOps> rsOps, final String dbName, final String collName) {
        return db(rsOps, dbName).getCollection(collName).withDocumentClass(BsonDocument.class);
    }

    /**
     * Returns the number of documents in the given collection (taking into
     * account the filters in case).
     *
     * @param cs the ClientSession
     * @param db the MongoDatabase
     * @param collName the collection name
     * @param filters the filters to apply. it is a Deque collection of mongodb
     * query conditions.
     * @return the number of documents in the given collection (taking into
     * account the filters in case)
     */
    public long getCollectionSize(final Optional<ClientSession> cs, final Optional<RSOps> rsOps, final String dbName, String collName, final BsonDocument filters) {
        return getCollectionSize(cs, collection(rsOps, dbName, collName), filters);
    }

    /**
     * Returns the number of documents in the given collection (taking into
     * account the filters in case).
     *
     * @param cs the ClientSession
     * @param coll the MongoCollection
     * @param filters the filters to apply. it is a Deque collection of mongodb
     * query conditions.
     * @return the number of documents in the given collection (taking into
     * account the filters in case)
     */
    long getCollectionSize(final Optional<ClientSession> cs, final MongoCollection<BsonDocument> coll, final BsonDocument filters) {
        return cs.isPresent() ?  coll.countDocuments(cs.get(), filters) : coll.countDocuments(filters);
    }

    /**
     *
     * @param rsOps the ReplicaSet connection options
     * @param dbName the name of the db
     * @return the MongoDatabase
     */
    private MongoDatabase db(Optional<RSOps> rsOps, String dbName) {
        return rsOps.isPresent() ? rsOps.get().apply(client.getDatabase(dbName)) : client.getDatabase(dbName);
    }

    /**
     * Returs the FindIterable<BsonDocument> of the collection applying sorting,
     * filtering and projection.
     *
     * @param cs the ClientSession
     * @param coll the MongoCollection
     * @param sortBy the sort expression to use for sorting (prepend field name
     * with - for descending sorting)
     * @param filters the filters to apply.
     * @param keys the keys to return (projection)
     * @return
     * @throws JsonParseException
     */
    FindIterable<BsonDocument> findIterable(
        final Optional<ClientSession> cs,
        final MongoCollection<BsonDocument> coll,
        final BsonDocument sortBy,
        final BsonDocument filters,
        final BsonDocument hint,
        final BsonDocument keys) throws JsonParseException {
        var ret = cs.isPresent()
            ? coll.find(cs.get(), filters)
            : coll.find(filters);

        return ret.projection(keys)
            .sort(sortBy)
            .batchSize(BATCH_SIZE)
            .hint(hint)
            .maxTime(MongoServiceConfiguration.get().getQueryTimeLimit(), TimeUnit.MILLISECONDS);
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName the collection name
     * @param page
     * @param pagesize
     * @param sortBy
     * @param filter
     * @param hint
     * @param keys
     * @param cursorAllocationPolicy
     * @return the documents in the collection as a BsonArray
     */
    BsonArray getCollectionData(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName,
        final int page,
        final int pagesize,
        final BsonDocument sortBy,
        final BsonDocument filters,
        final BsonDocument hint,
        final BsonDocument keys,
        final boolean useCache)
        throws JsonParseException {
        var coll = collection(rsOps, dbName, collName);
        var ret = new BsonArray();

        // TODO invalidate cursor on write 

        if (!useCache) {
            return getCollectionDataFromDb(cs, coll, rsOps, dbName, collName, page, pagesize, sortBy, filters, hint, keys, useCache);
        } else {
            var from = pagesize * (page - 1);
            var to = from + pagesize;

            var match = GetCollectionCache.getInstance().find(new GetCollectionCacheKey(cs, coll, sortBy, filters, hint, keys, from, to, 0));

            if (match == null) {
                return getCollectionDataFromDb(cs, coll, rsOps, dbName, collName, page, pagesize, sortBy, filters, hint, keys, useCache);
            } else {
                var offset = match.getKey().from();
                ret.addAll(match.getValue().subList(from - offset, from - offset + pagesize));
                return ret;
            }
        }
    }

    private int cursorCount(MongoCursor<?> cursor) {
        try {
            var _batchCursor = MongoBatchCursorAdapter.class.getDeclaredField("batchCursor");
            _batchCursor.setAccessible(true);
            var batchCursor = _batchCursor.get(cursor);

            var _count = batchCursor.getClass().getDeclaredField("count");
            _count.setAccessible(true);
            return (int) _count.get(batchCursor);
        } catch(NoSuchFieldException | IllegalAccessException ex) {
            LOGGER.warn("cannot access field Cursor.batchCursor.count ", ex);
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private List<BsonDocument> cursorDocs(MongoCursor<?> cursor) {
        try {
            var _batchCursor = MongoBatchCursorAdapter.class.getDeclaredField("curBatch");
            _batchCursor.setAccessible(true);
            return (List<BsonDocument>) _batchCursor.get(cursor);
        } catch(NoSuchFieldException | IllegalAccessException ex) {
            LOGGER.warn("cannot access field Cursor.curBatch ", ex);
            return Lists.newArrayList();
        }
    }

    private BsonArray getCollectionDataFromDb(final Optional<ClientSession> cs,
        final MongoCollection<BsonDocument> coll,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName,
        final int page,
        final int pagesize,
        final BsonDocument sortBy,
        final BsonDocument filters,
        final BsonDocument hint,
        final BsonDocument keys,
        final boolean useCache) {
        var ret = new BsonArray();
        int from = pagesize * (page - 1);

        try (var cursor = findIterable(cs, coll, sortBy, filters, hint, keys).skip(from).cursor()) {
            int added = 0;
            while(added < pagesize) {
                var next = cursor.tryNext();
                if (next == null) {
                    break;
                } else {
                    ret.add(next);
                    added++;
                }
            }

            // cache the cursor
            // TODO avoid adding cursor if already available or invalidate old entries
            if (useCache) {
                var to = from + cursorCount(cursor);
                var newkey = new GetCollectionCacheKey(cs, coll, sortBy, filters, hint, keys, from, to, System.nanoTime());
                LOGGER.debug("{} entry in collection cache: {}", ansi().fg(YELLOW).bold().a("new").reset().toString(), newkey);
                GetCollectionCache.getInstance().put(newkey, cursorDocs(cursor));
            }
        }

        return ret;
    }

    /**
     * Returns the collection properties document.
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName the collection name
     * @return the collection properties document
     */
    public BsonDocument getCollectionProps(final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName) {
        var propsColl = collection(rsOps, dbName, META_COLLNAME);

        var query = new BsonDocument("_id", new BsonString("_properties.".concat(collName)));

        var props = cs.isPresent()
                ? propsColl.find(cs.get(), query).limit(1).first()
                : propsColl.find(query).limit(1).first();

        if (props != null) {
            props.append("_id", new BsonString(collName));
        } else if (doesCollectionExist(cs, rsOps, dbName, collName)) {
            return new BsonDocument("_id", new BsonString(collName));
        }

        return props;
    }

    /**
     * Returns true if the collection exists
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName the collection name
     * @return true if the collection exists
     */
    public boolean doesCollectionExist(final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName) {
        var db = db(rsOps, dbName);
        var dbCollections = cs.isPresent()
            ? db.listCollectionNames(cs.get())
            : db.listCollectionNames();

        return StreamSupport.stream(dbCollections.spliterator(),false).anyMatch(dbCollection -> collName.equals(dbCollection));
    }

    /**
     * Upsert the collection properties.
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName the collection name
     * @param method the request method
     * @param properties the new collection properties
     * @param requestEtag the entity tag. must match to allow actual write if
     * checkEtag is true (otherwise http error code is returned)
     * @param updating true if updating existing document
     * @param patching true if use patch semantic (update only specified fields)
     * @param checkEtag true if etag must be checked
     * @return the HttpStatus code to set in the http response
     */
    OperationResult upsertCollection(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName,
        final METHOD method,
        final boolean updating,
        final BsonDocument properties,
        final String requestEtag,
        final boolean checkEtag) {
        var db = db(rsOps, dbName);
        var _updating = updating;

        if (METHOD.PATCH.equals(method) && !updating) {
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        }

        if (!updating) {
            try {
                if(cs.isPresent()) {
                    db.createCollection(cs.get(), collName);
                } else {
                    db.createCollection(collName);
                }
            } catch (MongoCommandException ex) {
                // error 48 is NamespaceExists
                // this can happen when a request A creates a collection
                // and a concurrent request B checks if it exists before A
                // completes (updating = false) and try to create it after A
                // actually created it.
                // see https://github.com/SoftInstigate/restheart/issues/297
                if (ex.getErrorCode() != 48) {
                    throw ex;
                } else {
                    _updating = true;
                }
            }
        }

        var newEtag = new ObjectId();

        final var content = DbUtils.validContent(properties);

        content.put("_etag", new BsonObjectId(newEtag));
        content.remove("_id"); // make sure we don't change this field

        var mcoll = collection(rsOps, dbName, META_COLLNAME);

        if (checkEtag && _updating) {
            var query = eq("_id", COLL_META_DOCID_PREFIX.concat(collName));

            var oldProperties = cs.isPresent()
                ? mcoll.find(cs.get(), query).projection(FIELDS_TO_RETURN).first()
                : mcoll.find(query).projection(FIELDS_TO_RETURN).first();

            if (oldProperties != null) {
                var oldEtag = oldProperties.get("_etag");

                if (oldEtag != null && requestEtag == null) {
                    return new OperationResult(HttpStatus.SC_CONFLICT, oldEtag);
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
                    return doCollPropsUpdate(cs, method, _updating, collName, mcoll, content, newEtag);
                } else {
                    return new OperationResult(HttpStatus.SC_PRECONDITION_FAILED, oldEtag);
                }
            } else {
                // this is the case when the coll does not have properties
                // e.g. it has not been created by restheart
                return doCollPropsUpdate(cs, method, _updating, collName, mcoll, content, newEtag);
            }
        } else {
            return doCollPropsUpdate(cs, method, _updating, collName, mcoll, content, newEtag);
        }
    }

    private OperationResult doCollPropsUpdate(
        final Optional<ClientSession> cs,
        final METHOD method,
        final boolean updating,
        final String collName,
        final MongoCollection<BsonDocument> mcoll,
        final BsonDocument dcontent,
        final ObjectId newEtag) {
           return switch(method) {
                case PATCH -> {
                    var ret = DbUtils.writeDocument(
                        cs,
                        METHOD.PATCH,
                        WRITE_MODE.UPSERT,
                        mcoll,
                        Optional.of(new BsonString("_properties.".concat(collName))),
                        Optional.empty(),
                        Optional.empty(),
                        dcontent);

                    yield new OperationResult(ret.getHttpCode() > 0 ? ret.getHttpCode() : HttpStatus.SC_OK, newEtag);
                }

                case PUT -> {
                    var ret = DbUtils.writeDocument(
                        cs,
                        METHOD.PUT,
                        WRITE_MODE.UPSERT,
                        mcoll,
                        Optional.of(new BsonString("_properties.".concat(collName))),
                        Optional.empty(),
                        Optional.empty(),
                        dcontent);
                    yield new OperationResult(ret.getHttpCode() > 0 ? ret.getHttpCode() : updating ? HttpStatus.SC_OK : HttpStatus.SC_CREATED, newEtag, ret.getOldData(), ret.getNewData());
                }

                default -> throw new UnsupportedOperationException("unsupported method: " + method);
            };
    }

    /**
     * Deletes a collection.
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName the collection name
     * @param requestEtag the entity tag. must match to allow actual write
     * (otherwise http error code is returned)
     * @return the HttpStatus code to set in the http response
     */
    OperationResult deleteCollection(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName,
        final BsonObjectId requestEtag,
        final boolean checkEtag) {
        var mcoll = collection(rsOps, dbName, META_COLLNAME);

        var query = eq("_id", COLL_META_DOCID_PREFIX.concat(collName));

        var properties = cs.isPresent()
            ? mcoll.find(cs.get(), query).projection(FIELDS_TO_RETURN).first()
            : mcoll.find(query).projection(FIELDS_TO_RETURN).first();

        if (checkEtag) {
            if (properties != null) {
                var oldEtag = properties.get("_etag");

                if (oldEtag != null) {
                    if (requestEtag == null) {
                        return new OperationResult(HttpStatus.SC_CONFLICT, oldEtag, properties, properties);
                    } else if (!requestEtag.equals(oldEtag)) {
                        return new OperationResult(HttpStatus.SC_PRECONDITION_FAILED, oldEtag, properties, properties);
                    }
                }
            }
        }

        var collToDelete = collection(rsOps, dbName, collName);

        if (cs.isPresent()) {
            collToDelete.drop(cs.get());
            mcoll.deleteOne(cs.get(), query);
        } else {
            collToDelete.drop();
            mcoll.deleteOne(query);
        }

        return new OperationResult(HttpStatus.SC_NO_CONTENT, null, properties, null);
    }
}
