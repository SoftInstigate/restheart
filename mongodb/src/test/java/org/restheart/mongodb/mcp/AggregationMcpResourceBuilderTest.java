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
import org.restheart.security.AggregationPipelineSecurityChecker;

public class AggregationMcpResourceBuilderTest {

    private static final String COLLECTION_URI = "https://host/db/orders";
    private static final BsonArray STAGES = BsonArray.parse("""
            [{"$match": {"status": {"$var": "status"}}}, {"$group": {"_id": "$sku"}}]
            """);

    @Test
    public void noMcpBlock_notBuilt() {
        assertTrue(AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, null, "db", null).isEmpty());
    }

    @Test
    public void explicitlyDisabled_notBuilt() {
        var mcp = BsonDocument.parse("{\"enabled\": false, \"description\": \"By status\"}");
        assertTrue(AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).isEmpty());
    }

    @Test
    public void missingDescription_notBuilt() {
        var mcp = new BsonDocument();
        assertTrue(AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).isEmpty());
    }

    @Test
    public void enabledWithDescription_buildsResourceWithUriAndAction() {
        var mcp = BsonDocument.parse("{\"description\": \"Orders grouped by status\"}");

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).orElseThrow();

        assertEquals(COLLECTION_URI + "/_aggrs/byStatus", resource.uri());
        assertEquals("aggregation", resource.kind());
        assertEquals("Orders grouped by status", resource.description());
        assertEquals("GET", resource.actions().get("execute").method());
        // path_template is relative to the resource's own uri (which already ends in
        // "/_aggrs/byStatus") — how_to_call composes url = resource.uri() + path_template,
        // so this must be empty or the rendered URL would double up the aggregation path
        assertEquals("", resource.actions().get("execute").pathTemplate());
    }

    @Test
    public void noOperatorSummary_fallsBackToHeuristic() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).orElseThrow();
        assertEquals("$match → $group", resource.extra().get("pipeline_summary"));
    }

    @Test
    public void operatorSummary_takesPrecedenceOverHeuristic() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "pipeline_summary": "Orders whose stock covers less than the last quarter's demand"}
                """);
        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).orElseThrow();
        assertEquals("Orders whose stock covers less than the last quarter's demand", resource.extra().get("pipeline_summary"));
    }

    @Test
    public void avarsParam_bundlesAllVariablesAsAnObjectParam() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).orElseThrow();

        var avars = resource.actions().get("execute").params().get("avars");
        assertEquals("object", avars.type());
        assertTrue(resource.actions().get("execute").params().containsKey("avars"));
    }

    @Test
    public void noVarsInPipeline_noAvarsParamDeclared() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var stagesWithNoVars = BsonArray.parse("[{\"$match\": {\"status\": \"A\"}}]");

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", stagesWithNoVars, mcp, "db", null).orElseThrow();

        assertTrue(resource.actions().get("execute").params().isEmpty());
    }

    @Test
    public void referencedVarWithDeclaredParam_usesDeclaredDefinition() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "params": {"status": {"type": "string", "description": "Order status", "enum": ["open", "closed"]}}}
                """);

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).orElseThrow();
        var param = resource.actions().get("execute").params().get("avars").properties().get("status");

        assertEquals("string", param.type());
        assertEquals("Order status", param.description());
        assertEquals(List.of("open", "closed"), param.enumValues());
        assertNull(resource.extra().get("warnings"));
    }

    @Test
    public void referencedVarWithNoDeclaredParam_defaultsToStringRequiredByPipelineShape() {
        // STAGES references "status" as a bare {"$var": "status"} (no default, not inside
        // $ifvar) -- StagesInterpolator would throw QueryVariableNotBoundException if it's not
        // bound, so it must default to required even with no mcp.params override
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).orElseThrow();
        var param = resource.actions().get("execute").params().get("avars").properties().get("status");

        assertEquals("string", param.type());
        assertTrue(param.required());
        @SuppressWarnings("unchecked")
        var warnings = (List<String>) resource.extra().get("warnings");
        assertTrue(warnings.get(0).contains("status"));
    }

    @Test
    public void referencedVarWithDefaultAndNoDeclaredParam_defaultsToOptionalString() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var stagesWithDefault = BsonArray.parse("[{\"$match\": {\"status\": {\"$var\": [\"status\", \"A\"]}}}]");

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", stagesWithDefault, mcp, "db", null).orElseThrow();
        var param = resource.actions().get("execute").params().get("avars").properties().get("status");

        assertFalse(param.required());
    }

    @Test
    public void declaredParamWithNoExplicitRequiredField_fallsBackToPipelineShape() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "params": {"status": {"type": "string", "description": "Order status"}}}
                """);

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).orElseThrow();
        var param = resource.actions().get("execute").params().get("avars").properties().get("status");

        // mcp.params declares "status" but doesn't set its own "required" -- since the pipeline
        // references it as a bare, non-defaulted, non-conditional $var, it's required
        assertTrue(param.required());
    }

    @Test
    public void declaredParamExplicitlyNotRequired_overridesPipelineShape() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "params": {"status": {"type": "string", "required": false}}}
                """);

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).orElseThrow();
        var param = resource.actions().get("execute").params().get("avars").properties().get("status");

        // the pipeline shape says required, but the operator explicitly overrode it
        assertFalse(param.required());
    }

    @Test
    public void declaredParamNotReferencedByPipeline_warns() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "params": {"status": {"type": "string"}, "unused": {"type": "string"}}}
                """);

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).orElseThrow();

        @SuppressWarnings("unchecked")
        var warnings = (List<String>) resource.extra().get("warnings");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("unused")));
    }

    @Test
    public void examples_convertedToResourceExamples() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "examples": [{"description": "Open orders", "args": {"status": "open"}}]}
                """);

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).orElseThrow();

        assertEquals(1, resource.examples().size());
        assertEquals("Open orders", resource.examples().get(0).description());
        assertEquals("execute", resource.examples().get(0).action());
        assertEquals(Map.of("status", "open"), resource.examples().get(0).args());
    }

    private static AggregationPipelineSecurityChecker defaultChecker() {
        return new AggregationPipelineSecurityChecker(Map.of(
                "enabled", true,
                "stageBlacklist", List.of("$out", "$merge", "$lookup", "$graphLookup", "$unionWith")));
    }

    @Test
    public void safePipeline_withSecurityCheckerClearingIt_isMarkedReadable() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", defaultChecker()).orElseThrow();

        assertTrue(resource.actions().get("execute").readable());
    }

    @Test
    public void pipelineWithBlacklistedStage_isNotMarkedReadable() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var stagesWithOut = BsonArray.parse("[{\"$match\": {\"status\": \"A\"}}, {\"$out\": \"copy\"}]");

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", stagesWithOut, mcp, "db", defaultChecker()).orElseThrow();

        assertFalse(resource.actions().get("execute").readable());
    }

    @Test
    public void noSecurityChecker_isNotMarkedReadable() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).orElseThrow();

        assertFalse(resource.actions().get("execute").readable());
    }
}
