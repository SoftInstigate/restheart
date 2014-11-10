/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart.db;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.softinstigate.restheart.utils.HttpStatus;
import java.util.List;

/**
 *
 * @author uji
 */
public class IndexDAO {
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();

    public static final BasicDBObject METADATA_QUERY = new BasicDBObject("_id", "_properties");

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

    public static List<DBObject> getCollectionIndexes(String dbName, String collName) {
        List<DBObject> indexes = client.getDB(dbName).getCollection("system.indexes").find(new BasicDBObject("ns", dbName + "." + collName), fieldsToReturnIndexes).sort(new BasicDBObject("name", 1)).toArray();

        indexes.forEach((i) -> {
            i.put("_id", i.get("name"));
            i.removeField("name");
        });

        return indexes;
    }

    public static void createIndex(String db, String co, DBObject keys, DBObject ops) {
        if (ops == null) {
            client.getDB(db).getCollection(co).createIndex(keys);
        }
        else {
            client.getDB(db).getCollection(co).createIndex(keys, ops);
        }
    }

    public static int deleteIndex(String db, String co, String indexId) {
        client.getDB(db).getCollection(co).dropIndex(indexId);
        return HttpStatus.SC_NO_CONTENT;
    }
}
