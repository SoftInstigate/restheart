/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.graphql.interceptors;

import java.util.Map;

import org.restheart.configuration.Configuration;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.graphql.GraphQLService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.mcp.McpCatalogInvalidator;
import org.restheart.utils.HttpStatus;

/**
 * Drops the MCP catalogue when an app definition is written.
 *
 * <p>The same job {@code mcpCatalogInvalidatorOnMetadataWrite} does for collections, for the one
 * kind of publishable thing that does not live in collection metadata: a GraphQL app is a document,
 * and its {@code mcp} block is a field of that document. Without this, publishing an app would take
 * until the catalogue expired to be visible, while publishing a collection was immediate — the same
 * action behaving differently depending on what it was applied to.
 *
 * <p>Here rather than in the MongoDB module for the same reason the check that escapes these
 * documents is here: which database and collection hold app definitions is this module's business,
 * and it is configurable.
 */
@RegisterPlugin(
        name = "mcpCatalogInvalidatorOnAppWrite",
        description = "Drops the MCP catalogue of a scope when a GraphQL app definition is written",
        interceptPoint = InterceptPoint.RESPONSE,
        enabledByDefault = true)
public class McpCatalogInvalidatorOnAppWrite implements MongoInterceptor {

    private String defaultAppDefDb = GraphQLService.DEFAULT_APP_DEF_DB;
    private String coll = GraphQLService.DEFAULT_APP_DEF_COLLECTION;
    private boolean enabled = false;

    @Inject("rh-config")
    private Configuration config;

    @Inject("registry")
    private PluginsRegistry registry;

    private McpCatalogInvalidator invalidator;

    @OnInit
    public void init() {
        try {
            Map<String, Object> graphqlArgs = config.getOrDefault("graphql", null);

            if (graphqlArgs != null) {
                this.defaultAppDefDb = arg(graphqlArgs, "db");
                this.coll = arg(graphqlArgs, "collection");
                this.enabled = isGQLSrvEnabled();
            }
        } catch (ConfigurationException ce) {
            // nothing to do, using default values
        }

        this.invalidator = registry.getProviders().stream()
                .filter(p -> McpCatalogInvalidator.class.isAssignableFrom(p.getInstance().rawType()))
                .findFirst()
                .map(p -> (McpCatalogInvalidator) p.getInstance().get(null))
                .orElse(null);
    }

    private boolean isGQLSrvEnabled() {
        var gql$ = registry.getServices().stream().filter(s -> s.getName().equals("graphql")).findFirst();
        return gql$.isPresent() && gql$.get().isEnabled();
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        invalidator.invalidate(request);
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        var overrideGQLAppsDb = request.attachedParam("override-gql-apps-db");
        var db = overrideGQLAppsDb == null ? this.defaultAppDefDb : (String) overrideGQLAppsDb;

        return enabled
                && invalidator != null
                && db.equals(request.getDBName())
                && this.coll.equals(request.getCollectionName())
                && request.isWriteDocument()
                && response.getStatusCode() > 0
                && response.getStatusCode() < HttpStatus.SC_BAD_REQUEST;
    }
}
