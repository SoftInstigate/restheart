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

import java.util.Map;

import org.restheart.plugins.mcp.McpAware;

/**
 * An {@link McpAware} plugin found eligible for MCP exposure at boot, together with
 * the boot-time-resolved metadata a per-request {@code McpContext} is built from.
 * {@code baseUrl} and {@code principal} are not here — those only exist once a real
 * request arrives, so the caller (e.g. {@code ListApisTool}) supplies them when it
 * builds the actual {@code McpContext} for a call to {@link McpAware#describeMcp}.
 */
public record RegisteredMcpAware(
        McpAware instance,
        String pluginName,
        String pluginUri,
        Map<String, Object> pluginConfiguration) {
}
