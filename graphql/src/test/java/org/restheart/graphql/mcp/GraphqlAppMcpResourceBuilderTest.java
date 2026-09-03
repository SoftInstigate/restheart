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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

public class GraphqlAppMcpResourceBuilderTest {

    private static final String APP_URI = "https://host/graphql/warehouse";
    private static final String SDL = """
            type Query {
                lowStock(threshold: Int!): [Item]
            }
            type Mutation {
                createOrder(sku: String!): Order
            }
            type Item { sku: String, qty: Int }
            type Order { id: ID, sku: String }
            """;

    @Test
    public void noMcpBlock_notBuilt() {
        assertTrue(GraphqlAppMcpResourceBuilder.build(APP_URI, null, SDL).isEmpty());
    }

    @Test
    public void explicitlyDisabled_notBuilt() {
        var mcp = BsonDocument.parse("{\"enabled\": false, \"description\": \"Warehouse.\"}");
        assertTrue(GraphqlAppMcpResourceBuilder.build(APP_URI, mcp, SDL).isEmpty());
    }

    @Test
    public void missingDescription_notBuilt() {
        assertTrue(GraphqlAppMcpResourceBuilder.build(APP_URI, new BsonDocument(), SDL).isEmpty());
    }

    @Test
    public void enabledWithDescription_buildsExecuteActionWithFixedBodySchema() {
        var mcp = BsonDocument.parse("{\"description\": \"Warehouse queries.\"}");

        var resource = GraphqlAppMcpResourceBuilder.build(APP_URI, mcp, SDL).orElseThrow();

        assertEquals(APP_URI, resource.uri());
        assertEquals("graphql-app", resource.kind());
        assertEquals("Warehouse queries.", resource.description());

        var execute = resource.actions().get("execute");
        assertEquals("POST", execute.method());
        assertEquals("", execute.pathTemplate());

        var bodySchema = execute.bodySchema();
        assertEquals("object", bodySchema.get("type"));
        assertEquals(List.of("query"), bodySchema.get("required"));
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) bodySchema.get("properties");
        assertTrue(properties.containsKey("query"));
        assertTrue(properties.containsKey("variables"));
        assertTrue(properties.containsKey("operationName"));
    }

    @Test
    public void operations_surfaceUnderExtraNotParams() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");

        var resource = GraphqlAppMcpResourceBuilder.build(APP_URI, mcp, SDL).orElseThrow();

        assertTrue(resource.actions().get("execute").params().isEmpty());

        @SuppressWarnings("unchecked")
        var operations = (Map<String, Object>) resource.extra().get("operations");
        @SuppressWarnings("unchecked")
        var queries = (List<Map<String, Object>>) operations.get("queries");

        assertEquals(1, queries.size());
        assertEquals("lowStock", queries.get(0).get("name"));
        assertEquals("[Item]", queries.get(0).get("return_type"));
    }

    @Test
    public void mutationsInSdl_neverSurfaced() {
        // RESTHeart's GraphQL API is read-only; the SDL's Mutation type (createOrder) must never
        // appear anywhere in the resource, since RESTHeart has no way to execute it
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = GraphqlAppMcpResourceBuilder.build(APP_URI, mcp, SDL).orElseThrow();

        @SuppressWarnings("unchecked")
        var operations = (Map<String, Object>) resource.extra().get("operations");
        assertNull(operations.get("mutations"));
    }

    @Test
    public void queryArgs_carryTypeAndRequiredFlag() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = GraphqlAppMcpResourceBuilder.build(APP_URI, mcp, SDL).orElseThrow();

        @SuppressWarnings("unchecked")
        var operations = (Map<String, Object>) resource.extra().get("operations");
        @SuppressWarnings("unchecked")
        var queries = (List<Map<String, Object>>) operations.get("queries");
        @SuppressWarnings("unchecked")
        var args = (List<Map<String, Object>>) queries.get(0).get("args");

        assertEquals("threshold", args.get(0).get("name"));
        assertEquals("Int!", args.get(0).get("type"));
        assertEquals(true, args.get(0).get("required"));
    }

    @Test
    public void sdlWithNeitherQueryNorMutation_noOperationsKeyInExtra() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = GraphqlAppMcpResourceBuilder.build(APP_URI, mcp, "type Item { sku: String }").orElseThrow();

        assertNull(resource.extra().get("operations"));
    }

    @Test
    public void examples_convertedToResourceExamples() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "examples": [{"description": "Find low-stock items", "args": {"query": "{ lowStock(threshold: 10) { sku } }"}}]}
                """);

        var resource = GraphqlAppMcpResourceBuilder.build(APP_URI, mcp, SDL).orElseThrow();

        assertEquals(1, resource.examples().size());
        assertEquals("Find low-stock items", resource.examples().get(0).description());
        assertEquals("execute", resource.examples().get(0).action());
        assertEquals("{ lowStock(threshold: 10) { sku } }", resource.examples().get(0).args().get("query"));
    }
}
