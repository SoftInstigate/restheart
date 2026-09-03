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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

public class AggregationMcpResourceBuilderTest {

    private static final String COLLECTION_URI = "https://host/db/orders";
    private static final BsonArray STAGES = BsonArray.parse("""
            [{"$match": {"status": {"$var": "status"}}}, {"$group": {"_id": "$sku"}}]
            """);

    @Test
    public void noMcpBlock_notBuilt() {
        assertTrue(AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, null).isEmpty());
    }

    @Test
    public void explicitlyDisabled_notBuilt() {
        var mcp = BsonDocument.parse("{\"enabled\": false, \"description\": \"By status\"}");
        assertTrue(AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).isEmpty());
    }

    @Test
    public void missingDescription_notBuilt() {
        var mcp = new BsonDocument();
        assertTrue(AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).isEmpty());
    }

    @Test
    public void enabledWithDescription_buildsResourceWithUriAndAction() {
        var mcp = BsonDocument.parse("{\"description\": \"Orders grouped by status\"}");

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();

        assertEquals(COLLECTION_URI + "/_aggrs/byStatus", resource.uri());
        assertEquals("aggregation", resource.kind());
        assertEquals("Orders grouped by status", resource.description());
        assertEquals("GET", resource.actions().get("execute").method());
        assertEquals("/_aggrs/byStatus", resource.actions().get("execute").pathTemplate());
    }

    @Test
    public void noOperatorSummary_fallsBackToHeuristic() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();
        assertEquals("$match → $group", resource.extra().get("pipeline_summary"));
    }

    @Test
    public void operatorSummary_takesPrecedenceOverHeuristic() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "pipeline_summary": "Orders whose stock covers less than the last quarter's demand"}
                """);
        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();
        assertEquals("Orders whose stock covers less than the last quarter's demand", resource.extra().get("pipeline_summary"));
    }

    @Test
    public void referencedVarWithDeclaredParam_usesDeclaredDefinition() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "params": {"status": {"type": "string", "description": "Order status", "enum": ["open", "closed"]}}}
                """);

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();
        var param = resource.actions().get("execute").params().get("status");

        assertEquals("string", param.type());
        assertEquals("Order status", param.description());
        assertEquals(List.of("open", "closed"), param.enumValues());
        assertNull(resource.extra().get("warnings"));
    }

    @Test
    public void referencedVarWithNoDeclaredParam_defaultsToOptionalStringWithWarning() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();
        var param = resource.actions().get("execute").params().get("status");

        assertEquals("string", param.type());
        assertFalse(param.required());
        @SuppressWarnings("unchecked")
        var warnings = (List<String>) resource.extra().get("warnings");
        assertTrue(warnings.get(0).contains("status"));
    }

    @Test
    public void declaredParamNotReferencedByPipeline_warns() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "params": {"status": {"type": "string"}, "unused": {"type": "string"}}}
                """);

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();

        @SuppressWarnings("unchecked")
        var warnings = (List<String>) resource.extra().get("warnings");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("unused")));
    }

    @Test
    public void examples_convertedToResourceExamples() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "examples": [{"description": "Open orders", "args": {"status": "open"}}]}
                """);

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();

        assertEquals(1, resource.examples().size());
        assertEquals("Open orders", resource.examples().get(0).description());
        assertEquals("execute", resource.examples().get(0).action());
        assertEquals(Map.of("status", "open"), resource.examples().get(0).args());
    }
}
