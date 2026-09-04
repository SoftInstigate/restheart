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

/**
 * The actual data returned by {@link McpAware#readResource(McpContext, String, String, Map)}
 * ("documents mode" — see #617) — as opposed to {@link McpResource}, which describes how to call
 * a resource rather than carrying its content.
 *
 * @param content the resource's real content (e.g. a {@code List} of documents, or a single
 *                document as a {@code Map}), serialized as JSON by the MCP framework
 * @param meta optional metadata about the read (e.g. {@code total_count}, {@code page},
 *             {@code next} for a paginated collection query) — {@code null} if not applicable
 */
public record McpReadResult(Object content, Map<String, Object> meta) {
    public McpReadResult(Object content) {
        this(content, null);
    }
}
