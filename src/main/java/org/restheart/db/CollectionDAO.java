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

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;

import org.restheart.utils.HttpStatus;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Objects;

import org.bson.BSONObject;
import org.bson.Document;
import org.bson.types.ObjectId;
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

    private final MongoClient client;

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionDAO.class);

    private static final BasicDBObject FIELDS_TO_RETURN;

    CollectionDAO(MongoClient client) {
        this.client = client;
    }

    static {
        FIELDS_TO_RETURN = new BasicDBObject();
        FIELDS_TO_RETURN.put("_id", 1);
        FIELDS_TO_RETURN.put("_etag", 1);
    }

    /**
     * Returns the mongodb DBCollection object for the collection in db dbName.
     *
     * @param dbName the database name of the collection the database name of
     * the collection
     * @param collName the collection name
     * @return the mongodb DBCollection object for the collection in db dbName
     */
    DBCollection getCollection(final String dbName, final String collName) {
        return client.getDB(dbName).getCollection(collName);
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
    public boolean isCollectionEmpty(final DBCollection coll) {
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
    public long getCollectionSize(final DBCollection coll, final Deque<String> filters) {
        final BasicDBObject query = new BasicDBObject();

        if (filters != null) {
            try {
                filters.stream().forEach(f -> {
                    query.putAll((BSONObject) JSON.parse(f));  // this can throw JSONParseException for invalid filter parameters
                });
            } catch (JSONParseException jpe) {
                LOGGER.warn("****** error parsing filter expression {}", filters, jpe);
            }
        }

        return coll.count(query);
    }

    /**
     * Returs the DBCursor of the collection applying sorting and filtering.
     *
     * @param coll the mongodb DBCollection object <<<<<<< HEAD
     * @param sortBy the Deque collection of fields to use for sorting (prepend
     * field name with - for descending sorting)
     * @param filters the filters to apply. it is a Deque collection of mongodb
     * query conditions. =======
     * @param sortBy the Deque collection of fields to use for sorting (prepend
     * field name with - for descending sorting)
     * @param filters the filters to apply. it is a Deque collection of mongodb
     * query conditions. >>>>>>> 3d60559d7f3582061f69b35b54ef03c3a01c20de
     * @param keys
     * @return
     * @throws JSONParseException
     */
    DBCursor getCollectionDBCursor(
            final DBCollection coll,
            final Deque<String> sortBy,
            final Deque<String> filters,
            final Deque<String> keys) throws JSONParseException {
        // apply sort_by
        DBObject sort = new BasicDBObject();

        if (sortBy == null || sortBy.isEmpty()) {
            sort.put("_id", -1);
        } else {
            sortBy.stream().forEach((s) -> {

                String _s = s.trim(); // the + sign is decoded into a space, in case remove it

                if (_s.startsWith("-")) {
                    sort.put(_s.substring(1), -1);
                } else if (_s.startsWith("+")) {
                    sort.put(_s.substring(1), 1);
                } else {
                    sort.put(_s, 1);
                }
            });
        }

        // apply filter
        // TODO refactoring mongodb 3.2 driver. use RequestContext.getComposedFilters()
        final BasicDBObject query = new BasicDBObject();

        if (filters != null) {
            if (filters.size() > 1) {
                BasicDBList _filters = new BasicDBList();

                filters.stream().forEach((String f) -> {
                    _filters.add((BSONObject) JSON.parse(f));
                });

                query.put("$and", _filters);
            } else {
                BSONObject filterQuery = (BSONObject) JSON.parse(filters.getFirst());

                query.putAll(filterQuery);  // this can throw JSONParseException for invalid filter parameters
            }
        }

        final BasicDBObject fields = new BasicDBObject();

        if (keys
                != null) {
            keys.stream().forEach((String f) -> {
                BSONObject keyQuery = (BSONObject) JSON.parse(f);

                fields.putAll(keyQuery);  // this can throw JSONParseException for invalid filter parameters
            });
        }

        return coll.find(query, fields)
                .sort(sort);
    }

    ArrayList<DBObject> getCollectionData(
            final DBCollection coll,
            final int page,
            final int pagesize,
            final Deque<String> sortBy,
            final Deque<String> filters,
            final Deque<String> keys, DBCursorPool.EAGER_CURSOR_ALLOCATION_POLICY eager) throws JSONParseException {

        ArrayList<DBObject> ret = new ArrayList<>();

        int toskip = pagesize * (page - 1);

        SkippedDBCursor _cursor = null;

        if (eager != DBCursorPool.EAGER_CURSOR_ALLOCATION_POLICY.NONE) {

            _cursor = DBCursorPool.getInstance().get(new DBCursorPoolEntryKey(coll, sortBy, filters, keys, toskip, 0), eager);
        }

        int _pagesize = pagesize;

        // in case there is not cursor in the pool to reuse
        DBCursor cursor;
        if (_cursor == null) {
            cursor = getCollectionDBCursor(coll, sortBy, filters, keys);
            cursor.skip(toskip);

            while (_pagesize > 0 && cursor.hasNext()) {
                ret.add(cursor.next());
                _pagesize--;
            }
        } else {
            int alreadySkipped;

            cursor = _cursor.getCursor();
            alreadySkipped = _cursor.getAlreadySkipped();
            
            long startSkipping = 0;
            int cursorSkips = alreadySkipped;
            
            if (LOGGER.isDebugEnabled()) {
                startSkipping = System.currentTimeMillis();
            }
            
            LOGGER.debug("got cursor from pool with skips {}. need to reach {} skips.", alreadySkipped, toskip);

            while (toskip > alreadySkipped && cursor.hasNext()) {
                cursor.next();
                alreadySkipped++;
            }
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("skipping {} times took {} msecs", toskip - cursorSkips, System.currentTimeMillis() - startSkipping);
            }

            while (_pagesize > 0 && cursor.hasNext()) {
                ret.add(cursor.next());
                _pagesize--;
            }
        }
        
        // the pool is populated here because, skipping with cursor.next() is heavy operation
        // and we want to minimize the chances that pool cursors are allocated in parallel
        DBCursorPool.getInstance().populateCache(
                new DBCursorPoolEntryKey(coll, sortBy, filters, keys, toskip, 0), 
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
    public DBObject getCollectionProps(final String dbName, final String collName, final boolean fixMissingProperties) {
        DBCollection propsColl = getCollection(dbName, "_properties");

        return propsColl.findOne(new BasicDBObject("_id", "_properties.".concat(collName)));
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
            final DBObject properties,
            final String requestEtag,
            final boolean updating,
            final boolean patching,
            final boolean checkEtag) {

        if (patching && !updating) {
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        }

        if (!updating) {
            client.getDatabase(dbName).createCollection(collName);
        }

        ObjectId newEtag = new ObjectId();

        final DBObject content = DAOUtils.validContent(properties);

        content.put("_etag", newEtag);
        content.removeField("_id"); // make sure we don't change this field

        //TODO remove this after migration to mongodb driver 3.2 completes
        Document dcontent = new Document(content.toMap());

        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<Document> mcoll = mdb.getCollection("_properties");

        if (checkEtag && updating) {
            Document oldProperties = mcoll.find(eq("_id", "_properties.".concat(collName)))
                    .projection(FIELDS_TO_RETURN).first();

            if (oldProperties != null) {
                Object oldEtag = oldProperties.get("_etag");

                if (oldEtag != null && requestEtag == null) {
                    return new OperationResult(HttpStatus.SC_CONFLICT, oldEtag);
                }

                String _oldEtag;

                if (oldEtag != null) {
                    _oldEtag = oldEtag.toString();
                } else {
                    _oldEtag = null;
                }

                if (Objects.equals(requestEtag, _oldEtag)) {
                    return doCollPropsUpdate(collName, patching, updating, mcoll, dcontent, newEtag);
                } else {
                    return new OperationResult(HttpStatus.SC_PRECONDITION_FAILED, oldEtag);
                }
            } else {
                // this is the case when the coll does not have properties
                // e.g. it has not been created by restheart
                return doCollPropsUpdate(collName, patching, updating, mcoll, dcontent, newEtag);
            }
        } else {
            return doCollPropsUpdate(collName, patching, updating, mcoll, dcontent, newEtag);
        }
    }

    private OperationResult doCollPropsUpdate(String collName, boolean patching, boolean updating, MongoCollection<Document> mcoll, Document dcontent, ObjectId newEtag) {
        if (patching) {
            DAOUtils.updateDocument(
                    mcoll,
                    "_properties.".concat(collName),
                    null,
                    dcontent,
                    false);
            return new OperationResult(HttpStatus.SC_OK, newEtag);
        } else if (updating) {
            DAOUtils.updateDocument(
                    mcoll,
                    "_properties.".concat(collName),
                    null,
                    dcontent,
                    true);
            return new OperationResult(HttpStatus.SC_OK, newEtag);
        } else {
            DAOUtils.updateDocument(
                    mcoll,
                    "_properties.".concat(collName),
                    null,
                    dcontent,
                    false);
            return new OperationResult(HttpStatus.SC_CREATED, newEtag);
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
            Document properties = mcoll.find(eq("_id", "_properties.".concat(collName)))
                    .projection(FIELDS_TO_RETURN).first();

            if (properties != null) {
                Object oldEtag = properties.get("_etag");

                if (oldEtag != null) {
                    if (requestEtag == null) {
                        return new OperationResult(HttpStatus.SC_CONFLICT, oldEtag);
                    } else if (!Objects.equals(oldEtag.toString(), requestEtag)) {
                        return new OperationResult(HttpStatus.SC_PRECONDITION_FAILED, oldEtag);
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
