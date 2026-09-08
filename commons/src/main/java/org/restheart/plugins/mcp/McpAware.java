/*-
 * ========================LICENSE_START=================================
 * restheart-commons
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
package org.restheart.plugins.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implemented by a plugin that wants to be exposed to AI agents via MCP.
 * Implementing this interface is itself the opt-in signal — there is no
 * companion {@code @RegisterPlugin} attribute to keep in sync with it. The
 * operator can still disable exposure per deployment via a plain {@code mcp: false}
 * key in the plugin's own configuration; default, once the interface is
 * implemented, is exposed.
 *
 * <h2>Mode A — minimal effort with a code-baked default</h2>
 * Override only {@link #defaultMcpConfig()}. The default {@link #describeMcp(McpContext)}
 * deep-merges it with the operator's {@code mcp-config} (operator wins on conflict) and
 * builds a single resource — no imperative MCP logic in the plugin. Suits a plugin with
 * one resource and a sensible default description.
 *
 * <h2>Mode B — custom</h2>
 * Override {@link #describeMcp(McpContext)} directly. Used when a plugin contributes
 * multiple resources dynamically (one per MongoDB collection, one per GraphQL app, ...),
 * when the resource list depends on runtime state, or when per-principal ACL filtering
 * of the catalog is needed (via {@link McpContext#principal()}). A custom override owns
 * its own configuration handling; {@link #defaultMcpConfig()} is not automatically
 * consulted for it.
 */
public interface McpAware {
    /**
     * Returns the MCP resources this plugin contributes to the catalog. Called by the
     * MCP server at boot and whenever a registered {@link InvalidationHook} fires.
     *
     * <p>The default implementation builds a single resource from the deep-merge of
     * {@link #defaultMcpConfig()} and the operator-supplied {@code mcp-config} in
     * {@code ctx.pluginConfiguration()} (operator wins on conflict) — see {@link McpResourceParser}.
     */
    default List<McpResource> describeMcp(McpContext ctx) {
        var mcpConfig = ctx.pluginConfiguration().get("mcp-config");
        var operatorConfig = mcpConfig instanceof Map<?, ?> m ? castKeys(m) : null;
        var transports = TransportDeriver.derive(this);
        var resource = McpResourceParser.fromMerged(defaultMcpConfig(), operatorConfig).buildSingle(ctx, transports);
        return List.of(resource);
    }

    /**
     * Starts watching a resource's content, so the framework can tell subscribed clients it
     * changed (#617, {@code notifications/resources/updated}).
     *
     * <p>Default: not watchable. A plugin that cannot tell when its data changed says so by not
     * overriding this, and {@code resources/subscribe} on such a resource is refused — accepting a
     * subscription that will never fire is worse than declining it, because the client waits
     * instead of polling.
     *
     * <p>{@code onChange} is a bare signal, not an event: the MCP notification carries no payload,
     * it only tells a client to re-read. So an implementation is free — and expected — to collapse
     * a burst of underlying changes into one call, and the framework rate-limits it again on the
     * way out. A collection taking a thousand writes a second must not produce a thousand of
     * anything.
     *
     * @param ctx         the calling context
     * @param resourceUri the resource to watch
     * @param onChange    invoked, on any thread, whenever the resource's content may have changed
     * @return a handle whose {@code close()} stops the watch, or empty if this resource cannot be
     *         watched
     */
    default Optional<AutoCloseable> watch(McpContext ctx, String resourceUri, Runnable onChange) {
        return Optional.empty();
    }

    /**
     * Optional default MCP configuration baked into the plugin code, used exclusively by
     * the default {@link #describeMcp(McpContext)} to build a resource without requiring
     * operator YAML (e.g. a built-in {@code /ping} service that should just work).
     *
     * <p>Deep-merged with the operator's {@code mcp-config} (operator wins on conflict).
     * Default returns {@code null} — no code-baked default; if the operator also supplies
     * no {@code mcp-config}, the resource is registered with an empty description.
     *
     * <p>Not consulted by a plugin that overrides {@link #describeMcp(McpContext)}.
     */
    default Map<String, Object> defaultMcpConfig() {
        return null;
    }

    /**
     * Registers a hook this implementation can call when it knows its resources have
     * changed (a metadata write, a configuration reload). Default is a no-op — caching
     * relies on TTL instead.
     */
    default void registerInvalidationHook(InvalidationHook hook) {
    }

    /**
     * Returns the URI-template shapes (RFC 6570) this plugin can produce concrete
     * {@link McpResource}s for — e.g. one per {@code mongo-mounts} entry shape, rather than one
     * per actual database/collection. Used only for the MCP {@code resources/templates/list}
     * primitive; unrelated to {@link #describeMcp(McpContext)}, which still enumerates every
     * concrete resource regardless of whether templates are also advertised.
     *
     * <p>Default returns no templates — most plugins expose a fixed, small resource set where a
     * template adds nothing a client couldn't already see via {@code resources/list}.
     */
    default List<McpResourceTemplate> describeTemplates(McpContext ctx) {
        return List.of();
    }

    /**
     * Implements {@code resources/read} "documents mode" (#617) for one action of a resource this
     * plugin contributes — called only for an action the resource itself declared
     * {@code readable: true} on (see {@link McpResource.Action#readable()}); the framework
     * validates that before calling this, so an implementation never has to re-check eligibility.
     *
     * <p>Called in-process — no {@code HttpServerExchange} is created or required by the
     * framework. The implementation talks directly to its own engine (a {@code MongoClient}, a
     * GraphQL execution engine, ...), exactly as it already does inside its own {@code handle()}
     * method, applying the exact same ACL/validation enforcement its normal REST path does.
     *
     * @param resource the resource URI being read (without any query string/extra path — the
     *                 same URI {@link #describeMcp(McpContext)} produced for it)
     * @param action   the {@code readable} action being invoked (e.g. {@code query}, {@code get})
     * @param args     already-parsed arguments (query string for a collection, path segment for
     *                 a single document, ...), validated against the action's declared
     *                 {@code params}/{@code body_schema} before this is called
     * @return the resource's actual content, or {@link Optional#empty()} on failure to produce
     *         it — a plugin that marks no action readable never needs to override this at all
     *         (the default already returns empty), since the framework only ever registers a
     *         resource with the MCP resources primitive when it has a {@code readable} action;
     *         {@code resources/read} always means "here is data", never a description of the
     *         resource — that's exclusively {@code list_apis}/{@code how_to_call}'s job
     */
    default Optional<McpReadResult> readResource(McpContext ctx, String resource, String action, Map<String, Object> args) {
        return Optional.empty();
    }

    private static Map<String, Object> castKeys(Map<?, ?> m) {
        var result = new LinkedHashMap<String, Object>();
        m.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
}
