/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2023 SoftInstigate
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

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCursor;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import static org.restheart.utils.BsonUtils.document;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoUtils {
    MongoClient client;

    public MongoUtils(MongoClient client) {
        this.client = client;
    }

    public boolean doesDbExist(final String dbName) {
        // at least one collection exists for an existing db
        return client.getDatabase(dbName).listCollectionNames().first() != null;
    }

    public boolean doesCollectionExist(final String dbName, final String collName) {
        MongoCursor<String> dbCollections = client.getDatabase(dbName).listCollectionNames().iterator();

        while (dbCollections.hasNext()) {
            String dbCollection = dbCollections.next();

            if (collName.equals(dbCollection)) {
                return true;
            }
        }

        return false;
    }

    public void createDb(final String dbName) {
        client.getDatabase(dbName).createCollection("_properties");

        var pc = client.getDatabase(dbName).getCollection("_properties", BsonDocument.class);

        pc.insertOne(document()
            .put("_id", new BsonString("_properties"))
            .put("_etag", new BsonObjectId())
            .get());
    }

    public void createCollection(final String dbName, final String collName) {
        var pc = client.getDatabase(dbName).getCollection("_properties", BsonDocument.class);

        pc.insertOne(document()
            .put("_id", new BsonString("_properties.".concat(collName)))
            .put("_etag", new BsonObjectId())
            .get());

        client.getDatabase(dbName).createCollection(collName);
    }
}
