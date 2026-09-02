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
package org.restheart.ai.mcp.api;

/**
 * Callback a {@link McpAware} implementation invokes to tell the MCP server
 * that the resources it contributes have changed (e.g. a metadata write, a
 * configuration reload). The server is registered via
 * {@link McpAware#registerInvalidationHook(InvalidationHook)} at boot; calling
 * {@link #invalidate()} triggers a cache invalidation and, once the transport
 * exists, a {@code notifications/tools/list_changed} notification.
 */
@FunctionalInterface
public interface InvalidationHook {
    /**
     * Signals that the resources contributed by the registering {@link McpAware}
     * have changed and should be re-fetched via {@link McpAware#describeMcp(McpContext)}.
     */
    void invalidate();
}
