/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.mongodb.db.Databases;
import org.restheart.plugins.mcp.McpContext;
import org.restheart.plugins.mcp.McpResource;

/**
 * The {@code McpAware} logic backing {@code MongoService.describeMcp(ctx)} (see #616): walks
 * every real MongoDB database/collection, resolves each one's externally-visible URL via
 * {@link MountUriResolver} (skipping anything no {@code mongo-mounts} entry actually exposes),
 * and — for whichever ones are reachable at all — builds an {@code McpResource} through the
 * per-kind builders, which independently decide MCP visibility from each resource's own
 * {@code mcp.enabled}/{@code description}. No ACL check happens here: see
 * {@link CollectionMcpResourceBuilder} for why.
 *
 * <p>Reads go through {@link MetadataSource}, a narrow seam over {@link Databases} — production
 * code gets it from {@link #create()}, tests supply a fake so this class's iteration/wiring
 * logic (which builder gets called with what, how the enabled-collection list feeds
 * {@code DatabaseMcpResourceBuilder}) is verifiable without a real MongoDB.
 */
public final class MongoMcpAwareImpl {

    /** Narrow read seam over {@link Databases}, so this class's orchestration is unit-testable without a real MongoDB. */
    interface MetadataSource {
        List<String> databaseNames();

        List<String> collectionNames(String dbName);

        BsonDocument databaseProperties(String dbName);

        BsonDocument collectionProperties(String dbName, String collName);
    }

    private final MetadataSource metadata;
    private final MountUriResolver mountResolver;

    MongoMcpAwareImpl(MetadataSource metadata, MountUriResolver mountResolver) {
        this.metadata = metadata;
        this.mountResolver = mountResolver;
    }

    public static MongoMcpAwareImpl create() {
        var databases = Databases.get();
        MetadataSource source = new MetadataSource() {
            @Override
            public List<String> databaseNames() {
                return databases.getDatabaseNames(Optional.empty()).stream()
                        .filter(name -> !MongoRequest.isReservedDbName(name))
                        .toList();
            }

            @Override
            public List<String> collectionNames(String dbName) {
                return databases.getCollectionNames(Optional.empty(), Optional.empty(), dbName);
            }

            @Override
            public BsonDocument databaseProperties(String dbName) {
                return databases.getDatabaseProperties(Optional.empty(), Optional.empty(), dbName);
            }

            @Override
            public BsonDocument collectionProperties(String dbName, String collName) {
                return databases.getCollectionProperties(Optional.empty(), Optional.empty(), dbName, collName);
            }
        };
        return new MongoMcpAwareImpl(source, MountUriResolver.fromConfig());
    }

    public List<McpResource> describeMcp(McpContext ctx) {
        var baseUrl = ctx.baseUrl();
        var resources = new ArrayList<McpResource>();

        for (var dbName : metadata.databaseNames()) {
            var enabledCollectionUris = new ArrayList<String>();

            for (var collName : metadata.collectionNames(dbName)) {
                var collPath = mountResolver.collectionPath(dbName, collName);
                if (collPath.isEmpty()) {
                    continue;
                }

                var collUri = baseUrl + collPath.get();
                var collProps = metadata.collectionProperties(dbName, collName);
                if (collProps == null) {
                    continue;
                }

                describeCollection(collUri, collProps, resources, enabledCollectionUris);
            }

            mountResolver.databasePath(dbName).ifPresent(dbPath -> {
                var dbUri = baseUrl + dbPath;
                var dbProps = metadata.databaseProperties(dbName);
                var dbMcp = dbProps != null ? asDocument(dbProps.get("mcp")) : null;
                DatabaseMcpResourceBuilder.build(dbUri, dbMcp, enabledCollectionUris).ifPresent(resources::add);
            });
        }

        return resources;
    }

    private void describeCollection(String collUri, BsonDocument collProps, List<McpResource> resources, List<String> enabledCollectionUris) {
        var mcp = asDocument(collProps.get("mcp"));
        var jsonSchema = collProps.get("jsonSchema");
        var aggrs = collProps.get("aggrs") instanceof BsonArray a ? a : null;
        var streams = collProps.get("streams") instanceof BsonArray s ? s : null;

        CollectionMcpResourceBuilder.build(collUri, mcp, jsonSchema, aggrs, streams).ifPresent(resource -> {
            resources.add(resource);
            enabledCollectionUris.add(collUri);
        });

        if (aggrs != null) {
            for (var entry : aggrs) {
                describeEntry(entry, collUri, (uri, name, stages, entryMcp) -> AggregationMcpResourceBuilder
                        .build(uri, name, stages, entryMcp).ifPresent(resources::add));
            }
        }

        if (streams != null) {
            for (var entry : streams) {
                describeEntry(entry, collUri, (uri, name, stages, entryMcp) -> ChangeStreamMcpResourceBuilder
                        .build(uri, name, stages, entryMcp).ifPresent(resources::add));
            }
        }
    }

    @FunctionalInterface
    private interface EntryBuilder {
        void build(String collUri, String entryUri, BsonValue stages, BsonDocument entryMcp);
    }

    private void describeEntry(BsonValue entry, String collUri, EntryBuilder builder) {
        if (!entry.isDocument()) {
            return;
        }
        var doc = entry.asDocument();
        var uri = doc.get("uri");
        if (uri == null || !uri.isString()) {
            return;
        }
        builder.build(collUri, uri.asString().getValue(), doc.get("stages"), asDocument(doc.get("mcp")));
    }

    private static BsonDocument asDocument(BsonValue v) {
        return v instanceof BsonDocument d ? d : null;
    }
}
