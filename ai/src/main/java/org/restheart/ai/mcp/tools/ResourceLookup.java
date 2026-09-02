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
import java.util.List;
import java.util.Optional;

import org.restheart.ai.mcp.McpAwareRegistry;
import org.restheart.ai.mcp.api.McpContext;
import org.restheart.ai.mcp.api.McpResource;
import org.restheart.security.BaseAccount;

/**
 * Shared by {@link ListApisTool} and {@link HowToCallTool}: calls {@code describeMcp(ctx)}
 * on every plugin {@link McpAwareRegistry} found eligible at boot, building a fresh
 * per-plugin {@code McpContext} from the request's own {@code principal}/{@code baseUrl}
 * plus that plugin's boot-time-resolved name/uri/configuration.
 *
 * <p>Uncached — every call re-invokes {@code describeMcp()} on every registered plugin.
 * Caching (with TTL and invalidation-hook-driven refresh, per restheart#615's {@code discover}
 * config section) is deferred to whenever {@code InvalidationHook} wiring is built; doing it
 * here first would be optimizing before there is a real hit to measure.
 */
final class ResourceLookup {

    private ResourceLookup() {
    }

    static List<McpResource> all(McpAwareRegistry registry, BaseAccount principal, String baseUrl) {
        var result = new ArrayList<McpResource>();
        for (var registered : registry.registered()) {
            var ctx = new McpContext(principal, baseUrl, registered.pluginName(), registered.pluginUri(), registered.pluginConfiguration());
            result.addAll(registered.instance().describeMcp(ctx));
        }
        return result;
    }

    static Optional<McpResource> find(McpAwareRegistry registry, BaseAccount principal, String baseUrl, String resourceUri) {
        for (var registered : registry.registered()) {
            var ctx = new McpContext(principal, baseUrl, registered.pluginName(), registered.pluginUri(), registered.pluginConfiguration());
            for (var resource : registered.instance().describeMcp(ctx)) {
                if (resource.uri().equals(resourceUri)) {
                    return Optional.of(resource);
                }
            }
        }
        return Optional.empty();
    }
}
