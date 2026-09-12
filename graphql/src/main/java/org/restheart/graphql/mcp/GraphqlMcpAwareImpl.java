/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.graphql.mcp;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.plugins.mcp.McpContext;
import org.restheart.plugins.mcp.McpResource;

import com.mongodb.client.MongoClient;

/**
 * The {@code McpAware} logic backing {@code GraphQLService.describeMcp(ctx)} (see #616): reads
 * every document in the configured {@code gql-apps} collection, and — for the ones RESTHeart
 * would actually serve ({@code descriptor.enabled == true}, matching
 * {@code AppDefinitionLoader}'s own query) and that opt into MCP via a top-level {@code mcp}
 * block — builds an {@code McpResource} via {@link GraphqlAppMcpResourceBuilder}.
 *
 * <p>The app's URL is {@code ctx.baseUrl() + ctx.pluginUri() + "/" + descriptor.uri} — unlike
 * MongoDB collections, a GraphQL app has no {@code mongo-mounts}-style remapping: it's always
 * served at wherever this one {@code GraphQLService} plugin itself is mounted (confirmed against
 * {@code GraphQLService.appURI(HttpServerExchange)}, which strips exactly the mount's own path
 * segments before matching the rest against {@code descriptor.uri}).
 */
public final class GraphqlMcpAwareImpl {

    /** Narrow read seam over the {@code gql-apps} collection, so orchestration is unit-testable without a real MongoDB. */
    interface MetadataSource {
        List<BsonDocument> appDocuments(String db);
    }

    private final MetadataSource metadata;

    /** Where app definitions live when the deployment partitions nothing — {@code graphql.db}. */
    private final String defaultAppDefDb;

    GraphqlMcpAwareImpl(MetadataSource metadata, String defaultAppDefDb) {
        this.metadata = metadata;
        this.defaultAppDefDb = defaultAppDefDb;
    }

    public static GraphqlMcpAwareImpl create(MongoClient mclient, String db, String collection) {
        MetadataSource source = appDefDb -> {
            var docs = new ArrayList<BsonDocument>();
            mclient.getDatabase(appDefDb).getCollection(collection, BsonDocument.class).find().into(docs);
            return docs;
        };
        return new GraphqlMcpAwareImpl(source, db);
    }

    /**
     * Which database to read app definitions from.
     *
     * <p>The configured {@code graphql.db} can be overridden per request, and a deployment that
     * gives each caller its own apps does exactly that. That override cannot be used here: the MCP
     * catalogue is built outside any request — on a scope, when its cache entry expires — so there
     * is no exchange to read an override from. Binding the database at init instead, as this used
     * to, described the same one default database's apps to every caller.
     *
     * <p>The scope is what is available here, and it names the database directly.
     */
    private String appDefDb(McpContext ctx) {
        return ctx.unpartitioned() ? defaultAppDefDb : ctx.scope();
    }

    public List<McpResource> describeMcp(McpContext ctx) {
        var mountBase = ctx.baseUrl() + ctx.pluginUri();
        var resources = new ArrayList<McpResource>();

        for (var doc : metadata.appDocuments(appDefDb(ctx))) {
            if (!(doc.get("descriptor") instanceof BsonDocument descriptor) || !isEnabled(descriptor)) {
                continue;
            }
            var uri = descriptor.get("uri");
            if (uri == null || !uri.isString()) {
                continue;
            }

            var appUri = mountBase + "/" + uri.asString().getValue();
            var mcp = doc.get("mcp") instanceof BsonDocument m ? m : null;
            var sdl = doc.get("schema") instanceof BsonString s ? s.getValue() : null;

            GraphqlAppMcpResourceBuilder.build(appUri, mcp, sdl).ifPresent(resources::add);
        }

        return resources;
    }

    private static boolean isEnabled(BsonDocument descriptor) {
        var enabled = descriptor.get("enabled");
        return enabled != null && enabled.isBoolean() && enabled.asBoolean().getValue();
    }
}
