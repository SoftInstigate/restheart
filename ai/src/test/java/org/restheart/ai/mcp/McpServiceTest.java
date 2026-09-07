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
        // no "token": the descriptor never carries a credential, so it stays stable and reusable —
        // get_token issues one separately, when the caller is about to send the request
        assertEquals(Set.of("resource", "action", "args", "transport"), properties.keySet());
    }

    @Test
    public void howToCallToolDefinition_descriptionPointsAtGetToken() {
        var description = McpService.howToCallToolDefinition().description();

        assertTrue(description.contains(DescriptorRenderer.TOKEN_PLACEHOLDER),
                "must quote the placeholder it actually emits, so the agent can match the two");
        assertTrue(description.contains("get_token"), "must name the tool that fills the placeholder in");
    }

    @Test
    public void getTokenToolDefinition_takesNoArguments() {
        var tool = McpService.getTokenToolDefinition();

        assertEquals("get_token", tool.name());

        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) tool.inputSchema().get("properties");
        assertTrue(properties.isEmpty(), "the token is for the current session — there is nothing to parameterize");
        assertNull(tool.inputSchema().get("required"));
    }

    @Test
    public void getTokenToolDefinition_statesTheTokenIsShortLived() {
        var description = McpService.getTokenToolDefinition().description();

        assertTrue(description.contains(DescriptorRenderer.TOKEN_PLACEHOLDER),
                "must quote the placeholder it fills in");
        assertTrue(description.contains("expires_in"),
                "must point at the field telling the agent how long the token lasts");
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
