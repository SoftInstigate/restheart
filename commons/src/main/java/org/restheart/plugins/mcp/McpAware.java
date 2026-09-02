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

    private static Map<String, Object> castKeys(Map<?, ?> m) {
        var result = new LinkedHashMap<String, Object>();
        m.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
}
