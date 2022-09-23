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
import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;
import static org.restheart.exchange.ExchangeKeys.DB_META_DOCID;

import org.restheart.exchange.ExchangeKeys.EAGER_CURSOR_ALLOCATION_POLICY;
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.ExchangeKeys.WRITE_MODE;

import static org.restheart.exchange.ExchangeKeys.META_COLLNAME;
import org.restheart.exchange.IllegalQueryParamenterException;
import org.restheart.exchange.MongoRequest;
import org.restheart.mongodb.RHMongoClients;
import org.restheart.mongodb.RSOps;
import org.restheart.mongodb.interceptors.MetadataCachesSingleton;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Databases {

    /**
     *
     */
    public static final Bson PROPS_QUERY = eq("_id", DB_META_DOCID);

    private static final Document FIELDS_TO_RETURN;

    static {
        FIELDS_TO_RETURN = new Document();
        FIELDS_TO_RETURN.put("_id", 1);
        FIELDS_TO_RETURN.put("_etag", 1);
    }

    private final MongoClient client = RHMongoClients.mclient();

    /**
     * delegated object for collection operations
     */
    private final Collections collections;

    /**
     * delegated object for indexes operations
     */
    private final Indexes indexes;

    private Databases() {
        this.collections = Collections.get();
        this.indexes  = Indexes.get();
    }

    private static Databases INSTANCE = new Databases();

    public static Databases get() {
        return INSTANCE;
    }

    /**
     *
     * @param rsOps the ReplicaSet connection options
     * @param dbName the name of the db
     * @return the MongoDatabase
     */
    MongoDatabase db(Optional<RSOps> rsOps, String dbName) {
        return rsOps.isPresent() ? rsOps.get().apply(client.getDatabase(dbName)) : client.getDatabase(dbName);
    }

    /**
     * Returns true if the collection exists
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the name of the db
     *
     * @return true if the db exists
     */
    public boolean doesDbExist(final Optional<ClientSession> cs, Optional<RSOps> rsOps, String dbName) {
        var db = db(rsOps, dbName);
        // at least one collection exists for an existing db
        return cs.isPresent()
            ? db.listCollectionNames(cs.get()).first() != null
            : db.listCollectionNames().first() != null;
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @return A ordered List of collection names
     */
    public List<String> getCollectionNames(final Optional<ClientSession> cs, Optional<RSOps> rsOps, final String dbName) {
        var db = db(rsOps, dbName);

        var _colls = new ArrayList<String>();

        if (cs.isPresent()) {
            db.listCollectionNames(cs.get()).into(_colls);
        } else {
            db.listCollectionNames().into(_colls);
        }

        // filter out reserved collections
        return _colls.stream()
            .filter(coll -> !MongoRequest.isReservedCollectionName(coll))
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * @param colls the collections list got from getCollectionNames()
     * @return the number of collections in this db
     *
     */
    public long getDBSize(final List<String> colls) {
        // filter out reserved resources
        var _colls = colls.stream()
            .filter(coll -> !MongoRequest
            .isReservedCollectionName(coll))
            .collect(Collectors.toList());

        return _colls.size();
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @return the db props
     *
     */
    public BsonDocument getDatabaseProperties(final Optional<ClientSession> cs, Optional<RSOps> rsOps, final String dbName) {
        var propsColl = collections.collection(rsOps, dbName, META_COLLNAME);

        var props = cs.isPresent()
            ? propsColl.find(cs.get(), PROPS_QUERY).limit(1).first()
            : propsColl.find(PROPS_QUERY).limit(1).first();

        if (props != null) {
            props.append("_id", new BsonString(dbName));
        } else if (doesDbExist(cs, rsOps, dbName)) {
            return new BsonDocument("_id", new BsonString(dbName));
        }

        return props;
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param colls the collections list as got from getCollectionNames()
     * @param page
     * @param pagesize
     * @param noCache
     * @return the db data
     * @throws org.restheart.exchange.IllegalQueryParamenterException
     *
     */
    public BsonArray getDatabaseData(
        final Optional<ClientSession> cs,
        Optional<RSOps> rsOps,
        final String dbName,
        final List<String> colls,
        final int page,
        final int pagesize,
        boolean noCache) throws IllegalQueryParamenterException {
        // filter out reserved resources
        var _colls = colls.stream()
            .filter(coll -> !MongoRequest.isReservedCollectionName(coll))
            .collect(Collectors.toList());

        int size = _colls.size();

        // *** arguments check
        long total_pages;

        if (size > 0) {
            float _size = size + 0f;
            float _pagesize = pagesize + 0f;

            total_pages = Math.max(1, Math.round(Math.ceil(_size / _pagesize)));

            if (page > total_pages) {
                return new BsonArray();
            }
        }

        // apply page and pagesize
        _colls = _colls.subList((page - 1) * pagesize, (page - 1) * pagesize + pagesize > _colls.size() ? _colls.size() : (page - 1) * pagesize + pagesize);

        var data = new BsonArray();

        _colls.stream().map((collName) -> {
            var properties= new BsonDocument("_id", new BsonString(collName));

            BsonDocument collProperties;

            if (MetadataCachesSingleton.isEnabled() && !noCache) {
                collProperties = MetadataCachesSingleton.getInstance().getCollectionProperties(dbName, collName);
            } else {
                collProperties = collections.getCollectionProps(cs, rsOps, dbName, collName);
            }

            if (collProperties != null) {
                properties.putAll(collProperties);
            }

            return properties;
        }
        ).forEach((item) -> {
            data.add(item);
        });

        return data;
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param method
     * @param updating
     * @param newContent
     * @param requestEtag
     * @return the OperationResult
     */
    public OperationResult upsertDB(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final METHOD method,
        final boolean updating,
        final BsonDocument newContent,
        final String requestEtag,
        final boolean checkEtag) {
        var newEtag = new ObjectId();

        final BsonDocument content = DbUtils.validContent(newContent);

        content.put("_etag", new BsonObjectId(newEtag));
        content.remove("_id"); // make sure we don't change this field

        var mcoll = collections.collection(rsOps, dbName, META_COLLNAME);

        if (checkEtag && updating) {
            var oldProperties = cs.isPresent()
                ? mcoll.find(cs.get(), eq("_id", DB_META_DOCID)).projection(FIELDS_TO_RETURN).first()
                : mcoll.find(eq("_id", DB_META_DOCID)).projection(FIELDS_TO_RETURN).first();

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
                    return doDbPropsUpdate(
                        cs,
                        rsOps,
                        method,
                        updating,
                        mcoll,
                        content,
                        newEtag);
                } else {
                    return new OperationResult(HttpStatus.SC_PRECONDITION_FAILED, oldEtag);
                }
            } else {
                // this is the case when the db does not have properties
                // e.g. it has not been created by restheart
                return doDbPropsUpdate(
                    cs,
                    rsOps,
                    method,
                    updating,
                    mcoll,
                    content,
                    newEtag);
            }
        } else {
            return doDbPropsUpdate(
                cs,
                rsOps,
                method,
                updating,
                mcoll,
                content,
                newEtag);
        }
    }

    private OperationResult doDbPropsUpdate(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final METHOD method,
        final boolean updating,
        final MongoCollection<BsonDocument> mcoll,
        final BsonDocument dcontent,
        final ObjectId newEtag) {
        var ret = DbUtils.writeDocument(
            cs,
            method,
            WRITE_MODE.UPSERT,
            mcoll,
            Optional.of(new BsonString(DB_META_DOCID)),
            Optional.empty(),
            Optional.empty(),
            dcontent);
        return new OperationResult(ret.getHttpCode() > 0 ? ret.getHttpCode() : updating ? HttpStatus.SC_OK : HttpStatus.SC_CREATED, newEtag);
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param requestEtag
     * @param checkEtag
     * @return the OperationResult
     */
    public OperationResult deleteDatabase(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final BsonObjectId requestEtag,
        final boolean checkEtag) {
        var db = db(rsOps, dbName);
        var mcoll = collections.collection(rsOps, dbName, META_COLLNAME);

        if (checkEtag) {
            var query = eq("_id", DB_META_DOCID);
            var properties = cs.isPresent()
                ? mcoll.find(cs.get(), query).projection(FIELDS_TO_RETURN).first()
                : mcoll.find(query).projection(FIELDS_TO_RETURN).first();

            if (properties != null) {
                var oldEtag = properties.get("_etag");

                if (oldEtag != null) {
                    if (requestEtag == null) {
                        return new OperationResult(HttpStatus.SC_CONFLICT, oldEtag);
                    } else if (!requestEtag.equals(oldEtag)) {
                        return new OperationResult(HttpStatus.SC_PRECONDITION_FAILED, oldEtag);
                    }
                }
            }
        }

        if (cs.isPresent()) {
            db.drop(cs.get());
        } else {
            db.drop();
        }

        return new OperationResult(HttpStatus.SC_NO_CONTENT);
    }

    /**
     *
     * @param cs the client session
     * @return and ordered list of the databases
     */
    public List<String> getDatabaseNames(final Optional<ClientSession> cs) {
        var dbNames = new ArrayList<String>();

        if (cs.isPresent()) {
            client.listDatabaseNames(cs.get()).into(dbNames);
        } else {
            client.listDatabaseNames().into(dbNames);
        }

        return dbNames;
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName
     * @return the collection properties
     */
    public BsonDocument getCollectionProperties(final Optional<ClientSession> cs, final Optional<RSOps> rsOps, final String dbName, final String collName) {
        return collections.getCollectionProps(cs, rsOps, dbName, collName);
    }

    /**
     *
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName
     * @return the MongoCollection<BsonDocument>
     */
    public MongoCollection<BsonDocument> collection(Optional<RSOps> rsOps, String dbName, final String collName) {
        return collections.collection(rsOps, dbName, collName);
    }

    /**
     * Returns the number of documents in the given collection (taking into
     * account the filters in case).
     *
     * @param cs the ClientSession
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName the collection name
     * @param filters the filters to apply. it is a Deque collection of mongodb
     * query conditions.
     * @return the number of documents in the given collection (taking into
     * account the filters in case)
     */
    public long getCollectionSize(final Optional<ClientSession> cs, Optional<RSOps> rsOps, String dbName, final String collName, BsonDocument filter) {
        return collections.getCollectionSize(cs, rsOps, dbName, collName, filter);
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
    public BsonArray getCollectionData(
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
        final EAGER_CURSOR_ALLOCATION_POLICY eager)
        throws JsonParseException {
        return collections.getCollectionData(cs, rsOps, dbName, collName, page, pagesize, sortBy, filters, hint, keys, eager);
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName the collection name
     * @param requestEtag
     * @param checkEtag
     * @return the OperationResult
     */
    public OperationResult deleteCollection(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName,
        final BsonObjectId requestEtag,
        final boolean checkEtag) {
        return collections.deleteCollection(
            cs,
            rsOps,
            dbName,
            collName,
            requestEtag,
            checkEtag);
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param method the request method
     * @param updating true if updating, false if creating
     * @param collName
     * @param content
     * @param requestEtag
     * @param checkEtag
     * @return the OperationResult
     */
    public OperationResult upsertCollection(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName,
        final METHOD method,
        final boolean updating,
        final BsonDocument content,
        final String requestEtag,
        final boolean checkEtag) {
        return collections.upsertCollection(
            cs,
            rsOps,
            dbName,
            collName,
            method,
            updating,
            content,
            requestEtag,
            checkEtag);
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collection
     * @param indexId
     * @return the HTTP status code
     */
    public int deleteIndex(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collection,
        final String indexId) {
        return indexes.deleteIndex(cs, rsOps, dbName, collection, indexId);
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collectionName
     * @return an ordered list of the indexes
     */
    public List<BsonDocument> getCollectionIndexes(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collectionName) {
        return indexes.getCollectionIndexes(cs, rsOps, dbName, collectionName);
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName the collection name
     * @param sortBy
     * @param filters
     * @param hint
     * @param keys
     * @return the FindIterable
     */
    public FindIterable<BsonDocument> findIterable(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName,
        final BsonDocument sortBy,
        final BsonDocument filters,
        final BsonDocument hint,
        final BsonDocument keys) {
        return collections.findIterable(
            cs,
            collections.collection(rsOps, dbName, collName),
            sortBy,
            filters,
            hint,
            keys);
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param collName the collection name
     * @param keys
     * @param options
     */
    public void createIndex(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName,
        final BsonDocument keys,
        final Optional<BsonDocument> options) {
        indexes.createIndex(cs, rsOps, dbName, collName, keys, options);
    }
}
