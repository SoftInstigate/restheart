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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.restheart.ai.mcp.api.McpAware;
import org.restheart.ai.mcp.api.McpContext;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.Service;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers, at boot, which registered plugins are eligible for MCP exposure.
 *
 * <p>A plugin is eligible when:
 * <ol>
 *   <li>it implements {@link McpAware} — the sole opt-in signal, no companion
 *       {@code @RegisterPlugin} attribute exists or is needed;</li>
 *   <li>the plugin itself is enabled ({@link PluginRecord#isEnabled()});</li>
 *   <li>its own configuration does not set {@code mcp: false} — default, once
 *       {@code McpAware} is implemented, is exposed.</li>
 * </ol>
 *
 * <p>Scoped to {@link PluginsRegistry#getServices()} for now: every worked example
 * in restheart#615 — and the MongoDB/GraphQL integration in restheart#616 — is a
 * {@code Service}, which is uniformly URI-resolvable via {@link PluginUtils#actualUri}.
 * {@code SseService} does not extend {@code Service} and has no such resolver yet;
 * extending discovery to it is left for whenever a real {@code McpAware}
 * {@code SseService} needs it, rather than special-cased here speculatively.
 */
public final class McpAwareRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(McpAwareRegistry.class);

    private final List<RegisteredMcpAware> registered;

    private McpAwareRegistry(List<RegisteredMcpAware> registered) {
        this.registered = registered;
    }

    public List<RegisteredMcpAware> registered() {
        return registered;
    }

    /** Builds a registry directly from already-resolved entries — for tests, or for merging entries from multiple sources. */
    public static McpAwareRegistry of(List<RegisteredMcpAware> registered) {
        return new McpAwareRegistry(List.copyOf(registered));
    }

    public static McpAwareRegistry discover(PluginsRegistry pluginsRegistry) {
        var result = new ArrayList<RegisteredMcpAware>();

        for (var record : pluginsRegistry.getServices()) {
            addIfEligible(result, record);
        }

        return new McpAwareRegistry(result);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void addIfEligible(List<RegisteredMcpAware> result, PluginRecord<Service<?, ?>> record) {
        if (!(record.getInstance() instanceof McpAware mcpAware)) {
            return;
        }

        if (!record.isEnabled()) {
            LOGGER.debug("plugin '{}' implements McpAware but is itself disabled; not exposed via MCP", record.getName());
            return;
        }

        var confArgs = record.getConfArgs() == null ? Map.<String, Object>of() : record.getConfArgs();

        if (Boolean.FALSE.equals(confArgs.get("mcp"))) {
            LOGGER.debug("plugin '{}' has mcp: false in its configuration; not exposed via MCP", record.getName());
            return;
        }

        var serviceClass = (Class) record.getInstance().getClass();
        var uri = PluginUtils.actualUri(confArgs, serviceClass);

        if (usesDefaultDescribeMcp(mcpAware) && mcpAware.defaultMcpConfig() == null && confArgs.get("mcp-config") == null) {
            LOGGER.warn("plugin '{}' implements McpAware but supplies neither defaultMcpConfig() nor "
                    + "'mcp-config' in its configuration; it will appear in the MCP catalog with an empty description",
                    record.getName());
        }

        result.add(new RegisteredMcpAware(mcpAware, record.getName(), uri, confArgs));
    }

    /**
     * @return {@code true} if {@code instance} relies on {@link McpAware}'s default
     *         {@code describeMcp()} (Mode A) rather than overriding it (Mode B) — Mode B
     *         plugins typically build resources from their own runtime state, so the
     *         "empty description" heuristic above does not apply to them.
     */
    private static boolean usesDefaultDescribeMcp(McpAware instance) {
        try {
            Method m = instance.getClass().getMethod("describeMcp", McpContext.class);
            return m.getDeclaringClass() == McpAware.class;
        } catch (NoSuchMethodException e) {
            // describeMcp() is always present, inherited at minimum from the default method
            return false;
        }
    }
}
