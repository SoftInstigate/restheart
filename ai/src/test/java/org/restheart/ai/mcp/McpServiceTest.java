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
package org.restheart.ai.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.restheart.ai.mcp.transport.DescriptorRenderer;
import org.restheart.plugins.mcp.McpResource;

/**
 * Covers the pure, argument-parsing and tool-definition parts of {@link McpService}.
 * The JSON-RPC/session/SSE machinery itself comes from the MCP SDK and
 * {@link UndertowStreamableServerTransportProvider} (already exercised in
 * production by Sophia) and is not re-verified here — an end-to-end check needs a
 * running RESTHeart instance and a real MCP client.
 */
public class McpServiceTest {

    @Test
    public void stringArg_missingKey_returnsNull() {
        assertNull(McpService.stringArg(Map.of(), "resource"));
    }

    @Test
    public void stringArg_presentValue_returnsItsStringForm() {
        assertEquals("42", McpService.stringArg(Map.of("limit", 42), "limit"));
        assertEquals("x", McpService.stringArg(Map.of("resource", "x"), "resource"));
    }

    @Test
    public void intArg_missingKey_returnsNull() {
        assertNull(McpService.intArg(Map.of(), "limit"));
    }

    @Test
    public void intArg_numberValue_returnsIntValue() {
        assertEquals(5, McpService.intArg(Map.of("limit", 5), "limit"));
        assertEquals(5, McpService.intArg(Map.of("limit", 5L), "limit"));
    }

    @Test
    public void intArg_numericStringValue_parses() {
        assertEquals(5, McpService.intArg(Map.of("limit", "5"), "limit"));
    }

    @Test
    public void intArg_nonNumeric_returnsNull() {
        assertNull(McpService.intArg(Map.of("limit", "not a number"), "limit"));
    }

    @Test
    public void listApisToolDefinition_hasExpectedNameAndParams() {
        var tool = McpService.listApisToolDefinition();

        assertEquals("list_apis", tool.name());
        assertTrue(tool.description() != null && !tool.description().isBlank());

        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) tool.inputSchema().get("properties");
        assertEquals(Set.of("resource", "query", "kind", "limit", "cursor"), properties.keySet());
    }

    @Test
    public void howToCallToolDefinition_requiresResourceAndAction() {
        var tool = McpService.howToCallToolDefinition();

        assertEquals("how_to_call", tool.name());
        assertEquals(List.of("resource", "action"), tool.inputSchema().get("required"));

        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) tool.inputSchema().get("properties");
        // no "token": the descriptor never carries a credential — it is documentation for code
        // the agent writes for the user, and the user's own credential goes in the placeholder
        assertEquals(Set.of("resource", "action", "args", "transport"), properties.keySet());
    }

    @Test
    public void howToCallToolDefinition_describesAndPointsAtCallApiForExecution() {
        var description = McpService.howToCallToolDefinition().description();

        assertTrue(description.contains(DescriptorRenderer.CREDENTIAL_PLACEHOLDER),
                "must quote the placeholder it actually emits, so the agent can match the two");
        assertTrue(description.contains("call_api"), "must name the tool that executes, or an agent reads this as the way to act");
    }

    @Test
    public void callApiToolDefinition_takesWhatHowToCallTakes_andStatesTheWorstCase() {
        var tool = McpService.callApiToolDefinition();

        assertEquals("call_api", tool.name());

        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) tool.inputSchema().get("properties");
        // the same call, executed rather than described: resource, action, args — and never a token
        assertEquals(Set.of("resource", "action", "args"), properties.keySet());
        assertEquals(List.of("resource", "action"), tool.inputSchema().get("required"));

        // one tool for every action: a host that asks before a destructive tool asks before every call
        assertFalse(tool.annotations().readOnlyHint(), "call_api writes");
        assertTrue(tool.annotations().destructiveHint(), "must state the worst case, since the action is not known per tool");
    }

    /**
     * A tool that declares an output schema must answer with structured content, so the client
     * gets the result as data rather than as a string to parse a second time. The shape never
     * varies with the resource being called, which is what makes declaring it possible at all.
     */
    @Test
    public void callApiToolDefinition_declaresTheShapeOfItsAnswer() {
        var schema = McpService.callApiToolDefinition().outputSchema();

        assertNotNull(schema, "without an output schema a client has no reason to read structuredContent");
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.keySet().containsAll(Set.of("status", "headers", "body")),
                "status, headers and body are what call_api answers with");
        assertEquals(List.of("status", "headers"), schema.get("required"),
                "a body is not guaranteed — a 204 has none");
    }

    @Test
    public void callApiToolDefinition_tellsTheAgentANon2xxIsAResult() {
        var description = McpService.callApiToolDefinition().description();

        assertTrue(description.contains("status"), "must name the field carrying the HTTP status");
        assertTrue(description.contains("403"), "must say a refusal comes back as a status to read, not a tool error");
        assertTrue(description.contains("resources/read"), "must still point reads at the direct channel");
    }

    @Test
    public void resourceName_isTheLastPathSegment() {
        var resource = McpResource.builder().uri("https://host/warehouse/inventory").build();
        assertEquals("inventory", McpService.resourceName(resource));
    }

    @Test
    public void resourceName_nestedUri_isStillJustTheLastSegment() {
        var resource = McpResource.builder().uri("https://host/warehouse/inventory/_aggrs/byStatus").build();
        assertEquals("byStatus", McpService.resourceName(resource));
    }

    @Test
    public void resourceName_rootUri_fallsBackToFullUri() {
        var resource = McpResource.builder().uri("https://host/").build();
        assertEquals("https://host/", McpService.resourceName(resource));
    }

    @Test
    public void pathVariableNames_extractsBraceNames() {
        assertEquals(Set.of("id"), McpService.pathVariableNames("/{id}"));
    }

    @Test
    public void pathVariableNames_blankOrNull_isEmpty() {
        assertTrue(McpService.pathVariableNames("").isEmpty());
        assertTrue(McpService.pathVariableNames(null).isEmpty());
    }

    @Test
    public void pathVariableNames_ignoresTheQueryExpansionGroup() {
        assertTrue(McpService.pathVariableNames("{?filter,sort}").isEmpty());
    }

    @Test
    public void flatParamNames_flatScalarParams_returnTheirOwnNames() {
        var resource = McpResource.builder()
                .uri("https://host/x")
                .action("query", a -> {
                    a.param("filter", "object", false);
                    a.param("page", "integer", true);
                })
                .build();
        var action = resource.actions().get("query");

        assertEquals(Set.of("filter", "page"), Set.copyOf(McpService.flatParamNames(action, p -> true)));
        assertEquals(List.of("page"), McpService.flatParamNames(action, McpResource.Param::required));
    }

    @Test
    public void flatParamNames_objectParam_expandsToItsPropertiesByTheirOwnNames() {
        var properties = Map.of(
                "status", new McpResource.Param("string", null, true, null, null),
                "region", new McpResource.Param("string", null, false, null, null));
        var resource = McpResource.builder()
                .uri("https://host/x")
                .action("execute", a -> a.param("avars", new McpResource.Param("object", null, true, null, null, properties)))
                .build();
        var action = resource.actions().get("execute");

        assertEquals(Set.of("status", "region"), Set.copyOf(McpService.flatParamNames(action, p -> true)));
        assertEquals(List.of("status"), McpService.flatParamNames(action, McpResource.Param::required));
    }

    @Test
    public void pathId_keepsTheWholePath_soSameNamedCollectionsInTwoDatabasesDoNotCollide() {
        assertEquals("db1/inventory", McpService.pathId("https://host/db1/inventory"));
        assertEquals("db2/inventory", McpService.pathId("https://host/db2/inventory"));
        // an action's own literal path is part of what the entry addresses, so it is part of the id
        assertEquals("db1/inventory/_size", McpService.pathId("https://host/db1/inventory/_size"));
    }

    @Test
    public void paramsTemplate_titleSpellsOutEveryParameterTheReadAccepts() {
        var resource = McpResource.builder().uri("https://host/inventory").description("Product inventory.").build();

        var template = McpService.paramsTemplate(resource, "", "inventory-documents",
                "https://host/inventory{?filter,page}", List.of("filter", "page"), Set.of(), List.of());

        assertEquals("inventory-documents", template.name());
        // built from the same list that produced the URI template, so the label cannot drift
        assertEquals("inventory — documents by filter, page", template.title());
        assertEquals("Product inventory.", template.description());
    }

    @Test
    public void paramsTemplate_pathVariable_titleSaysOneDocument() {
        var resource = McpResource.builder().uri("https://host/inventory").description("Product inventory.").build();

        var template = McpService.paramsTemplate(resource, "", "inventory-by-id",
                "https://host/inventory/{id}", List.of(), Set.of("id"), List.of("id"));

        assertEquals("inventory-by-id", template.name());
        assertEquals("inventory — one document by id", template.title());
    }

    @Test
    public void paramsTemplate_actionWithItsOwnPath_isNamedAndTitledAfterIt() {
        // /_size answers with a number, not with documents: without its own path in the name it
        // and the collection's template would both be called "inventory-documents"
        var resource = McpResource.builder().uri("https://host/inventory").description("Product inventory.").build();

        var template = McpService.paramsTemplate(resource, "-size", "inventory-size-filtered",
                "https://host/inventory/_size{?filter,count}", List.of("filter", "count"), Set.of(), List.of());

        assertEquals("inventory-size-filtered", template.name());
        assertEquals("inventory size — by filter, count", template.title());
    }

    @Test
    public void paramsTemplate_withRequiredParams_notesThemInDescription() {
        var resource = McpResource.builder().uri("https://host/inventory/_aggrs/byStatus").description("Total by status.").build();

        var template = McpService.paramsTemplate(resource, "", "byStatus-documents",
                "https://host/inventory/_aggrs/byStatus{?status}", List.of("status"), Set.of(), List.of("status"));

        assertEquals("byStatus-documents", template.name());
        assertEquals("Total by status. (requires: status)", template.description());
    }
}
