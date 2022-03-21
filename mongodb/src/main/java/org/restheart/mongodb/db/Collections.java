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

import com.mongodb.client.MongoClient;
import com.mongodb.MongoCommandException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import static com.mongodb.client.model.Filters.eq;
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
import org.restheart.exchange.ExchangeKeys.EAGER_CURSOR_ALLOCATION_POLICY;
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.ExchangeKeys.WRITE_MODE;
import static org.restheart.exchange.ExchangeKeys.META_COLLNAME;
import org.restheart.mongodb.MongoServiceConfiguration;
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



    private final MongoClient client = MongoClientSingleton.getInstance().getClient();

    private Collections() {
    }

    private static Collections INSTANCE = new Collections();

    public static Collections get() {
        return INSTANCE;
    }

    /**
     * Returns the MongoCollection object for the collection in db dbName.
     *
     * @param dbName the database name of the collection the database name of
     * the collection
     * @param collName the collection name
     * @return the mongodb DBCollection object for the collection in db dbName
     */
    MongoCollection<BsonDocument> getCollection(final String dbName, final String collName) {
        return client.getDatabase(dbName).getCollection(collName, BsonDocument.class);
    }

    /**
     * Returns the number of documents in the given collection (taking into
     * account the filters in case).
     *
     * @param cs the session id, can be null
     * @param coll the mongodb DBCollection object.
     * @param filters the filters to apply. it is a Deque collection of mongodb
     * query conditions.
     * @return the number of documents in the given collection (taking into
     * account the filters in case)
     */
    public long getCollectionSize(final Optional<ClientSession> cs, final MongoCollection<BsonDocument> coll, final BsonDocument filters) {
        return cs.isPresent() ?  coll.countDocuments(cs.get(), filters) : coll.countDocuments(filters);
    }

    /**
     * Returs the FindIterable<BsonDocument> of the collection applying sorting,
     * filtering and projection.
     *
     * @param cs the client session
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

    BsonArray getCollectionData(
        final Optional<ClientSession> cs,
        final MongoCollection<BsonDocument> coll,
        final int page,
        final int pagesize,
        final BsonDocument sortBy,
        final BsonDocument filters,
        final BsonDocument hint,
        final BsonDocument keys,
        final EAGER_CURSOR_ALLOCATION_POLICY eager)
        throws JsonParseException {

        var ret = new BsonArray();

        int toskip = pagesize * (page - 1);

        SkippedFindIterable _cursor = null;

        if (eager != EAGER_CURSOR_ALLOCATION_POLICY.NONE) {
            _cursor = CursorPool.getInstance().get(new CursorPoolEntryKey(cs, coll, sortBy, filters, hint, keys, toskip,0), eager);
        }

        // in case there is not cursor in the pool to reuse
        FindIterable<BsonDocument> cursor;

        if (_cursor == null) {
            cursor = findIterable(cs, coll, sortBy, filters, hint, keys);
            cursor.skip(toskip).limit(pagesize);

            StreamSupport.stream(cursor.spliterator(),false).forEachOrdered(c -> ret.add(c));
        } else {
            int alreadySkipped;

            cursor = _cursor.findIterable();
            alreadySkipped = _cursor.alreadySkipped();

            long startSkipping = 0;
            int cursorSkips = alreadySkipped;

            if (LOGGER.isDebugEnabled()) {
                startSkipping = System.currentTimeMillis();
            }

            LOGGER.debug("got cursor from pool with skips {}. need to reach {} skips.", alreadySkipped, toskip);

            var mc = cursor.iterator();

            while (toskip > alreadySkipped && mc.hasNext()) {
                mc.next();
                alreadySkipped++;
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("skipping {} times took {} msecs",toskip - cursorSkips, System.currentTimeMillis() - startSkipping);
            }

            for (int cont = pagesize; cont > 0 && mc.hasNext(); cont--) {
                ret.add(mc.next());
            }
        }

        // the pool is populated here because, skipping with cursor.next() is heavy operation
        // and we want to minimize the chances that pool cursors are allocated in parallel
        CursorPool.getInstance().populateCache(new CursorPoolEntryKey(cs, coll, sortBy, filters, hint, keys, toskip, 0), eager);

        return ret;
    }

    /**
     * Returns the collection properties document.
     *
     * @param cs the client session
     * @param dbName the database name of the collection
     * @param collName the collection name
     * @return the collection properties document
     */
    public BsonDocument getCollectionProps(final Optional<ClientSession> cs, final String dbName, final String collName) {
        var propsColl = getCollection(dbName, META_COLLNAME);

        var query = new BsonDocument("_id", new BsonString("_properties.".concat(collName)));

        var props = cs.isPresent()
                ? propsColl.find(cs.get(), query).limit(1).first()
                : propsColl.find(query).limit(1).first();

        if (props != null) {
            props.append("_id", new BsonString(collName));
        } else if (doesCollectionExist(cs, dbName, collName)) {
            return new BsonDocument("_id", new BsonString(collName));
        }

        return props;
    }

    /**
     * Returns true if the collection exists
     *
     * @param cs the client session
     * @param dbName the database name of the collection
     * @param collName the collection name
     * @return true if the collection exists
     */
    public boolean doesCollectionExist(final Optional<ClientSession> cs, final String dbName, final String collName) {
        var dbCollections = cs.isPresent()
            ? client.getDatabase(dbName).listCollectionNames(cs.get())
            : client.getDatabase(dbName).listCollectionNames();

        return StreamSupport.stream(dbCollections.spliterator(),false).anyMatch(dbCollection -> collName.equals(dbCollection));
    }

    /**
     * Upsert the collection properties.
     *
     * @param cs the client session
     * @param dbName the database name of the collection
     * @param method the request method
     * @param collName the collection name
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
        final METHOD method,
        final boolean updating,
        final String dbName,
        final String collName,
        final BsonDocument properties,
        final String requestEtag,
        final boolean checkEtag) {
        var _updating = updating;

        if (METHOD.PATCH.equals(method) && !updating) {
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        }

        if (!updating) {
            try {
                if(cs.isPresent()) {
                    client.getDatabase(dbName).createCollection(cs.get(), collName);
                } else {
                    client.getDatabase(dbName).createCollection(collName);
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

        var mcoll = getCollection(dbName, META_COLLNAME);

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
     * @param dbName the database name of the collection
     * @param collName the collection name
     * @param requestEtag the entity tag. must match to allow actual write
     * (otherwise http error code is returned)
     * @return the HttpStatus code to set in the http response
     */
    OperationResult deleteCollection(
        final Optional<ClientSession> cs,
        final String dbName,
        final String collName,
        final BsonObjectId requestEtag,
        final boolean checkEtag) {
        var mcoll = getCollection(dbName, META_COLLNAME);

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

        var collToDelete = getCollection(dbName, collName);

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
