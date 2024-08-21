/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
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
package org.restheart.graphql.initializers;

import org.restheart.configuration.Configuration;
import org.restheart.configuration.ConfigurationException;
import org.restheart.graphql.GraphQLService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;

import java.util.Map;

import org.bson.BsonDocument;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import org.restheart.graphql.cache.AppDefinitionLoadingCache;
import org.restheart.graphql.models.builder.AppBuilder;
import org.restheart.plugins.Initializer;

import com.mongodb.client.MongoClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(name="graphAppsInitializer",
        description = "initializes and caches all GQL Apps at boot timeGraphQL",
        enabledByDefault = true
)
public class GraphAppsInitializer implements Initializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphAppsInitializer.class);

    private String db = GraphQLService.DEFAULT_APP_DEF_DB;
    private String coll = GraphQLService.DEFAULT_APP_DEF_COLLECTION;

    private boolean enabled = false;

    @Inject("rh-config")
    private Configuration config;

    @Inject("mclient")
    private MongoClient mclient;

    @OnInit
    public void onInit() {
        try {
            Map<String, Object> graphqlArgs = config.getOrDefault("graphql", null);
            if (graphqlArgs != null) {
                this.db = arg(graphqlArgs, "db");
                this.coll = arg(graphqlArgs, "collection");
                this.enabled = true;
            } else {
                this.enabled = false;
            }
        } catch(ConfigurationException ce) {
            // nothing to do, using default values
        }
    }

    @Override
    public void init() {
        if (this.enabled) {
            this.mclient
                .getDatabase(this.db)
                .getCollection(this.coll)
                .withDocumentClass(BsonDocument.class)
                .find()
                .forEach(appDef -> {
                    try {
                        var app = AppBuilder.build(appDef);
                        var appUri = app.getDescriptor().getUri() != null ? app.getDescriptor().getUri() :  app.getDescriptor().getAppName();
                        AppDefinitionLoadingCache.getCache().put(appUri, app);
                        LOGGER.debug("GQL App Definition {} initialized", appUri);
                    } catch (GraphQLIllegalAppDefinitionException e) {
                        LOGGER.warn("GQL App Definition {} is invalid", appDef.get("_id"), e);
                    }
                });
        }
    }
}