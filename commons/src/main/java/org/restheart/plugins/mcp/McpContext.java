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

import java.util.Map;

import org.restheart.security.BaseAccount;

/**
 * Passed to {@link McpAware#describeMcp(McpContext)}. Carries everything a
 * plugin needs to build its {@link McpResource}s without depending on
 * {@code HttpServerExchange} or any other transport-level type.
 *
 * @param principal the authenticated account of the MCP session — {@link BaseAccount}
 *                  rather than the bare JDK {@code Principal}, since ACL-aware filtering
 *                  (see {@link McpAware#describeMcp(McpContext)}) needs roles, not just a
 *                  name; every RESTHeart account type ({@code MongoRealmAccount},
 *                  {@code JwtAccount}, ...) extends it. {@code null} for an unauthenticated
 *                  call, if the deployment allows one
 * @param baseUrl the public base URL of the RESTHeart instance (no trailing slash),
 *                used to build absolute resource URIs
 * @param pluginName the name the plugin registered with ({@code @RegisterPlugin(name = ...)})
 * @param pluginUri the URI the plugin is mounted at ({@code uri} config, falling back to
 *                  {@code defaultURI}), used together with {@code baseUrl} to build the
 *                  resource URI in the default {@code describeMcp()} path
 * @param pluginConfiguration the plugin's resolved configuration map, so a custom
 *                            {@code describeMcp()} can also read {@code mcp-config} directly
 */
public record McpContext(
        BaseAccount principal,
        String baseUrl,
        String pluginName,
        String pluginUri,
        Map<String, Object> pluginConfiguration) {

    public McpContext {
        if (pluginConfiguration == null) {
            pluginConfiguration = Map.of();
        }
    }
}
