/*-
 * ========================LICENSE_START=================================
 * restheart-ai
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.ai.mcp;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.restheart.ai.mcp.tools.CachedResourceLookup;
import org.restheart.ai.mcp.tools.HowToCallTool;
import org.restheart.ai.mcp.tools.ListApisTool;
import org.restheart.ai.mcp.tools.UnknownActionException;
import org.restheart.ai.mcp.tools.UnknownResourceException;
import org.restheart.ai.mcp.tools.ValidationFailedException;
import org.restheart.ai.mcp.validation.ParamValidator;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.exchange.Request;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.mcp.McpContext;
import org.restheart.plugins.mcp.McpReadResult;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.plugins.mcp.McpResourceTemplate;
import org.restheart.plugins.security.RequestDescriptor;
import org.restheart.security.BaseAccount;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.json.schema.jackson3.JacksonJsonSchemaValidatorSupplier;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.undertow.server.HttpServerExchange;

/**
 * RESTHeart's MCP server: exposes exactly two tools, {@code list_apis} and
 * {@code how_to_call}, over the MCP Streamable HTTP transport (restheart#615
 * design principles — "one tool, one model", no per-resource tools).
 *
 * <p>Transport is {@link UndertowStreamableServerTransportProvider}, ported from
 * Sophia (already running there in production) — session lifecycle, SSE for
 * tool-call responses and server notifications, and stale-session auto-recovery
 * all come from it; this class only wires RESTHeart's own {@link ListApisTool}/
 * {@link HowToCallTool} into the MCP SDK's tool-call dispatch and resolves the
 * per-request {@code principal}/{@code baseUrl} the tools need.
 *
 * <p>Both tools report execution failures (unknown resource, unknown action,
 * failed validation) uniformly as an {@code isError: true} {@link CallToolResult}
 * with a plain-text message — the MCP SDK's own convention (a tool call failing
 * is not a JSON-RPC protocol error) — rather than a distinct JSON-RPC error code
 * per failure kind.
 */
@RegisterPlugin(
        name = "mcpService",
        description = "RESTHeart MCP server — exposes MCP-enabled APIs to AI agents via the Model Context Protocol",
        defaultURI = "/mcp",
        secure = true)
public class McpService implements ByteArrayService {
    private static final Logger LOGGER = LoggerFactory.getLogger(McpService.class);

    private static final String CTX_PRINCIPAL = "principal";
    private static final String CTX_BASE_URL = "baseUrl";

    private static final int DEFAULT_CATALOG_TTL_SECONDS = 300;

    @Inject("registry")
    private PluginsRegistry pluginsRegistry;

    @Inject("config")
    private Map<String, Object> config;

    private UndertowStreamableServerTransportProvider provider;
    private ListApisTool listApisTool;
    private HowToCallTool howToCallTool;
    private McpJsonMapper jsonMapper;
    private CachedResourceLookup resourceLookup;
    private McpSyncServer server;

    /**
     * Absolute base URL used for the MCP {@code resources} primitive (#617) — {@code null}
     * disables it entirely. Unlike {@code list_apis}/{@code how_to_call}, which resolve
     * {@code baseUrl} fresh per request (see {@link #resolveBaseUrl}), the official MCP SDK's
     * resources API (verified against its bytecode: {@code McpSyncServer.addResource}/
     * {@code removeResource}) is a single mutable registry for the whole server, not a
     * per-request computation — so it needs one canonical, operator-configured URL rather than
     * whatever a given request's {@code Host}/{@code X-Forwarded-*} headers happen to say.
     */
    private String publicBaseUrl;

    /** Guards the one-time initial resource-registry population — see {@link #handle} and {@link #init()}'s comment on why it can't happen in {@code init()} itself. */
    private final AtomicBoolean resourcesInitialized = new AtomicBoolean(false);

    @OnInit
    public void init() {
        var mcpAwareRegistry = McpAwareRegistry.discover(pluginsRegistry);

        jsonMapper = new JacksonMcpJsonMapperSupplier().get();
        var schemaValidator = new JacksonJsonSchemaValidatorSupplier().get();

        provider = new UndertowStreamableServerTransportProvider(jsonMapper);

        publicBaseUrl = config != null && config.get("public-base-url") instanceof String s && !s.isBlank() ? s : null;

        // Catalog data is cached for this TTL rather than invalidated by watching every
        // McpAware implementation's own data source (a MongoDB write, a config change, ...) —
        // one uniform mechanism for all of them, trading instant consistency for a bounded
        // staleness window. On expiry, connected agents are told to refetch.
        var catalogTtlSeconds = argOrDefault(config, "catalog-ttl-seconds", DEFAULT_CATALOG_TTL_SECONDS);
        resourceLookup = new CachedResourceLookup(mcpAwareRegistry, Duration.ofSeconds(catalogTtlSeconds), this::onCatalogExpired);
        listApisTool = new ListApisTool(resourceLookup);
        howToCallTool = new HowToCallTool(resourceLookup);

        var serverBuilder = McpServer.sync(provider)
                .serverInfo("restheart-mcp", "1.0.0")
                .jsonMapper(jsonMapper)
                .jsonSchemaValidator(schemaValidator)
                .capabilities(capabilities())
                .toolCall(listApisToolDefinition(), this::callListApis)
                .toolCall(howToCallToolDefinition(), this::callHowToCall);

        // Neither resources nor templates are registered here: both are built by calling
        // describeMcp()/describeTemplates() on every registered McpAware plugin, and @OnInit
        // methods run in an unspecified cross-plugin order (see OnInit's own javadoc) — another
        // plugin's own @OnInit (e.g. GraphQLService's) may not have run yet, leaving its internal
        // state null and throwing (confirmed live). list_apis/how_to_call avoid this by only ever
        // calling describeMcp() from an actual incoming request, which can't happen before every
        // plugin's @OnInit has completed — resources and templates must be seeded the same way,
        // in handle() on first real traffic, not here.
        server = serverBuilder.build();

        // Without this, a client's open GET/SSE stream (or an in-flight tool-call's SSE
        // response) blocks its worker thread forever inside
        // UndertowStreamableServerTransportProvider's queue.take() loop — nothing signals
        // it to unblock on its own. RESTHeart's graceful shutdown then waits for that
        // worker thread to finish and hangs. closeGracefully() closes every open session
        // (and so every associated queue), which is what actually lets the pending
        // request complete. Same fix Sophia's own MCP service applies for the same reason.
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
            LOGGER.info("MCP shutdown: closing transport provider...");
            provider.closeGracefully().block();
            LOGGER.info("MCP shutdown: transport provider closed.");
        }));

        LOGGER.info("MCP service initialized on {} (Streamable HTTP transport)", "/mcp");
        if (publicBaseUrl != null) {
            LOGGER.info("MCP resources primitive enabled, public-base-url={}", publicBaseUrl);
        }
    }

    private McpSchema.ServerCapabilities capabilities() {
        var builder = McpSchema.ServerCapabilities.builder().tools(true);
        if (publicBaseUrl != null) {
            // subscribe not yet implemented (#617 phase 5) — listChanged only for now
            builder.resources(false, true);
        }
        return builder.build();
    }

    /**
     * Runs once per catalog cache entry that expires (see {@link CachedResourceLookup}): tells
     * already-connected agents to refetch tools, and — if the resources primitive is enabled —
     * re-syncs the MCP SDK's resource registry against the same fresh catalog.
     */
    private void onCatalogExpired() {
        notifyToolsListChanged();
        if (publicBaseUrl != null) {
            syncResourceRegistry();
        }
    }

    private void notifyToolsListChanged() {
        provider.notifyClients("notifications/tools/list_changed", null)
                .subscribe(v -> {}, err -> LOGGER.warn("Failed to notify clients of tools/list_changed: {}", err.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Resources primitive (#617): resources/list, resources/templates/list,
    // resources/read. A resource is documents-only — reading it always returns
    // real data, never a description of how to call it (that's list_apis/
    // how_to_call's job, exclusively — one channel per concern, not two ways to
    // say the same thing). A catalog entry with no readable action at all (a
    // database container, a change stream, an aggregation a security check
    // rejected, ...) is therefore never registered as an MCP resource — it
    // stays reachable through list_apis/how_to_call only.
    // resources/subscribe is still a later phase.
    // -------------------------------------------------------------------------

    /**
     * Full remove-then-readd, rather than diffing — simpler, and correct even when a resource's
     * content (not just its existence) changed: each {@code SyncResourceSpecification}'s read
     * handler closes over a specific {@code McpResource} snapshot, so an in-place content change
     * still needs a fresh registration to be reflected. Also doubles as the very first
     * population (called from {@code handle()} on first traffic, see {@link #init()}'s comment
     * on why it can't happen any earlier) — {@code server.listResources()} is simply empty then,
     * so "remove current" is a no-op and this just adds the initial set.
     *
     * <p>Only catalog entries with at least one {@code readable} action are registered — the rest
     * (databases, change streams, a non-readable aggregation, ...) are real catalog entries for
     * {@code list_apis}/{@code how_to_call}, just not MCP resources: {@code resources/read} must
     * always return real data, and there's nothing document-shaped to return for those.
     *
     * <p>A readable resource whose default action has at least one genuinely required parameter
     * (e.g. an aggregation's {@code $var} with no default, per {@link PipelineParamScanner}) is
     * registered as a <em>template</em>, not a concrete resource: a concrete resource has no
     * per-read arguments at all — reading its bare URI is exactly what {@link #readBareResource}
     * does, with no parameters supplied — so offering one for an action that would just throw on
     * missing arguments invites exactly that. A template's URI at least names the parameter the
     * client needs to fill in (MCP resource templates carry no formal schema — no client-enforced
     * "required" — but naming it beats a bare, guaranteed-to-fail concrete resource).
     */
    private void syncResourceRegistry() {
        server.listResources().stream().map(McpSchema.Resource::uri).toList().forEach(server::removeResource);
        server.listResourceTemplates().stream().map(McpSchema.ResourceTemplate::uriTemplate).toList().forEach(server::removeResourceTemplate);

        resourceLookup.all(null, publicBaseUrl).forEach(r -> {
            var actionName = defaultReadableAction(r);
            if (actionName == null) {
                return;
            }
            var requiredParams = requiredFlatParamNames(r.actions().get(actionName));
            if (requiredParams.isEmpty()) {
                server.addResource(toResourceSpec(r));
            } else {
                server.addResourceTemplate(toTemplateSpec(requiredParamsTemplate(r, requiredParams)));
            }
        });

        resourceLookup.templates(publicBaseUrl).forEach(t -> server.addResourceTemplate(toTemplateSpec(t)));

        server.notifyResourcesListChanged();
    }

    /**
     * The flat query-param names a caller must supply for {@code action} to avoid a bound-variable
     * error — an object param's (i.e. {@code avars}) required properties surface by their own
     * name, since the flat-query-param shorthand (see {@code MongoRequestPropsInjector}) binds
     * them individually, not nested under {@code avars}.
     */
    private static List<String> requiredFlatParamNames(McpResource.Action action) {
        var names = new ArrayList<String>();
        action.params().forEach((name, param) -> {
            if ("object".equals(param.type()) && param.properties() != null) {
                param.properties().forEach((propName, propParam) -> {
                    if (propParam.required()) {
                        names.add(propName);
                    }
                });
            } else if (param.required()) {
                names.add(name);
            }
        });
        return names;
    }

    private static McpResourceTemplate requiredParamsTemplate(McpResource resource, List<String> requiredParams) {
        var uriTemplate = resource.uri() + "{?" + String.join(",", requiredParams) + "}";
        var description = resource.description() + " (requires: " + String.join(", ", requiredParams) + ")";
        return new McpResourceTemplate(uriTemplate, resourceName(resource), resourceName(resource), description);
    }

    private McpServerFeatures.SyncResourceSpecification toResourceSpec(McpResource resource) {
        var sdkResource = McpSchema.Resource.builder()
                .uri(resource.uri())
                .name(resourceName(resource))
                .description(resource.description())
                .mimeType("application/json")
                .build();
        return new McpServerFeatures.SyncResourceSpecification(sdkResource,
                (exchange, request) -> readBareResource(principal(exchange.transportContext()), resource));
    }

    /**
     * Reads the bare resource URI — the SDK's own exact-URI match (verified against its bytecode:
     * it tries concrete {@link McpServerFeatures.SyncResourceSpecification}s registered via {@link
     * #toResourceSpec} before ever falling back to a template) always routes here for a known
     * resource's own URI, never through {@link #readTemplateMatch}.
     *
     * <p>Documents-mode with no filter (default pagination) — matches what attaching this resource
     * in a host UI (Claude Desktop, Cursor, ...) actually means: load its real content. {@code
     * actionName} is null only defensively (a resource can reach here via {@link
     * #readTemplateMatch}'s exact-match fallback without having gone through {@link
     * #syncResourceRegistry}'s readable-only filter); in that case there's nothing to read.
     */
    private McpSchema.ReadResourceResult readBareResource(BaseAccount principal, McpResource resource) {
        var actionName = defaultReadableAction(resource);
        if (actionName == null) {
            return errorResourceResult(resource.uri(), "resource has no readable data; use how_to_call to invoke its actions");
        }
        return readOperation(principal, resource.uri(), actionName, Map.of())
                .orElseGet(() -> errorResourceResult(resource.uri(), "failed to read resource"));
    }

    /** {@code query} is preferred (the natural "give me the collection" action) over any other {@code readable} action a future kind might declare. */
    private static String defaultReadableAction(McpResource resource) {
        if (resource.actions().get("query") instanceof McpResource.Action a && a.readable()) {
            return "query";
        }
        return resource.actions().entrySet().stream()
                .filter(e -> e.getValue().readable())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /** Display label for a resource — the last path segment of its URI (e.g. {@code "inventory"}, {@code "byStatus"}); not required to be unique. */
    static String resourceName(McpResource resource) {
        var path = resource.uri().replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/]+", "");
        var lastSegment = path.substring(path.lastIndexOf('/') + 1);
        return lastSegment.isBlank() ? resource.uri() : lastSegment;
    }

    /**
     * Wraps a {@link McpResourceTemplate} contributed by a plugin's {@code describeTemplates(ctx)}
     * (e.g. Mongo's, mount-aware via {@code MountUriResolver} — see #617) into the MCP SDK's own
     * registry entry. A template only advertises URI *shape* — it never bypasses the opt-in
     * requirement: its read handler looks up the requested URI in the same cached catalog as
     * everything else, so a syntactically-matching URI for a resource that isn't {@code
     * mcp.enabled} still fails, exactly as it would via {@code list_apis}/{@code how_to_call}.
     */
    private McpServerFeatures.SyncResourceTemplateSpecification toTemplateSpec(McpResourceTemplate resourceTemplate) {
        var template = McpSchema.ResourceTemplate.builder(resourceTemplate.uriTemplate(), resourceTemplate.name())
                .title(resourceTemplate.title())
                .description(resourceTemplate.description())
                .mimeType("application/json")
                .build();
        return new McpServerFeatures.SyncResourceTemplateSpecification(template, (exchange, request) -> readTemplateMatch(exchange.transportContext(), request.uri()));
    }

    /**
     * A URI matching a template's shape still must be an actual, currently mcp-enabled resource —
     * same lookup {@code how_to_call} uses. Also the sole entry point for documents-mode (#617
     * Phase 2/2b): the MCP SDK's own URI-template matching (verified against its bytecode — its
     * {@code McpUriTemplateManager} is a simplified regex substitution, not full RFC 6570 — a
     * bare {@code {collection}} placeholder already matches a query-string-bearing URI, since its
     * capture group is {@code [^/]+} with no anchoring against {@code ?}) routes a documents-mode
     * URI here; an exact concrete-resource URI (never carrying a query string or extra path
     * segment) is instead routed straight to {@link #readBareResource} by the SDK before this is
     * ever called.
     *
     * <p>Mode detection purely from the URI shape:
     * <ul>
     *   <li>a query string ({@code ?...}) → documents mode, using whichever action {@link
     *       #defaultReadableAction} picks for the matched resource (e.g. {@code query} for a
     *       collection, {@code execute} for an aggregation — <b>not</b> hardcoded to {@code
     *       query}, since an aggregation has no action by that name)</li>
     *   <li>no exact catalog match, but stripping the last path segment does match → single
     *       document mode, action {@code get}, {@code id} = that segment</li>
     *   <li>otherwise, an exact catalog match → {@link #readBareResource} (documents-mode default
     *       action)</li>
     * </ul>
     */
    private McpSchema.ReadResourceResult readTemplateMatch(McpTransportContext ctx, String uri) {
        var principal = principal(ctx);

        var queryIdx = uri.indexOf('?');
        if (queryIdx >= 0) {
            var base = uri.substring(0, queryIdx);
            var args = parseQueryArgs(uri.substring(queryIdx + 1));
            var resource = resourceLookup.find(principal, publicBaseUrl, base);
            if (resource.isEmpty()) {
                return unknownResourceResult(uri);
            }
            var actionName = defaultReadableAction(resource.get());
            if (actionName == null) {
                // a real catalog entry (e.g. an aggregation whose pipeline didn't clear the
                // security checker), but nothing document-shaped to read — resources/read must
                // always mean "here is data", never a description; use how_to_call for this one
                return errorResourceResult(base, "resource has no readable data; use how_to_call to invoke its actions");
            }
            return readOperation(principal, base, actionName, args).orElseGet(() -> unknownResourceResult(uri));
        }

        // Defensive: in practice the SDK's own exact-match check (see readBareResource's javadoc)
        // means this never actually fires for a bare URI, since that's always caught by the
        // concrete SyncResourceSpecification's own handler first.
        var exact = resourceLookup.find(principal, publicBaseUrl, uri);
        if (exact.isPresent()) {
            return readBareResource(principal, exact.get());
        }

        var lastSlash = uri.lastIndexOf('/');
        if (lastSlash > 0) {
            var base = uri.substring(0, lastSlash);
            var id = uri.substring(lastSlash + 1);
            var singleDoc = readOperation(principal, base, "get", Map.of("id", id));
            if (singleDoc.isPresent()) {
                return singleDoc.get();
            }
        }

        return unknownResourceResult(uri);
    }

    private static McpSchema.ReadResourceResult unknownResourceResult(String uri) {
        return new McpSchema.ReadResourceResult(
                List.of(new TextResourceContents(uri, "text/plain", "Error: unknown or not MCP-enabled resource: " + uri)));
    }

    /**
     * Documents-mode dispatch for one candidate base resource URI (#617 Phase 2/2b) — {@code
     * Optional.empty()} only when {@code resourceUri} itself matches no catalog resource at all
     * (so the caller can try a different URI-shape interpretation, or finally report "unknown
     * resource"). Every other outcome — no such {@code readable} action, failed validation, the
     * plugin declining, or the plugin actually returning content — is a definite result: real
     * data, or a hard error (never a description of the resource — that's never what {@code
     * resources/read} returns; use {@code list_apis}/{@code how_to_call} for that).
     *
     * <p><b>Known gap:</b> the actual ACL read-filter/projection a {@code DescriptorAwareAuthorizer}
     * resolves while authorizing this operation (restheart#722) is not yet threaded through to
     * the {@code readResource()} call below — only the base allow/deny decision is enforced
     * end-to-end today. Tracked as a #617 follow-up.
     */
    private Optional<McpSchema.ReadResourceResult> readOperation(BaseAccount principal, String resourceUri, String actionName, Map<String, Object> rawArgs) {
        var resourceOpt = resourceLookup.find(principal, publicBaseUrl, resourceUri);
        if (resourceOpt.isEmpty()) {
            return Optional.empty();
        }
        var resource = resourceOpt.get();

        var action = resource.actions().get(actionName);
        if (action == null || !action.readable()) {
            return Optional.of(errorResourceResult(resourceUri, "resource has no readable data; use how_to_call to invoke its actions"));
        }

        var args = coerceArgs(rawArgs, action);
        var errors = ParamValidator.validate(action, args);
        if (!errors.isEmpty()) {
            return Optional.of(errorResourceResult(resourceUri, String.join("; ", errors)));
        }

        var owner = resourceLookup.findOwner(principal, publicBaseUrl, resourceUri);
        if (owner.isEmpty()) {
            return Optional.of(errorResourceResult(resourceUri, "internal error: resource owner not found"));
        }

        var readCtx = new McpContext(principal, publicBaseUrl, owner.get().pluginName(), owner.get().pluginUri(), owner.get().pluginConfiguration());

        try {
            return Optional.of(owner.get().instance().readResource(readCtx, resourceUri, actionName, args)
                    .map(result -> toReadResourceResult(resourceUri, result))
                    .orElseGet(() -> errorResourceResult(resourceUri, "failed to read resource")));
        } catch (Exception e) {
            LOGGER.error("readResource failed for {} action {}", resourceUri, actionName, e);
            return Optional.of(errorResourceResult(resourceUri, "internal error: " + e.getMessage()));
        }
    }

    /**
     * {@link McpReadResult.RawJson} content is embedded verbatim — it's already correctly
     * rendered JSON from the plugin's own native format (e.g. {@code MongoMcpAwareImpl} uses
     * MongoDB Extended JSON, which a generic Jackson mapper can't reproduce for types like {@code
     * ObjectId}). Anything else is a plain Java object graph the framework's own mapper serializes.
     */
    private McpSchema.ReadResourceResult toReadResourceResult(String uri, McpReadResult result) {
        try {
            String text;
            if (result.content() instanceof McpReadResult.RawJson raw) {
                text = raw.json();
            } else {
                Object payload = result.meta() == null ? result.content() : Map.of("content", result.content(), "meta", result.meta());
                text = jsonMapper.writeValueAsString(payload);
            }
            return new McpSchema.ReadResourceResult(List.of(new TextResourceContents(uri, "application/json", text)));
        } catch (Exception e) {
            LOGGER.error("Failed to serialize documents-mode read result for {}", uri, e);
            return errorResourceResult(uri, "internal error: " + e.getMessage());
        }
    }

    private static McpSchema.ReadResourceResult errorResourceResult(String uri, String message) {
        return new McpSchema.ReadResourceResult(List.of(new TextResourceContents(uri, "text/plain", "Error: " + message)));
    }

    /** Parses a raw {@code key=value&...} query string into string-valued args — types are coerced afterward, once the target action's declared param types are known. */
    private static Map<String, Object> parseQueryArgs(String queryString) {
        var args = new LinkedHashMap<String, Object>();
        for (var pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            var eq = pair.indexOf('=');
            var key = URLDecoder.decode(eq >= 0 ? pair.substring(0, eq) : pair, StandardCharsets.UTF_8);
            var value = URLDecoder.decode(eq >= 0 ? pair.substring(eq + 1) : "", StandardCharsets.UTF_8);
            args.put(key, value);
        }
        return args;
    }

    /**
     * Query-string args arrive as raw strings; coerces each to the type its action declares (e.g.
     * {@code page} to an integer, {@code filter} to a parsed JSON object) so {@link ParamValidator}
     * sees the right Java type.
     *
     * <p>Also synthesizes any declared object-typed param that has named sub-properties (e.g. an
     * aggregation's {@code avars}, one property per {@code $var} it references) from flat
     * top-level keys, when the object itself wasn't explicitly provided — so {@code
     * ?status=A&limit=5} works exactly like {@code ?avars=\{"status":"A","limit":5\}}. Fully
     * generic — not aggregation-specific: any resource kind whose schema declares an object param
     * with {@code properties} benefits from this. {@code how_to_call}'s own composed URL still
     * needs the nested form (real RESTHeart REST API requirement, unrelated to this in-process
     * dispatch), so this only matters for {@code resources/read}.
     */
    private Map<String, Object> coerceArgs(Map<String, Object> rawArgs, McpResource.Action action) {
        var coerced = new LinkedHashMap<>(rawArgs);
        action.params().forEach((name, param) -> {
            // Only when the object param is entirely absent — not when it's present but not yet
            // coerced (e.g. a raw "?avars={...}" JSON string still awaiting the coercion step
            // below): checking `instanceof Map` here instead would run synthesis before that
            // string is parsed, clobbering an explicitly-provided nested value with the
            // synthesized one.
            if ("object".equals(param.type()) && param.properties() != null && !param.properties().isEmpty()
                    && !coerced.containsKey(name)) {
                var synthesized = new LinkedHashMap<String, Object>();
                param.properties().forEach((propName, propParam) -> {
                    if (coerced.containsKey(propName)) {
                        synthesized.put(propName, coerceScalar(coerced.get(propName), propParam.type()));
                    }
                });
                if (!synthesized.isEmpty()) {
                    coerced.put(name, synthesized);
                }
            }

            if (coerced.get(name) instanceof String) {
                coerced.put(name, coerceScalar(coerced.get(name), param.type()));
            }
        });
        return coerced;
    }

    /** Coerces a query-string-sourced raw {@link String} value to the declared param type; any non-{@code String} value (already the right shape, or absent/{@code null}) passes through unchanged. */
    @SuppressWarnings("unchecked")
    private Object coerceScalar(Object value, String type) {
        if (type == null || !(value instanceof String raw)) {
            return value;
        }
        try {
            return switch (type) {
                case "integer" -> Integer.parseInt(raw);
                case "number" -> Double.parseDouble(raw);
                case "boolean" -> Boolean.parseBoolean(raw);
                case "object" -> jsonMapper.readValue(raw, Map.class);
                case "array" -> jsonMapper.readValue(raw, List.class);
                default -> raw;
            };
        } catch (Exception e) {
            // leave the raw string in place; ParamValidator reports the resulting type mismatch
            return raw;
        }
    }

    // -------------------------------------------------------------------------
    // Authorization for documents-mode reads (restheart#722)
    // -------------------------------------------------------------------------

    /**
     * Overrides the default (identity) — see {@code Service#operationsToAuthorize} — for exactly
     * one case: a {@code resources/read} JSON-RPC call whose URI resolves to a documents-mode
     * read (a {@code readable} action on a known resource — which, since only such resources are
     * ever registered with the resources primitive, is every successful {@code resources/read}).
     * Every other request handled by {@code /mcp} (tools, {@code initialize}, ...) returns the
     * identity descriptor unchanged, since none of them execute a real, in-process data operation
     * server-side that a REST-shaped ACL rule could meaningfully apply to.
     *
     * <p>Reads the request body via {@link ByteArrayRequest#getContent()} — safe to do this early
     * (before {@code handle()} itself parses the same body): {@code ServiceRequest} caches the
     * parsed content on the exchange after the first read, so this doesn't consume the channel a
     * second time or interfere with the real dispatch that follows once authorization passes.
     */
    @Override
    public List<RequestDescriptor> operationsToAuthorize(HttpServerExchange exchange) {
        if (publicBaseUrl != null) {
            try {
                var descriptor = documentsModeDescriptor(exchange);
                if (descriptor != null) {
                    return List.of(descriptor);
                }
            } catch (Exception e) {
                LOGGER.warn("operationsToAuthorize: failed to inspect /mcp request body, treating as non-documents-mode", e);
            }
        }
        return List.of(RequestDescriptor.of(exchange));
    }

    /** @return a descriptor for the underlying REST-equivalent operation, or {@code null} if this request isn't a documents-mode-eligible {@code resources/read}. */
    private RequestDescriptor documentsModeDescriptor(HttpServerExchange exchange) throws Exception {
        var body = ByteArrayRequest.of(exchange).getContent();
        if (body == null || body.length == 0) {
            return null;
        }

        if (!(McpSchema.deserializeJsonRpcMessage(jsonMapper, new String(body, StandardCharsets.UTF_8))
                instanceof McpSchema.JSONRPCRequest rpcReq) || !"resources/read".equals(rpcReq.method())) {
            return null;
        }

        var params = rpcReq.params() instanceof Map<?, ?> m ? m : Map.of();
        if (!(params.get("uri") instanceof String uri)) {
            return null;
        }

        var identity = RequestDescriptor.of(exchange);
        var principal = identity.principal();

        var queryIdx = uri.indexOf('?');
        if (queryIdx >= 0) {
            var base = uri.substring(0, queryIdx);
            if (!isReadableAction(principal, base, "query")) {
                return null;
            }
            return withMethodPathAndQuery(identity, pathOf(base), parseQueryParameters(uri.substring(queryIdx + 1)));
        }

        var exact = resourceLookup.find(principal, publicBaseUrl, uri);
        if (exact.isPresent()) {
            // A bare resource read defaults to documents-mode whenever the resource has a
            // readable action (McpService.readBareResource, #617) — a real backend read that
            // must be authorized exactly like its REST-equivalent GET. A resource with no
            // readable action has nothing to authorize (and, since it's never registered with
            // the resources primitive, can't actually be reached this way by a normal client).
            var actionName = defaultReadableAction(exact.get());
            return actionName == null ? null : withMethodPathAndQuery(identity, pathOf(uri), Map.of());
        }

        var lastSlash = uri.lastIndexOf('/');
        if (lastSlash > 0 && isReadableAction(principal, uri.substring(0, lastSlash), "get")) {
            return withMethodPathAndQuery(identity, pathOf(uri), Map.of());
        }

        return null;
    }

    private boolean isReadableAction(BaseAccount principal, String resourceUri, String actionName) {
        return resourceLookup.find(principal, publicBaseUrl, resourceUri)
                .map(McpResource::actions)
                .map(actions -> actions.get(actionName))
                .map(McpResource.Action::readable)
                .orElse(false);
    }

    private static RequestDescriptor withMethodPathAndQuery(RequestDescriptor identity, String path, Map<String, Deque<String>> queryParameters) {
        return new RequestDescriptor(identity.principal(), "GET", path, queryParameters,
                identity.headers(), identity.cookies(), identity.remoteAddress(), identity.scheme());
    }

    private static String pathOf(String absoluteUri) {
        try {
            return new URI(absoluteUri).getPath();
        } catch (Exception e) {
            return absoluteUri;
        }
    }

    private static Map<String, Deque<String>> parseQueryParameters(String queryString) {
        var result = new LinkedHashMap<String, Deque<String>>();
        for (var pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            var eq = pair.indexOf('=');
            var key = URLDecoder.decode(eq >= 0 ? pair.substring(0, eq) : pair, StandardCharsets.UTF_8);
            var value = URLDecoder.decode(eq >= 0 ? pair.substring(eq + 1) : "", StandardCharsets.UTF_8);
            result.computeIfAbsent(key, k -> new ArrayDeque<>()).add(value);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // ByteArrayService contract
    // -------------------------------------------------------------------------

    @Override
    public void handle(ByteArrayRequest req, ByteArrayResponse res) throws Exception {
        // First real request to /mcp — the earliest point every plugin's own @OnInit is
        // guaranteed to have completed (the HTTP listener doesn't start serving until plugin
        // init finishes), so it's the earliest SAFE point to call describeMcp() on other
        // plugins. See init()'s comment: doing this inside mcpService's own @OnInit crashed on
        // GraphQLService's not-yet-initialized state (cross-plugin @OnInit order is unspecified).
        if (publicBaseUrl != null && resourcesInitialized.compareAndSet(false, true)) {
            syncResourceRegistry();
        }

        if (req.isOptions()) {
            handleOptions(req);
            return;
        }

        var ctx = buildContext(req);

        if (req.isPost()) {
            provider.handlePost(req, res, ctx);
        } else if (req.isGet()) {
            provider.handleGet(req, res, ctx);
        } else if (req.isDelete()) {
            provider.handleDelete(req, res, ctx);
        } else {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    @Override
    public String accessControlAllowMethods(Request<?> request) {
        return "GET, POST, DELETE";
    }

    @Override
    public String accessControlAllowHeaders(Request<?> request) {
        return "Authorization, Content-Type, X-Requested-With, No-Auth-Challenge, mcp-session-id, mcp-protocol-version";
    }

    @Override
    public String accessControlExposeHeaders(Request<?> request) {
        return "mcp-session-id";
    }

    // -------------------------------------------------------------------------
    // Per-request context: principal + public base URL
    // -------------------------------------------------------------------------

    private McpTransportContext buildContext(ByteArrayRequest req) {
        var ctx = new HashMap<String, Object>();
        ctx.put(CTX_BASE_URL, resolveBaseUrl(req));
        if (req.getAuthenticatedAccount() instanceof BaseAccount principal) {
            ctx.put(CTX_PRINCIPAL, principal);
        }
        return McpTransportContext.create(ctx);
    }

    /** Mirrors {@code OAuthProtectedResourceMetadataService.resolveServerUrl} (restheart-security). */
    private static String resolveBaseUrl(ByteArrayRequest req) {
        var exchange = req.getExchange();
        var headers = exchange.getRequestHeaders();

        var forwardedProto = headers.getFirst("X-Forwarded-Proto");
        var forwardedHost = headers.getFirst("X-Forwarded-Host");
        if (forwardedProto != null && forwardedHost != null) {
            return forwardedProto + "://" + forwardedHost;
        }

        var host = headers.getFirst("Host");
        if (host != null) {
            return exchange.getRequestScheme() + "://" + host;
        }

        return "";
    }

    private static BaseAccount principal(McpTransportContext ctx) {
        return ctx.get(CTX_PRINCIPAL) instanceof BaseAccount ba ? ba : null;
    }

    private static String baseUrl(McpTransportContext ctx) {
        return ctx.get(CTX_BASE_URL) instanceof String s ? s : "";
    }

    // -------------------------------------------------------------------------
    // Tool definitions
    // -------------------------------------------------------------------------

    static McpSchema.Tool listApisToolDefinition() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("resource", schemaProp("string", "Optional. Omit for the catalog."));
        properties.put("query", schemaProp("string",
                "Optional. Case-insensitive substring match against each resource's uri/kind/description. Ignored if `resource` is given."));
        properties.put("kind", schemaProp("string",
                "Optional. Restrict the catalog to a single kind (e.g. `collection`, `service`). Ignored if `resource` is given."));
        properties.put("limit", schemaProp("integer",
                "Optional. Max catalog entries to return (default: framework-configured page size). Ignored if `resource` is given."));
        properties.put("cursor", schemaProp("string", "Optional. Continues a previous paged catalog call."));

        return McpSchema.Tool.builder("list_apis")
                .description("Lists or describes MCP-enabled APIs exposed by RESTHeart. Without arguments, returns the "
                        + "catalog (URIs, kinds, short descriptions) — optionally narrowed with `query`/`kind` and "
                        + "paged with `limit`/`cursor`. With a resource URI, returns full context: kind, supported "
                        + "transports, actions with parameter types, auth requirements, examples. On a deployment "
                        + "with many resources, prefer a filtered call over an unfiltered one. Call this before "
                        + "how_to_call to learn what you can do with a resource.")
                .inputSchema(inputSchema(properties, null))
                .build();
    }

    static McpSchema.Tool howToCallToolDefinition() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("resource", schemaProp("string", "Resource URI."));
        properties.put("action", schemaProp("string", "Action name as declared in the resource's actions map."));
        properties.put("args", schemaProp("object", "Action arguments — values for params and body declared by the resource."));
        properties.put("transport", schemaProp("string",
                "Optional transport preference (e.g. websocket vs sse for streams). If omitted, the resource's default transport is used."));
        properties.put("token", schemaProp("string", "Optional access token. If omitted, a `<token>` placeholder is embedded."));

        return McpSchema.Tool.builder("how_to_call")
                .description("Returns a request descriptor (transport, URL, headers, body) for invoking a known MCP resource. "
                        + "The tool COMPOSES the request — it does NOT execute it. After receiving the response, "
                        + "choose any client appropriate to the descriptor's transport and your host environment "
                        + "(HTTP libraries, WebSocket libraries, OS shells with curl/httpie/wscat, generated code in "
                        + "any language). The MCP server does not prescribe the tool.\n\nDispatch by action — the "
                        + "set of valid actions for a given resource is declared in the resource's list_apis output. "
                        + "Validate args against the declared params and body_schema before calling.")
                .inputSchema(inputSchema(properties, List.of("resource", "action")))
                .build();
    }

    private static Map<String, Object> inputSchema(Map<String, Object> properties, List<String> required) {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required != null) {
            schema.put("required", required);
        }
        return schema;
    }

    private static Map<String, Object> schemaProp(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    // -------------------------------------------------------------------------
    // Tool call handlers
    // -------------------------------------------------------------------------

    private CallToolResult callListApis(McpSyncServerExchange exchange, CallToolRequest request) {
        var ctx = exchange.transportContext();
        var args = request.arguments();

        try {
            var result = listApisTool.list(
                    principal(ctx), baseUrl(ctx),
                    stringArg(args, "resource"), stringArg(args, "query"), stringArg(args, "kind"),
                    intArg(args, "limit"), stringArg(args, "cursor"));
            return textResult(jsonMapper.writeValueAsString(result));
        } catch (UnknownResourceException | UnknownActionException | ValidationFailedException e) {
            return errorResult(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("list_apis failed", e);
            return errorResult("internal error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private CallToolResult callHowToCall(McpSyncServerExchange exchange, CallToolRequest request) {
        var ctx = exchange.transportContext();
        var args = request.arguments();

        try {
            var actionArgs = args.get("args") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
            var result = howToCallTool.call(
                    principal(ctx), baseUrl(ctx),
                    stringArg(args, "resource"), stringArg(args, "action"), actionArgs,
                    stringArg(args, "transport"), stringArg(args, "token"));
            return textResult(jsonMapper.writeValueAsString(result));
        } catch (UnknownResourceException | UnknownActionException | ValidationFailedException e) {
            return errorResult(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("how_to_call failed", e);
            return errorResult("internal error: " + e.getMessage());
        }
    }

    private static CallToolResult textResult(String text) {
        return new CallToolResult(List.of(new TextContent(text)), false, null, null);
    }

    private static CallToolResult errorResult(String message) {
        return new CallToolResult(List.of(new TextContent("Error: " + message)), true, null, null);
    }

    static String stringArg(Map<String, Object> args, String key) {
        var v = args.get(key);
        return v == null ? null : v.toString();
    }

    static Integer intArg(Map<String, Object> args, String key) {
        var v = args.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
