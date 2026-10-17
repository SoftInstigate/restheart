/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.graphql.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.restheart.graphql.mcp.GraphqlMcpAwareImpl.MetadataSource;
import org.restheart.plugins.mcp.McpContext;

public class GraphqlMcpAwareImplTest {

    private static final McpContext CTX = new McpContext(null, "https://host", "graphql", "/graphql", Map.of());

    private static final String APPS_DB = "gqlapps";

    private static MetadataSource of(BsonDocument... docs) {
        return db -> List.of(docs);
    }

    /** Records which database was asked for, so the scope-to-database mapping can be asserted. */
    private static MetadataSource recording(List<String> asked, BsonDocument... docs) {
        return db -> {
            asked.add(db);
            return List.of(docs);
        };
    }

    private static GraphqlMcpAwareImpl impl(MetadataSource source) {
        return new GraphqlMcpAwareImpl(source, APPS_DB);
    }

    @Test
    public void enabledAppWithMcpBlock_producesResourceAtMountedUri() {
        var doc = BsonDocument.parse("""
                {
                  "descriptor": { "uri": "warehouse", "enabled": true },
                  "schema": "type Query { lowStock: [String] }",
                  "mcp": { "description": "Warehouse queries." }
                }
                """);

        var resources = impl(of(doc)).describeMcp(CTX);

        assertEquals(1, resources.size());
        assertEquals("https://host/graphql/warehouse", resources.get(0).uri());
        assertEquals("graphql-app", resources.get(0).kind());
    }

    @Test
    public void disabledApp_notSurfacedEvenWithMcpBlock() {
        var doc = BsonDocument.parse("""
                {
                  "descriptor": { "uri": "warehouse", "enabled": false },
                  "schema": "type Query { lowStock: [String] }",
                  "mcp": { "description": "x" }
                }
                """);

        assertTrue(impl(of(doc)).describeMcp(CTX).isEmpty());
    }

    @Test
    public void appWithNoMcpBlock_notSurfaced() {
        var doc = BsonDocument.parse("""
                {
                  "descriptor": { "uri": "warehouse", "enabled": true },
                  "schema": "type Query { lowStock: [String] }"
                }
                """);

        assertTrue(impl(of(doc)).describeMcp(CTX).isEmpty());
    }

    @Test
    public void missingDescriptor_skippedWithoutError() {
        var doc = BsonDocument.parse("{\"schema\": \"type Query { x: String }\", \"mcp\": {\"description\": \"x\"}}");
        assertTrue(impl(of(doc)).describeMcp(CTX).isEmpty());
    }

    @Test
    public void multipleApps_eachResolvedIndependently() {
        var warehouse = BsonDocument.parse("""
                {"descriptor": {"uri": "warehouse", "enabled": true}, "schema": "type Query { x: String }", "mcp": {"description": "a"}}
                """);
        var billing = BsonDocument.parse("""
                {"descriptor": {"uri": "billing", "enabled": true}, "schema": "type Query { y: String }", "mcp": {"description": "b"}}
                """);

        var resources = impl(of(warehouse, billing)).describeMcp(CTX);

        var uris = resources.stream().map(r -> r.uri()).toList();
        assertTrue(uris.contains("https://host/graphql/warehouse"));
        assertTrue(uris.contains("https://host/graphql/billing"));
    }

    @Test
    public void unpartitioned_readsTheConfiguredAppDefinitionDatabase() {
        var asked = new java.util.ArrayList<String>();

        impl(recording(asked)).describeMcp(CTX);

        assertEquals(List.of(APPS_DB), asked);
    }

    @Test
    public void partitioned_readsTheScopesOwnDatabase() {
        // The configured database can be overridden per request, and a deployment giving each
        // caller its own apps does exactly that — but the catalogue is built outside any request,
        // so there is no override to read. The scope is what is available, and it names the
        // database. Without this every caller was described the same default database's apps.
        var asked = new java.util.ArrayList<String>();
        var scoped = new McpContext(null, "https://host", "f0f0f0", "graphql", "/graphql", Map.of());

        impl(recording(asked)).describeMcp(scoped);

        assertEquals(List.of("f0f0f0"), asked);
    }
}
