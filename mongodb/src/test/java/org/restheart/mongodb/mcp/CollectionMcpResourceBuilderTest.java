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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

public class CollectionMcpResourceBuilderTest {

    private static final String COLLECTION_URI = "https://host/db/orders";

    @Test
    public void noMcpBlock_notBuilt() {
        assertTrue(CollectionMcpResourceBuilder.build(COLLECTION_URI, null, null, null, null).isEmpty());
    }

    @Test
    public void explicitlyDisabled_notBuilt() {
        var mcp = BsonDocument.parse("{\"enabled\": false, \"description\": \"Orders.\"}");
        assertTrue(CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null).isEmpty());
    }

    @Test
    public void missingDescription_notBuilt() {
        assertTrue(CollectionMcpResourceBuilder.build(COLLECTION_URI, new BsonDocument(), null, null, null).isEmpty());
    }

    @Test
    public void enabledWithDescription_buildsAllFiveActions() {
        var mcp = BsonDocument.parse("{\"description\": \"Customer orders.\"}");

        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null).orElseThrow();

        assertEquals(COLLECTION_URI, resource.uri());
        assertEquals("collection", resource.kind());
        assertEquals("Customer orders.", resource.description());

        var actions = resource.actions();
        assertEquals("GET", actions.get("query").method());
        assertEquals("", actions.get("query").pathTemplate());
        assertEquals("GET", actions.get("get").method());
        assertEquals("/{id}", actions.get("get").pathTemplate());
        assertEquals("POST", actions.get("create").method());
        assertEquals("PATCH", actions.get("update").method());
        assertEquals("/{id}", actions.get("update").pathTemplate());
        assertEquals("DELETE", actions.get("delete").method());
        assertEquals("/{id}", actions.get("delete").pathTemplate());
    }

    @Test
    public void queryAction_declaresStandardParams() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null).orElseThrow();

        var params = resource.actions().get("query").params();
        assertEquals("object", params.get("filter").type());
        assertEquals("string", params.get("sort").type());
        assertEquals("object", params.get("keys").type());
        assertEquals("integer", params.get("page").type());
        assertEquals("integer", params.get("pagesize").type());
    }

    @Test
    public void jsonSchemaPresent_usedAsBodySchemaForCreateAndUpdate() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var jsonSchema = BsonDocument.parse("""
                { "type": "object", "properties": { "sku": { "type": "string" } } }
                """);

        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, jsonSchema, null, null).orElseThrow();

        assertEquals("object", resource.actions().get("create").bodySchema().get("type"));
        assertEquals("object", resource.actions().get("update").bodySchema().get("type"));
    }

    @Test
    public void noJsonSchema_noBodySchemaOnCreateOrUpdate() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null).orElseThrow();

        assertNull(resource.actions().get("create").bodySchema());
        assertNull(resource.actions().get("update").bodySchema());
    }

    @Test
    public void examples_convertedWithDeclaredAction() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "examples": [
                    {"description": "Find low-stock", "action": "query", "args": {"filter": {"quantity": {"$lt": 10}}}}
                ]}
                """);

        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null).orElseThrow();

        assertEquals(1, resource.examples().size());
        assertEquals("Find low-stock", resource.examples().get(0).description());
        assertEquals("query", resource.examples().get(0).action());
    }

    @Test
    public void exampleWithNoDeclaredAction_defaultsToQuery() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "examples": [{"description": "y", "args": {}}]}
                """);
        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null).orElseThrow();
        assertEquals("query", resource.examples().get(0).action());
    }

    @Test
    public void enabledAggrsAndStreams_linkedInExtra() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var aggrs = BsonArray.parse("""
                [{"uri": "byStatus", "stages": [], "mcp": {"enabled": true, "description": "y"}}]
                """);
        var streams = BsonArray.parse("""
                [{"uri": "lowStock", "stages": [], "mcp": {"enabled": true, "description": "z"}}]
                """);

        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, aggrs, streams).orElseThrow();

        assertEquals(List.of(COLLECTION_URI + "/_aggrs/byStatus"), resource.extra().get("aggregations"));
        assertEquals(List.of(COLLECTION_URI + "/_streams/lowStock"), resource.extra().get("streams"));
    }

    @Test
    public void disabledOrMcpLessAggrsAndStreams_notLinked() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var aggrs = BsonArray.parse("""
                [
                    {"uri": "byStatus", "stages": [], "mcp": {"enabled": false, "description": "y"}},
                    {"uri": "noMcp", "stages": []}
                ]
                """);

        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, aggrs, null).orElseThrow();

        assertNull(resource.extra().get("aggregations"));
    }

    @Test
    public void noAggrsOrStreams_extraOmitsBothKeys() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null).orElseThrow();

        assertTrue(resource.extra().isEmpty());
    }
}
