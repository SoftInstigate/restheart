/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.mcp;

import java.util.List;
import java.util.Optional;

import org.bson.BsonDocument;
import org.restheart.plugins.mcp.McpResource;

/**
 * Builds the {@code McpResource} for a MongoDB database — a pure discovery namespace, not an
 * invokable resource ({@code transports} is always empty).
 *
 * <p>Like every other kind, exposure is opt-in via the database's own {@code mcp} block
 * ({@code enabled} + required {@code description}); a database is never listed just because it
 * happens to contain MCP-enabled collections. The developer/operator controls MCP exposure at
 * every level independently of ACL — ACL is enforced naturally on the real request, never used
 * to decide catalog visibility (see {@link CollectionMcpResourceBuilder}).
 */
public final class DatabaseMcpResourceBuilder {

    private DatabaseMcpResourceBuilder() {
    }

    /**
     * @param databaseUri            the database's resource URI (e.g. {@code https://host/db})
     * @param mcp                    the database's own {@code mcp} block, or {@code null} if absent
     * @param mcpEnabledCollectionUris URIs of this database's collections that are themselves
     *                                 MCP-enabled (i.e. {@link CollectionMcpResourceBuilder} produced
     *                                 a resource for them) — referenced, not re-derived, here
     * @return the resource, or empty if not MCP-enabled: no {@code mcp} block,
     *         {@code mcp.enabled == false}, or a missing required {@code description}
     */
    public static Optional<McpResource> build(String databaseUri, BsonDocument mcp, List<String> mcpEnabledCollectionUris) {
        if (mcp == null || isExplicitlyDisabled(mcp) || description(mcp) == null) {
            return Optional.empty();
        }

        var builder = McpResource.builder()
                .uri(databaseUri)
                .kind("database")
                .description(description(mcp));

        if (mcpEnabledCollectionUris != null && !mcpEnabledCollectionUris.isEmpty()) {
            builder.extra("collections", List.copyOf(mcpEnabledCollectionUris));
        }

        return Optional.of(builder.build());
    }

    private static boolean isExplicitlyDisabled(BsonDocument mcp) {
        var enabled = mcp.get("enabled");
        return enabled != null && enabled.isBoolean() && !enabled.asBoolean().getValue();
    }

    private static String description(BsonDocument mcp) {
        var v = mcp.get("description");
        return v != null && v.isString() ? v.asString().getValue() : null;
    }
}
