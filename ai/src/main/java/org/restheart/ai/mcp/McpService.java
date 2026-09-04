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

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.restheart.ai.mcp.tools.CachedResourceLookup;
import org.restheart.ai.mcp.tools.HowToCallTool;
import org.restheart.ai.mcp.tools.ListApisTool;
import org.restheart.ai.mcp.tools.UnknownActionException;
import org.restheart.ai.mcp.tools.UnknownResourceException;
import org.restheart.ai.mcp.tools.ValidationFailedException;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.exchange.Request;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.plugins.mcp.McpResourceTemplate;
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
    // Resources primitive (#617) — phase 1: resources/list, resources/templates/list,
    // and resources/read context-mode only. Documents-mode (readable actions,
    // McpAware.readResource()) and resources/subscribe are later phases.
    // -------------------------------------------------------------------------

    /**
     * Full remove-then-readd, rather than diffing — simpler, and correct even when a resource's
     * content (not just its existence) changed: each {@code SyncResourceSpecification}'s read
     * handler closes over a specific {@code McpResource} snapshot, so an in-place content change
     * still needs a fresh registration to be reflected. Also doubles as the very first
     * population (called from {@code handle()} on first traffic, see {@link #init()}'s comment
     * on why it can't happen any earlier) — {@code server.listResources()} is simply empty then,
     * so "remove current" is a no-op and this just adds the initial set.
     */
    private void syncResourceRegistry() {
        server.listResources().stream().map(McpSchema.Resource::uri).toList().forEach(server::removeResource);
        resourceLookup.all(null, publicBaseUrl).forEach(r -> server.addResource(toResourceSpec(r)));

        server.listResourceTemplates().stream().map(McpSchema.ResourceTemplate::uriTemplate).toList().forEach(server::removeResourceTemplate);
        resourceLookup.templates(publicBaseUrl).forEach(t -> server.addResourceTemplate(toTemplateSpec(t)));

        server.notifyResourcesListChanged();
    }

    private McpServerFeatures.SyncResourceSpecification toResourceSpec(McpResource resource) {
        var sdkResource = McpSchema.Resource.builder()
                .uri(resource.uri())
                .name(resourceName(resource))
                .description(resource.description())
                .mimeType("application/json")
                .build();
        return new McpServerFeatures.SyncResourceSpecification(sdkResource, (exchange, request) -> readContext(resource));
    }

    /** Context mode: identical content to {@code list_apis(resource)} — the same per-kind builders, just reached via {@code resources/read}. */
    private McpSchema.ReadResourceResult readContext(McpResource resource) {
        try {
            var text = jsonMapper.writeValueAsString(resource.toMap());
            return new McpSchema.ReadResourceResult(List.of(new TextResourceContents(resource.uri(), "application/json", text)));
        } catch (Exception e) {
            LOGGER.error("Failed to serialize resource {}", resource.uri(), e);
            return new McpSchema.ReadResourceResult(List.of(new TextResourceContents(resource.uri(), "text/plain", "internal error: " + e.getMessage())));
        }
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
        return new McpServerFeatures.SyncResourceTemplateSpecification(template, (exchange, request) -> readTemplateMatch(request.uri()));
    }

    /** A URI matching a template's shape still must be an actual, currently mcp-enabled resource — same lookup {@code how_to_call} uses. */
    private McpSchema.ReadResourceResult readTemplateMatch(String uri) {
        return resourceLookup.find(null, publicBaseUrl, uri)
                .map(this::readContext)
                .orElseGet(() -> new McpSchema.ReadResourceResult(
                        List.of(new TextResourceContents(uri, "text/plain", "Error: unknown or not MCP-enabled resource: " + uri))));
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
