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

    private static final BasicDBObject fieldsToReturn;

    CollectionDAO(MongoClient client) {
        this.client = client;
    }

    static {
        fieldsToReturn = new BasicDBObject();
        fieldsToReturn.put("_id", 1);
        fieldsToReturn.put("_created_on", 1);
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
    boolean doesCollectionExist(String dbName, String collName) {
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
    DBCollection getCollection(String dbName, String collName) {
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
    public boolean isCollectionEmpty(DBCollection coll) {
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
    public long getCollectionSize(DBCollection coll, Deque<String> filters) {
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
     * @param coll the mongodb DBCollection object
     * @param sortBy the Deque collection of fields to use for sorting (prepend
     * field name with - for descending sorting)
     * @param filters the filters to apply. it is a Deque collection of mongodb
     * query conditions.
     * @return
     * @throws JSONParseException
     */
    DBCursor getCollectionDBCursor(DBCollection coll, Deque<String> sortBy, Deque<String> filters) throws JSONParseException {
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

        return coll.find(query).sort(sort);
    }

    ArrayList<DBObject> getCollectionData(
            DBCollection coll,
            int page,
            int pagesize,
            Deque<String> sortBy,
            Deque<String> filters,
            DBCursorPool.EAGER_CURSOR_ALLOCATION_POLICY eager) throws JSONParseException {
        ArrayList<DBObject> ret = new ArrayList<>();

        int toskip = pagesize * (page - 1);

        DBCursor cursor;
        SkippedDBCursor _cursor = null;

        if (eager != DBCursorPool.EAGER_CURSOR_ALLOCATION_POLICY.NONE) {

            _cursor = DBCursorPool.getInstance().get(new DBCursorPoolEntryKey(coll, sortBy, filters, toskip, 0), eager);
        }

        int alreadySkipped;

        // in case there is not cursor in the pool to reuse
        if (_cursor == null) {
            cursor = getCollectionDBCursor(coll, sortBy, filters);
            alreadySkipped = 0;
        } else {
            cursor = _cursor.getCursor();
            alreadySkipped = _cursor.getAlreadySkipped();
        }

        if (toskip - alreadySkipped > 0) {
            cursor.skip(toskip - alreadySkipped);
        }

        while (pagesize > 0 && cursor.hasNext()) {
            ret.add(cursor.next());
            pagesize--;
        }

        // add the _lastupdated_on and _created_on
        ret.forEach(row -> {
            Object etag = row.get("_etag");

            if (row.get("_lastupdated_on") == null && etag != null && etag instanceof ObjectId) {
                row.put("_lastupdated_on", Instant.ofEpochSecond(((ObjectId) etag).getTimestamp()).toString());
            }

            Object id = row.get("_id");

            // generate the _created_on timestamp from the _id if this is an instance of ObjectId
            if (row.get("_created_on") == null && id != null && id instanceof ObjectId) {
                row.put("_created_on", Instant.ofEpochSecond(((ObjectId) id).getTimestamp()).toString());
            }
        }
        );

        return ret;
    }

    /**
     * Returns the collection properties document.
     *
     * @param dbName the database name of the collection
     * @param collName the collection name
     * @return the collection properties document
     */
    public DBObject getCollectionProps(String dbName, String collName, boolean fixMissingProperties) {
        DBCollection propsColl = getCollection(dbName, "_properties");

        DBObject properties = propsColl.findOne(new BasicDBObject("_id", "_properties.".concat(collName)));

        if (properties != null) {
            properties.put("_id", collName);

            Object etag = properties.get("_etag");

            if (etag != null && etag instanceof ObjectId) {
                properties.put("_lastupdated_on", Instant.ofEpochSecond(((ObjectId) etag).getTimestamp()).toString());
            }
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
     * @param content the new collection properties
     * @param etag the entity tag. must match to allow actual write (otherwise
     * http error code is returned)
     * @param updating true if updating existing document
     * @param patching true if use patch semantic (update only specified fields)
     * @return the HttpStatus code to set in the http response
     */
    int upsertCollection(String dbName, String collName, DBObject content, ObjectId etag, boolean updating, boolean patching) {
        DB db = client.getDB(dbName);

        DBCollection propsColl = db.getCollection("_properties");

        if (patching && !updating) {
            return HttpStatus.SC_NOT_FOUND;
        }

        if (updating) {
            if (etag == null) {
                return HttpStatus.SC_CONFLICT;
            }

            BasicDBObject idAndEtagQuery = new BasicDBObject("_id", "_properties.".concat(collName));
            idAndEtagQuery.append("_etag", etag);

            if (propsColl.count(idAndEtagQuery) < 1) {
                return HttpStatus.SC_PRECONDITION_FAILED;
            }
        }

        ObjectId timestamp = new ObjectId();
        Instant now = Instant.ofEpochSecond(timestamp.getTimestamp());

        if (content == null) {
            content = new BasicDBObject();
        }

        content.removeField("_id"); // make sure we don't change this field

        if (updating) {
            content.removeField("_crated_on"); // don't allow to update this field
            content.put("_etag", timestamp);
        } else {
            content.put("_id", "_properties.".concat(collName));
            content.put("_created_on", now.toString());
            content.put("_etag", timestamp);
        }

        if (patching) {
            propsColl.update(new BasicDBObject("_id", "_properties.".concat(collName)), new BasicDBObject("$set", content), true, false);
            return HttpStatus.SC_OK;
        } else {
            // we use findAndModify to get the @created_on field value from the existing properties document
            // we need to put this field back using a second update 
            // it is not possible in a single update even using $setOnInsert update operator
            // in this case we need to provide the other data using $set operator and this makes it a partial update (patch semantic) 

            DBObject old = propsColl.findAndModify(new BasicDBObject("_id", "_properties.".concat(collName)), fieldsToReturn, null, false, content, false, true);

            if (old != null) {
                Object oldTimestamp = old.get("_created_on");

                if (oldTimestamp == null) {
                    oldTimestamp = now.toString();
                    LOGGER.warn("properties of collection {} had no @created_on field. set it to now", dbName + "." + collName);
                }

                // need to readd the @created_on field 
                BasicDBObject createdContent = new BasicDBObject("_created_on", "" + oldTimestamp);
                createdContent.markAsPartialObject();
                propsColl.update(new BasicDBObject("_id", "_properties.".concat(collName)), new BasicDBObject("$set", createdContent), true, false);

                return HttpStatus.SC_OK;
            } else {
                // need to readd the @created_on field 
                BasicDBObject createdContent = new BasicDBObject("_created_on", now.toString());
                createdContent.markAsPartialObject();
                propsColl.update(new BasicDBObject("_id", "_properties.".concat(collName)), new BasicDBObject("$set", createdContent), true, false);

                initDefaultIndexes(db.getCollection(collName));

                return HttpStatus.SC_CREATED;
            }
        }
    }

    /**
     * Deletes a collection.
     *
     * @param dbName the database name of the collection
     * @param collName the collection name
     * @param etag the entity tag. must match to allow actual write (otherwise
     * http error code is returned)
     * @return the HttpStatus code to set in the http response
     */
    int deleteCollection(String dbName, String collName, ObjectId etag) {
        DBCollection coll = getCollection(dbName, collName);
        DBCollection propsColl = getCollection(dbName, "_properties");

        BasicDBObject checkEtag = new BasicDBObject("_id", "_properties.".concat(collName));
        checkEtag.append("_etag", etag);

        DBObject exists = propsColl.findOne(checkEtag, fieldsToReturn);

        if (exists == null) {
            return HttpStatus.SC_PRECONDITION_FAILED;
        } else {
            propsColl.remove(new BasicDBObject("_id", "_properties.".concat(collName)));
            coll.drop();
            return HttpStatus.SC_NO_CONTENT;
        }
    }

    private void initDefaultIndexes(DBCollection coll) {
        coll.createIndex(new BasicDBObject("_id", 1).append("_etag", 1), new BasicDBObject("name", "_id_etag_idx"));
        coll.createIndex(new BasicDBObject("_etag", 1), new BasicDBObject("name", "_etag_idx"));
    }
}
