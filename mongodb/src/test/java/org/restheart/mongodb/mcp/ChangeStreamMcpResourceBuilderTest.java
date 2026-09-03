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
import org.restheart.plugins.mcp.McpResource;

public class ChangeStreamMcpResourceBuilderTest {

    private static final String COLLECTION_URI = "https://host/db/orders";
    private static final BsonArray STAGES = BsonArray.parse("""
            [{"$match": {"fullDocument.status": {"$var": "status"}}}]
            """);

    @Test
    public void noMcpBlock_notBuilt() {
        assertTrue(ChangeStreamMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, null).isEmpty());
    }

    @Test
    public void explicitlyDisabled_notBuilt() {
        var mcp = BsonDocument.parse("{\"enabled\": false, \"description\": \"By status\"}");
        assertTrue(ChangeStreamMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).isEmpty());
    }

    @Test
    public void missingDescription_notBuilt() {
        assertTrue(ChangeStreamMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, new BsonDocument()).isEmpty());
    }

    @Test
    public void enabledWithDescription_buildsSubscribeActionOverSse() {
        var mcp = BsonDocument.parse("{\"description\": \"Notifies on order status changes\"}");

        var resource = ChangeStreamMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();

        assertEquals(COLLECTION_URI + "/_streams/byStatus", resource.uri());
        assertEquals("change-stream", resource.kind());
        assertEquals("Notifies on order status changes", resource.description());
        assertEquals("GET", resource.actions().get("subscribe").method());
        // see AggregationMcpResourceBuilderTest: path_template is relative to the resource's own
        // uri, which already is the full stream address, so it must be empty here
        assertEquals("", resource.actions().get("subscribe").pathTemplate());
        assertEquals(List.of(McpResource.Transport.WEBSOCKET, McpResource.Transport.SSE), resource.transportsFor("subscribe"));
    }

    @Test
    public void eventType_appearsInExtraWhenDeclared() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "event_type": "Update events where quantity < 10"}
                """);
        var resource = ChangeStreamMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();
        assertEquals("Update events where quantity < 10", resource.extra().get("event_type"));
    }

    @Test
    public void noEventType_absentFromExtra() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = ChangeStreamMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();
        assertNull(resource.extra().get("event_type"));
    }

    @Test
    public void noOperatorSummary_fallsBackToHeuristic() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = ChangeStreamMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();
        assertEquals("$match", resource.extra().get("pipeline_summary"));
    }

    @Test
    public void operatorSummary_takesPrecedenceOverHeuristic() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "pipeline_summary": "Notifies whenever an order's status changes"}
                """);
        var resource = ChangeStreamMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();
        assertEquals("Notifies whenever an order's status changes", resource.extra().get("pipeline_summary"));
    }

    @Test
    public void avarsParam_bundlesAllVariablesAsAnObjectParam() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = ChangeStreamMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();

        var avars = resource.actions().get("subscribe").params().get("avars");
        assertEquals("object", avars.type());
    }

    @Test
    public void noVarsInPipeline_noAvarsParamDeclared() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var stagesWithNoVars = BsonArray.parse("[{\"$match\": {\"fullDocument.status\": \"A\"}}]");

        var resource = ChangeStreamMcpResourceBuilder.build(COLLECTION_URI, "byStatus", stagesWithNoVars, mcp).orElseThrow();

        assertTrue(resource.actions().get("subscribe").params().isEmpty());
    }

    @Test
    public void referencedVarWithDeclaredParam_usesDeclaredDefinition() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "params": {"status": {"type": "string", "enum": ["open", "closed"]}}}
                """);

        var resource = ChangeStreamMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();
        var param = resource.actions().get("subscribe").params().get("avars").properties().get("status");

        assertEquals("string", param.type());
        assertEquals(List.of("open", "closed"), param.enumValues());
        assertNull(resource.extra().get("warnings"));
    }

    @Test
    public void referencedVarWithNoDeclaredParam_defaultsToOptionalStringWithWarning() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");

        var resource = ChangeStreamMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();
        var param = resource.actions().get("subscribe").params().get("avars").properties().get("status");

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

        var resource = ChangeStreamMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();

        @SuppressWarnings("unchecked")
        var warnings = (List<String>) resource.extra().get("warnings");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("unused")));
    }

    @Test
    public void examples_convertedToResourceExamples() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "examples": [{"description": "Open orders only", "args": {"status": "open"}}]}
                """);

        var resource = ChangeStreamMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp).orElseThrow();

        assertEquals(1, resource.examples().size());
        assertEquals("subscribe", resource.examples().get(0).action());
        assertEquals(Map.of("status", "open"), resource.examples().get(0).args());
    }
}
