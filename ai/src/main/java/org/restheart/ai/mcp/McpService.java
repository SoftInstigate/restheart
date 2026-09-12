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
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.restheart.ai.util.RequestOverrides;
import org.restheart.ai.mcp.tools.CachedResourceLookup;
import org.restheart.ai.mcp.tools.HowToCallTool;
import org.restheart.ai.mcp.tools.ListApisTool;
import org.restheart.ai.mcp.tools.UnknownActionException;
import org.restheart.ai.mcp.tools.UnknownResourceException;
import org.restheart.ai.mcp.tools.ValidationFailedException;
import org.restheart.ai.mcp.transport.DescriptorRenderer;
import org.restheart.ai.mcp.validation.ParamValidator;
import org.restheart.ai.util.PluginModelResolver;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.exchange.Request;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.mcp.McpContext;
import org.restheart.plugins.mcp.McpScopeProvider;
import org.restheart.plugins.mcp.McpReadResult;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.plugins.mcp.McpResourceTemplate;
import org.restheart.plugins.security.DescriptorAuthorization;
import org.restheart.plugins.security.DescriptorAwareAuthorizer;
import org.restheart.plugins.security.DescriptorAwareAuthorizer.Decision;
import org.restheart.plugins.security.JwtIssuer;
import org.restheart.plugins.security.RequestDescriptor;
import org.restheart.security.BaseAccount;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.jackson3.JacksonJsonSchemaValidatorSupplier;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.undertow.server.HttpServerExchange;

/**
 * RESTHeart's MCP server: exposes exactly three tools — {@code list_apis} (what exists),
 * {@code how_to_call} (how to invoke it) and {@code get_token} (a short-lived credential to
 * invoke it with) — over the MCP Streamable HTTP transport (restheart#615 design principles —
 * "one tool, one model", no per-resource tools: three concerns, not three per resource).
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
    private static final String CTX_REQUEST = "request";
    private static final String CTX_SCOPE = "scope";

    private static final int DEFAULT_CATALOG_TTL_SECONDS = 300;

    /**
     * Lifetime of a token issued by {@code get_token} — see {@link #callGetToken}. Deliberately
     * short and not operator-configurable: the whole point is bounding the blast radius of a
     * leaked token by expiry, not by trusting the agent (or whatever it hands the token to next)
     * to handle it carefully. It is short enough that it must be fetched right before use, which
     * is why it is a tool of its own rather than something baked into a descriptor.
     */
    private static final Duration EPHEMERAL_TOKEN_TTL = Duration.ofSeconds(60);

    @Inject("registry")
    private PluginsRegistry pluginsRegistry;

    /** The framework's own answer to "could this caller perform this operation?" — see {@link #visibleTo}. */
    @Inject("descriptor-authorization")
    private DescriptorAuthorization authorization;

    @Inject("config")
    private Map<String, Object> config;

    private ListApisTool listApisTool;
    private HowToCallTool howToCallTool;
    private McpJsonMapper jsonMapper;
    private CachedResourceLookup resourceLookup;
    private JsonSchemaValidator schemaValidator;

    /**
     * Decides which partition of the catalogue a request belongs to. Never {@code null}: with no
     * implementation registered this answers {@link McpScopeProvider#UNPARTITIONED} for
     * everything, which is what makes partitioning opt-in with no configuration switch.
     */
    private McpScopeProvider scopeProvider;

    /**
     * One MCP server per scope, created on that scope's first request.
     *
     * <p>Not one server with a per-request view: {@code resources/list} is answered from the SDK's
     * own registry, which belongs to the server, so a request-scoped value can never reach it.
     * And not one server per (scope, service) either — a session talks to exactly one server, and
     * the catalogue is aggregated across every {@code McpAware}, so splitting by service would
     * leave a client seeing part of it with no way to know.
     *
     * <p>One provider each, not one shared: {@code setSessionFactory} is singular, and
     * {@code McpServer.sync(provider)} installs the server as that provider's session factory —
     * one provider can only ever serve one server.
     */
    private final Map<String, ScopedServer> scopes = new ConcurrentHashMap<>();

    /** An MCP server, its transport, and whether its registry has been populated yet. */
    private record ScopedServer(String scope,
                                UndertowStreamableServerTransportProvider provider,
                                McpSyncServer server,
                                AtomicBoolean resourcesInitialized) {
    }

    /** Resolved lazily via {@link PluginModelResolver}, not in {@code @OnInit} — see its own javadoc on plugin init ordering. */
    private final Map<String, JwtIssuer> resolvedJwtIssuers = new ConcurrentHashMap<>();

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

    /**
     * The running service, so {@link McpCatalogFilterInterceptor} can reuse this catalog and this
     * visibility rule instead of building its own. There is exactly one instance: RESTHeart plugins
     * are singletons.
     */
    private static volatile McpService instance;

    @OnInit
    public void init() {
        instance = this;

        var mcpAwareRegistry = McpAwareRegistry.discover(pluginsRegistry);

        jsonMapper = new JacksonMcpJsonMapperSupplier().get();
        schemaValidator = new JacksonJsonSchemaValidatorSupplier().get();
        scopeProvider = discoverScopeProvider();

        publicBaseUrl = config != null && config.get("public-base-url") instanceof String s && !s.isBlank() ? s : null;

        // Catalog data is cached for this TTL rather than invalidated by watching every
        // McpAware implementation's own data source (a MongoDB write, a config change, ...) —
        // one uniform mechanism for all of them, trading instant consistency for a bounded
        // staleness window. On expiry, connected agents are told to refetch.
        var notifyIntervalSeconds = argOrDefault(config, "subscription-notify-interval-seconds", DEFAULT_NOTIFY_INTERVAL_SECONDS);
        subscriptions = new ResourceSubscriptions(Duration.ofSeconds(notifyIntervalSeconds), this::notifyResourceUpdated);

        var catalogTtlSeconds = argOrDefault(config, "catalog-ttl-seconds", DEFAULT_CATALOG_TTL_SECONDS);
        resourceLookup = new CachedResourceLookup(mcpAwareRegistry, Duration.ofSeconds(catalogTtlSeconds), this::onCatalogExpired);
        listApisTool = new ListApisTool(resourceLookup);
        howToCallTool = new HowToCallTool(resourceLookup);

        // Without this, a client's open GET/SSE stream (or an in-flight tool-call's SSE
        // response) blocks its worker thread forever inside
        // UndertowStreamableServerTransportProvider's queue.take() loop — nothing signals
        // it to unblock on its own. RESTHeart's graceful shutdown then waits for that
        // worker thread to finish and hangs. closeGracefully() closes every open session
        // (and so every associated queue), which is what actually lets the pending
        // request complete. Same fix Sophia's own MCP service applies for the same reason.
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
            LOGGER.info("MCP shutdown: closing {} transport provider(s)...", scopes.size());
            scopes.values().forEach(scoped -> scoped.provider().closeGracefully().block());
            LOGGER.info("MCP shutdown: transport providers closed.");
        }));

        LOGGER.info("MCP service initialized on {} (Streamable HTTP transport)", "/mcp");
        if (publicBaseUrl != null) {
            LOGGER.info("MCP resources primitive enabled, public-base-url={}", publicBaseUrl);
        }
    }

    /**
     * The deployment's {@link McpScopeProvider}, or one answering
     * {@link McpScopeProvider#UNPARTITIONED} for everything when none is registered.
     *
     * <p>Found by the type it provides rather than by an agreed name: a deployment that
     * partitions should not also have to match a string, and nothing else in RESTHeart provides
     * this type.
     */
    private McpScopeProvider discoverScopeProvider() {
        var self = pluginsRegistry.getServices().stream()
                .filter(r -> r.getInstance() == this)
                .findFirst()
                .orElse(null);

        for (var record : pluginsRegistry.getProviders()) {
            var instance = record.getInstance();
            if (!McpScopeProvider.class.isAssignableFrom(instance.rawType())) {
                continue;
            }

            if (instance.get(self) instanceof McpScopeProvider resolved) {
                LOGGER.info("MCP catalogue partitioned by scope, resolved by '{}'", record.getName());
                return resolved;
            }

            LOGGER.warn("Provider '{}' declares McpScopeProvider but supplied nothing — the MCP catalogue stays unpartitioned", record.getName());
        }

        return request -> McpScopeProvider.UNPARTITIONED;
    }

    /**
     * The scope this request belongs to, resolved once per request.
     *
     * <p>A provider that throws is treated as one that could not decide, not as one that said
     * "no partitioning": on a shared process the difference between those two is the difference
     * between refusing a request and handing over everybody's catalogue.
     */
    private String resolveScope(Request<?> req) {
        try {
            var scope = scopeProvider.scopeOf(req);
            return scope == null || scope.isBlank() ? McpScopeProvider.UNRESOLVED : scope;
        } catch (Exception e) {
            LOGGER.error("McpScopeProvider failed; refusing the request rather than serving the whole catalogue", e);
            return McpScopeProvider.UNRESOLVED;
        }
    }

    /** The server serving {@code scope}, created on that scope's first request. */
    private ScopedServer scopedServer(String scope) {
        return scopes.computeIfAbsent(scope, this::newScopedServer);
    }

    private ScopedServer newScopedServer(String scope) {
        var scopedProvider = new UndertowStreamableServerTransportProvider(jsonMapper);
        scopedProvider.onSessionEnded(this::sessionEnded);

        // Neither resources nor templates are registered here: both are built by calling
        // describeMcp()/describeTemplates() on every registered McpAware plugin, and @OnInit
        // methods run in an unspecified cross-plugin order (see OnInit's own javadoc) — another
        // plugin's own @OnInit (e.g. GraphQLService's) may not have run yet, leaving its internal
        // state null and throwing (confirmed live). list_apis/how_to_call avoid this by only ever
        // calling describeMcp() from an actual incoming request, which can't happen before every
        // plugin's @OnInit has completed — resources and templates must be seeded the same way,
        // on this scope's first real traffic, not here.
        var scopedSrv = McpServer.sync(scopedProvider)
                // Keeps handlers on the caller's virtual thread: without it the SDK wraps every one in subscribeOn(Schedulers.boundedElastic()), a pool of up to 10x CPU platform threads — a default meant for event-loop callers, and the opposite of RESTHeart's threading model
                .immediateExecution(true)
                .serverInfo("restheart-mcp", "1.0.0")
                .jsonMapper(jsonMapper)
                .jsonSchemaValidator(schemaValidator)
                .capabilities(capabilities())
                .toolCall(listApisToolDefinition(), this::callListApis)
                .toolCall(howToCallToolDefinition(), this::callHowToCall)
                .toolCall(getTokenToolDefinition(), this::callGetToken)
                .build();

        LOGGER.debug("MCP server created for scope '{}'", scope);

        return new ScopedServer(scope, scopedProvider, scopedSrv, new AtomicBoolean(false));
    }

    /**
     * Starts watching a resource the client is subscribing to, if nothing watches it yet.
     *
     * <p>Read off the request rather than from the SDK, which keeps its subscription map private.
     * Unsubscribes are read here too, and the transport reports a session ending, so a watch lives
     * exactly as long as somebody wants the resource. The one case that escapes this is a client
     * that vanishes without the {@code DELETE} that closes its session: its watch survives until
     * the resource leaves the catalog or the server stops.
     */
    private boolean watchIfSubscribing(ByteArrayRequest req, ByteArrayResponse res, McpTransportContext ctx) {
        try {
            var body = req.getContent();
            if (body == null || body.length == 0) {
                return true;
            }

            if (!(McpSchema.deserializeJsonRpcMessage(jsonMapper, new String(body, StandardCharsets.UTF_8))
                    instanceof McpSchema.JSONRPCRequest rpc)
                    || !(rpc.params() instanceof Map<?, ?> params)
                    || !(params.get("uri") instanceof String uri)) {
                return true;
            }

            var sessionId = req.getHeader(HttpHeaders.MCP_SESSION_ID);
            if (sessionId == null || sessionId.isBlank()) {
                return true;
            }

            if (McpSchema.METHOD_RESOURCES_SUBSCRIBE.equals(rpc.method())) {
                var resource = resourceLookup.find(principal(ctx), effectiveBaseUrl(ctx), effectiveScope(ctx), uri);

                if (resource.isEmpty() || !resource.get().subscribable()) {
                    refuseSubscription(res, rpc, uri, resource.map(McpResource::kind).orElse(null));
                    return false;
                }

                if (!canReceiveNotifications(ctx)) {
                    refuseUndeliverableSubscription(res, rpc, uri);
                    return false;
                }

                demand.subscribed(uri, sessionId);

                // On every subscribe, not only the first: a change stream can die on its own —
                // its collection dropped, a primary stepping down, a network blip — and
                // CollectionWatchers drops the watch when it does. Keying this on "is anyone
                // already subscribed" would then leave the resource unwatched for good, with the
                // subscribers still recorded and never told anything again. startWatching is a
                // no-op when a live watch is already there.
                startWatching(principal(ctx), effectiveBaseUrl(ctx), effectiveScope(ctx), uri);
            } else if (McpSchema.METHOD_RESOURCES_UNSUBSCRIBE.equals(rpc.method())
                    && demand.unsubscribed(uri, sessionId)) {
                releaseWatchesFor(List.of(uri));
            }
        } catch (Exception e) {
            LOGGER.warn("could not inspect a /mcp request for a resource subscription", e);
        }

        return true;
    }

    /**
     * Whether this caller could open the stream notifications are delivered on.
     *
     * <p>The transport splits the endpoint by method: {@code POST} carries the client's JSON-RPC
     * messages, and {@code GET} opens the long-lived stream the server pushes on. A subscription is
     * server-initiated later, when no request is pending, so it has nowhere to go without that
     * stream — and an ACL granting only {@code POST} on the endpoint is enough to produce a
     * subscription that succeeds, opens a change stream, generates notifications, and drops every
     * one of them.
     *
     * <p>Asking here, before accepting, turns a silent forever-wait into an answer. The question is
     * put to the framework's own authorization, so it cannot disagree with what the {@code GET}
     * would actually get.
     */
    private boolean canReceiveNotifications(McpTransportContext ctx) {
        var request = request(ctx);

        if (request == null || authorization == null) {
            return true;
        }

        var caller = RequestDescriptor.of(request.getExchange());
        var endpoint = PluginUtils.actualUri(config, McpService.class);

        return authorization.isAllowed(new RequestDescriptor(caller.principal(), "GET", endpoint,
                Map.of(), caller.headers(), caller.cookies(), caller.remoteAddress(), caller.scheme()));
    }

    /** @see #canReceiveNotifications */
    private void refuseUndeliverableSubscription(ByteArrayResponse res, McpSchema.JSONRPCRequest rpc, String uri) {
        var endpoint = PluginUtils.actualUri(config, McpService.class);

        writeJsonRpcError(res, rpc, """
                cannot subscribe to %s: notifications are delivered on the stream a client opens with \
                GET %s, and this caller is not authorized to open it. Grant GET (and DELETE, which \
                ends the session) on %s, not POST alone.\
                """.formatted(uri, endpoint, endpoint));
    }

    /**
     * Answers a {@code resources/subscribe} the server can never honour, instead of letting it
     * through to succeed and stay silent.
     *
     * <p>The SDK owns {@code resources/subscribe}: it registers a private handler that only records
     * the subscription, and offers no hook to decline one (see java-sdk#1128). But this service
     * sees the message first, so the refusal happens here — before {@code handlePost} hands it to
     * the SDK, which is why {@link #watchIfSubscribing} reports whether to continue.
     *
     * <p>It is a JSON-RPC error rather than an HTTP status because it is a protocol-level answer to
     * a well-formed request, and because the client needs to read the reason: the message names the
     * collection to subscribe to instead, which is the thing that is actually not obvious.
     */
    private void refuseSubscription(ByteArrayResponse res, McpSchema.JSONRPCRequest rpc, String uri, String kind) {
        var reason = kind == null
                ? "no such resource: %s".formatted(uri)
                : """
                        a resource of kind '%s' cannot be subscribed to: %s. Notifications come from a change \
                        stream on a collection, and this resource has none to watch. Collections and aggregations \
                        over them are subscribable — list_apis marks those with "subscribable": true.\
                        """.formatted(kind, uri);

        writeJsonRpcError(res, rpc, reason);
    }

    /**
     * Answers one JSON-RPC request with an error, in place of dispatching it to the SDK.
     *
     * <p>HTTP stays {@code 200}: the request was well-formed and this is the protocol's own answer
     * to it, not a transport failure — and the client needs to read the reason.
     */
    private void writeJsonRpcError(ByteArrayResponse res, McpSchema.JSONRPCRequest rpc, String reason) {
        var error = new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.INVALID_PARAMS, reason);

        try {
            res.setContentType("application/json");
            res.setContent(jsonMapper.writeValueAsString(McpSchema.JSONRPCResponse.error(rpc.id(), error)));
            res.setStatusCode(HttpStatus.SC_OK);
        } catch (Exception e) {
            LOGGER.error("could not write a JSON-RPC error answering {}", rpc.method(), e);
            res.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Releases what a session was keeping alive. Called by the transport, which is where a session
     * actually ends — the SDK drops its own subscriptions for it without saying so, and a watch
     * left behind would keep a change stream open for a client that has gone.
     */
    void sessionEnded(String sessionId) {
        releaseWatchesFor(demand.sessionEnded(sessionId));
    }

    /** One watch per resource URI; the owning plugin decides what that costs — for a collection, one shared change stream. */
    private synchronized void startWatching(BaseAccount principal, String baseUrl, String scope, String uri) {
        if (watches.containsKey(uri)) {
            return;
        }

        var owner = resourceLookup.findOwner(principal, baseUrl, scope, uri);
        if (owner.isEmpty()) {
            return;
        }

        var watchCtx = new McpContext(principal, baseUrl, scope, owner.get().pluginName(),
                owner.get().pluginUri(), owner.get().pluginConfiguration());

        owner.get().instance().watch(watchCtx, uri, () -> subscriptions.changed(uri))
                .ifPresentOrElse(handle -> watches.put(uri, new Watch(scope, handle)),
                        () -> LOGGER.debug("resource {} cannot be watched; subscribers to it are never notified", uri));
    }

    /**
     * Stops watching what is no longer in the catalog — a collection that lost its {@code mcp}
     * block, or was dropped — and what the last subscriber has left.
     *
     * <p>Synchronized on the same monitor as {@link #startWatching}, and re-checking demand under
     * it, because neither alone is enough. The caller decided to release when the last subscriber
     * left; a new subscriber arriving in between would find the watch still in the map, skip
     * opening one of its own, and then have it closed underneath. It would stay subscribed to
     * something nobody is watching, and hear nothing for the life of its session.
     */
    private synchronized void releaseWatchesFor(Collection<String> goneUris) {
        goneUris.forEach(uri -> {
            if (demand.hasSubscribers(uri)) {
                LOGGER.debug("keeping the watch on {}: somebody subscribed while the last one was leaving", uri);
                return;
            }

            var watch = watches.remove(uri);
            if (watch != null) {
                subscriptions.forget(uri);
                try {
                    watch.handle().close();
                } catch (Exception e) {
                    LOGGER.warn("failed to stop watching {}", uri, e);
                }
            }
        });
    }

    /**
     * Only the scope that watches this URI is told. Its clients are the only ones that could have
     * subscribed to it, and on a shared process a notification is itself information — that
     * something, somewhere, changed.
     */
    private void notifyResourceUpdated(String uri) {
        var watch = watches.get(uri);
        if (watch == null) {
            return;
        }

        var scoped = scopes.get(watch.scope());
        if (scoped != null) {
            scoped.server().notifyResourcesUpdated(new McpSchema.ResourcesUpdatedNotification(uri));
        }
    }

    private McpSchema.ServerCapabilities capabilities() {
        var builder = McpSchema.ServerCapabilities.builder().tools(true);
        if (publicBaseUrl != null) {
            builder.resources(true, true);
        }
        return builder.build();
    }

    /**
     * Runs once per catalog cache entry that expires (see {@link CachedResourceLookup}): tells
     * already-connected agents to refetch tools, and — if the resources primitive is enabled —
     * re-syncs the MCP SDK's resource registry against the same fresh catalog.
     */
    /**
     * The expired key names one scope's catalogue, and only that scope is rebuilt and notified.
     * Waking every connected client because some other partition changed is work they cannot use
     * and news they should not have.
     */
    private void onCatalogExpired(CachedResourceLookup.CatalogKey key) {
        var scoped = scopes.get(key.scope());
        if (scoped == null) {
            return;
        }

        notifyToolsListChanged(scoped);

        if (publicBaseUrl != null) {
            syncResourceRegistry(scoped, key.baseUrl());
        }
    }

    private void notifyToolsListChanged(ScopedServer scoped) {
        scoped.provider().notifyClients("notifications/tools/list_changed", null)
                .subscribe(v -> {
                }, err -> LOGGER.warn("Failed to notify clients of tools/list_changed: {}", err.getMessage()));
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
    //
    // resources/subscribe: the SDK owns the protocol side — it keeps uri -> sessions, cleans it
    // up when a session ends, and delivers notifyResourcesUpdated only to subscribers. What is
    // ours is knowing when a resource's data changed, which the owning plugin answers through
    // McpAware.watch(), and rate-limiting the result (ResourceSubscriptions).
    // -------------------------------------------------------------------------

    /**
     * A rendering-format toggle, not a data-shaping parameter — its mere presence on an action
     * (e.g. every readable action gets a {@code jsonMode} param) shouldn't by itself force an
     * otherwise argument-free resource into template-only registration below.
     */
    private static final Set<String> COSMETIC_PARAMS = Set.of("jsonMode");

    /**
     * How often at most a subscriber hears that one resource changed. The notification carries no
     * payload — it says "re-read" — so telling a client again before it has read teaches it
     * nothing, and a busy collection would otherwise turn every write into a message.
     */
    private static final int DEFAULT_NOTIFY_INTERVAL_SECONDS = 5;

    /** Rate-limits resources/updated; one entry per URI something is subscribed to. */
    private ResourceSubscriptions subscriptions;

    /** Open watches, by resource URI — see {@link #startWatching}. */
    private final Map<String, Watch> watches = new ConcurrentHashMap<>();

    /**
     * An open watch, and the scope whose clients asked for it — needed to send the change
     * notification to that scope's server and no other.
     */
    private record Watch(String scope, AutoCloseable handle) {
    }

    /** Who still wants each resource, so a watch outlives no one — see {@link ResourceDemand}. */
    private final ResourceDemand demand = new ResourceDemand();

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^?][^}]*)\\}");

    /**
     * Full remove-then-readd, rather than diffing — simpler, and correct even when a resource's
     * content (not just its existence) changed: each {@code SyncResourceSpecification}'s read
     * handler closes over a specific {@code McpResource} snapshot, so an in-place content change
     * still needs a fresh registration to be reflected. Also doubles as the very first
     * population (called from {@code handle()} on first traffic, see {@link #init()}'s comment
     * on why it can't happen any earlier) — {@code server.listResources()} is simply empty then,
     * so "remove current" is a no-op and this just adds the initial set.
     *
     * <p>Registers, independently, every {@code readable} action of every catalog entry (not just
     * one "default" action per resource — a collection's {@code query} and {@code get} both
     * register their own entry, at their own URI shape). A non-readable catalog entry (a database,
     * a change stream, a non-readable aggregation, ...) is never registered here at all — it stays
     * reachable through {@code list_apis}/{@code how_to_call} only, since {@code resources/read}
     * must always return real data and there's nothing document-shaped to return for those.
     *
     * <p>An action with no required parameter registers as a concrete <b>resource</b> at its bare
     * URI — reading it with no arguments is exactly what {@link #readBareResource} does, and is
     * guaranteed to succeed. An action with any parameter at all — required or optional, but
     * excluding {@link #COSMETIC_PARAMS} — <em>also</em> (or, if it has a required one, instead;
     * see below) registers as a <b>template</b> naming those parameters, so a client can construct
     * a refined read.
     *
     * <p>An action with a required parameter never registers as a concrete resource — it would be
     * guaranteed to fail with no arguments. This is also why {@code CollectionMcpResourceBuilder}
     * deliberately declares its {@code query} action's {@code page} as required (with no default,
     * even though the real REST endpoint defaults it) — a collection would otherwise register as
     * both a resource and an all-optional template, and the MCP SDK's own template matcher (its
     * {@code {?a,b,c}} query-expansion syntax requires at least one non-slash character after the
     * base to route at all — verified directly against the SDK's matcher, it does not implement
     * RFC 6570's "every variable omitted" case) would make that template unreachable with every
     * field left blank anyway. Requiring one parameter sidesteps both problems in one move: a
     * collection registers as a template only, and every real call necessarily satisfies the
     * matcher.
     */
    private void syncResourceRegistry(ScopedServer scoped, String baseUrl) {
        // The desired state is computed first, before anything is touched. Rebuilding it is the
        // slow part — this runs on catalog expiry, so resourceLookup is always a miss here and
        // goes out to every McpAware plugin (a MongoDB round trip for Mongo's). Clearing the
        // registry up front and repopulating afterwards left that whole span as a window in which
        // resources/list answered with an empty or half-built catalog. Found by McpResourcesIT,
        // which intermittently saw no templates at all.
        var desiredResources = new LinkedHashMap<String, ConcreteEntry>();
        var desiredTemplates = new LinkedHashMap<String, McpResourceTemplate>();

        resourceLookup.all(null, baseUrl, scoped.scope()).forEach(r -> r.actions().forEach((actionName, action) -> {
            if (!action.readable()) {
                return;
            }

            var pathVars = pathVariableNames(action.pathTemplate());
            var allParams = flatParamNames(action, p -> true);
            var queryParams = allParams.stream().filter(n -> !pathVars.contains(n)).toList();
            var significantQueryParams = queryParams.stream().filter(n -> !COSMETIC_PARAMS.contains(n)).toList();
            var requiredParams = flatParamNames(action, McpResource.Param::required);

            var base = r.uri() + (action.pathTemplate() == null ? "" : action.pathTemplate());

            // Whenever a bare read would succeed, the bare URI is registered as a concrete
            // resource — even when a template exists for the same base.
            //
            // Not a duplicate for its own sake: it is what stops the failure this design would
            // otherwise guarantee. A template advertises parameters and has no way to mark any of
            // them required (an MCP ResourceTemplate carries no schema at all), so an agent quite
            // reasonably submits it with nothing filled in. That expands to the bare URI, and the
            // SDK's matcher compiles every {...} to a group needing at least one character — so
            // the template cannot match it, and the agent gets "resource not found" for a request
            // the server itself invited. Confirmed against MCP Inspector.
            if (requiredParams.isEmpty() && pathVars.isEmpty()) {
                desiredResources.putIfAbsent(base, new ConcreteEntry(r, actionName, action));
            }

            if (!pathVars.isEmpty() || !significantQueryParams.isEmpty()) {
                // The query-expansion suffix is emitted only when there is something worth
                // filling in. A template carrying nothing but a cosmetic param would read
                // ".../{id}{?jsonMode}", two adjacent capture groups over one path segment —
                // matched by the SDK, but a misleading thing to advertise.
                var uriTemplate = significantQueryParams.isEmpty()
                        ? base
                        : base + "{?" + String.join(",", queryParams) + "}";

                // Named after the set of things the entry addresses, not after the McpResource it
                // was derived from — several entries share one resource (the collection, its
                // single-document reader, its count), and naming them all after it published two
                // identical "inventory" templates. This is also the convention the MCP spec's own
                // examples use: the template behind file:///{path} is "Project Files", not the
                // name of any one file.
                // An action with a literal path of its own addresses a different thing than the
                // resource it hangs off — /_size answers with a number, not with documents — so
                // that path is part of the name, or the collection's template and its count's
                // would both be called "inventory-documents".
                var literal = literalSuffix(action.pathTemplate());
                var name = pathId(r.uri()) + literal + (!pathVars.isEmpty()
                        ? "-by-" + String.join("-", new java.util.TreeSet<>(pathVars))
                        : literal.isEmpty() ? "-documents" : "-filtered");

                desiredTemplates.putIfAbsent(uriTemplate,
                        paramsTemplate(r, literal, name, uriTemplate, queryParams, pathVars, requiredParams));
            }
        }));

        resourceLookup.templates(baseUrl, scoped.scope()).forEach(t -> desiredTemplates.putIfAbsent(t.uriTemplate(), t));

        var currentResourceUris = scoped.server().listResources().stream().map(McpSchema.Resource::uri).toList();
        var currentTemplateUris = scoped.server().listResourceTemplates().stream().map(McpSchema.ResourceTemplate::uriTemplate).toList();

        var changed = false;

        var gone = new ArrayList<String>();

        for (var uri : currentResourceUris) {
            if (!desiredResources.containsKey(uri)) {
                scoped.server().removeResource(uri);
                gone.add(uri);
                changed = true;
            }
        }

        // a resource that left the catalog — its collection dropped, or its mcp block removed —
        // has nothing left to watch, and nobody to tell about it
        releaseWatchesFor(gone);

        for (var uriTemplate : currentTemplateUris) {
            if (!desiredTemplates.containsKey(uriTemplate)) {
                scoped.server().removeResourceTemplate(uriTemplate);
                changed = true;
            }
        }

        // An entry already registered under the same URI is left alone rather than replaced: its
        // read handler closes over an McpResource only for the URI and the action name it
        // resolves — the data itself is looked up afresh on every read — so an existing
        // registration cannot go stale in a way a re-add would fix.
        for (var e : desiredResources.entrySet()) {
            if (!currentResourceUris.contains(e.getKey())) {
                var hasTemplate = desiredTemplates.keySet().stream().anyMatch(t -> t.startsWith(e.getKey() + "{?"));
                scoped.server().addResource(toResourceSpec(e.getKey(), e.getValue(), concreteName(e.getValue()),
                        concreteTitle(e.getValue(), hasTemplate)));
                changed = true;
            }
        }

        for (var e : desiredTemplates.entrySet()) {
            if (!currentTemplateUris.contains(e.getKey())) {
                scoped.server().addResourceTemplate(toTemplateSpec(e.getValue()));
                changed = true;
            }
        }

        // Only when something actually moved: this runs every catalog TTL, and notifying on an
        // unchanged catalog wakes every connected client for nothing.
        if (changed) {
            scoped.server().notifyResourcesListChanged();
        }
    }

    /** {@code {name}} placeholders in a path template (e.g. {@code "/{id}"} -> {@code ["id"]}) — never the {@code {?a,b,c}} query-expansion group. */
    static Set<String> pathVariableNames(String pathTemplate) {
        if (pathTemplate == null || pathTemplate.isBlank()) {
            return Set.of();
        }
        var names = new HashSet<String>();
        var m = PATH_VARIABLE.matcher(pathTemplate);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /**
     * The flat query-param names {@code action} declares, filtered by {@code filter} — an object
     * param's (i.e. {@code avars}) properties surface by their own name, since the flat-query-param
     * shorthand (see {@code MongoRequestPropsInjector}) binds them individually, not nested under
     * {@code avars}.
     */
    static List<String> flatParamNames(McpResource.Action action, Predicate<McpResource.Param> filter) {
        var names = new ArrayList<String>();
        action.params().forEach((name, param) -> {
            if ("object".equals(param.type()) && param.properties() != null) {
                param.properties().forEach((propName, propParam) -> {
                    if (filter.test(propParam)) {
                        names.add(propName);
                    }
                });
            } else if (filter.test(param)) {
                names.add(name);
            }
        });
        return names;
    }

    /**
     * The template entry for one readable action.
     *
     * <p>{@code name} is the identifier; {@code title} is what a picker shows, and it spells out
     * every parameter the read accepts — built from the same lists that produced the URI template,
     * so the label cannot drift from what the template actually takes. A template can mark nothing
     * as required (it carries no schema at all), which is why required-ness has to be stated in
     * prose, in the description, rather than folded into the name where more than one variable
     * would make it unreadable.
     */
    static McpResourceTemplate paramsTemplate(McpResource resource, String literalSuffix, String name, String uriTemplate,
                                              List<String> queryParams, Set<String> pathVars, List<String> requiredParams) {
        // "inventory", or "inventory size" for an action that addresses something of its own —
        // whose name then already says what it yields, so the title goes straight to the params
        var subject = resourceName(resource) + literalSuffix.replace("-", " ");

        var title = new StringBuilder(subject).append(" — ");
        if (!pathVars.isEmpty()) {
            title.append("one document by ").append(String.join(", ", pathVars));
        } else if (literalSuffix.isEmpty()) {
            title.append("documents");
        }
        if (!queryParams.isEmpty()) {
            title.append(pathVars.isEmpty() && !literalSuffix.isEmpty() ? "by " : pathVars.isEmpty() ? " by " : ", ")
                    .append(String.join(", ", queryParams));
        }

        var description = requiredParams.isEmpty()
                ? resource.description()
                : resource.description() + " (requires: " + String.join(", ", requiredParams) + ")";

        return new McpResourceTemplate(uriTemplate, name, title.toString(), description);
    }

    /**
     * One readable action reachable with no arguments at all, and the URI that reaches it. An
     * action may add a literal path of its own ({@code /_size}), so the entry's URI is not
     * necessarily the resource's own — which is why the action travels with it rather than being
     * re-derived from the resource at read time.
     */
    private record ConcreteEntry(McpResource resource, String actionName, McpResource.Action action) {
    }

    /**
     * The identifier for a no-argument entry: the resource's own path, plus the literal path the
     * action appends — {@code inventory} for the collection's documents, {@code inventory-size}
     * for its count. Without that suffix both would be called {@code inventory}, since both are
     * derived from the same collection.
     */
    private static String concreteName(ConcreteEntry entry) {
        return pathId(entry.resource().uri()) + literalSuffix(entry.action().pathTemplate());
    }

    /**
     * {@code "/_size"} -> {@code "-size"}; blank for an action that adds no path of its own, and
     * blank for one whose path is a variable ({@code "/{id}"}) — that is not a name, it is a slot,
     * and it is already accounted for by the {@code -by-<var>} suffix.
     */
    private static String literalSuffix(String pathTemplate) {
        if (pathTemplate == null || pathTemplate.isBlank() || pathTemplate.contains("{")) {
            return "";
        }
        return "-" + pathTemplate.replace("/", "-").replace("_", "").replaceAll("^-+", "");
    }

    /**
     * What a picker shows for a no-argument entry. Two entries can be derived from the same
     * collection — its documents and its {@code /_size} — so the label has to say which, and it
     * says it with the literal path the action appends, stripped of the leading underscore that
     * marks RESTHeart's reserved resources. {@code first page} is added only when a template
     * beside it offers the paging and filtering this bare read does not.
     */
    private static String concreteTitle(ConcreteEntry entry, boolean hasTemplate) {
        var subject = resourceName(entry.resource());
        var path = entry.action().pathTemplate();
        if (path == null || path.isBlank()) {
            return hasTemplate ? subject + " — first page" : subject;
        }
        return subject + " — " + literalSuffix(path).substring(1).replace("-", " ");
    }

    private McpServerFeatures.SyncResourceSpecification toResourceSpec(String uri, ConcreteEntry entry, String name, String title) {
        var sdkResource = McpSchema.Resource.builder(uri, name)
                .title(title)
                .description(entry.action().description() != null
                        ? entry.action().description()
                        : entry.resource().description())
                .mimeType("application/json")
                .build();
        return new McpServerFeatures.SyncResourceSpecification(sdkResource,
                (exchange, request) -> readBareResource(exchange.transportContext(), uri, entry));
    }

    /**
     * Reads the bare resource URI — the SDK's own exact-URI match (verified against its bytecode:
     * it tries concrete {@link McpServerFeatures.SyncResourceSpecification}s registered via {@link
     * #toResourceSpec} before ever falling back to a template) always routes here for a known
     * resource's own URI, never through {@link #readTemplateMatch} (which has its own, independent
     * resolution for a URI that isn't registered as a concrete resource).
     *
     * <p>Documents-mode with no arguments — matches what attaching this resource in a host UI
     * (Claude Desktop, Cursor, ...) actually means: load its real content.
     */
    private McpSchema.ReadResourceResult readBareResource(McpTransportContext ctx, String uri, ConcreteEntry entry) {
        var principal = principal(ctx);
        return readOperation(ctx, principal, entry.resource().uri(), entry.actionName(), Map.of())
                .orElseGet(() -> errorResourceResult(uri, "failed to read resource"));
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

    /**
     * Identifier for an entry, unique across the catalog: the addressed URI's whole path, since
     * the last segment alone is not enough — two databases may each hold a collection called
     * {@code inventory}, and under a wildcard mount both are exposed at once. Uniqueness is
     * required of the URI, not the name, but names that collide leave an agent unable to tell two
     * entries apart, which is the whole reason they carry names.
     */
    static String pathId(String uri) {
        var path = uri.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/]+", "");
        var trimmed = path.startsWith("/") ? path.substring(1) : path;
        return trimmed.isBlank() ? uri : trimmed;
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
     * ever called — reachable today only for an action with no parameters at all (e.g. an
     * aggregation with no {@code $var}s), since anything with a parameter registers as a template
     * (see {@link #syncResourceRegistry}).
     *
     * <p>The query string, if any, is split off first and its args carried through either
     * resolution below, so a single-document read can carry query args too (e.g.
     * {@code .../inventory/<id>?jsonMode=RELAXED}) — the two are independent, not mutually
     * exclusive URI shapes:
     * <ul>
     *   <li>the part before the query string exactly matches a catalog resource → documents mode,
     *       using whichever action {@link #defaultReadableAction} picks for it (e.g. {@code query}
     *       for a collection, {@code execute} for an aggregation — <b>not</b> hardcoded to {@code
     *       query}, since an aggregation has no action by that name)</li>
     *   <li>otherwise, stripping its last path segment matches a catalog resource → single
     *       document mode, action {@code get}, {@code id} = that segment</li>
     * </ul>
     */
    private McpSchema.ReadResourceResult readTemplateMatch(McpTransportContext ctx, String uri) {
        var principal = principal(ctx);
        var baseUrl = effectiveBaseUrl(ctx);
        var scope = effectiveScope(ctx);

        var queryIdx = uri.indexOf('?');
        var base = queryIdx >= 0 ? uri.substring(0, queryIdx) : uri;
        var queryArgs = queryIdx >= 0 ? parseQueryArgs(uri.substring(queryIdx + 1)) : Map.<String, Object>of();

        var resource = resourceLookup.find(principal, baseUrl, scope, base);
        if (resource.isPresent()) {
            var actionName = defaultReadableAction(resource.get());
            if (actionName == null) {
                // a real catalog entry (e.g. an aggregation whose pipeline didn't clear the
                // security checker), but nothing document-shaped to read — resources/read must
                // always mean "here is data", never a description; use how_to_call for this one
                return errorResourceResult(base, "resource has no readable data; use how_to_call to invoke its actions");
            }
            return readOperation(ctx, principal, base, actionName, queryArgs).orElseGet(() -> unknownResourceResult(uri));
        }

        var lastSlash = base.lastIndexOf('/');
        if (lastSlash > 0) {
            var parent = base.substring(0, lastSlash);
            var segment = base.substring(lastSlash + 1);

            // An action with a literal path of its own — /_size — addresses something else on the
            // same resource, and has to be recognized before the single-document reading, or
            // ".../inventory/_size?filter={...}" looks for a document whose _id is "_size".
            var byPath = readableActionAtPath(principal, effectiveBaseUrl(ctx), effectiveScope(ctx), parent, "/" + segment);
            if (byPath != null) {
                return readOperation(ctx, principal, parent, byPath, queryArgs).orElseGet(() -> unknownResourceResult(uri));
            }

            var args = new LinkedHashMap<String, Object>(queryArgs);
            args.put("id", segment);
            var singleDoc = readOperation(ctx, principal, parent, "get", args);
            if (singleDoc.isPresent()) {
                return singleDoc.get();
            }
        }

        return unknownResourceResult(uri);
    }

    /** The name of {@code resourceUri}'s readable action whose {@code pathTemplate} is exactly {@code path}, or {@code null} if it has none. */
    private String readableActionAtPath(BaseAccount principal, String baseUrl, String scope, String resourceUri, String path) {
        return resourceLookup.find(principal, baseUrl, scope, resourceUri)
                .flatMap(r -> r.actions().entrySet().stream()
                        .filter(e -> e.getValue().readable() && path.equals(e.getValue().pathTemplate()))
                        .map(Map.Entry::getKey)
                        .findFirst())
                .orElse(null);
    }

    /** {@code TextResourceContents.builder} takes only the two required fields, {@code (uri, text)} — the mime type is optional and set separately. */
    private static TextResourceContents textContents(String uri, String mimeType, String text) {
        return TextResourceContents.builder(uri, text).mimeType(mimeType).build();
    }

    private static McpSchema.ReadResourceResult unknownResourceResult(String uri) {
        return McpSchema.ReadResourceResult
                .builder(List.of(textContents(uri, "text/plain", "Error: unknown or not MCP-enabled resource: " + uri)))
                .build();
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
     * <p>The decision that authorized this very operation travels into {@link McpContext}, so the
     * executing plugin applies the same ACL {@code readFilter}/{@code projectResponse} the REST
     * path would (restheart#722): this read never touches the HTTP interceptor chain, so nothing
     * else would apply them.
     */
    private Optional<McpSchema.ReadResourceResult> readOperation(McpTransportContext ctx, BaseAccount principal, String resourceUri, String actionName, Map<String, Object> rawArgs) {
        var resourceOpt = resourceLookup.find(principal, effectiveBaseUrl(ctx), effectiveScope(ctx), resourceUri);
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

        var owner = resourceLookup.findOwner(principal, effectiveBaseUrl(ctx), effectiveScope(ctx), resourceUri);
        if (owner.isEmpty()) {
            return Optional.of(errorResourceResult(resourceUri, "internal error: resource owner not found"));
        }

        var readCtx = new McpContext(principal, effectiveBaseUrl(ctx), effectiveScope(ctx), owner.get().pluginName(), owner.get().pluginUri(),
                owner.get().pluginConfiguration(), authorization(ctx));

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
            return McpSchema.ReadResourceResult.builder(List.of(textContents(uri, "application/json", text))).build();
        } catch (Exception e) {
            LOGGER.error("Failed to serialize documents-mode read result for {}", uri, e);
            return errorResourceResult(uri, "internal error: " + e.getMessage());
        }
    }

    private static McpSchema.ReadResourceResult errorResourceResult(String uri, String message) {
        return McpSchema.ReadResourceResult.builder(List.of(textContents(uri, "text/plain", "Error: " + message))).build();
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

            // A valueless parameter means "not supplied", not "supplied as an empty string".
            // This is what a client sends when it expands a resource template whose fields the
            // user left blank — `?filter=&page=` — and reading it any other way turns the most
            // ordinary request there is (give me this resource, unfiltered) into a wall of type
            // errors: '' is not an object, '' is not an integer.
            if (!value.isEmpty()) {
                args.put(key, value);
            }
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
        final var baseUrl = effectiveBaseUrl(exchange);
        // Resolved from the request, exactly as handle() does: this path runs outside the MCP
        // transport context, and answering with a different scope than the one serving the
        // session would authorize against a catalogue the caller is not being served.
        final var scope = resolveScope(Request.of(exchange));
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

        // Mirrors readTemplateMatch's own resolution exactly (see its javadoc): split the query
        // string off first, then try an exact resource match before a single-document fallback,
        // so a combined shape (.../inventory/<id>?jsonMode=...) is still recognized as one
        // documents-mode operation to authorize, not silently skipped.
        var queryIdx = uri.indexOf('?');
        var base = queryIdx >= 0 ? uri.substring(0, queryIdx) : uri;
        var queryParameters = queryIdx >= 0 ? parseQueryParameters(uri.substring(queryIdx + 1)) : Map.<String, Deque<String>>of();

        var resource = resourceLookup.find(principal, baseUrl, scope, base);
        if (resource.isPresent()) {
            // A bare or filtered resource read defaults to documents-mode whenever the resource
            // has a readable action (McpService.readBareResource/readTemplateMatch, #617) — a
            // real backend read that must be authorized exactly like its REST-equivalent GET.
            // defaultReadableAction, not a hardcoded "query": an aggregation's readable action is
            // "execute", not "query" — hardcoding it here previously meant an aggregation's
            // documents-mode read skipped this check entirely, always falling back to identity.
            var actionName = defaultReadableAction(resource.get());
            return actionName == null ? null : withMethodPathAndQuery(identity, pathOf(base), queryParameters);
        }

        // Same two fallbacks readTemplateMatch uses, in the same order — this descriptor must
        // describe the operation that will actually run, so what counts as reachable here and
        // what counts as reachable there cannot drift apart.
        var lastSlash = base.lastIndexOf('/');
        if (lastSlash > 0) {
            var parent = base.substring(0, lastSlash);
            if (readableActionAtPath(principal, baseUrl, scope, parent, base.substring(lastSlash)) != null
                    || isReadableAction(principal, baseUrl, scope, parent, "get")) {
                return withMethodPathAndQuery(identity, pathOf(base), queryParameters);
            }
        }

        return null;
    }

    private boolean isReadableAction(BaseAccount principal, String baseUrl, String scope, String resourceUri, String actionName) {
        return resourceLookup.find(principal, baseUrl, scope, resourceUri)
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

            // Dropped for the same reason as in parseQueryArgs, and it has to be the same reason:
            // this builds the descriptor an ACL is evaluated against, so it must describe the
            // operation that will actually run. A parameter ignored at execution but present here
            // would let a predicate match on something the request does not really carry.
            if (!value.isEmpty()) {
                result.computeIfAbsent(key, k -> new ArrayDeque<>()).add(value);
            }
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
        // Before the scope is resolved: a CORS preflight carries no credentials and no session,
        // so there is nothing to resolve a scope from and nothing it could leak.
        if (req.isOptions()) {
            handleOptions(req);
            return;
        }

        var scope = resolveScope(req);

        if (McpScopeProvider.UNRESOLVED.equals(scope)) {
            // Refused, not served an empty catalogue: empty is indistinguishable from "you have
            // not created anything yet", and would cost an afternoon of support to tell apart.
            LOGGER.warn("Refusing an MCP request whose scope could not be resolved");
            res.setStatusCode(HttpStatus.SC_FORBIDDEN);
            return;
        }

        var scoped = scopedServer(scope);
        var ctx = buildContext(req, scope);

        // Per scope, not per process: one flag for the whole instance would let whichever scope
        // arrived first switch initialisation off for every other, leaving the second caller a
        // registry that is never populated.
        if (publicBaseUrl != null && scoped.resourcesInitialized().compareAndSet(false, true)) {
            syncResourceRegistry(scoped, effectiveBaseUrl(req.getExchange()));
        }

        if (req.isPost()) {
            // Opened on demand, before the SDK records the subscription: the SDK keeps that map
            // private, so this is how we learn a resource is wanted. Watching every exposed
            // collection instead would pay for streams nobody asked for.
            if (watchIfSubscribing(req, res, ctx)) {
                scoped.provider().handlePost(req, res, ctx);
            }
        } else if (req.isGet()) {
            scoped.provider().handleGet(req, res, ctx);
        } else if (req.isDelete()) {
            scoped.provider().handleDelete(req, res, ctx);
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

    private McpTransportContext buildContext(ByteArrayRequest req, String scope) {
        var ctx = new HashMap<String, Object>();
        ctx.put(CTX_BASE_URL, resolveBaseUrl(req));
        ctx.put(CTX_REQUEST, req);
        // Carried, not recomputed: the scope decided which server serves this request, and a
        // second resolution could disagree with the first.
        ctx.put(CTX_SCOPE, scope);
        if (req.getAuthenticatedAccount() instanceof BaseAccount principal) {
            ctx.put(CTX_PRINCIPAL, principal);
        }
        return McpTransportContext.create(ctx);
    }

    /** Mirrors {@code OAuthProtectedResourceMetadataService.resolveServerUrl} (restheart-security). */
    /**
     * The base URL every resource URI this request produces is built from.
     *
     * <p>{@code public-base-url} wins whenever it is configured. The alternative — deriving it from
     * the request — is a guess that a proxy can make wrong: a TLS-terminating one hands RESTHeart
     * an {@code http://} {@code Host} for a site published over {@code https://}. Worse, only
     * {@code list_apis} could do the guessing at all: the {@code resources} primitive is served
     * from a registry the SDK builds once for the whole server, so it has always used the
     * configured value. Preferring the configured value here is what stops the two channels from
     * naming the same resource differently — which is exactly what a client noticed, offered a
     * {@code localhost} URI by one and an external one by the other.
     *
     * <p>Falling back to the request keeps a deployment that never configured it working as before,
     * including the one where {@code public-base-url} is commented out to switch the {@code
     * resources} primitive off entirely.
     */
    private String resolveBaseUrl(ByteArrayRequest req) {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl;
        }

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

    /**
     * The {@code /mcp} request being served, for the one thing that needs it: resolving the
     * per-request {@code account-properties-claims} override (attached by an interceptor, which
     * runs on {@code /mcp} like on any other request) when {@code get_token} mints a token. A
     * multi-tenant deployment selects a different claim set per tenant that way, and a token
     * missing those claims would fail ACL rules written against them.
     */
    /**
     * The base URL this request's resource URIs are built from: the tenant's own when an
     * interceptor attached one, the configured value otherwise.
     *
     * <p>Separate from the {@link #publicBaseUrl} field, which keeps its other job — being
     * non-null is what says the resources primitive is enabled on this node. That is a property of
     * the deployment; which host the URIs name is a property of the caller.
     */
    /** The scope this request was routed to — put there by {@link #buildContext}, never recomputed. */
    private static String effectiveScope(McpTransportContext ctx) {
        return ctx.get(CTX_SCOPE) instanceof String s && !s.isBlank() ? s : McpScopeProvider.UNPARTITIONED;
    }

    private String effectiveBaseUrl(McpTransportContext ctx) {
        return RequestOverrides.str(request(ctx), RequestOverrides.MCP_PUBLIC_BASE_URL, publicBaseUrl);
    }

    /**
     * The same, for {@code operationsToAuthorize}: it runs before the service, with the exchange
     * and no transport context. Resolving with the wrong base would fail to identify the resource
     * the URI names, and an authorization decision would then be taken about the wrong thing.
     */
    private String effectiveBaseUrl(HttpServerExchange exchange) {
        try {
            return RequestOverrides.str(Request.of(exchange), RequestOverrides.MCP_PUBLIC_BASE_URL, publicBaseUrl);
        } catch (final Exception e) {
            LOGGER.debug("could not read the base URL override from the exchange; using the configured one", e);
            return publicBaseUrl;
        }
    }

    private static Request<?> request(McpTransportContext ctx) {
        return ctx.get(CTX_REQUEST) instanceof Request<?> r ? r : null;
    }

    /**
     * The decision {@code AuthorizersHandler} took for this request's documents-mode operation.
     *
     * <p>{@code null} whenever the request wasn't authorized through a descriptor — a {@code
     * secured: false} deployment, or an authorizer that resolves no permissions. That is not a
     * gap: there is no ACL entry behind such a request, so there is no filter to apply either.
     */
    private static Decision authorization(McpTransportContext ctx) {
        var request = request(ctx);
        if (request == null) {
            return null;
        }
        var decisions = request.getExchange().getAttachment(DescriptorAwareAuthorizer.AUTHORIZED_OPERATIONS);
        // exactly one, since operationsToAuthorize() returns a single descriptor for the single
        // documents-mode read this request performs
        return decisions != null && decisions.size() == 1 ? decisions.get(0) : null;
    }

    /**
     * The catalog filter for whoever is asking: a resource is listed only if a read of it would be
     * authorized, decided by the very rule that authorizes the read
     * ({@link org.restheart.plugins.security.DescriptorAuthorization}).
     *
     * <p>Everything is visible when there is no request to derive an identity from — which happens
     * only for calls that do not come from a client, and never for a real one.
     */
    /**
     * Whether the caller of {@code exchange} should see the resource at {@code uri} in a listing —
     * the same question {@link #visibleTo} answers, asked by URI because that is all a listing
     * carries. Returns a predicate rather than a boolean so the authorizers and the caller's
     * identity are resolved once for a whole list, not once per entry.
     *
     * <p>A listing carries URIs the catalog has no resource for — {@code /coll/_size} and
     * {@code /coll/{id}} are entries in their own right, synthesized from a collection's actions.
     * Those are decided too, on their own path: the catalog is consulted only to learn which HTTP
     * method the read uses, falling back to the owning resource's and then to {@code GET}. Not
     * finding a URI is never a reason to show it.
     *
     * <p><strong>Undecidable means not visible.</strong> With no catalog and no authorization to
     * consult — the service not initialized yet — this hides everything rather than showing
     * everything. On a process serving one tenant that costs an empty listing and a warning; on a
     * shared one, answering "visible" to a question it cannot answer hands a caller the names of
     * resources belonging to someone else. In practice this branch is unreachable: the interceptor
     * that calls it only runs on a response {@code mcpService} itself produced.
     */
    static Predicate<String> catalogVisibility(HttpServerExchange exchange) {
        var self = instance;

        if (self == null || self.resourceLookup == null || self.publicBaseUrl == null || self.authorization == null) {
            LOGGER.warn("Catalog visibility asked before mcpService is ready — hiding every entry rather than showing entries nobody has checked");
            return uri -> false;
        }

        var identity = RequestDescriptor.of(exchange);

        return uri -> CatalogVisibility.isReadable(self.authorization, identity,
                CatalogVisibility.pathOf(uri), self.readMethodOf(identity, self.resolveScope(Request.of(exchange)), uri));
    }

    /**
     * The HTTP method a read of {@code uri} would use: the resource's own when the catalog knows
     * that URI, otherwise the method of the resource it hangs off — {@code /coll/_size} reads as
     * {@code /coll} does — and {@code GET} when neither is known.
     */
    private String readMethodOf(RequestDescriptor identity, String scope, String uri) {
        var exact = resourceLookup.find(identity.principal(), publicBaseUrl, scope, uri);

        if (exact.isPresent()) {
            return CatalogVisibility.methodOf(CatalogVisibility.readAction(exact.get()));
        }

        var lastSlash = uri.lastIndexOf('/');

        if (lastSlash > 0) {
            var owner = resourceLookup.find(identity.principal(), publicBaseUrl, scope, uri.substring(0, lastSlash));

            if (owner.isPresent()) {
                return CatalogVisibility.methodOf(CatalogVisibility.readAction(owner.get()));
            }
        }

        return "GET";
    }

    private Predicate<McpResource> visibleTo(McpTransportContext ctx) {
        var request = request(ctx);

        if (request == null) {
            return r -> true;
        }

        var identity = RequestDescriptor.of(request.getExchange());

        return resource -> CatalogVisibility.isVisible(authorization, identity, resource);
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

        return McpSchema.Tool.builder("list_apis", inputSchema(properties, null))
                .description("""
                        Lists or describes MCP-enabled APIs exposed by RESTHeart. Without arguments, returns the \
                        catalog (URIs, kinds, short descriptions) — optionally narrowed with `query`/`kind` and \
                        paged with `limit`/`cursor`. With a resource URI, returns full context: kind, supported \
                        transports, actions with parameter types, auth requirements, examples. On a deployment \
                        with many resources, prefer a filtered call over an unfiltered one.

                        To READ a resource, use resources/read — it returns the data directly. Call this \
                        before how_to_call only when you need to write, or to invoke something \
                        resources/read does not cover.\
                        """)
                .build();
    }

    static McpSchema.Tool howToCallToolDefinition() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("resource", schemaProp("string", "Resource URI."));
        properties.put("action", schemaProp("string", "Action name as declared in the resource's actions map."));
        properties.put("args", schemaProp("object", "Action arguments — values for params and body declared by the resource."));
        properties.put("transport", schemaProp("string",
                "Optional transport preference (e.g. websocket vs sse for streams). If omitted, the resource's default transport is used."));

        return McpSchema.Tool.builder("how_to_call", inputSchema(properties, List.of("resource", "action")))
                .description("""
                        NOT FOR READS. If the resource appears in resources/list, read it with \
                        resources/read: the data comes back inside the MCP response, with no request \
                        for you to send and no token to fetch. Use this tool for writes, and for \
                        anything resources/read does not cover.

                        Returns a request descriptor (transport, URL, headers, body) for invoking a known MCP \
                        resource. The tool COMPOSES the request — it does NOT execute it. After receiving the \
                        response, choose any client appropriate to the descriptor's transport and your host \
                        environment (HTTP libraries, WebSocket libraries, OS shells with curl/httpie/wscat, \
                        generated code in any language). The MCP server does not prescribe the tool.

                        Dispatch by action — the set of valid actions for a given resource is declared in the \
                        resource's list_apis output. Validate args against the declared params and body_schema \
                        before calling.

                        The descriptor is stable and safe to reuse: it carries no credential. Its Authorization \
                        header holds the placeholder `%s` — call get_token to obtain a token and substitute it just \
                        before sending the request, not when you receive this descriptor.

                        Call this ONCE PER ACTION SHAPE, not once per request. The descriptor for, say, creating a \
                        document in a collection is the same every time apart from the body: keep it and reuse it, \
                        changing only what varies. A repeated write is then two steps, not three — get_token, then \
                        send — and the second is unavoidable for any short-lived credential. Calling how_to_call \
                        before every write costs a round trip that buys nothing, and in a contended situation that \
                        delay can lose you the operation.\
                        """.formatted(DescriptorRenderer.TOKEN_PLACEHOLDER))
                .build();
    }

    static McpSchema.Tool getTokenToolDefinition() {
        return McpSchema.Tool.builder("get_token", inputSchema(new LinkedHashMap<>(), null))
                .description("""
                        Issues a short-lived access token for the current session, to fill in the `%s` placeholder \
                        of a descriptor returned by how_to_call.

                        The token expires within seconds (see `expires_in` in the response), so call this \
                        immediately before sending the request — not in advance, and do not store it. Getting a \
                        fresh one costs nothing; reusing a stale one fails with 401. One token can serve several \
                        requests made within its window.

                        It carries the identity and roles of the current session and no more, so it can do exactly \
                        what this session can do. Requires an authenticated session.

                        The response is a JSON object: read `access_token` from it and put it in the descriptor's \
                        Authorization header in place of the placeholder, as `Bearer <token>`.\
                        """.formatted(DescriptorRenderer.TOKEN_PLACEHOLDER))
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
                    principal(ctx), baseUrl(ctx), effectiveScope(ctx),
                    stringArg(args, "resource"), stringArg(args, "query"), stringArg(args, "kind"),
                    intArg(args, "limit"), stringArg(args, "cursor"), visibleTo(ctx));
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
                    principal(ctx), baseUrl(ctx), effectiveScope(ctx),
                    stringArg(args, "resource"), stringArg(args, "action"), actionArgs,
                    stringArg(args, "transport"), visibleTo(ctx));
            return textResult(jsonMapper.writeValueAsString(result));
        } catch (UnknownResourceException | UnknownActionException | ValidationFailedException e) {
            return errorResult(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("how_to_call failed", e);
            return errorResult("internal error: " + e.getMessage());
        }
    }

    /**
     * Issues the short-lived credential that fills in {@code how_to_call}'s
     * {@link DescriptorRenderer#TOKEN_PLACEHOLDER}.
     *
     * <p>A tool of its own rather than something embedded in the descriptor, because the two are
     * needed at different moments: a descriptor answers "how do I call this", is stable, and is
     * worth reusing; a token is worth seconds. Minting it with the descriptor started the clock at
     * the wrong time — an agent that reasoned, or asked its user, between receiving the descriptor
     * and sending the request could find the token already dead. Here the window opens when the
     * caller is about to use it.
     *
     * <p>Deliberately never falls back to a token from another source (the configured {@code
     * TokenManager}'s own, say): those are session-length by design, and quietly handing one over
     * would reintroduce exactly the long-lived-secret risk this exists to avoid. No issuer or no
     * authenticated session is an error the caller is told about, not a weaker credential.
     */
    private CallToolResult callGetToken(McpSyncServerExchange exchange, CallToolRequest request) {
        var ctx = exchange.transportContext();
        var principal = principal(ctx);

        if (principal == null) {
            return errorResult("""
                    no authenticated session: get_token issues a token for the caller's own identity, so the MCP \
                    session must itself be authenticated\
                    """);
        }

        var issuer = PluginModelResolver.resolve(pluginsRegistry, resolvedJwtIssuers, "jwtIssuer", JwtIssuer.class);

        if (issuer.isEmpty()) {
            return errorResult("""
                    token issuance is not available on this deployment: the 'jwtIssuer' provider is disabled, or \
                    the 'jwtConfigProvider' it builds on is not configured\
                    """);
        }

        try {
            // request(ctx), not the bare overload: it carries the per-request claim-set override a
            // multi-tenant deployment attaches, without which the token would miss the very claims
            // that deployment's ACL rules match on
            var token = issuer.get().issue(principal, EPHEMERAL_TOKEN_TTL, request(ctx));

            var result = new LinkedHashMap<String, Object>();
            // same field names as RESTHeart's own /token endpoint (and OAuth): an agent that has
            // seen either recognizes this without being told, and expires_in is what tells it
            // whether the token it holds is still worth sending
            result.put("access_token", token);
            result.put("token_type", "Bearer");
            result.put("expires_in", EPHEMERAL_TOKEN_TTL.toSeconds());
            result.put("username", principal.getPrincipal().getName());
            result.put("roles", principal.getRoles());

            return textResult(jsonMapper.writeValueAsString(result));
        } catch (Exception e) {
            LOGGER.error("get_token failed", e);
            return errorResult("internal error: " + e.getMessage());
        }
    }

    private static CallToolResult textResult(String text) {
        return new CallToolResult(List.of(TextContent.builder(text).build()), false, null, null);
    }

    private static CallToolResult errorResult(String message) {
        return new CallToolResult(List.of(TextContent.builder("Error: " + message).build()), true, null, null);
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
