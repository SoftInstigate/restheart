/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2021 SoftInstigate
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
package org.restheart.graphql.cache;

import com.mongodb.MongoClient;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.graphql.GraphQLAppDeserializer;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import org.restheart.graphql.models.*;


public class AppDefinitionLoader {

    private static final String APP_URI_FIELD = "descriptor.uri";
    private static final String APP_NAME_FIELD = "descriptor.name";
    private static final String APP_ENABLED_FIELD = "descriptor.enabled";

    private static MongoClient mongoClient;
    private static String appDB;
    private static String appCollection;

    public static void setup(String _db, String _collection, MongoClient mclient){
        appDB = _db;
        appCollection = _collection;
        mongoClient = mclient;
    }

    public static GraphQLApp loadAppDefinition(String appURI) throws GraphQLIllegalAppDefinitionException {


        BsonArray conditions = new BsonArray();
        BsonArray uriOrNameCond = new BsonArray();
        uriOrNameCond.add(new BsonDocument(APP_URI_FIELD, new BsonString(appURI)));
        uriOrNameCond.add(new BsonDocument(APP_NAME_FIELD, new BsonString(appURI)));
        conditions.add(new BsonDocument("$or", uriOrNameCond));
        conditions.add(new BsonDocument(APP_ENABLED_FIELD, new BsonBoolean(true)));
        BsonDocument findArg = new BsonDocument("$and",conditions);

        BsonDocument appDefinition = mongoClient.getDatabase(appDB).getCollection(appCollection, BsonDocument.class)
                .find(findArg).first();

        if (appDefinition != null) {
            return GraphQLAppDeserializer.fromBsonDocument(appDefinition);
        } else {
            return null;
        }
    }


}
