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
package org.restheart.ai.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.restheart.ai.mcp.McpAwareRegistry;
import org.restheart.ai.mcp.RegisteredMcpAware;
import org.restheart.plugins.mcp.McpContext;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.plugins.mcp.McpResourceTemplate;
import org.restheart.security.BaseAccount;

/**
 * The uncached computation behind {@link CachedResourceLookup}: calls {@code describeMcp(ctx)}
 * on every plugin {@link McpAwareRegistry} found eligible at boot, building a fresh
 * per-plugin {@code McpContext} from the request's own {@code principal}/{@code baseUrl}
 * plus that plugin's boot-time-resolved name/uri/configuration.
 *
 * <p>{@link ListApisTool} and {@link HowToCallTool} never call this directly — they go through
 * {@link CachedResourceLookup}, which memoizes {@link #all} per {@code baseUrl} for a fixed TTL.
 */
final class ResourceLookup {

    private ResourceLookup() {
    }

    /** The catalog's resources together with which registered plugin produced each one — see {@link #catalog}. */
    record Catalog(List<McpResource> resources, Map<String, RegisteredMcpAware> owners) {
    }

    static List<McpResource> all(McpAwareRegistry registry, BaseAccount principal, String baseUrl, String scope) {
        return catalog(registry, principal, baseUrl, scope).resources();
    }

    /**
     * Same computation as {@link #all}, additionally tracking which {@link RegisteredMcpAware}
     * produced each {@link McpResource} — needed to dispatch a documents-mode {@code
     * resources/read} to the right plugin's {@code readResource(...)} (see #617).
     */
    static Catalog catalog(McpAwareRegistry registry, BaseAccount principal, String baseUrl, String scope) {
        var resources = new ArrayList<McpResource>();
        var owners = new LinkedHashMap<String, RegisteredMcpAware>();
        for (var registered : registry.registered()) {
            var ctx = new McpContext(principal, baseUrl, scope, registered.pluginName(), registered.pluginUri(), registered.pluginConfiguration());
            for (var resource : registered.instance().describeMcp(ctx)) {
                resources.add(resource);
                owners.put(resource.uri(), registered);
            }
        }
        return new Catalog(resources, owners);
    }

    /** Same per-plugin {@code McpContext} construction as {@link #all}, for {@code describeTemplates(ctx)} instead of {@code describeMcp(ctx)}. */
    static List<McpResourceTemplate> templates(McpAwareRegistry registry, String baseUrl, String scope) {
        var result = new ArrayList<McpResourceTemplate>();
        for (var registered : registry.registered()) {
            var ctx = new McpContext(null, baseUrl, scope, registered.pluginName(), registered.pluginUri(), registered.pluginConfiguration());
            result.addAll(registered.instance().describeTemplates(ctx));
        }
        return result;
    }
}
