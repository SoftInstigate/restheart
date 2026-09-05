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
        assertEquals(Set.of("resource", "action", "args", "transport", "token"), properties.keySet());
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
    public void paramsTemplate_noRequiredParams_descriptionUnchanged() {
        var resource = McpResource.builder().uri("https://host/inventory").description("Product inventory.").build();

        var template = McpService.paramsTemplate(resource, "https://host/inventory{?filter}", List.of());

        assertEquals("inventory", template.name());
        assertEquals("Product inventory.", template.description());
    }

    @Test
    public void paramsTemplate_withRequiredParams_notesThemInDescription() {
        var resource = McpResource.builder().uri("https://host/inventory/_aggrs/byStatus").description("Total by status.").build();

        var template = McpService.paramsTemplate(resource, "https://host/inventory/_aggrs/byStatus{?status}", List.of("status"));

        assertEquals("byStatus", template.name());
        assertEquals("Total by status. (requires: status)", template.description());
    }
}
