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
import java.util.Set;

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

    /**
     * Since 9.9 RESTHeart binds any query parameter it does not reserve as a {@code $var}, so a
     * variable is a param of its own. An agent then fills a field per variable instead of
     * composing a JSON object, and {@code how_to_call} renders {@code ?status=A}.
     */
    @Test
    public void eachVariableIsAParamOfItsOwn() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).orElseThrow();

        var params = resource.actions().get("execute").params();

        assertTrue(params.containsKey("status"));
        assertEquals("string", params.get("status").type());
        assertFalse(params.containsKey("avars"), "the legacy blob is only for names RESTHeart reserves");
    }

    /**
     * A variable named after a query parameter RESTHeart reads itself would never reach the
     * pipeline: `?sort=...` is the sort of the request. Those keep the legacy form, and the
     * resource says so in its warnings rather than declaring something that cannot work.
     */
    @Test
    public void aVariableNamedLikeAReservedParameterKeepsTheLegacyForm() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var stages = BsonArray.parse("[{\"$sort\": {\"$var\": \"sort\"}}]");

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "bySort", stages, mcp, "db", null).orElseThrow();
        var params = resource.actions().get("execute").params();

        assertFalse(params.containsKey("sort"));
        assertEquals("object", params.get("avars").type());
        assertTrue(params.get("avars").properties().containsKey("sort"));

        @SuppressWarnings("unchecked")
        var warnings = (List<String>) resource.extra().get("warnings");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("reserved query parameter")));
    }

    @Test
    public void noVarsInPipeline_noVariableParamDeclared() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var stagesWithNoVars = BsonArray.parse("[{\"$match\": {\"status\": \"A\"}}]");

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", stagesWithNoVars, mcp, "db", null).orElseThrow();

        // a pipeline with no $var declares no variable at all; "jsonMode" is declared by every
        // execute action regardless, so the params map is not empty in either case
        var params = resource.actions().get("execute").params();
        assertFalse(params.containsKey("avars"));
        assertEquals(Set.of("jsonMode"), params.keySet());
    }

    @Test
    public void referencedVarWithDeclaredParam_usesDeclaredDefinition() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "params": {"status": {"type": "string", "description": "Order status", "enum": ["open", "closed"]}}}
                """);

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).orElseThrow();
        var param = resource.actions().get("execute").params().get("status");

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
        var param = resource.actions().get("execute").params().get("status");

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
        var param = resource.actions().get("execute").params().get("status");

        assertFalse(param.required());
    }

    @Test
    public void declaredParamWithNoExplicitRequiredField_fallsBackToPipelineShape() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "params": {"status": {"type": "string", "description": "Order status"}}}
                """);

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).orElseThrow();
        var param = resource.actions().get("execute").params().get("status");

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
        var param = resource.actions().get("execute").params().get("status");

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
    public void pipelineWithBlacklistedStage_saysInItsDescriptionWhyItCannotBeCalled() {
        // the catalogue is the only place an agent can learn this before trying: resources/read
        // answers "resource not found" (nothing is registered for a non-readable action) and
        // call_api answers 403, and neither says the pipeline itself is the reason
        var mcp = BsonDocument.parse("{\"description\": \"Archives the sales.\"}");
        var stagesWithMerge = BsonArray.parse("[{\"$match\": {\"status\": \"A\"}}, {\"$merge\": \"copy\"}]");

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "archive", stagesWithMerge, mcp, "db", defaultChecker()).orElseThrow();
        var description = resource.actions().get("execute").description();

        assertTrue(description.startsWith("Archives the sales."), "the owner's own description comes first: " + description);
        assertTrue(description.contains("$merge"), "must name the stage that is refused: " + description);
        assertTrue(description.contains("403"), "must say what calling it answers: " + description);
    }

    @Test
    public void aClearedPipeline_keepsTheOwnersDescriptionUntouched() {
        var mcp = BsonDocument.parse("{\"description\": \"Sales by status.\"}");

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", defaultChecker()).orElseThrow();

        assertEquals("Sales by status.", resource.actions().get("execute").description());
    }

    @Test
    public void noSecurityChecker_isNotMarkedReadable() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");

        var resource = AggregationMcpResourceBuilder.build(COLLECTION_URI, "byStatus", STAGES, mcp, "db", null).orElseThrow();

        assertFalse(resource.actions().get("execute").readable());
    }
}
