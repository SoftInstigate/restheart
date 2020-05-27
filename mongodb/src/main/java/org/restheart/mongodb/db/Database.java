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
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.json.JsonParseException;
import org.restheart.exchange.ExchangeKeys.EAGER_CURSOR_ALLOCATION_POLICY;
import org.restheart.exchange.IllegalQueryParamenterException;
import org.restheart.exchange.OperationResult;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public interface Database {

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collectionName
     * @param requestEtag
     * @param checkEtag
     * @return HTTP status code
     */
    OperationResult deleteCollection(
            final ClientSession cs,
            final String dbName,
            final String collectionName,
            final String requestEtag,
            final boolean checkEtag);

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param requestEtag
     * @param checkEtag
     * @return HTTP status code
     */
    OperationResult deleteDatabase(
            final ClientSession cs,
            final String dbName,
            final String requestEtag,
            final boolean checkEtag);

    /**
     * @param cs
     * @param dbName
     * @return true if DB dbName exists
     *
     */
    boolean doesDbExist(
            final ClientSession cs,
            final String dbName);

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @return true if exists the collection collName exists in DB dbName
     *
     */
    boolean doesCollectionExist(
            final ClientSession cs,
            final String dbName,
            final String collName);

    /**
     *
     * @param dbName
     * @param collectionName
     * @return A Collection
     */
    MongoCollection<BsonDocument> getCollection(
            final String dbName,
            final String collectionName);

    /**
     *
     * @param cs the client session
     * @param collection
     * @param page
     * @param pagesize
     * @param sortBy
     * @param filter
     * @param hint
     * @param keys
     * @param cursorAllocationPolicy
     * @return Collection Data as ArrayList of BsonDocument
     */
    BsonArray getCollectionData(
            final ClientSession cs,
            final MongoCollection<BsonDocument> collection,
            final int page,
            final int pagesize,
            final BsonDocument sortBy,
            final BsonDocument filter,
            final BsonDocument hint,
            final BsonDocument keys,
            final EAGER_CURSOR_ALLOCATION_POLICY cursorAllocationPolicy);

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collectionName
     * @return Collection properties
     */
    BsonDocument getCollectionProperties(
            final ClientSession cs,
            final String dbName,
            final String collectionName);

    /**
     *
     * @param cs the client session
     * @param collection
     * @param filters
     * @return the number of documents in the given collection (taking into
     * account the filters in case)
     */
    long getCollectionSize(
            final ClientSession cs,
            final MongoCollection<BsonDocument> collection,
            final BsonDocument filters);

    /**
     *
     * @param dbName
     * @return the MongoDatabase
     */
    MongoDatabase getDatabase(final String dbName);

    /**
     *
     * @param collections the collection names
     * @return the number of collections in this db
     *
     */
    long getDBSize(final List<String> collections);

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collections the collections list as got from getCollectionNames()
     * @param page
     * @param pagesize
     * @param noCache true to avoid using caching
     * @return the db data
     * @throws org.restheart.exchange.IllegalQueryParamenterException
     *
     */
    BsonArray getDatabaseData(
            final ClientSession cs,
            final String dbName, List<String> collections,
            final int page,
            final int pagesize,
            final boolean noCache)
            throws IllegalQueryParamenterException;

    /**
     *
     * @param cs the client session
     * @return A List of database names
     */
    List<String> getDatabaseNames(final ClientSession cs);

    /**
     *
     * @param cs the client session
     * @param dbName
     * @return A List of collection names
     */
    List<String> getCollectionNames(
            final ClientSession cs,
            final String dbName);

    /**
     * @param cs
     * @param dbName
     * @return the db props
     *
     */
    BsonDocument getDatabaseProperties(
            final ClientSession cs,
            final String dbName);

    /**
     *
     * @param cs the client session
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
            final ClientSession cs,
            final String dbName,
            final String collectionName,
            final BsonDocument content,
            final String requestEtag,
            boolean updating,
            final boolean patching,
            final boolean checkEtag);

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param content
     * @param requestEtag
     * @param updating
     * @param patching
     * @param checkEtag
     * @return
     */
    OperationResult upsertDB(
            final ClientSession cs,
            final String dbName,
            final BsonDocument content,
            final String requestEtag,
            final boolean updating,
            final boolean patching,
            final boolean checkEtag);

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collection
     * @param indexId
     * @return the operation result
     */
    int deleteIndex(
            final ClientSession cs,
            final String dbName,
            final String collection,
            final String indexId);

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collectionName
     * @return A List of indexes for collectionName in dbName
     */
    List<BsonDocument> getCollectionIndexes(
            final ClientSession cs,
            final String dbName,
            final String collectionName);

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collection
     * @param keys
     * @param options
     */
    void createIndex(
            final ClientSession cs,
            final String dbName,
            final String collection,
            final BsonDocument keys,
            final BsonDocument options);

    /**
     * Returs the FindIterable of the collection applying sorting, filtering and
     * projection.
     *
     * @param cs the client session
     * @param collection the mongodb MongoCollection<BsonDocument> object
     * @param sortBy the Deque collection of fields to use for sorting (prepend
     * field name with - for descending sorting)
     * @param filters the filters to apply. it is a Deque collection of mongodb
     * query conditions.
     * @param hint the index hint to apply.
     * @param keys
     * @return
     * @throws JsonParseException
     */
    FindIterable<BsonDocument> getFindIterable(
            final ClientSession cs,
            final MongoCollection<BsonDocument> collection,
            final BsonDocument sortBy,
            final BsonDocument filters,
            final BsonDocument hint,
            final BsonDocument keys)
            throws JsonParseException;
}
