/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2024 SoftInstigate
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

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.graphql.GraphQLAppDefNotFoundException;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import org.restheart.graphql.models.GraphQLApp;
import org.restheart.graphql.models.builder.AppBuilder;
import static org.restheart.utils.BsonUtils.array;
import static org.restheart.utils.BsonUtils.document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

public class AppDefinitionLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppDefinitionLoader.class);

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

    /**
     * @param appURI
     * @param etag the etag of cached gql app
     * @return true if the app definition appURI has been updated, i.e. has a different _etag
     */
    public static boolean isUpdated(String appURI, BsonValue etag) {
        var uriOrNameCond = array()
            .add(document().put(APP_URI_FIELD, appURI))
            .add(document().put(APP_NAME_FIELD, appURI));

        var conditions = array()
            .add(document().put("$or", uriOrNameCond))
            .add(document().put(APP_ENABLED_FIELD, true));

        var findArg = document().put("$and", conditions);

        var gqlApp = mongoClient.getDatabase(appDB).getCollection(appCollection, BsonDocument.class).find(findArg.get()).first();

        if (gqlApp != null) {
            var newEtag = gqlApp.get("_etag");
            LOGGER.trace("oldEtag {}, newEtag {}", etag, newEtag);
            return etag == null || newEtag == null || !etag.equals(newEtag);
        } else {
            return true; // app has been deleted
        }
    }

    public static GraphQLApp load(String appURI) throws GraphQLIllegalAppDefinitionException, GraphQLAppDefNotFoundException {
        LOGGER.trace("Loading GQL App Definition {} from db", appURI);

        var uriOrNameCond = array()
            .add(document().put(APP_URI_FIELD, appURI))
            .add(document().put(APP_NAME_FIELD, appURI));

        var conditions = array()
            .add(document().put("$or", uriOrNameCond))
            .add(document().put(APP_ENABLED_FIELD, true));

        var findArg = document().put("$and", conditions);

        var gqlApp = mongoClient.getDatabase(appDB).getCollection(appCollection, BsonDocument.class).find(findArg.get()).first();

        if (gqlApp != null) {
            return AppBuilder.build(gqlApp);
        } else {
            throw new GraphQLAppDefNotFoundException("GQL App Definition for uri " + appURI + " not found. ");
        }
    }
}
