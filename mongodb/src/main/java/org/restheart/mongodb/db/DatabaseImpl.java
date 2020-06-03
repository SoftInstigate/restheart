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

import com.mongodb.MongoClient;
import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import static org.restheart.exchange.ExchangeKeys.DB_META_DOCID;
import org.restheart.exchange.ExchangeKeys.EAGER_CURSOR_ALLOCATION_POLICY;
import static org.restheart.exchange.ExchangeKeys.META_COLLNAME;
import org.restheart.exchange.IllegalQueryParamenterException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.OperationResult;
import org.restheart.mongodb.interceptors.MetadataCachesSingleton;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DatabaseImpl implements Database {

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

    /**
     * delegated object for collection operations
     */
    private final CollectionDAO collectionDAO;
    private final IndexDAO indexDAO;

    private final MongoClient client;

    /**
     *
     */
    public DatabaseImpl() {
        client = MongoClientSingleton.getInstance().getClient();
        this.collectionDAO = new CollectionDAO(client);
        this.indexDAO = new IndexDAO(client);
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @return
     *
     */
    @Override
    public boolean doesDbExist(
            final ClientSession cs,
            final String dbName) {
        // at least one collection exists for an existing db
        return cs == null
                ? client.getDatabase(dbName)
                        .listCollectionNames()
                        .first() != null
                : client.getDatabase(dbName)
                        .listCollectionNames(cs)
                        .first() != null;
    }

    /**
     * Returns true if the collection exists
     *
     * @param cs the client session
     * @param dbName the database name of the collection
     * @param collName the collection name
     * @return true if the collection exists
     */
    @Override
    public boolean doesCollectionExist(
            final ClientSession cs,
            final String dbName,
            final String collName) {
        return collectionDAO.doesCollectionExist(cs, dbName, collName);
    }

    /**
     *
     * @param dbName
     * @return the MongoDatabase
     */
    @Override
    public MongoDatabase getDatabase(
            final String dbName) {
        return client.getDatabase(dbName);
    }

    /**
     *
     * @param cs the client session
     * @param dbName the database name of the collection
     * @return A ordered List of collection names
     */
    @Override
    public List<String> getCollectionNames(
            final ClientSession cs,
            final String dbName) {
        MongoDatabase db = getDatabase(dbName);

        List<String> _colls = new ArrayList<>();

        if (cs == null) {
            db.listCollectionNames().into(_colls);
        } else {
            db.listCollectionNames(cs).into(_colls);
        }

        // filter out reserved collections
        return _colls.stream().filter(coll -> !MongoRequest
                .isReservedCollectionName(coll))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * @param colls the collections list got from getCollectionNames()
     * @return the number of collections in this db
     *
     */
    @Override
    public long getDBSize(final List<String> colls) {
        // filter out reserved resources
        List<String> _colls = colls.stream()
                .filter(coll -> !MongoRequest
                .isReservedCollectionName(coll))
                .collect(Collectors.toList());

        return _colls.size();
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @return the db props
     *
     */
    @Override
    public BsonDocument getDatabaseProperties(
            final ClientSession cs, final String dbName) {
        var propsColl = getCollection(dbName, META_COLLNAME);

        BsonDocument props = cs == null
                ? propsColl.find(PROPS_QUERY).limit(1).first()
                : propsColl.find(cs, PROPS_QUERY).limit(1).first();

        if (props != null) {
            props.append("_id", new BsonString(dbName));
        } else if (doesDbExist(cs, dbName)) {
            return new BsonDocument("_id", new BsonString(dbName));
        }

        return props;
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param colls the collections list as got from getCollectionNames()
     * @param page
     * @param pagesize
     * @return the db data
     * @throws org.restheart.exchange.IllegalQueryParamenterException
     *
     */
    @Override
    public BsonArray getDatabaseData(
            final ClientSession cs,
            final String dbName,
            final List<String> colls,
            final int page,
            final int pagesize,
            boolean noCache)
            throws IllegalQueryParamenterException {
        // filter out reserved resources
        List<String> _colls = colls.stream()
                .filter(coll -> !MongoRequest
                .isReservedCollectionName(coll))
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
        _colls = _colls
                .subList(
                        (page - 1) * pagesize,
                        (page - 1) * pagesize + pagesize > _colls.size()
                        ? _colls.size()
                        : (page - 1) * pagesize + pagesize);

        var data = new BsonArray();

        _colls.stream().map((collName) -> {
            BsonDocument properties
                    = new BsonDocument("_id", new BsonString(collName));

            BsonDocument collProperties;

            if (MetadataCachesSingleton.isEnabled() && !noCache) {
                collProperties = MetadataCachesSingleton.getInstance()
                        .getCollectionProperties(dbName, collName);
            } else {
                collProperties = collectionDAO.getCollectionProps(
                        cs,
                        dbName,
                        collName);
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
     * @param dbName
     * @param newContent
     * @param requestEtag
     * @param patching
     * @return
     */
    @Override
    @SuppressWarnings("unchecked")
    public OperationResult upsertDB(
            final ClientSession cs,
            final String dbName,
            final BsonDocument newContent,
            final String requestEtag,
            final boolean updating,
            final boolean patching,
            final boolean checkEtag) {

        if (patching && !updating) {
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        }

        ObjectId newEtag = new ObjectId();

        final BsonDocument content = DAOUtils.validContent(newContent);

        content.put("_etag", new BsonObjectId(newEtag));
        content.remove("_id"); // make sure we don't change this field

        var mcoll = getCollection(dbName, META_COLLNAME);

        if (checkEtag && updating) {
            BsonDocument oldProperties = cs == null
                    ? mcoll.find(eq("_id", DB_META_DOCID))
                            .projection(FIELDS_TO_RETURN).first()
                    : mcoll.find(cs, eq("_id", DB_META_DOCID))
                            .projection(FIELDS_TO_RETURN).first();

            if (oldProperties != null) {
                BsonValue oldEtag = oldProperties.get("_etag");

                if (oldEtag != null
                        && requestEtag == null) {
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
                            patching,
                            updating,
                            mcoll,
                            content,
                            newEtag);
                } else {
                    return new OperationResult(
                            HttpStatus.SC_PRECONDITION_FAILED, oldEtag);
                }
            } else {
                // this is the case when the db does not have properties
                // e.g. it has not been created by restheart
                return doDbPropsUpdate(
                        cs,
                        patching,
                        updating,
                        mcoll,
                        content,
                        newEtag);
            }
        } else {
            return doDbPropsUpdate(
                    cs,
                    patching,
                    updating,
                    mcoll,
                    content,
                    newEtag);
        }
    }

    private OperationResult doDbPropsUpdate(
            final ClientSession cs,
            final boolean patching,
            final boolean updating,
            final MongoCollection<BsonDocument> mcoll,
            final BsonDocument dcontent,
            final ObjectId newEtag) {
        if (patching) {
            OperationResult ret = DAOUtils.updateDocument(
                    cs,
                    mcoll,
                    DB_META_DOCID,
                    null,
                    null,
                    dcontent,
                    false);
            return new OperationResult(ret.getHttpCode() > 0
                    ? ret.getHttpCode()
                    : HttpStatus.SC_OK, newEtag);
        } else if (updating) {
            OperationResult ret = DAOUtils.updateDocument(
                    cs,
                    mcoll,
                    DB_META_DOCID,
                    null,
                    null,
                    dcontent,
                    true);
            return new OperationResult(ret.getHttpCode() > 0
                    ? ret.getHttpCode()
                    : HttpStatus.SC_OK, newEtag);
        } else {
            OperationResult ret = DAOUtils.updateDocument(
                    cs,
                    mcoll,
                    DB_META_DOCID,
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
     *
     * @param cs the client session
     * @param dbName
     * @param requestEtag
     * @param checkEtag
     * @return
     */
    @Override
    public OperationResult deleteDatabase(
            final ClientSession cs,
            final String dbName,
            final BsonObjectId requestEtag,
            final boolean checkEtag) {
        var mcoll = getCollection(dbName, META_COLLNAME);

        if (checkEtag) {
            var query = eq("_id", DB_META_DOCID);
            var properties = cs == null
                    ? mcoll.find(query)
                            .projection(FIELDS_TO_RETURN).first()
                    : mcoll.find(cs, query)
                            .projection(FIELDS_TO_RETURN).first();

            if (properties != null) {
                var oldEtag = properties.get("_etag");

                if (oldEtag != null) {
                    if (requestEtag == null) {
                        return new OperationResult(
                                HttpStatus.SC_CONFLICT, oldEtag);
                    } else if (!requestEtag.equals(oldEtag)) {
                        return new OperationResult(
                                HttpStatus.SC_PRECONDITION_FAILED,
                                oldEtag);
                    }
                }
            }
        }

        if (cs == null) {
            getDatabase(dbName).drop();
        } else {
            getDatabase(dbName).drop(cs);
        }

        return new OperationResult(HttpStatus.SC_NO_CONTENT);
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @return
     */
    @Override
    public BsonDocument getCollectionProperties(
            final ClientSession cs,
            final String dbName,
            final String collName) {
        return collectionDAO.getCollectionProps(
                cs,
                dbName,
                collName);
    }

    /**
     *
     * @param dbName
     * @param collName
     * @return
     */
    @Override
    public MongoCollection<BsonDocument> getCollection(
            final String dbName,
            final String collName) {
        return collectionDAO.getCollection(dbName, collName);
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param content
     * @param requestEtag
     * @param updating
     * @param patching
     * @param checkEtag
     * @return
     */
    @Override
    public OperationResult upsertCollection(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final BsonDocument content,
            final String requestEtag,
            final boolean updating,
            final boolean patching,
            final boolean checkEtag) {
        return collectionDAO.upsertCollection(
                cs,
                dbName,
                collName,
                content,
                requestEtag,
                updating,
                patching,
                checkEtag);
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collectionName
     * @param requestEtag
     * @param checkEtag
     * @return
     */
    @Override
    public OperationResult deleteCollection(
            final ClientSession cs,
            final String dbName,
            final String collectionName,
            final BsonObjectId requestEtag,
            final boolean checkEtag) {
        return collectionDAO.deleteCollection(
                cs,
                dbName,
                collectionName,
                requestEtag,
                checkEtag);
    }

    /**
     *
     * @param cs the client session
     * @param coll
     * @param filters
     * @return
     */
    @Override
    public long getCollectionSize(
            final ClientSession cs,
            final MongoCollection<BsonDocument> coll,
            final BsonDocument filters) {
        return collectionDAO.getCollectionSize(cs, coll, filters);
    }

    /**
     *
     * @param cs the client session
     * @param coll
     * @param page
     * @param pagesize
     * @param sortBy
     * @param filter
     * @param hint
     * @param keys
     * @param cursorAllocationPolicy
     * @return
     */
    @Override
    public BsonArray getCollectionData(
            final ClientSession cs,
            final MongoCollection<BsonDocument> coll,
            final int page,
            final int pagesize,
            final BsonDocument sortBy,
            final BsonDocument filter,
            final BsonDocument hint,
            final BsonDocument keys,
            final EAGER_CURSOR_ALLOCATION_POLICY cursorAllocationPolicy) {
        return collectionDAO.getCollectionData(
                cs,
                coll,
                page,
                pagesize,
                sortBy,
                filter,
                hint,
                keys,
                cursorAllocationPolicy);
    }

    /**
     *
     * @param cs the client session
     * @return
     */
    @Override
    public List<String> getDatabaseNames(final ClientSession cs) {
        ArrayList<String> dbNames = new ArrayList<>();

        if (cs == null) {
            client.listDatabaseNames().into(dbNames);
        } else {
            client.listDatabaseNames(cs).into(dbNames);
        }

        return dbNames;
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collection
     * @param indexId
     * @return
     */
    @Override
    public int deleteIndex(
            final ClientSession cs,
            final String dbName,
            final String collection,
            final String indexId) {
        return indexDAO.deleteIndex(cs, dbName, collection, indexId);
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collectionName
     * @return
     */
    @Override
    public List<BsonDocument> getCollectionIndexes(
            final ClientSession cs,
            final String dbName,
            final String collectionName) {
        return indexDAO.getCollectionIndexes(cs, dbName, collectionName);
    }

    /**
     *
     * @param cs the client session
     * @param collection
     * @param sortBy
     * @param filters
     * @param hint
     * @param keys
     * @return
     */
    @Override
    public FindIterable<BsonDocument> getFindIterable(
            final ClientSession cs,
            final MongoCollection<BsonDocument> collection,
            final BsonDocument sortBy,
            final BsonDocument filters,
            final BsonDocument hint,
            final BsonDocument keys) {
        return collectionDAO.getFindIterable(
                cs,
                collection,
                sortBy,
                filters,
                hint,
                keys);
    }

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collection
     * @param keys
     * @param options
     */
    @Override
    public void createIndex(
            final ClientSession cs,
            final String dbName,
            final String collection,
            final BsonDocument keys,
            final BsonDocument options) {
        indexDAO.createIndex(cs, dbName, collection, keys, options);
    }
}
