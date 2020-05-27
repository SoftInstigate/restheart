/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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
package org.restheart.security.utils;

import com.mongodb.MongoClient;
import com.mongodb.MongoCommandException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import java.util.Objects;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import static org.restheart.exchange.ExchangeKeys.COLL_META_DOCID_PREFIX;
import static org.restheart.exchange.ExchangeKeys.META_COLLNAME;
import org.restheart.exchange.OperationResult;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class MongoUtils {
    MongoClient client;
    
    public MongoUtils(MongoClient client) {
        this.client = client;
    }
    
    public boolean doesDbExist(final String dbName) {
        // at least one collection exists for an existing db
        return client.getDatabase(dbName)
                        .listCollectionNames()
                        .first() != null;
    }
    
    public boolean doesCollectionExist(
            final String dbName,
            final String collName) {
        MongoCursor<String> dbCollections = client
                .getDatabase(dbName).listCollectionNames().iterator();

        while (dbCollections.hasNext()) {
            String dbCollection = dbCollections.next();

            if (collName.equals(dbCollection)) {
                return true;
            }
        }

        return false;
    }
    
    public void createDb(final String dbName) {
        client.getDatabase(dbName)
                .createCollection("_properties");
        
        var pc = client.getDatabase(dbName)
                .getCollection("_properties", BsonDocument.class);
        
        var doc = new BsonDocument();
        
        doc.put("_id", new BsonString("_properties"));
        doc.put("_etag", new BsonObjectId());
        
        pc.insertOne(doc);
    }
    
    public void createCollection(
            final String dbName,
            final String collName) {

        var pc = client.getDatabase(dbName)
                .getCollection("_properties", BsonDocument.class);
        
        var doc = new BsonDocument();
        
        doc.put("_id", new BsonString("_properties.".concat(collName)));
        doc.put("_etag", new BsonObjectId());
        
        pc.insertOne(doc);
        
        client.getDatabase(dbName).createCollection(collName);
    }
}
