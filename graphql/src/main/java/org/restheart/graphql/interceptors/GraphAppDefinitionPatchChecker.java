/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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

import java.util.Map;

import org.restheart.configuration.Configuration;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import org.restheart.graphql.GraphQLService;
import org.restheart.graphql.cache.AppDefinitionLoadingCache;
import org.restheart.graphql.cache.AppDefinitionRef;
import org.restheart.graphql.models.builder.AppBuilder;
import org.restheart.plugins.Inject;
import static org.restheart.plugins.InterceptPoint.RESPONSE;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

@RegisterPlugin(name="graphAppDefinitionPatchChecker",
        description = "checks GraphQL application definitions on PATCH requests",
        interceptPoint = RESPONSE,
        enabledByDefault = true
)
public class GraphAppDefinitionPatchChecker implements MongoInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphAppDefinitionPatchChecker.class);

    private String defaultAppDefDb = GraphQLService.DEFAULT_APP_DEF_DB;
    private String coll = GraphQLService.DEFAULT_APP_DEF_COLLECTION;

    private boolean enabled = false;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("rh-config")
    private Configuration config;

    @Inject("registry")
    private PluginsRegistry registry;

    @OnInit
    public void init() {
        try {
            Map<String, Object> graphqlArgs = config.getOrDefault("graphql", null);
            if (graphqlArgs != null) {
                this.defaultAppDefDb = arg(graphqlArgs, "db");
                this.coll = arg(graphqlArgs, "collection");
                this.enabled = true;
            } else {
                this.enabled = isGQLSrvEnabled();
            }
        } catch(ConfigurationException ce) {
            // nothing to do, using default values
        }
    }

    private boolean isGQLSrvEnabled() {
        var gql$ = registry.getServices().stream().filter(s -> s.getName().equals("graphql")).findFirst();
        return gql$.isPresent() && gql$.get().isEnabled();
    }

    /**
     * Checks if there is a URI collision with another GraphQL app definition.
     * A collision occurs when:
     * 1. Another app has descriptor.uri equal to the target URI
     * 2. Another app has _id equal to the target URI (without leading slash) and no descriptor.uri
     *
     * @param currentDocId the _id of the current document being created/updated
     * @param targetUri the URI where this app will be accessible (with leading slash)
     * @return true if there is a collision, false otherwise
     */
    private boolean hasUriCollision(String db, String currentDocId, String targetUri) {
		var collection = mclient.getDatabase(db).getCollection(coll, org.bson.BsonDocument.class);

        // Search for apps that would be accessible at the same URI
        // Exclude the current document from the search
        var orConditions = org.restheart.utils.BsonUtils.array()
            // Another app explicitly sets descriptor.uri to our target URI
            .add(org.restheart.utils.BsonUtils.document().put("descriptor.uri", targetUri))
            // Another app has _id that would default to our target URI
            .add(org.restheart.utils.BsonUtils.document()
                .put("_id", targetUri)
                .put("$or", org.restheart.utils.BsonUtils.array()
                    .add(org.restheart.utils.BsonUtils.document().put("descriptor.uri", org.restheart.utils.BsonUtils.document().put("$exists", false)))
                    .add(org.restheart.utils.BsonUtils.document().put("descriptor.uri", (String) null))
                )
            );

        var query = org.restheart.utils.BsonUtils.document()
            .put("_id", org.restheart.utils.BsonUtils.document().put("$ne", currentDocId))
            .put("$or", orConditions);

        var conflictingApp = collection.find(query.get()).first();
        return conflictingApp != null;
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        if (request.isBulkDocuments()) {
            response.setInError(HttpStatus.SC_NOT_IMPLEMENTED, "GraphQL App definition cannot be updated with bulk patch requests");
            return;
        }

		String overrideGQLAppsDb = request.attachedParam("override-gql-apps-db");
		var db = overrideGQLAppsDb == null ? this.defaultAppDefDb : overrideGQLAppsDb;

        var appDef = response.getDbOperationResult().getNewData();

        try {
            // Note: restrictMappingDb validation happens at runtime via AppDefinitionLoader
            var app = AppBuilder.build(appDef, db, false);
            // Use descriptor.uri if present, otherwise use _id (without leading slash for cache key)
            var appUri = app.getDescriptor().getUri() != null
                ? app.getDescriptor().getUri()
                : (appDef.containsKey("_id") ? appDef.get("_id").asString().getValue() : "");

            // Check for URI collision
            var docId = appDef.containsKey("_id") ? appDef.get("_id").asString().getValue() : "";
            if (hasUriCollision(db, docId, appUri)) {
                response.rollback(this.mclient);
                response.setInError(HttpStatus.SC_CONFLICT, "URI collision: another GraphQL app is already accessible at /" + appUri);
                return;
            }

            AppDefinitionLoadingCache.getCache().put(new AppDefinitionRef(db, this.coll, appUri), app);
        } catch(GraphQLIllegalAppDefinitionException e) {
            LOGGER.debug("Wrong GraphQL App definition", e);
            response.rollback(this.mclient);
            response.setInError(HttpStatus.SC_BAD_REQUEST, "Wrong GraphQL App definition: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean resolve(MongoRequest req, MongoResponse res) {
		String overrideGQLAppsDb = req.attachedParam("override-gql-apps-db");
		var db = overrideGQLAppsDb == null ? this.defaultAppDefDb : overrideGQLAppsDb;

        return enabled
            && db.equals(req.getDBName())
            && this.coll.equals(req.getCollectionName())
            && (req.isBulkDocuments()
            || (req.isDocument()
            && req.isPatch()
            && res.getDbOperationResult() != null
            && res.getDbOperationResult().getNewData() != null));
    }
}