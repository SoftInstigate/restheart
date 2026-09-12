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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.restheart.mongodb.security.ProjectResponse;
import org.restheart.security.AclVarsInterpolator;
import org.restheart.security.MongoPermissions;
import org.bson.json.JsonMode;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.mongodb.db.Databases;
import org.restheart.mongodb.handlers.aggregation.AbstractAggregationOperation;
import org.restheart.mongodb.handlers.aggregation.AggregationPipeline;
import org.restheart.mongodb.handlers.schema.JsonSchemaCacheSingleton;
import org.restheart.mongodb.handlers.schema.JsonSchemaNotFoundException;
import org.restheart.mongodb.utils.MongoMountResolver;
import org.restheart.mongodb.utils.MongoMountResolverImpl;
import org.restheart.mongodb.utils.StagesInterpolator;
import org.restheart.mongodb.utils.StagesInterpolator.STAGE_OPERATOR;
import org.restheart.mongodb.utils.VarsInterpolator.VAR_OPERATOR;
import org.restheart.plugins.mcp.McpContext;
import org.restheart.plugins.mcp.McpReadResult;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.security.AggregationPipelineSecurityChecker;
import org.restheart.utils.BsonUtils;

import com.mongodb.MongoCommandException;

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

    /** {@code databases}/{@code securityChecker} are {@code null} only via the test-only 2-arg constructor above, which never exercises {@link #readResource} or aggregation description. */
    MongoMcpAwareImpl(MetadataSource metadata, MountUriResolver mountResolver, Databases databases, AggregationPipelineSecurityChecker securityChecker) {
        this.metadata = metadata;
        this.mountResolver = mountResolver;
        this.databases = databases;
        this.securityChecker = securityChecker;
    }

    public static MongoMcpAwareImpl create() {
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
    public Optional<McpReadResult> readResource(McpContext ctx, String resourceUri, String action, Map<String, Object> args) {
        if (databases == null) {
            return Optional.empty();
        }

        var effectiveArgs = args == null ? Map.<String, Object>of() : args;

        if ("execute".equals(action)) {
            return executeAggregation(ctx, resourceUri, effectiveArgs);
        }

        var resolved = resolveMount(resourceUri);
        if (resolved == null) {
            return Optional.empty();
        }

        return switch (action) {
            case "query" -> Optional.of(queryDocuments(ctx, resolved, effectiveArgs));
            case "get" -> Optional.of(getSingleDocument(ctx, resolved, effectiveArgs));
            case "size" -> Optional.of(countDocuments(ctx, resolved, effectiveArgs));
            default -> Optional.empty();
        };
    }

    /**
     * Executes an aggregation's {@code execute} action (#617, per the operator's explicit request
     * to allow this once {@code AggregationPipelineSecurityChecker} clears the pipeline) — the same
     * pipeline resolution ({@link AbstractAggregationOperation#getFromJson}), {@code $var} binding
     * ({@link StagesInterpolator}), and execution ({@code MongoCollection.aggregate}) {@code
     * GetAggregationHandler} performs for a real {@code GET}. The security check re-runs here on
     * the fully-bound pipeline (defense in depth — {@link AggregationMcpResourceBuilder} already
     * checked the raw, un-interpolated one before ever marking this {@code readable}), so a
     * blacklist change or an avars-influenced pipeline shape is still caught before execution.
     *
     * <p>Unlike {@link #injectAvars} in the real handler, {@code @page}/{@code @user}/{@code
     * @mongoPermissions} built-in variables are not available here — there is no real request to
     * derive them from. Only explicit {@code $var} bindings from the caller's own {@code avars}
     * work.
     */
    @SuppressWarnings("unchecked")
    private Optional<McpReadResult> executeAggregation(McpContext ctx, String resourceUri, Map<String, Object> args) {
        var split = resourceUri.indexOf("/_aggrs/");
        if (split < 0) {
            return Optional.empty();
        }
        var collectionUri = resourceUri.substring(0, split);
        var aggrName = resourceUri.substring(split + "/_aggrs/".length());

        var resolved = resolveMount(collectionUri);
        if (resolved == null) {
            return Optional.empty();
        }

        var collProps = metadata.collectionProperties(resolved.database(), resolved.collection());
        if (collProps == null) {
            return Optional.empty();
        }

        List<AbstractAggregationOperation> aggregations;
        try {
            aggregations = AbstractAggregationOperation.getFromJson(collProps);
        } catch (Exception e) {
            return Optional.empty();
        }

        var pipeline = aggregations.stream()
                .filter(a -> a.getUri().equals(aggrName))
                .findFirst()
                .filter(AggregationPipeline.class::isInstance)
                .map(AggregationPipeline.class::cast);
        if (pipeline.isEmpty()) {
            return Optional.empty();
        }
        if (securityChecker == null) {
            return Optional.of(errorReadResult("aggregation execution is unavailable"));
        }

        var avars = args.get("avars") instanceof Map<?, ?> m ? BsonUtils.toBsonDocument((Map<String, Object>) m) : new BsonDocument();

        // mirror MongoRequestPropsInjector's real-endpoint behavior: any other flat arg is also
        // bound as an avar (e.g. a bare "status" arg satisfies a pipeline's {"$var": "status"}, and
        // a JSON-shaped one like "[1,2,3]" or "{\"a\":1}" is bound as the real array/document, not
        // a literal string), so a caller need not wrap a single variable in an "avars" object; an
        // explicit "avars" entry always wins over a same-named flat arg
        args.forEach((key, value) -> {
            if (!"avars".equals(key) && !"jsonMode".equals(key) && !avars.containsKey(key) && value != null) {
                avars.put(key, toBsonValue(value));
            }
        });

        List<BsonDocument> stages;
        try {
            stages = StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, pipeline.get().getStages(), avars);
        } catch (QueryVariableNotBoundException e) {
            // A required (non-conditional) $var has no value. A hard, actionable error — never a
            // silent switch to context: resources/read on a readable resource must consistently
            // mean "here is the data" (or a clear reason it couldn't be produced), not sometimes
            // data and sometimes a description depending on which args happened to be supplied
            // (that inconsistency is exactly what collections' bare-read-prefers-documents design
            // was fixing in the first place).
            return Optional.of(errorReadResult(e.getMessage() + " — provide it as a query param, e.g. ?"
                    + e.getMessage().replaceAll(".*variable (\\S+) not bound.*", "$1") + "=..."));
        } catch (Exception e) {
            return Optional.of(errorReadResult(e.getMessage()));
        }

        var stagesArray = new BsonArray();
        stages.forEach(stagesArray::add);

        try {
            securityChecker.validatePipelineOrThrow(stagesArray, resolved.database());
        } catch (SecurityException se) {
            return Optional.of(errorReadResult("aggregation pipeline security violation: " + se.getMessage()));
        }

        var results = new ArrayList<BsonDocument>();
        try {
            var output = databases.collection(Optional.empty(), resolved.database(), resolved.collection())
                    .aggregate(stages)
                    .maxTime(MongoServiceConfiguration.get().getAggregationTimeLimit(), TimeUnit.MILLISECONDS)
                    .allowDiskUse(pipeline.get().getAllowDiskUse().getValue());

            // matches GetAggregationHandler: a $merge/$out-suffixed pipeline writes to a
            // collection rather than returning a result set — running it .into(results) would
            // otherwise return the entire target view, in the worst case the whole collection
            var writesToCollection = !stages.isEmpty() && stages.get(stages.size() - 1).keySet().stream()
                    .anyMatch(k -> "$merge".equals(k) || "$out".equals(k));
            if (writesToCollection) {
                output.toCollection();
            } else {
                output.into(results);
            }
        } catch (MongoCommandException mce) {
            return Optional.of(errorReadResult("error executing aggregation: " + mce.getErrorMessage()));
        }

        var data = new BsonArray();
        results.forEach(data::add);

        // projectResponse applies (an aggregation GET reaches the RESPONSE interceptor that
        // enforces it); readFilter does not — no aggregation handler consults request.getFilter(),
        // so applying it here would make MCP stricter than the REST endpoint it mirrors
        return Optional.of(new McpReadResult(new McpReadResult.RawJson(
                "{\"content\":" + BsonUtils.toJson(project(ctx, data), jsonModeOf(args)) + "}")));
    }

    /**
     * Converts an arbitrary MCP argument value to the BsonValue it should bind to as a {@code
     * $var}: a JSON-shaped string (object, array, number, boolean, quoted string) parses to the
     * real BsonValue it represents; a plain word like {@code A} — not valid JSON on its own — falls
     * back to a literal BsonString, matching how a caller would type it in a URL without bothering
     * to quote it; a non-string value (already a Map/List/Number/Boolean from a JSON-RPC call)
     * converts via the same codec {@link #executeAggregation} uses for the {@code avars} map
     * itself, so nested structures survive intact.
     */
    private static BsonValue toBsonValue(Object value) {
        if (value instanceof String s) {
            try {
                var parsed = BsonUtils.parse(s);
                return parsed != null ? parsed : new BsonString(s);
            } catch (JsonParseException e) {
                return new BsonString(s);
            }
        }
        return BsonUtils.toBsonDocument(Map.of("v", value)).get("v");
    }

    private static McpReadResult errorReadResult(String message) {
        var text = message == null ? "unknown error" : message;
        var escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
        return new McpReadResult(new McpReadResult.RawJson("{\"error\":\"" + escaped + "\"}"));
    }

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

    /**
     * Renders documents as MongoDB Extended JSON via {@link BsonUtils#toJson(BsonValue, JsonMode)}
     * — not {@link BsonJavaConverter}, which is built for collection *metadata* (schemas, MCP
     * resource descriptions) where exotic BSON types like {@code ObjectId} aren't expected and
     * fall back to a plain {@code toString()}; real document data is full of them, and a generic
     * Jackson mapper (which is what ends up serializing an {@code McpReadResult} that isn't a
     * {@link McpReadResult.RawJson}) has no idea how to render an {@code ObjectId} either — hence
     * pre-rendering the whole response text here, respecting the caller's optional {@code
     * jsonMode} (the same query param RESTHeart's real REST API accepts).
     */
    /**
     * The {@code _size} endpoint's answer: how many documents match, exactly as {@code GET
     * /<coll>/_size} would report it — including the same choice of strategy. {@code
     * count=estimated} reads the figure from collection metadata in constant time, and applies
     * only when there is no filter, since {@code estimatedDocumentCount} cannot apply one; asking
     * for both silently falls back to the exact count, which is what the REST endpoint does too.
     */
    @SuppressWarnings("unchecked")
    private McpReadResult countDocuments(McpContext ctx, MongoMountResolver.ResolvedContext resolved, Map<String, Object> args) {
        var requested = args.get("filter") instanceof Map<?, ?> m ? BsonUtils.toBsonDocument((Map<String, Object>) m) : new BsonDocument();
        var filter = and(requested, aclReadFilter(ctx));
        // an ACL read filter makes the estimate wrong for the same reason a requested one does:
        // estimatedDocumentCount counts the whole collection, filter or no filter
        var estimate = "estimated".equals(args.get("count")) && filter.isEmpty();

        var size = databases.getCollectionSize(Optional.empty(), Optional.empty(),
                resolved.database(), resolved.collection(), filter, estimate);

        return new McpReadResult(new McpReadResult.RawJson("{\"size\":" + size + "}"));
    }

    private McpReadResult queryDocuments(McpContext ctx, MongoMountResolver.ResolvedContext resolved, Map<String, Object> args) {
        var requested = args.get("filter") instanceof Map<?, ?> m ? BsonUtils.toBsonDocument((Map<String, Object>) m) : new BsonDocument();
        var filter = and(requested, aclReadFilter(ctx));
        var keys = args.get("keys") instanceof Map<?, ?> m ? BsonUtils.toBsonDocument((Map<String, Object>) m) : null;
        var sort = args.get("sort") instanceof String s && !s.isBlank() ? BsonDocument.parse(s) : new BsonDocument();
        var page = args.get("page") instanceof Integer p ? p : DEFAULT_PAGE;
        var pagesize = args.get("pagesize") instanceof Integer ps ? ps : DEFAULT_PAGESIZE;
        var jsonMode = jsonModeOf(args);

        var docs = databases.getCollectionData(Optional.empty(), Optional.empty(), resolved.database(), resolved.collection(),
                page, pagesize, sort, filter, null, keys, false);

        // No count. Reading a page must cost one query, as the equivalent GET does: RESTHeart's
        // REST API makes counting opt-in (?count, or the dedicated _size endpoint) precisely
        // because countDocuments() walks the collection, and a read that silently paid for it
        // every time would be slower here than through the API it mirrors. The count has its own
        // resource — see the "size" action in CollectionMcpResourceBuilder.
        //
        // "next" is derived from the page actually returned rather than from a total: a full page
        // means there may be more, a short one means there is not. That is all a caller needs to
        // keep paging, and it costs nothing.
        var text = new StringBuilder("{\"content\":")
                .append(BsonUtils.toJson(project(ctx, docs), jsonMode))
                .append(",\"meta\":{\"page\":").append(page)
                .append(",\"returned\":").append(docs.size());
        if (docs.size() == pagesize) {
            text.append(",\"next\":\"?page=").append(page + 1).append('"');
        }
        text.append("}}");

        return new McpReadResult(new McpReadResult.RawJson(text.toString()));
    }

    /** {@code id} is treated as an ObjectId when it looks like one, else as a plain string {@code _id} — not RESTHeart's full doc-id type-inference grammar (prefixed encodings for other BSON types), which is out of scope here. */
    private McpReadResult getSingleDocument(McpContext ctx, MongoMountResolver.ResolvedContext resolved, Map<String, Object> args) {
        var id = args.get("id") instanceof String s ? s : null;
        var filter = and(new BsonDocument("_id", idValue(id)), aclReadFilter(ctx));
        var jsonMode = jsonModeOf(args);

        var docs = databases.getCollectionData(Optional.empty(), Optional.empty(), resolved.database(), resolved.collection(),
                1, 1, new BsonDocument(), filter, null, null, false);

        // A document the read filter excludes must be indistinguishable from one that does not
        // exist — anything else turns the filter into an existence oracle.
        var json = docs.isEmpty() ? "null" : BsonUtils.toJson(project(ctx, docs.get(0)), jsonMode);
        return new McpReadResult(new McpReadResult.RawJson(json));
    }

    // ---------------------------------------------------------------------------------------
    // ACL enforcement for reads performed in-process (restheart#722)
    //
    // A documents-mode resources/read never travels the HTTP pipeline, so neither
    // mongoPermissionFilters nor mongoPermissionProjectResponse — the two interceptors that apply
    // mongo.readFilter and mongo.projectResponse on the REST path — ever runs for it. The
    // permission that authorized this very operation is carried on the McpContext instead, and
    // applied here, against the same request it was matched against so its @user/%ROLES variables
    // resolve to the same values a real GET would resolve them to.
    // ---------------------------------------------------------------------------------------

    /** The ACL read filter for this operation, interpolated, or {@code null} if the permission declares none. */
    private static BsonDocument aclReadFilter(McpContext ctx) {
        var permissions = mongoPermissions(ctx);
        if (permissions == null || permissions.getReadFilter() == null) {
            return null;
        }
        return AclVarsInterpolator.interpolateBson(ctx.authorization().request(), permissions.getReadFilter()).asDocument();
    }

    /** Hides whatever {@code mongo.projectResponse} hides, using the interceptor's own implementation. */
    private static BsonValue project(McpContext ctx, BsonValue content) {
        var permissions = mongoPermissions(ctx);
        return permissions == null ? content : ProjectResponse.project(content, permissions.getProjectResponse());
    }

    private static MongoPermissions mongoPermissions(McpContext ctx) {
        if (ctx == null || ctx.authorization() == null || ctx.authorization().permission() == null) {
            return null;
        }
        try {
            return MongoPermissions.from(ctx.authorization().permission());
        } catch (Exception e) {
            // A permission whose mongo section cannot be read must not widen the read: treat it as
            // no permission at all, which leaves the caller with whatever the base allow granted.
            LOGGER.warn("cannot read the mongo permissions of the ACL entry that authorized this read", e);
            return null;
        }
    }

    /** How a caller's filter and the ACL's are combined — the same composition {@code ?filter} uses when it repeats. */
    private static BsonDocument and(BsonDocument requested, BsonDocument acl) {
        return BsonUtils.and(Arrays.asList(requested, acl));
    }

    private static BsonValue idValue(String id) {
        return id != null && ObjectId.isValid(id) ? new BsonObjectId(new ObjectId(id)) : new BsonString(id);
    }

    /** {@code jsonMode} mirrors the query param RESTHeart's real REST API accepts (e.g. {@code STRICT}/{@code RELAXED}/{@code SHELL}); an absent or unrecognized value falls back to {@link BsonUtils#toJson(BsonValue, JsonMode)}'s own default. */
    private static JsonMode jsonModeOf(Map<String, Object> args) {
        if (args.get("jsonMode") instanceof String s) {
            try {
                return JsonMode.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall through to the default
            }
        }
        return null;
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
