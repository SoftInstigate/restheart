/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;

import org.restheart.utils.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;

import org.bson.BSONObject;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Data Access Object for the mongodb Collection resource. NOTE: this class
 * is package-private and only meant to be used as a delagate within the DbsDAO
 * class.
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
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
     * Checks if the collection exists.
     *
     * WARNING: slow method. perf tests show this can take up to 35% overall
     * requests processing time when getting data from a collection
     *
     * @deprecated
     * @param dbName the database name of the collection
     * @param collName the collection name
     * @return true if the specified collection exits in the db dbName
     */
    boolean doesCollectionExist(final String dbName, final String collName) {
        if (dbName == null || dbName.isEmpty() || dbName.contains(" ")) {
            return false;
        }

        BasicDBObject query = new BasicDBObject("name", dbName + "." + collName);

        return client.getDB(dbName).getCollection("system.namespaces").findOne(query) != null;
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
        final BasicDBObject query = new BasicDBObject();

        if (filters != null) {
            filters.stream().forEach((String f) -> {
                BSONObject filterQuery = (BSONObject) JSON.parse(f);

                query.putAll(filterQuery);  // this can throw JSONParseException for invalid filter parameters
            });
        }

        final BasicDBObject fields = new BasicDBObject();

        if (keys != null) {
            keys.stream().forEach((String f) -> {
                BSONObject keyQuery = (BSONObject) JSON.parse(f);

                fields.putAll(keyQuery);  // this can throw JSONParseException for invalid filter parameters
            });
        }

        return coll.find(query, fields).sort(sort);
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

            while (toskip > alreadySkipped && cursor.hasNext()) {
                cursor.next();
                alreadySkipped++;
            }

            while (_pagesize > 0 && cursor.hasNext()) {
                ret.add(cursor.next());
                _pagesize--;
            }
        }

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

        DBObject properties = propsColl.findOne(new BasicDBObject("_id", "_properties.".concat(collName)));

        if (properties != null) {
            properties.put("_id", collName);
        } else if (fixMissingProperties) {
            new PropsFixer().addCollectionProps(dbName, collName);
            return getCollectionProps(dbName, collName, false);
        }

        return properties;
    }

    /**
     * Upsert the collection properties.
     *
     * @param dbName the database name of the collection
     * @param collName the collection name
     * @param properties the new collection properties
     * @param requestEtag the entity tag. must match to allow actual write
     * (otherwise http error code is returned)
     * @param updating true if updating existing document
     * @param patching true if use patch semantic (update only specified fields)
     * @return the HttpStatus code to set in the http response
     */
    OperationResult upsertCollection(
            final String dbName,
            final String collName,
            final DBObject properties,
            final ObjectId requestEtag,
            final boolean updating,
            final boolean patching) {
        if (patching && !updating) {
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        }
        
        DB db = client.getDB(dbName);

        final DBCollection propsColl = db.getCollection("_properties");

        final DBObject exists = propsColl.findOne(new BasicDBObject("_id", "_properties.".concat(collName)), FIELDS_TO_RETURN);

        if (exists == null && updating) {
            LOGGER.error("updating but cannot find collection _properties.{} for {}/{}", collName, dbName, collName);
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        } else if (exists != null) {
            Object oldEtag = exists.get("_etag");

            if (oldEtag != null) {
                if (requestEtag == null) {
                    return new OperationResult(HttpStatus.SC_CONFLICT, oldEtag);
                }

                if (!oldEtag.equals(requestEtag)) {
                    return new OperationResult(HttpStatus.SC_PRECONDITION_FAILED, oldEtag);
                }
            }
        }

        ObjectId newEtag = new ObjectId();

        final DBObject content = DAOUtils.validContent(properties);

        content.removeField("_id"); // make sure we don't change this field

        if (updating) {
            content.removeField("_crated_on"); // don't allow to update this field
            content.put("_etag", newEtag);
        } else {
            content.put("_id", "_properties.".concat(collName));
            content.put("_etag", newEtag);
        }

        if (patching) {
            propsColl.update(new BasicDBObject("_id", "_properties.".concat(collName)),
                    new BasicDBObject("$set", content), true, false);
            
            return new OperationResult(HttpStatus.SC_OK, newEtag);
        } else {
            propsColl.update(new BasicDBObject("_id", "_properties.".concat(collName)),
                    content, true, false);
            
            // create the default indexes
            createDefaultIndexes(db.getCollection(collName));
            
            if (updating) {
                return new OperationResult(HttpStatus.SC_OK, newEtag);
            } else {
                return new OperationResult(HttpStatus.SC_CREATED, newEtag);
            }
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
    OperationResult deleteCollection(final String dbName, final String collName, final ObjectId requestEtag) {
        DBCollection coll = getCollection(dbName, collName);
        DBCollection propsColl = getCollection(dbName, "_properties");

        final BasicDBObject checkEtag = new BasicDBObject("_id", "_properties.".concat(collName));

        final DBObject exists = propsColl.findOne(checkEtag, FIELDS_TO_RETURN);

        if (exists != null) {
            Object oldEtag = exists.get("_etag");

            if (oldEtag != null && requestEtag == null) {
                return new OperationResult(HttpStatus.SC_CONFLICT, oldEtag);
            }

            if (requestEtag.equals(oldEtag)) {
                propsColl.remove(new BasicDBObject("_id", "_properties.".concat(collName)));
                coll.drop();
                return new OperationResult(HttpStatus.SC_NO_CONTENT);
            } else {
                return new OperationResult(HttpStatus.SC_PRECONDITION_FAILED, oldEtag);
            }
        } else {
            LOGGER.error("cannot find collection _properties.{} for {}/{}", collName, dbName, collName);
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        }
    }

    private void createDefaultIndexes(final DBCollection coll) {
        coll.createIndex(new BasicDBObject("_id", 1).append("_etag", 1), new BasicDBObject("name", "_id_etag_idx"));
        coll.createIndex(new BasicDBObject("_etag", 1), new BasicDBObject("name", "_etag_idx"));
    }
}
