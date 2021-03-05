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
package org.restheart.graphql.interceptors;

import org.restheart.ConfigurationException;
import org.restheart.ConfigurationKeys;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.graphql.GraphQLAppDeserializer;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import org.restheart.graphql.GraphQLService;

import static org.restheart.plugins.InterceptPoint.RESPONSE;

import org.restheart.plugins.ConfigurationScope;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InjectMongoClient;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

import static org.restheart.plugins.ConfigurablePlugin.argValue;

import java.util.Map;

import com.mongodb.MongoClient;


@RegisterPlugin(name="graphAppDefinitionPutPatchChecker",
        description = "checks GraphQL application definitions on PATCH requests",
        interceptPoint = RESPONSE,
        enabledByDefault = true
)
public class GraphAppDefinitionPatchChecker implements MongoInterceptor {
    private String db = GraphQLService.DEFAULT_APP_DEF_DB;
    private String coll = GraphQLService.DEFAULT_APP_DEF_COLLECTION;
    private MongoClient mclient = null;

    private boolean enabled = false;

    @InjectMongoClient
    public void mc(MongoClient mclient) {
        this.mclient = mclient;
    }

    @InjectConfiguration(scope = ConfigurationScope.ALL)
    public void conf(Map<String, Object> args) {
        try {
            Map<String, Object> pluginsArgs = argValue(args, ConfigurationKeys.PLUGINS_ARGS_KEY);
            Map<String, Object> graphqlArgs = argValue(pluginsArgs, "graphql");

            this.db = argValue(graphqlArgs, "db");
            this.coll = argValue(graphqlArgs, "collection");
        } catch(ConfigurationException ce) {
            // nothing to do, using default values
        }

        this.enabled = true;
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        if (request.isBulkDocuments()) {
            response.setInError(HttpStatus.SC_NOT_IMPLEMENTED, "GraphQL App definition cannot be updated with bulk patch requests");
            return;
        }

        var appDef = response.getDbOperationResult().getNewData();

        try {
            GraphQLAppDeserializer.fromBsonDocument(appDef);
        } catch(GraphQLIllegalAppDefinitionException e) {
            response.rollback(this.mclient);
            response.setInError(HttpStatus.SC_BAD_REQUEST, "wrong GraphQL App definition: " + e.getMessage());
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return enabled
            && this.db.equals(request.getDBName())
            && this.coll.equals(request.getCollectionName())
            && (request.isBulkDocuments()
            || (request.isDocument()
            && request.isPatch()
            && response.getDbOperationResult() != null
            && response.getDbOperationResult().getNewData() != null));
    }
}