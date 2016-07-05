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

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.util.JSONParseException;

import java.util.ArrayList;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonString;

import org.restheart.handlers.IllegalQueryParamenterException;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public interface Database {
    BsonDocument METADATA_QUERY
            = new BsonDocument("_id", new BsonString("_properties"));

    /**
     *
     * @param dbName
     * @param collectionName
     * @param requestEtag
     * @param checkEtag
     * @return HTTP status code
     */
    OperationResult deleteCollection(
            String dbName,
            String collectionName,
            String requestEtag,
            boolean checkEtag);

    /**
     *
     * @param dbName
     * @param requestEtag
     * @param checkEtag
     * @return HTTP status code
     */
    OperationResult deleteDatabase(
            String dbName,
            String requestEtag,
            boolean checkEtag);

    /**
     * @param dbName
     * @return true if DB dbName exists
     *
     */
    boolean doesDbExist(String dbName);

    /**
     * @param dbName
     * @param collName
     * @return true if exists the collection collName exists in DB dbName
     *
     */
    boolean doesCollectionExist(
            String dbName,
            String collName);

    /**
     *
     * @param dbName
     * @param collectionName
     * @return A Collection
     */
    DBCollection getCollectionLegacy(
            String dbName,
            String collectionName);

    /**
     *
     * @param dbName
     * @param collectionName
     * @return A Collection
     */
    MongoCollection<BsonDocument> getCollection(
            String dbName,
            String collectionName);

    /**
     *
     * @param collection
     * @param page
     * @param pagesize
     * @param sortBy
     * @param filter
     * @param keys
     * @param cursorAllocationPolicy
     * @return Collection Data as ArrayList of BsonDocument
     */
    ArrayList<BsonDocument> getCollectionData(
            MongoCollection<BsonDocument> collection,
            int page,
            int pagesize,
            BsonDocument sortBy,
            BsonDocument filter,
            BsonDocument keys,
            CursorPool.EAGER_CURSOR_ALLOCATION_POLICY cursorAllocationPolicy);

    /**
     *
     * @param dbName
     * @param collectionName
     * @return Collection properties
     */
    BsonDocument getCollectionProperties(
            String dbName,
            String collectionName);

    /**
     *
     * @param collection
     * @param filters
     * @return the number of documents in the given collection (taking into
     * account the filters in case)
     */
    long getCollectionSize(
            final MongoCollection<BsonDocument> collection,
            BsonDocument filters);

    /**
     *
     * @param dbName
     * @return the Mongo DB
     */
    DB getDBLegacy(String dbName);

    /**
     *
     * @param dbName
     * @return the MongoDatabase
     */
    MongoDatabase getDatabase(String dbName);

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
    List<BsonDocument> getDatabaseData(
            String dbName, List<String> collections,
            int page,
            int pagesize)
            throws IllegalQueryParamenterException;

    /**
     *
     * @return A List of database names
     */
    List<String> getDatabaseNames();

    /**
     *
     * @param dbName
     * @return A List of collection names
     */
    List<String> getCollectionNames(String dbName);

    /**
     * @param dbName
     * @return the db props
     *
     */
    BsonDocument getDatabaseProperties(String dbName);

    /**
     *
     * @param dbName
     * @param collectionName
     * @param content
     * @param requestEtag
     * @param updating
     * @param patching
     * @param checkEtag
     * @return
     */
    OperationResult upsertCollection(
            String dbName,
            String collectionName,
            BsonDocument content,
            String requestEtag,
            boolean updating,
            boolean patching,
            boolean checkEtag);

    /**
     *
     * @param dbName
     * @param content
     * @param requestEtag
     * @param updating
     * @param patching
     * @param checkEtag
     * @return
     */
    OperationResult upsertDB(
            String dbName,
            BsonDocument content,
            String requestEtag,
            boolean updating,
            boolean patching,
            boolean checkEtag);

    /**
     *
     * @param dbName
     * @param collection
     * @param indexId
     * @return the operation result
     */
    int deleteIndex(
            String dbName,
            String collection,
            String indexId);

    /**
     *
     * @param dbName
     * @param collectionName
     * @return A List of indexes for collectionName in dbName
     */
    List<BsonDocument> getCollectionIndexes(
            String dbName,
            String collectionName);

    /**
     *
     * @param dbName
     * @param collection
     * @param keys
     * @param options
     */
    void createIndex(
            String dbName,
            String collection,
            BsonDocument keys,
            BsonDocument options);

    /**
     * Returs the FindIterable of the collection applying sorting, filtering and
     * projection.
     *
     * @param collection the mongodb MongoCollection<BsonDocument> object
     * @param sortBy the Deque collection of fields to use for sorting (prepend
     * field name with - for descending sorting)
     * @param filters the filters to apply. it is a Deque collection of mongodb
     * query conditions.
     * @param keys
     * @return
     * @throws JSONParseException
     */
    FindIterable<BsonDocument> getFindIterable(
            MongoCollection<BsonDocument> collection,
            BsonDocument sortBy,
            BsonDocument filters,
            BsonDocument keys)
            throws JSONParseException;
}
