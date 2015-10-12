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

import org.restheart.utils.HttpStatus;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.injectors.LocalCachesSingleton;
import org.restheart.utils.ResponseHelper;

import io.undertow.server.HttpServerExchange;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DbsDAO implements Database {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbsDAO.class);
    public static final BasicDBObject PROPS_QUERY = new BasicDBObject("_id", "_properties");

    private static final BasicDBObject FIELDS_TO_RETURN;

    static {
        FIELDS_TO_RETURN = new BasicDBObject();
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
     * WARNING: slow method.
     *
     * @param exchange
     * @param dbName
     * @return
     * @deprecated
     *
     */
    @Override
    public boolean checkDbExists(HttpServerExchange exchange, String dbName) {
        if (!existsDatabaseWithName(dbName)) {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return false;
        }
        return true;
    }

    /**
     * WARNING: slow method.
     *
     * @param dbName
     * @return
     *
     */
    @Override
    public boolean existsDatabaseWithName(String dbName) {
        return client.getDatabaseNames().contains(dbName);
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
        List<String> _colls = new ArrayList(db.getCollectionNames());

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
        if (!existsDatabaseWithName(dbName)) {
            // this check is important, otherwise the db would get created if not existing after the query
            return null;
        }

        DBCollection propsColl = collectionDAO.getCollection(dbName, "_properties");

        DBObject row = propsColl.findOne(PROPS_QUERY);

        if (row != null) {
            row.put("_id", dbName);
        } else if (fixMissingProperties) {
            new PropsFixer().addDbProps(dbName);
            return getDatabaseProperties(dbName, false);
        }

        return row;
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
    public OperationResult upsertDB(
            final String dbName,
            final DBObject newContent,
            final ObjectId requestEtag,
            final boolean patching) {

        DB db = client.getDB(dbName);

        boolean existing = db.getCollectionNames().size() > 0;

        if (patching && !existing) {
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        }

        // check the etag
        DBCollection coll = db.getCollection("_properties");

        DBObject exists = coll.findOne(new BasicDBObject("_id", "_properties"), FIELDS_TO_RETURN);

        if (exists != null) {
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

        final DBObject content = DAOUtils.validContent(newContent);

        content.put("_etag", newEtag);
        content.removeField("_id"); // make sure we don't change this field

        if (patching) {
            coll.update(PROPS_QUERY, new BasicDBObject("$set", content), true, false);
        } else {
            coll.update(PROPS_QUERY, content, true, false);
        }
        
        return new OperationResult(HttpStatus.SC_OK, newEtag);
    }


    /**
     *
     * @param dbName
     * @param requestEtag
     * @return
     */
    @Override
    public OperationResult deleteDatabase(final String dbName, final ObjectId requestEtag) {
        DB db = getDB(dbName);

        DBCollection coll = db.getCollection("_properties");

        BasicDBObject checkEtag = new BasicDBObject("_id", "_properties");

        DBObject exists = coll.findOne(checkEtag, FIELDS_TO_RETURN);

        if (exists != null) {
            Object oldEtag = exists.get("_etag");

            if (oldEtag != null && requestEtag == null) {
                return new OperationResult(HttpStatus.SC_CONFLICT, oldEtag);
            }

            if (requestEtag.equals(oldEtag)) {
                db.dropDatabase();
                return new OperationResult(HttpStatus.SC_NO_CONTENT);
            } else {
                return new OperationResult(HttpStatus.SC_PRECONDITION_FAILED, oldEtag);
            }
        } else {
            LOGGER.error("cannot find _properties for db " + dbName);
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        }
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
    public OperationResult upsertCollection(String dbName, String collName, DBObject content, ObjectId etag, boolean updating, boolean patching) {
        return collectionDAO.upsertCollection(dbName, collName, content, etag, updating, patching);
    }

    @Override
    public OperationResult deleteCollection(String dbName, String collectionName, ObjectId etag) {
        return collectionDAO.deleteCollection(dbName, collectionName, etag);
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
        return client.getDatabaseNames();
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
