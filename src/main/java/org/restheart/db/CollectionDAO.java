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

import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoCommandException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.util.JSONParseException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;
import org.restheart.Bootstrapper;
import org.restheart.Configuration;
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
class CollectionDAO {

    private static final int BATCH_SIZE = Bootstrapper
            .getConfiguration() != null
                    ? Bootstrapper
                            .getConfiguration()
                            .getCursorBatchSize()
                    : Configuration.DEFAULT_CURSOR_BATCH_SIZE;

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionDAO.class);
    private static final BsonDocument FIELDS_TO_RETURN;

    static {
        FIELDS_TO_RETURN = new BsonDocument();
        FIELDS_TO_RETURN.put("_id", new BsonInt32(1));
        FIELDS_TO_RETURN.put("_etag", new BsonInt32(1));
    }

    private final MongoClient client;

    CollectionDAO(MongoClient client) {
        this.client = client;
    }

    /**
     * Returns the mongodb DBCollection object for the collection in db dbName.
     *
     * @deprecated
     * @param dbName the database name of the collection the database name of
     * the collection
     * @param collName the collection name
     * @return the mongodb DBCollection object for the collection in db dbName
     */
    DBCollection getCollectionLegacy(
            final String dbName,
            final String collName) {
        return client.getDB(dbName).getCollection(collName);
    }

    /**
     * Returns the MongoCollection object for the collection in db dbName.
     *
     * @param dbName the database name of the collection the database name of
     * the collection
     * @param collName the collection name
     * @return the mongodb DBCollection object for the collection in db dbName
     */
    MongoCollection<BsonDocument> getCollection(
            final String dbName,
            final String collName) {
        return client
                .getDatabase(dbName)
                .getCollection(collName, BsonDocument.class);
    }

    /**
     * Checks if the given collection is empty. Note that RESTHeart creates a
     * reserved properties document in every collection (with _id
     * '_properties'). This method returns true even if the collection contains
     * such document.
     *
     * @param coll the mongodb DBCollection object
     * @return true if the commection is empty
     */
    public boolean isCollectionEmpty(final MongoCollection<BsonDocument> coll) {
        return coll.count() == 0;
    }

    /**
     * Returns the number of documents in the given collection (taking into
     * account the filters in case).
     *
     * @param coll the mongodb DBCollection object.
     * @param filters the filters to apply. it is a Deque collection of mongodb
     * query conditions.
     * @return the number of documents in the given collection (taking into
     * account the filters in case)
     */
    public long getCollectionSize(
            final MongoCollection<BsonDocument> coll,
            final BsonDocument filters) {
        return coll.count(filters);
    }

    /**
     * Returs the FindIterable<BsonDocument> of the collection applying sorting,
     * filtering and projection.
     *
     * @param sortBy the sort expression to use for sorting (prepend field name
     * with - for descending sorting)
     * @param filters the filters to apply.
     * @param keys the keys to return (projection)
     * @return
     * @throws JsonParseException
     */
    FindIterable<BsonDocument> getFindIterable(
            final MongoCollection<BsonDocument> coll,
            final BsonDocument sortBy,
            final BsonDocument filters,
            final BsonDocument keys) throws JsonParseException {

        return coll.find(filters)
                .projection(keys)
                .sort(sortBy)
                .batchSize(BATCH_SIZE)
                .maxTime(Bootstrapper.getConfiguration()
                        .getQueryTimeLimit(), TimeUnit.MILLISECONDS);
    }

    ArrayList<BsonDocument> getCollectionData(
            final MongoCollection<BsonDocument> coll,
            final int page,
            final int pagesize,
            final BsonDocument sortBy,
            final BsonDocument filters,
            final BsonDocument keys,
            CursorPool.EAGER_CURSOR_ALLOCATION_POLICY eager)
            throws JSONParseException {

        ArrayList<BsonDocument> ret = new ArrayList<>();

        int toskip = pagesize * (page - 1);

        SkippedFindIterable _cursor = null;

        if (eager != CursorPool.EAGER_CURSOR_ALLOCATION_POLICY.NONE) {

            _cursor = CursorPool.getInstance().get(
                    new CursorPoolEntryKey(
                            coll,
                            sortBy,
                            filters,
                            keys,
                            toskip,
                            0),
                    eager);
        }

        int _pagesize = pagesize;

        // in case there is not cursor in the pool to reuse
        FindIterable<BsonDocument> cursor;

        if (_cursor == null) {
            cursor = getFindIterable(coll, sortBy, filters, keys);
            cursor.skip(toskip);

            MongoCursor<BsonDocument> mc = cursor.iterator();

            while (_pagesize > 0 && mc.hasNext()) {
                ret.add(mc.next());
                _pagesize--;
            }
        } else {
            int alreadySkipped;

            cursor = _cursor.getFindIterable();
            alreadySkipped = _cursor.getAlreadySkipped();

            long startSkipping = 0;
            int cursorSkips = alreadySkipped;

            if (LOGGER.isDebugEnabled()) {
                startSkipping = System.currentTimeMillis();
            }

            LOGGER.debug("got cursor from pool with skips {}. "
                    + "need to reach {} skips.",
                    alreadySkipped,
                    toskip);

            MongoCursor<BsonDocument> mc = cursor.iterator();

            while (toskip > alreadySkipped && mc.hasNext()) {
                mc.next();
                alreadySkipped++;
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("skipping {} times took {} msecs",
                        toskip - cursorSkips,
                        System.currentTimeMillis() - startSkipping);
            }

            while (_pagesize > 0 && mc.hasNext()) {
                ret.add(mc.next());
                _pagesize--;
            }
        }

        // the pool is populated here because, skipping with cursor.next() is heavy operation
        // and we want to minimize the chances that pool cursors are allocated in parallel
        CursorPool.getInstance().populateCache(
                new CursorPoolEntryKey(coll, sortBy, filters, keys, toskip, 0),
                eager);

        return ret;
    }

    /**
     * Returns the collection properties document.
     *
     * @param dbName the database name of the collection
     * @param collName the collection name
     * @return the collection properties document
     */
    public BsonDocument getCollectionProps(
            final String dbName,
            final String collName) {
        MongoCollection<BsonDocument> propsColl
                = getCollection(dbName, "_properties");

        BsonDocument props = propsColl
                .find(new BsonDocument("_id",
                        new BsonString("_properties.".concat(collName))))
                .limit(1)
                .first();

        if (props != null) {
            props.append("_id", new BsonString(collName));
        } else if (doesCollectionExist(dbName, collName)) {
            return new BsonDocument("_id", new BsonString(collName));
        }

        return props;
    }

    /**
     * Returns true if the collection exists
     *
     * @param dbName the database name of the collection
     * @param collName the collection name
     * @return true if the collection exists
     */
    public boolean doesCollectionExist(String dbName, String collName) {
        MongoCursor<String> dbCollections = client
                .getDatabase(dbName)
                .listCollectionNames()
                .iterator();

        while (dbCollections.hasNext()) {
            String dbCollection = dbCollections.next();

            if (collName.equals(dbCollection)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Upsert the collection properties.
     *
     * @param dbName the database name of the collection
     * @param collName the collection name
     * @param properties the new collection properties
     * @param requestEtag the entity tag. must match to allow actual write if
     * checkEtag is true (otherwise http error code is returned)
     * @param updating true if updating existing document
     * @param patching true if use patch semantic (update only specified fields)
     * @param checkEtag true if etag must be checked
     * @return the HttpStatus code to set in the http response
     */
    @SuppressWarnings("unchecked")
    OperationResult upsertCollection(
            final String dbName,
            final String collName,
            final BsonDocument properties,
            final String requestEtag,
            boolean updating,
            final boolean patching,
            final boolean checkEtag) {

        if (patching && !updating) {
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        }

        if (!updating) {
            try {
                client.getDatabase(dbName).createCollection(collName);
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
                    updating = true;
                }
            }
        }

        ObjectId newEtag = new ObjectId();

        final BsonDocument content = DAOUtils.validContent(properties);

        content.put("_etag", new BsonObjectId(newEtag));
        content.remove("_id"); // make sure we don't change this field

        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<BsonDocument> mcoll
                = mdb.getCollection("_properties", BsonDocument.class);

        if (checkEtag && updating) {
            BsonDocument oldProperties
                    = mcoll.find(eq("_id", "_properties.".concat(collName)))
                            .projection(FIELDS_TO_RETURN).first();

            if (oldProperties != null) {
                BsonValue oldEtag = oldProperties.get("_etag");

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
                    return doCollPropsUpdate(
                            collName,
                            patching,
                            updating,
                            mcoll,
                            content,
                            newEtag);
                } else {
                    return new OperationResult(
                            HttpStatus.SC_PRECONDITION_FAILED,
                            oldEtag);
                }
            } else {
                // this is the case when the coll does not have properties
                // e.g. it has not been created by restheart
                return doCollPropsUpdate(
                        collName,
                        patching,
                        updating,
                        mcoll,
                        content,
                        newEtag);
            }
        } else {
            return doCollPropsUpdate(
                    collName,
                    patching,
                    updating,
                    mcoll,
                    content,
                    newEtag);
        }
    }

    private OperationResult doCollPropsUpdate(
            String collName,
            boolean patching,
            boolean updating,
            MongoCollection<BsonDocument> mcoll,
            BsonDocument dcontent,
            ObjectId newEtag) {
        if (patching) {
            OperationResult ret = DAOUtils.updateDocument(
                    mcoll,
                    "_properties.".concat(collName),
                    null,
                    null,
                    dcontent,
                    false);
            return new OperationResult(ret.getHttpCode() > 0
                    ? ret.getHttpCode()
                    : HttpStatus.SC_OK, newEtag);
        } else if (updating) {
            OperationResult ret = DAOUtils.updateDocument(
                    mcoll,
                    "_properties.".concat(collName),
                    null,
                    null,
                    dcontent,
                    true);
            return new OperationResult(ret.getHttpCode() > 0
                    ? ret.getHttpCode()
                    : HttpStatus.SC_OK, newEtag);
        } else {
            OperationResult ret = DAOUtils.updateDocument(
                    mcoll,
                    "_properties.".concat(collName),
                    null,
                    null,
                    dcontent,
                    false);
            return new OperationResult(ret.getHttpCode() > 0
                    ? ret.getHttpCode()
                    : HttpStatus.SC_CREATED, newEtag);
        }
    }

    /**
     * Deletes a collection.
     *
     * @param dbName the database name of the collection
     * @param collName the collection name
     * @param requestEtag the entity tag. must match to allow actual write
     * (otherwise http error code is returned)
     * @return the HttpStatus code to set in the http response
     */
    OperationResult deleteCollection(final String dbName,
            final String collName,
            final String requestEtag,
            final boolean checkEtag) {
        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<Document> mcoll = mdb.getCollection("_properties");

        if (checkEtag) {
            Document properties = mcoll.find(
                    eq("_id", "_properties.".concat(collName)))
                    .projection(FIELDS_TO_RETURN).first();

            if (properties != null) {
                Object oldEtag = properties.get("_etag");

                if (oldEtag != null) {
                    if (requestEtag == null) {
                        return new OperationResult(
                                HttpStatus.SC_CONFLICT,
                                oldEtag);
                    } else if (!Objects.equals(
                            oldEtag.toString(),
                            requestEtag)) {
                        return new OperationResult(
                                HttpStatus.SC_PRECONDITION_FAILED,
                                oldEtag);
                    }
                }
            }
        }

        MongoCollection<Document> collToDelete = mdb.getCollection(collName);
        collToDelete.drop();
        mcoll.deleteOne(eq("_id", "_properties.".concat(collName)));
        return new OperationResult(HttpStatus.SC_NO_CONTENT);
    }
}
