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

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.UpdateOptions;

import org.restheart.utils.HttpStatus;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.injectors.LocalCachesSingleton;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.bson.BsonDocument;
import org.bson.Document;

import org.bson.types.ObjectId;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DbsDAO implements Database {

    public static final BasicDBObject PROPS_QUERY_LEGACY = new BasicDBObject("_id", "_properties");
    public static final Document PROPS_QUERY = new Document("_id", "_properties");

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

    public DbsDAO() {
        client = MongoDBClientSingleton.getInstance().getClient();
        this.collectionDAO = new CollectionDAO(client);
        this.indexDAO = new IndexDAO(client);
    }

    /**
     *
     * @param dbName
     * @return
     *
     */
    @Override
    public boolean doesDbExist(String dbName) {
        // at least one collection exists for an existing db
        return client
                .getDatabase(dbName)
                .listCollectionNames()
                .first() != null;
    }

    /**
     *
     * @param dbName
     * @return
     *
     */
    @Override
    public boolean doesCollectionExist(String dbName, String collName) {
        // at least one collection exists for an existing db
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
     *
     * @param dbName
     * @return
     */
    @Override
    public DB getDB(String dbName) {
        return client.getDB(dbName);
    }

    /**
     *
     * @param db
     * @return A ordered List of collection names
     */
    @Override
    public List<String> getCollectionNames(final DB db) {
        List<String> _colls = new ArrayList<>(db.getCollectionNames());

        // filter out reserved dbs
        return _colls.stream().filter(coll -> !RequestContext.isReservedResourceCollection(coll)).sorted().collect(Collectors.toList());
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
                .filter(coll -> !RequestContext.isReservedResourceCollection(coll))
                .collect(Collectors.toList());

        return _colls.size();
    }

    /**
     * @param dbName
     * @param fixMissingProperties
     * @return the db props
     *
     */
    @Override
    public DBObject getDatabaseProperties(final String dbName, final boolean fixMissingProperties) {
        if (!doesDbExist(dbName)) {
            // this check is important, otherwise the db would get created if not existing after the query
            return null;
        }

        DBCollection propsColl = collectionDAO.getCollection(dbName, "_properties");

        DBObject properties = propsColl.findOne(PROPS_QUERY_LEGACY);

        if (properties != null) {
            properties.put("_id", dbName);
        } else if (fixMissingProperties) {
            try {
                new PropsFixer().addDbProps(dbName);
                return getDatabaseProperties(dbName, false);
            } catch (MongoException mce) {
                int errCode = mce.getCode();

                if (errCode == 13) {
                    // mongodb user is not allowed to write (no readWrite role)
                    // just return properties with _id
                    properties = new BasicDBObject("_id", dbName);
                } else {
                    throw mce;
                }
            }
        }

        return properties;
    }

    /**
     * @param dbName
     * @param colls the collections list as got from getCollectionNames()
     * @param page
     * @param pagesize
     * @return the db data
     * @throws org.restheart.handlers.IllegalQueryParamenterException
     *
     */
    @Override
    public List<DBObject> getData(final String dbName, final List<String> colls, final int page, final int pagesize)
            throws IllegalQueryParamenterException {
        // filter out reserved resources
        List<String> _colls = colls.stream()
                .filter(coll -> !RequestContext.isReservedResourceCollection(coll))
                .collect(Collectors.toList());

        int size = _colls.size();

        // *** arguments check
        long total_pages;

        if (size > 0) {
            float _size = size + 0f;
            float _pagesize = pagesize + 0f;

            total_pages = Math.max(1, Math.round(Math.ceil(_size / _pagesize)));

            if (page > total_pages) {
                throw new IllegalQueryParamenterException("illegal query paramenter,"
                        + " page is bigger that total pages which is " + total_pages);
            }
        }

        // apply page and pagesize
        _colls = _colls.subList((page - 1) * pagesize, (page - 1) * pagesize + pagesize > _colls.size()
                ? _colls.size()
                : (page - 1) * pagesize + pagesize);

        List<DBObject> data = new ArrayList<>();

        _colls.stream().map(
                (collName) -> {
                    BasicDBObject properties = new BasicDBObject();

                    properties.put("_id", collName);

                    DBObject collProperties;

                    if (LocalCachesSingleton.isEnabled()) {
                        collProperties = LocalCachesSingleton.getInstance()
                        .getCollectionProps(dbName, collName);
                    } else {
                        collProperties = collectionDAO.getCollectionProps(dbName, collName, true);
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
     * @param dbName
     * @param newContent
     * @param requestEtag
     * @param patching
     * @return
     */
    @Override
    @SuppressWarnings("unchecked")
    public OperationResult upsertDB(
            final String dbName,
            final DBObject newContent,
            final String requestEtag,
            final boolean updating,
            final boolean patching,
            final boolean checkEtag) {

        if (patching && !updating) {
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        }

        ObjectId newEtag = new ObjectId();

        final DBObject content = DAOUtils.validContent(newContent);

        content.put("_etag", newEtag);
        content.removeField("_id"); // make sure we don't change this field

        //TODO remove this after migration to mongodb driver 3.2 completes
        Document dcontent = new Document(content.toMap());

        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<Document> mcoll = mdb.getCollection("_properties");

        if (checkEtag && updating) {
            Document oldProperties = mcoll.find(eq("_id", "_properties"))
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
                    return doDbPropsUpdate(patching, updating, mcoll, dcontent, newEtag);
                } else {
                    return new OperationResult(HttpStatus.SC_PRECONDITION_FAILED, oldEtag);
                }
            } else {
                // this is the case when the db does not have properties
                // e.g. it has not been created by restheart
                return doDbPropsUpdate(patching, updating, mcoll, dcontent, newEtag);
            }
        } else {
            return doDbPropsUpdate(patching, updating, mcoll, dcontent, newEtag);
        }
    }

    private OperationResult doDbPropsUpdate(boolean patching, boolean updating, MongoCollection<Document> mcoll, Document dcontent, ObjectId newEtag) {
        if (patching) {
            DAOUtils.updateDocument(mcoll, "_properties", null, dcontent, false);
            return new OperationResult(HttpStatus.SC_OK, newEtag);
        } else if (updating) {
            DAOUtils.updateDocument(mcoll, "_properties", null, dcontent, true);
            return new OperationResult(HttpStatus.SC_OK, newEtag);
        } else {
            DAOUtils.updateDocument(mcoll, "_properties", null, dcontent, false);
            return new OperationResult(HttpStatus.SC_CREATED, newEtag);
        }
    }

    /**
     *
     * @param dbName
     * @param requestEtag
     * @return
     */
    @Override
    public OperationResult deleteDatabase(final String dbName, final String requestEtag, final boolean checkEtag) {
        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<Document> mcoll = mdb.getCollection("_properties");

        if (checkEtag) {
            Document properties = mcoll.find(eq("_id", "_properties"))
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

        mdb.drop();
        return new OperationResult(HttpStatus.SC_NO_CONTENT);
    }

    @Override
    public DBObject getCollectionProperties(String dbName, String collName, boolean fixMissingProperties) {
        return collectionDAO.getCollectionProps(dbName, collName, fixMissingProperties);
    }

    @Override
    public DBCollection getCollection(String dbName, String collName) {
        return collectionDAO.getCollection(dbName, collName);
    }

    @Override
    public MongoCollection<BsonDocument> getMongoCollection(String dbName, String collName) {
        MongoDatabase mdb = client.getDatabase(dbName);
        MongoCollection<BsonDocument> mcoll = mdb.getCollection(collName, BsonDocument.class);
        return mcoll;
    }

    @Override
    public OperationResult upsertCollection(String dbName, String collName, DBObject content, String requestEtag, boolean updating, boolean patching, final boolean checkEtag) {
        return collectionDAO.upsertCollection(dbName, collName, content, requestEtag, updating, patching, checkEtag);
    }

    @Override
    public OperationResult deleteCollection(String dbName, String collectionName, String requestEtag, final boolean checkEtag) {
        return collectionDAO.deleteCollection(dbName, collectionName, requestEtag, checkEtag);
    }

    @Override
    public long getCollectionSize(DBCollection coll, Deque<String> filters) {
        return collectionDAO.getCollectionSize(coll, filters);
    }

    @Override
    public ArrayList<DBObject> getCollectionData(DBCollection coll, int page, int pagesize, Deque<String> sortBy, Deque<String> filter, Deque<String> keys, DBCursorPool.EAGER_CURSOR_ALLOCATION_POLICY cursorAllocationPolicy) {
        return collectionDAO.getCollectionData(coll, page, pagesize, sortBy, filter, keys, cursorAllocationPolicy);
    }

    @Override
    public List<String> getDatabaseNames() {
        ArrayList<String> dbNames = new ArrayList<>();

        client.listDatabaseNames().into(dbNames);

        return dbNames;
    }

    @Override
    public int deleteIndex(String dbName, String collection, String indexId) {
        return indexDAO.deleteIndex(dbName, collection, indexId);
    }

    @Override
    public List<DBObject> getCollectionIndexes(String dbName, String collectionName) {
        return indexDAO.getCollectionIndexes(dbName, collectionName);
    }

    @Override
    public DBCursor getCollectionDBCursor(DBCollection collection, Deque<String> sortBy, Deque<String> filters, Deque<String> keys) {
        return collectionDAO.getCollectionDBCursor(collection, sortBy, filters, keys);
    }

    @Override
    public void createIndex(String dbName, String collection, DBObject keys, DBObject options) {
        indexDAO.createIndex(dbName, collection, keys, options);
    }
}
