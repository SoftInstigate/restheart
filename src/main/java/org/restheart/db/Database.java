/*
 * RESTHeart - the data REST API server
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

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.util.JSONParseException;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.bson.types.ObjectId;
import org.restheart.handlers.IllegalQueryParamenterException;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public interface Database {

    BasicDBObject METADATA_QUERY = new BasicDBObject("_id", "_properties");

    /**
     *
     * @param dbName
     * @param collectionName
     * @param etag
     * @return HTTP status code
     */
    int deleteCollection(String dbName, String collectionName, ObjectId etag);

    /**
     *
     * @param dbName
     * @param requestEtag
     * @return HTTP status code
     */
    int deleteDatabase(String dbName, ObjectId requestEtag);

    /**
     * WARNING: slow method.
     *
     * @param dbName
     * @return true if exists a DB with this name
     *
     */
    boolean existsDatabaseWithName(String dbName);

    /**
     *
     * @param dbName
     * @param collectionName
     * @return A Collection
     */
    DBCollection getCollection(String dbName, String collectionName);

    /**
     *
     * @param collection
     * @param page
     * @param pagesize
     * @param sortBy
     * @param filter
     * @param cursorAllocationPolicy
     * @param detectObjectids
     * @return Collection Data as ArrayList of DBObject
     */
    ArrayList<DBObject> getCollectionData(DBCollection collection, int page, int pagesize, Deque<String> sortBy, Deque<String> filter, DBCursorPool.EAGER_CURSOR_ALLOCATION_POLICY cursorAllocationPolicy);

    /**
     *
     * @param dbName
     * @param collectionName
     * @param fixMissingProperties if true, initialize the collection properties if missing
     * @return Collection properties
     */
    DBObject getCollectionProperties(String dbName, String collectionName, boolean fixMissingProperties);

    /**
     *
     * @param collection
     * @param filters
     * @return the number of documents in the given collection (taking into
     * account the filters in case)
     */
    long getCollectionSize(DBCollection collection, Deque<String> filters);

    /**
     *
     * @param dbName
     * @return the Mongo DB
     */
    DB getDB(String dbName);

    /**
     * @param collections the collection names
     * @return the number of collections in this db
     *
     */
    long getDBSize(List<String> collections);

    /**
     * @param dbName
     * @param collections the collections list as got from getCollectionNames()
     * @param page
     * @param pagesize
     * @return the db data
     * @throws org.restheart.handlers.IllegalQueryParamenterException
     *
     */
    List<DBObject> getData(String dbName, List<String> collections, int page, int pagesize) throws IllegalQueryParamenterException;

    /**
     *
     * @return A List of database names
     */
    List<String> getDatabaseNames();

    /**
     *
     * @param db
     * @return A List of collection names
     */
    List<String> getCollectionNames(DB db);

    /**
     * @param dbName
     * @param fixMissingProperties if true, initialize the db properties if missing
     * @return the db props
     *
     */
    DBObject getDatabaseProperties(String dbName, boolean fixMissingProperties);

    /**
     *
     * @param dbName
     * @param collectionName
     * @param content
     * @param etag
     * @param updating
     * @param patching
     * @return
     */
    int upsertCollection(String dbName, String collectionName, DBObject content, ObjectId etag, boolean updating, boolean patching);

    /**
     *
     * @param dbName
     * @param content
     * @param etag
     * @param patching
     * @return
     */
    int upsertDB(String dbName, DBObject content, ObjectId etag, boolean patching);

    /**
     *
     * WARNING: slow method.
     *
     * @param exchange
     * @param dbName
     * @return
     * @deprecated
     *
     */
    boolean checkDbExists(HttpServerExchange exchange, String dbName);

    /**
     *
     * @param dbName
     * @param collection
     * @param indexId
     * @return the operation result
     */
    int deleteIndex(String dbName, String collection, String indexId);

    /**
     *
     * @param dbName
     * @param collectionName
     * @return A List of indexes for collectionName in dbName
     */
    List<DBObject> getCollectionIndexes(String dbName, String collectionName);

    /**
     * Returs the DBCursor of the collection applying sorting and filtering.
     *
     * @param collection the mongodb DBCollection object
     * @param sortBy the Deque collection of fields to use for sorting (prepend
     * field name with - for descending sorting)
     * @param filters the filters to apply. it is a Deque collection of mongodb
     * query conditions.
     * @return
     * @throws JSONParseException
     */
    DBCursor getCollectionDBCursor(DBCollection collection, Deque<String> sortBy, Deque<String> filters) throws JSONParseException;

    /**
     *
     * @param dbName
     * @param collection
     * @param keys
     * @param options
     */
    void createIndex(String dbName, String collection, DBObject keys, DBObject options);
}
