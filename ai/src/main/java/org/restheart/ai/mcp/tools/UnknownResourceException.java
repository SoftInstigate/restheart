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

/**
 * No registered {@code McpAware} produced a resource with the requested URI (either it
 * doesn't exist, or it exists but was filtered out of {@code describeMcp(ctx)} for the
 * calling principal). Maps to MCP JSON-RPC error {@code -32002} (restheart#615 Security).
 */
public class UnknownResourceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UnknownResourceException(String resourceUri) {
        super("unknown resource: " + resourceUri);
    }
}
