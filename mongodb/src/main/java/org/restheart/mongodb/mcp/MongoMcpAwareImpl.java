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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.restheart.exchange.MongoRequest;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.mongodb.db.Databases;
import org.restheart.mongodb.handlers.schema.JsonSchemaCacheSingleton;
import org.restheart.mongodb.handlers.schema.JsonSchemaNotFoundException;
import org.restheart.mongodb.utils.MongoMountResolver;
import org.restheart.mongodb.utils.MongoMountResolverImpl;
import org.restheart.mongodb.utils.StagesInterpolator;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.mcp.McpContext;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.security.AggregationPipelineSecurityChecker;
import org.restheart.utils.BsonUtils;


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

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoMcpAwareImpl.class);

    /** Scopes already warned about, so a lasting misconfiguration is reported once and not every catalogue rebuild. */
    private final Set<String> emptyScopesWarned = ConcurrentHashMap.newKeySet();

    /** Narrow read seam over {@link Databases}, so this class's orchestration is unit-testable without a real MongoDB. */
    interface MetadataSource {
        List<String> databaseNames();

        List<String> collectionNames(String dbName);

        BsonDocument databaseProperties(String dbName);

        BsonDocument collectionProperties(String dbName, String collName);

        /**
         * Resolves a collection's {@code jsonSchema} metadata field — a pointer,
         * {@code {schemaId, schemaStoreDb?}}, not the schema body itself — into the actual schema
         * document, or {@code null} if absent/unresolvable. {@code dbName} is the collection's own
         * database, used when the pointer omits {@code schemaStoreDb}.
         */
        BsonDocument resolveJsonSchema(String dbName, BsonValue jsonSchemaPointer);
    }

    private final MetadataSource metadata;
    private final MountUriResolver mountResolver;
    private final Databases databases;
    private final AggregationPipelineSecurityChecker securityChecker;

    MongoMcpAwareImpl(MetadataSource metadata, MountUriResolver mountResolver) {
        this(metadata, mountResolver, null, null);
    }

    /** {@code databases}/{@code securityChecker} are {@code null} only via the test-only 2-arg constructor above, which never exercises watching or aggregation description. */
    MongoMcpAwareImpl(MetadataSource metadata, MountUriResolver mountResolver, Databases databases, AggregationPipelineSecurityChecker securityChecker) {
        this.metadata = metadata;
        this.mountResolver = mountResolver;
        this.databases = databases;
        this.securityChecker = securityChecker;
    }

    public static MongoMcpAwareImpl create(PluginsRegistry registry) {
        var databases = Databases.get();
        var securityChecker = new AggregationPipelineSecurityChecker(MongoServiceConfiguration.get().getAggregationSecurityConfiguration());
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
                return unescape(databases.getDatabaseProperties(Optional.empty(), Optional.empty(), dbName));
            }

            @Override
            public BsonDocument collectionProperties(String dbName, String collName) {
                return unescape(databases.getCollectionProperties(Optional.empty(), Optional.empty(), dbName, collName));
            }

            @Override
            public BsonDocument resolveJsonSchema(String dbName, BsonValue jsonSchemaPointer) {
                if (!(jsonSchemaPointer instanceof BsonDocument pointer) || !pointer.containsKey("schemaId")) {
                    return null;
                }
                var schemaStoreDb = pointer.get("schemaStoreDb") instanceof BsonString s ? s.getValue() : dbName;
                try {
                    return JsonSchemaCacheSingleton.getInstance().getRaw(schemaStoreDb, pointer.get("schemaId"));
                } catch (JsonSchemaNotFoundException e) {
                    return null;
                }
            }

            /**
             * RESTHeart stores {@code $}-prefixed keys (aggregation/change-stream {@code stages},
             * their {@code $var} references) escaped with a leading {@code _} to satisfy MongoDB's
             * restriction on literal {@code $} field names — see
             * {@code org.restheart.mongodb.utils.StagesInterpolator}, which applies the same
             * {@code BsonUtils.unescapeKeys} call before actually running a pipeline. Metadata read
             * directly via {@link Databases} bypasses that, so it's done here once for the whole
             * properties document (recursive — fixes nested {@code aggrs}/{@code streams} too).
             */
            private BsonDocument unescape(BsonDocument props) {
                return props == null ? null : BsonUtils.unescapeKeys(props).asDocument();
            }
        };
        return new MongoMcpAwareImpl(source, MountUriResolver.fromConfig(), databases, securityChecker);
    }

    public List<McpResource> describeMcp(McpContext ctx) {
        var baseUrl = ctx.baseUrl();
        var scope = ctx.scope();
        var resources = new ArrayList<McpResource>();

        for (var dbName : databasesInScope(ctx)) {
            var enabledCollectionUris = new ArrayList<String>();

            for (var collName : metadata.collectionNames(dbName)) {
                var collPath = mountResolver.collectionPath(dbName, collName, scope);
                if (collPath.isEmpty()) {
                    continue;
                }

                var collUri = baseUrl + collPath.get();
                var collProps = metadata.collectionProperties(dbName, collName);
                if (collProps == null) {
                    continue;
                }

                describeCollection(dbName, collUri, collProps, resources, enabledCollectionUris);
            }

            mountResolver.databasePath(dbName, scope).ifPresent(dbPath -> {
                var dbUri = baseUrl + dbPath;
                var dbProps = metadata.databaseProperties(dbName);
                var dbMcp = dbProps != null ? asDocument(dbProps.get("mcp")) : null;
                DatabaseMcpResourceBuilder.build(dbUri, dbMcp, enabledCollectionUris).ifPresent(resources::add);
            });
        }

        warnIfNothingReachable(ctx, !resources.isEmpty());

        return resources;
    }

    /**
     * The databases this scope may describe.
     *
     * <p>The filter is here, before any URI is built, and not applied to the finished list: a
     * mount can hide the database name — {@code what: /a/inventory} and {@code what: /b/inventory}
     * both landing on {@code /inv} — so by the time URIs exist one database has already
     * overwritten the other, and filtering then would filter whichever survived.
     *
     * <p>It also decides the cost. Unpartitioned, this lists every database on the instance, which
     * is the right answer for a single deployment. Partitioned, the scope already names the one
     * database to look at, so the scan no longer grows with the databases of callers who are not
     * asking.
     */
    private List<String> databasesInScope(McpContext ctx) {
        if (ctx.unpartitioned()) {
            return metadata.databaseNames();
        }

        if (MongoRequest.isReservedDbName(ctx.scope())) {
            LOGGER.warn("MCP scope '{}' names a reserved database; describing nothing for it", ctx.scope());
            return List.of();
        }

        return List.of(ctx.scope());
    }

    /**
     * Warns once per scope that nothing of its database is reachable, so its catalogue is empty.
     * Staying silent is the difference between a caller who can see why their collection is
     * missing and one who opens a support request.
     */
    private void warnIfNothingReachable(McpContext ctx, boolean anyResource) {
        if (ctx.unpartitioned() || anyResource || !emptyScopesWarned.add(ctx.scope())) {
            return;
        }

        LOGGER.warn("No mongo-mount exposes anything of database '{}', so the MCP catalogue for that scope is empty."
                + " A database that is in scope but not mounted is not reachable over HTTP, and cannot be advertised.",
                ctx.scope());
    }
    // No describeTemplates() override: McpService.syncResourceRegistry() now derives a
    // resource-specific template directly from each readable action's own declared params
    // (generic across every McpAware implementation, not just Mongo's) — there's no longer a
    // shared, mount-wide shape for this implementation to contribute on top of that. See its
    // javadoc for why a database, a change stream, and a non-readable aggregation still have no
    // template at all (they stay list_apis/how_to_call-only), and why a readable one derives its
    // own template per-resource rather than a generic mount-wide shape.

    /**
     * Watches the collection whose changes can make a resource stale (#617). Several resources map
     * to one collection — its documents, one document, its {@code _size}, an aggregation over it —
     * and they all share the one change stream {@link CollectionWatchers} keeps for it, so
     * subscribing to a collection and to an aggregation over it costs one stream, not two.
     *
     * <p>An aggregation included, deliberately. MongoDB cannot say when the <em>result</em> of a
     * pipeline changes — there is no change stream over a computation — but the notification
     * carries no payload: it means "re-read". Waking a subscriber whenever the source collection
     * changes answers exactly that, at the cost of the occasional wake-up for a write the pipeline
     * filters out. Re-reading and finding the same board is cheap; never being told is not.
     */
    public Optional<AutoCloseable> watch(McpContext ctx, String resourceUri, Runnable onChange) {
        if (databases == null) {
            return Optional.empty();
        }

        var sourceUri = sourceCollectionUri(resourceUri);
        if (sourceUri == null) {
            return Optional.empty();
        }

        var resolved = resolveMount(sourceUri);
        if (resolved == null) {
            return Optional.empty();
        }

        return Optional.of(watchers().watch(resolved.database(), resolved.collection(), onChange));
    }

    /**
     * The URI of the collection a resource reads from, or {@code null} when there is none to watch.
     *
     * <p>An aggregation URI is the collection's with {@code /_aggrs/<name>} appended, and the mount
     * resolver rejects those extra segments, so the suffix is dropped before resolving rather than
     * resolved through.
     *
     * <p>A change stream gets nothing: it is a live channel consumed over a websocket and is not
     * readable through {@code resources/read}, so there would be nothing to re-read when told it
     * changed.
     */
    private static String sourceCollectionUri(String resourceUri) {
        if (resourceUri.contains("/_streams/")) {
            return null;
        }

        var aggrs = resourceUri.indexOf("/_aggrs/");

        return aggrs < 0 ? resourceUri : resourceUri.substring(0, aggrs);
    }

    private volatile CollectionWatchers watchers;

    private synchronized CollectionWatchers watchers() {
        if (watchers == null) {
            watchers = new CollectionWatchers(
                    (db, coll) -> databases.collection(Optional.empty(), db, coll));
        }
        return watchers;
    }

    private MongoMountResolver.ResolvedContext resolveMount(String resourceUri) {
        try {
            var path = new URI(resourceUri).getPath();
            var resolved = MongoMountResolverImpl.getInstance().resolve(path);
            return resolved.database() == null || resolved.collection() == null ? null : resolved;
        } catch (Exception e) {
            return null;
        }
    }

    private void describeCollection(String dbName, String collUri, BsonDocument collProps, List<McpResource> resources, List<String> enabledCollectionUris) {
        var mcp = asDocument(collProps.get("mcp"));
        var jsonSchema = metadata.resolveJsonSchema(dbName, collProps.get("jsonSchema"));
        var aggrs = collProps.get("aggrs") instanceof BsonArray a ? a : null;
        var streams = collProps.get("streams") instanceof BsonArray s ? s : null;
        var constraints = collProps.get("constraints") instanceof BsonArray c ? c : null;

        CollectionMcpResourceBuilder.build(collUri, mcp, jsonSchema, aggrs, streams, constraints).ifPresent(resource -> {
            resources.add(resource);
            enabledCollectionUris.add(collUri);
        });

        if (aggrs != null) {
            for (var entry : aggrs) {
                describeEntry(entry, collUri, (uri, name, stages, entryMcp) -> AggregationMcpResourceBuilder
                        .build(uri, name, stages, entryMcp, dbName, securityChecker).ifPresent(resources::add));
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
