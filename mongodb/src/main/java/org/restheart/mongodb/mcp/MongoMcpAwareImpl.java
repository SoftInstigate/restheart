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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.exchange.MongoRequest;
import org.restheart.mongodb.db.Databases;
import org.restheart.mongodb.handlers.schema.JsonSchemaCacheSingleton;
import org.restheart.mongodb.handlers.schema.JsonSchemaNotFoundException;
import org.restheart.mongodb.utils.MongoMountResolver;
import org.restheart.mongodb.utils.MongoMountResolverImpl;
import org.restheart.plugins.mcp.McpContext;
import org.restheart.plugins.mcp.McpReadResult;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.plugins.mcp.McpResourceTemplate;
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

    MongoMcpAwareImpl(MetadataSource metadata, MountUriResolver mountResolver) {
        this(metadata, mountResolver, null);
    }

    /** {@code databases} is {@code null} only via the test-only 2-arg constructor above, which never exercises {@link #readResource}. */
    MongoMcpAwareImpl(MetadataSource metadata, MountUriResolver mountResolver, Databases databases) {
        this.metadata = metadata;
        this.mountResolver = mountResolver;
        this.databases = databases;
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
        return new MongoMcpAwareImpl(source, MountUriResolver.fromConfig(), databases);
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

                describeCollection(dbName, collUri, collProps, resources, enabledCollectionUris);
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

    /**
     * URI templates (RFC 6570) for the context-mode shapes this implementation produces,
     * derived from the actual {@code mongo-mounts} configuration via {@link MountUriResolver}
     * rather than a hardcoded {@code /{db}/{collection}} shape — a default {@code mongo-mounts}
     * (a single database mounted at {@code /}) exposes collections at {@code /{collection}}, with
     * no {@code {db}} segment at all.
     *
     * <p>Multiple mounts of the same shape (e.g. two separate {@code *} wildcard mounts) each
     * contribute their own template with the same {@code name} — harmless, since {@code name} is
     * only a display label and dispatch is by {@code uriTemplate} match, not by name.
     */
    public List<McpResourceTemplate> describeTemplates(McpContext ctx) {
        var baseUrl = ctx.baseUrl();
        var templates = new ArrayList<McpResourceTemplate>();

        for (var dbTemplate : mountResolver.databasePathTemplates()) {
            templates.add(new McpResourceTemplate(baseUrl + dbTemplate, "database-context", "Database — context"));
        }

        for (var collTemplate : mountResolver.collectionPathTemplates()) {
            var uri = baseUrl + collTemplate;
            templates.add(new McpResourceTemplate(uri, "collection-context", "Collection — context"));
            templates.add(new McpResourceTemplate(uri + "/_aggrs/{name}", "aggregation-context", "Aggregation — context"));
            templates.add(new McpResourceTemplate(uri + "/_streams/{name}", "change-stream-context", "Change stream — context"));
            // Discoverability only: McpService.readTemplateMatch() already detects a query string
            // on ANY matching template's URI and dispatches documents-mode regardless of which
            // template matched (confirmed live — restheart#617 Phase 2b) — this entry exists so
            // MCP clients (e.g. MCP Inspector's "Resource Templates" tab) actually see and can
            // construct the query-bearing shape, instead of only ever reading the bare, context-only
            // URI from resources/list.
            templates.add(new McpResourceTemplate(uri + "{?filter,sort,keys,page,pagesize}", "collection-documents", "Collection — documents"));
            templates.add(new McpResourceTemplate(uri + "/{id}", "document", "Document"));
        }

        return templates;
    }

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGESIZE = 100;

    /**
     * Implements documents-mode {@code resources/read} (#617 Phase 2b) for the {@code query} and
     * {@code get} actions {@link CollectionMcpResourceBuilder} marks {@code readable} — the same
     * {@link Databases#getCollectionData} call {@code GetCollectionHandler} makes for a real
     * {@code GET}, just invoked directly instead of through Undertow.
     *
     * <p><b>Known gap</b> (tracked against #617): does not yet apply any ACL-derived read filter
     * or field projection on top of the caller-supplied {@code filter}/{@code keys} — only the
     * base allow/deny decision from restheart#722's {@code DescriptorAwareAuthorizer} check is
     * enforced today. The filter/projection handoff from that check to here is a follow-up.
     */
    @SuppressWarnings("unchecked")
    public Optional<McpReadResult> readResource(McpContext ctx, String resourceUri, String action, Map<String, Object> args) {
        if (databases == null) {
            return Optional.empty();
        }

        var resolved = resolveMount(resourceUri);
        if (resolved == null) {
            return Optional.empty();
        }

        var effectiveArgs = args == null ? Map.<String, Object>of() : args;

        return switch (action) {
            case "query" -> Optional.of(queryDocuments(resolved, effectiveArgs));
            case "get" -> Optional.of(getSingleDocument(resolved, effectiveArgs));
            default -> Optional.empty();
        };
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

    @SuppressWarnings("unchecked")
    private McpReadResult queryDocuments(MongoMountResolver.ResolvedContext resolved, Map<String, Object> args) {
        var filter = args.get("filter") instanceof Map<?, ?> m ? BsonUtils.toBsonDocument((Map<String, Object>) m) : new BsonDocument();
        var keys = args.get("keys") instanceof Map<?, ?> m ? BsonUtils.toBsonDocument((Map<String, Object>) m) : null;
        var sort = args.get("sort") instanceof String s && !s.isBlank() ? BsonDocument.parse(s) : new BsonDocument();
        var page = args.get("page") instanceof Integer p ? p : DEFAULT_PAGE;
        var pagesize = args.get("pagesize") instanceof Integer ps ? ps : DEFAULT_PAGESIZE;

        var docs = databases.getCollectionData(Optional.empty(), Optional.empty(), resolved.database(), resolved.collection(),
                page, pagesize, sort, filter, null, keys, false);
        var total = databases.getCollectionSize(Optional.empty(), Optional.empty(), resolved.database(), resolved.collection(), filter);

        var content = new ArrayList<Object>();
        for (var doc : docs) {
            content.add(BsonJavaConverter.toMap(doc.asDocument()));
        }

        var meta = new LinkedHashMap<String, Object>();
        meta.put("total_count", total);
        meta.put("page", page);
        if ((long) page * pagesize < total) {
            meta.put("next", "?page=" + (page + 1));
        }

        return new McpReadResult(content, meta);
    }

    /** {@code id} is treated as an ObjectId when it looks like one, else as a plain string {@code _id} — not RESTHeart's full doc-id type-inference grammar (prefixed encodings for other BSON types), which is out of scope here. */
    private McpReadResult getSingleDocument(MongoMountResolver.ResolvedContext resolved, Map<String, Object> args) {
        var id = args.get("id") instanceof String s ? s : null;
        var filter = new BsonDocument("_id", idValue(id));

        var docs = databases.getCollectionData(Optional.empty(), Optional.empty(), resolved.database(), resolved.collection(),
                1, 1, new BsonDocument(), filter, null, null, false);

        return docs.isEmpty() ? new McpReadResult(null) : new McpReadResult(BsonJavaConverter.toMap(docs.get(0).asDocument()));
    }

    private static BsonValue idValue(String id) {
        return id != null && ObjectId.isValid(id) ? new BsonObjectId(new ObjectId(id)) : new BsonString(id);
    }

    private void describeCollection(String dbName, String collUri, BsonDocument collProps, List<McpResource> resources, List<String> enabledCollectionUris) {
        var mcp = asDocument(collProps.get("mcp"));
        var jsonSchema = metadata.resolveJsonSchema(dbName, collProps.get("jsonSchema"));
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
