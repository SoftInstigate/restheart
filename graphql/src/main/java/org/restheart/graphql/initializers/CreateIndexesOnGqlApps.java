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
package org.restheart.graphql.initializers;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.IndexOptions;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import org.restheart.configuration.Configuration;
import org.restheart.configuration.ConfigurationException;
import org.restheart.graphql.GraphQLService;
import org.restheart.plugins.*;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.ThreadsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.StreamSupport;

import static org.restheart.utils.BsonUtils.document;

@RegisterPlugin(name="createIndexesOnGqlApps",
                description = "initializes the indexes on the gql-apps collection to speedup fetching of graphql app definitions",
                enabledByDefault = false
)
public class CreateIndexesOnGqlApps implements Initializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateIndexesOnGqlApps.class);

    private String db = GraphQLService.DEFAULT_APP_DEF_DB;
    private String coll = GraphQLService.DEFAULT_APP_DEF_COLLECTION;

    private boolean enabled = false;

    @Inject("rh-config")
    private Configuration config;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("registry")
    private PluginsRegistry registry;

    @OnInit
    public void onInit() {
        try {
            Map<String, Object> graphqlArgs = config.getOrDefault("graphql", null);
            if (graphqlArgs != null) {
                this.db = arg(graphqlArgs, "db");
                this.coll = arg(graphqlArgs, "collection");
                this.enabled =  isGQLSrvEnabled() && argOrDefault(graphqlArgs, "app-cache-enabled", true);
            } else {
                this.enabled = false;
            }
        } catch(ConfigurationException ce) {
            // nothing to do, using default values
        }
    }

    private boolean isGQLSrvEnabled() {
        var gql$ = registry.getServices().stream().filter(s -> s.getName().equals("graphql")).findFirst();
        return gql$.isPresent() && gql$.get().isEnabled();
    }

    private static final IndexOptions INDEX_OPTIONS = new IndexOptions().unique(true).sparse(true);

    @Override
    public void init() {
        if (this.enabled) {
            try {
                var gqlApps = this.mclient.getDatabase(this.db).getCollection(this.coll).withDocumentClass(BsonDocument.class);

                var indexes = gqlApps.listIndexes(BsonDocument.class);

                var uriExists = StreamSupport.stream(indexes.spliterator(), false)
                    .anyMatch(index -> exists(index, "descriptor.uri", new BsonInt32(1)));

                if (uriExists) {
                    LOGGER.debug("Index {'descriptor.uri':1} exists on {}.{}", this.db, this.coll);
                } else {
                    LOGGER.info("Creating index {'descriptor.uri':1} on {}.{}", this.db, this.coll);
                    ThreadsUtils.virtualThreadsExecutor().execute(() -> gqlApps.createIndex(document().put("descriptor.uri", 1).get(), INDEX_OPTIONS));
                }

                var nameExists = StreamSupport.stream(indexes.spliterator(), false)
                    .anyMatch(index -> exists(index, "descriptor.name", new BsonInt32(1)));

                if (nameExists) {
                    LOGGER.debug("Index {'descriptor.name':1} exists on {}.{}", this.db, this.coll);
                } else {
                    LOGGER.info("Creating index {'descriptor.name':1}' on {}.{}", this.db, this.coll);
                    ThreadsUtils.virtualThreadsExecutor().execute(() -> gqlApps.createIndex(document().put("descriptor.name", 1).get(), INDEX_OPTIONS));
                }

            } catch (Throwable e) {
                LOGGER.warn("Error initializing indexes on {}.{}", this.db, this.coll, e);
            }
        }
    }

    private boolean exists(BsonDocument index, String key, BsonValue value) {
        var key$$$ = BsonUtils.get(index, "key");

        if (key$$$.isEmpty()) {
            return false;
        } else {
            var key$$ = key$$$.get();

            if (!key$$.isDocument()) {
                return false;
            } else {
                var key$ = key$$.asDocument();
                return key$.containsKey(key) && key$.get(key).equals(value);
            }
        }
    }
}