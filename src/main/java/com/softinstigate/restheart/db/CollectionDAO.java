/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
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
package com.softinstigate.restheart.db;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;
import com.softinstigate.restheart.utils.HttpStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import org.bson.BSONObject;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Data Access Object for the mongodb Collection resource.
 *
 * @author Andrea Di Cesare
 */
public class CollectionDAO {

    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();

    private static final Logger logger = LoggerFactory.getLogger(CollectionDAO.class);

    private static final BasicDBObject PROPS_QUERY = new BasicDBObject("_id", "_properties");
    private static final BasicDBObject DOCUMENTS_QUERY = new BasicDBObject("_id", new BasicDBObject("$ne", "_properties"));

    private static final BasicDBObject fieldsToReturn;

    static {
        fieldsToReturn = new BasicDBObject();
        fieldsToReturn.put("_id", 1);
        fieldsToReturn.put("_created_on", 1);
    }

    private static final BasicDBObject fieldsToReturnIndexes;

    static {
        fieldsToReturnIndexes = new BasicDBObject();
        fieldsToReturnIndexes.put("key", 1);
        fieldsToReturnIndexes.put("name", 1);
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
    public static boolean doesCollectionExist(String dbName, String collName) {
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
    public static DBCollection getCollection(String dbName, String collName) {
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
    public static boolean isCollectionEmpty(DBCollection coll) {
        return coll.count(DOCUMENTS_QUERY) == 0;
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
    public static long getCollectionSize(DBCollection coll, Deque<String> filters) {
        final BasicDBObject query = new BasicDBObject(DOCUMENTS_QUERY);

        if (filters != null) {
            try {
                filters.stream().forEach(f -> {
                    query.putAll((BSONObject) JSON.parse(f));  // this can throw JSONParseException for invalid filter parameters
                });
            } catch (JSONParseException jpe) {
                logger.warn("****** error parsing filter expression {}", filters, jpe);
            }
        }

        return coll.count(query);
    }

    /**
     * Returs the documents of the collection applying, sorting, pagination and
     * filtering.
     *
     * @param coll the mongodb DBCollection object
     * @param page the page number
     * @param pagesize the size of the page
     * @param sortBy the Deque collection of fields to use for sorting (prepend
     * field name with - for descending sorting)
     * @param filters the filters to apply. it is a Deque collection of mongodb
     * query conditions.
     * @return
     * @throws JSONParseException
     */
    public static ArrayList<DBObject> getCollectionData(DBCollection coll, int page, int pagesize, Deque<String> sortBy, Deque<String> filters) throws JSONParseException {
        // apply sort_by
        DBObject sort = new BasicDBObject();

        if (sortBy == null || sortBy.isEmpty()) {
            sort.put("_id", 1);
        } else {
            sortBy.stream().forEach((sf) -> {
                sf = sf.replaceAll("_lastupdated_on", "_etag"); // _lastupdated is not stored and actually generated from @tag

                if (sf.startsWith("-")) {
                    sort.put(sf.substring(1), -1);
                } else if (sf.startsWith("+")) {
                    sort.put(sf.substring(1), -1);
                } else {
                    sort.put(sf, 1);
                }
            });
        }

        // apply filter
        final BasicDBObject query = new BasicDBObject(DOCUMENTS_QUERY);

        if (filters != null) {
            filters.stream().forEach((String f) -> {
                BSONObject filterQuery = (BSONObject) JSON.parse(f);
                replaceObjectIds(filterQuery);

                query.putAll(filterQuery);  // this can throw JSONParseException for invalid filter parameters
            });
        }

        ArrayList<DBObject> data = getDataFromCursor(coll.find(query).sort(sort).limit(pagesize).skip(pagesize * (page - 1)));

        data.forEach(row -> {
            Object etag = row.get("_etag");

            if (etag != null && ObjectId.isValid("" + etag)) {
                ObjectId _etag = new ObjectId("" + etag);

                row.put("_lastupdated_on", Instant.ofEpochSecond(_etag.getTimestamp()).toString());
            }
        }
        );

        return data;
    }

    /**
     * Returns the collection properties document.
     *
     * @param dbName the database name of the collection
     * @param collName the collection name
     * @return the collection properties document
     */
    public static DBObject getCollectionProps(String dbName, String collName) {
        DBCollection coll = CollectionDAO.getCollection(dbName, collName);

        DBObject properties = coll.findOne(PROPS_QUERY);

        if (properties != null) {
            properties.put("_id", collName);

            Object etag = properties.get("_etag");

            if (etag != null && ObjectId.isValid("" + etag)) {
                ObjectId oid = new ObjectId("" + etag);

                properties.put("_lastupdated_on", Instant.ofEpochSecond(oid.getTimestamp()).toString());
            }
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
    public static int upsertCollection(String dbName, String collName, DBObject content, ObjectId etag, boolean updating, boolean patching) {
        DB db = DBDAO.getDB(dbName);

        DBCollection coll = db.getCollection(collName);

        if (patching && !updating) {
            return HttpStatus.SC_NOT_FOUND;
        }

        if (updating) {
            if (etag == null) {
                return HttpStatus.SC_CONFLICT;
            }

            BasicDBObject idAndEtagQuery = new BasicDBObject("_id", "_properties");
            idAndEtagQuery.append("_etag", etag);

            if (coll.count(idAndEtagQuery) < 1) {
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
            content.put("_id", "_properties");
            content.put("_created_on", now.toString());
            content.put("_etag", timestamp);
        }

        if (patching) {
            coll.update(PROPS_QUERY, new BasicDBObject("$set", content), true, false);
            return HttpStatus.SC_OK;
        } else {
            // we use findAndModify to get the @created_on field value from the existing properties document
            // we need to put this field back using a second update 
            // it is not possible in a single update even using $setOnInsert update operator
            // in this case we need to provide the other data using $set operator and this makes it a partial update (patch semantic) 

            DBObject old = coll.findAndModify(PROPS_QUERY, fieldsToReturn, null, false, content, false, true);

            if (old != null) {
                Object oldTimestamp = old.get("_created_on");

                if (oldTimestamp == null) {
                    oldTimestamp = now.toString();
                    logger.warn("properties of collection {} had no @created_on field. set to now", coll.getFullName());
                }

                // need to readd the @created_on field 
                BasicDBObject createdContet = new BasicDBObject("_created_on", "" + oldTimestamp);
                createdContet.markAsPartialObject();
                coll.update(PROPS_QUERY, new BasicDBObject("$set", createdContet), true, false);

                return HttpStatus.SC_OK;
            } else {
                // need to readd the @created_on field 
                BasicDBObject createdContet = new BasicDBObject("_created_on", now.toString());
                createdContet.markAsPartialObject();
                coll.update(PROPS_QUERY, new BasicDBObject("$set", createdContet), true, false);

                initDefaultIndexes(coll);

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
    public static int deleteCollection(String dbName, String collName, ObjectId etag) {
        DBCollection coll = getCollection(dbName, collName);

        BasicDBObject checkEtag = new BasicDBObject("_id", "_properties");
        checkEtag.append("_etag", etag);

        DBObject exists = coll.findOne(checkEtag, fieldsToReturn);

        if (exists == null) {
            return HttpStatus.SC_PRECONDITION_FAILED;
        } else {
            coll.drop();
            return HttpStatus.SC_NO_CONTENT;
        }
    }

    private static ArrayList<DBObject> getDataFromCursor(DBCursor cursor) {
        return new ArrayList<>(cursor.toArray());
    }

    private static void initDefaultIndexes(DBCollection coll) {
        coll.createIndex(new BasicDBObject("_id", 1).append("_etag", 1), new BasicDBObject("name", "_id_etag_idx"));
        coll.createIndex(new BasicDBObject("_etag", 1), new BasicDBObject("name", "_etag_idx"));
        coll.createIndex(new BasicDBObject("_created_on", 1), new BasicDBObject("name", "_created_on_idx"));
    }

    /**
     * this replaces string that are valid ObjectIds with ObjectIds objects.
     *
     * @param source
     * @return
     */
    private static void replaceObjectIds(BSONObject source) {
        if (source == null) {
            return;
        }

        source.keySet().stream().forEach((key) -> {
            Object o = source.get(key);

            if (o instanceof BSONObject) {
                replaceObjectIds((BSONObject) o);
            } else if (ObjectId.isValid(o.toString())) {
                source.put(key, new ObjectId(o.toString()));
            }
        });
    }
}
