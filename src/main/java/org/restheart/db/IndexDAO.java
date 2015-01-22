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
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import org.restheart.utils.HttpStatus;
import java.util.List;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class IndexDAO {

    private final MongoClient client;

    public static final BasicDBObject METADATA_QUERY = new BasicDBObject("_id", "_properties");

    private static final BasicDBObject fieldsToReturnIndexes;

    static {
        fieldsToReturnIndexes = new BasicDBObject();
        fieldsToReturnIndexes.put("key", 1);
        fieldsToReturnIndexes.put("name", 1);
    }

    public IndexDAO() {
        client = MongoDBClientSingleton.getInstance().getClient();
    }

    /**
     *
     * @param dbName
     * @param collName
     * @return
     */
    public List<DBObject> getCollectionIndexes(String dbName, String collName) {
        List<DBObject> indexes = client.getDB(dbName).getCollection("system.indexes").find(new BasicDBObject("ns", dbName + "." + collName), fieldsToReturnIndexes).sort(new BasicDBObject("name", 1)).toArray();

        indexes.forEach((i) -> {
            i.put("_id", i.get("name"));
            i.removeField("name");
        });

        return indexes;
    }

    /**
     *
     * @param db
     * @param collection
     * @param keys
     * @param ops
     */
    public void createIndex(String db, String collection, DBObject keys, DBObject ops) {
        if (ops == null) {
            client.getDB(db).getCollection(collection).createIndex(keys);
        } else {
            client.getDB(db).getCollection(collection).createIndex(keys, ops);
        }
    }

    /**
     *
     * @param db
     * @param collection
     * @param indexId
     * @return
     */
    public int deleteIndex(String db, String collection, String indexId) {
        client.getDB(db).getCollection(collection).dropIndex(indexId);
        return HttpStatus.SC_NO_CONTENT;
    }
}
