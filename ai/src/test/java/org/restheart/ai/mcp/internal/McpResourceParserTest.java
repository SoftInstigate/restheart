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
package org.restheart.ai.mcp.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.ai.mcp.api.McpContext;

public class McpResourceParserTest {

    private static McpContext ctx(Map<String, Object> pluginConfiguration) {
        return new McpContext(null, "https://cloud.restheart.com", "echo", "/echo", pluginConfiguration);
    }

    @Test
    public void bothNull_buildsMinimalResourceWithNoDescription() {
        var resource = McpResourceParser.fromMerged(null, null).buildSingle(ctx(Map.of()));

        assertEquals("https://cloud.restheart.com/echo", resource.uri());
        assertEquals("service", resource.kind());
        assertNull(resource.description());
        assertTrue(resource.actions().isEmpty());
    }

    @Test
    public void codeDefaultsOnly_populatesResource() {
        var defaults = Map.<String, Object>of(
                "description", "Echoes the request body.",
                "actions", Map.of("echo", Map.of("method", "POST")));

        var resource = McpResourceParser.fromMerged(defaults, null).buildSingle(ctx(Map.of()));

        assertEquals("Echoes the request body.", resource.description());
        assertEquals("POST", resource.actions().get("echo").method());
    }

    @Test
    public void operatorConfigOnly_populatesResource() {
        var operatorConfig = Map.<String, Object>of("description", "Operator-supplied description.");
        var resource = McpResourceParser.fromMerged(null, operatorConfig).buildSingle(ctx(Map.of()));
        assertEquals("Operator-supplied description.", resource.description());
    }

    @Test
    public void operatorDescription_overridesCodeDefault() {
        var defaults = Map.<String, Object>of("description", "code default");
        var operatorConfig = Map.<String, Object>of("description", "operator override");
        var resource = McpResourceParser.fromMerged(defaults, operatorConfig).buildSingle(ctx(Map.of()));
        assertEquals("operator override", resource.description());
    }

    @Test
    public void action_readsParamsBodySchemaAndDescription() {
        var defaults = Map.<String, Object>of("actions", Map.of(
                "query", Map.of(
                        "method", "get",
                        "description", "Runs a filtered query.",
                        "params", Map.of("filter", Map.of("type", "object", "required", false)))));

        var resource = McpResourceParser.fromMerged(defaults, null).buildSingle(ctx(Map.of()));
        var query = resource.actions().get("query");

        assertEquals("GET", query.method());
        assertEquals("object", query.params().get("filter").type());
        assertEquals(false, query.params().get("filter").required());
        assertEquals("Runs a filtered query.", query.description());
    }

    @Test
    public void action_acceptsKebabCaseAliases_bodySchemaAndPathTemplate() {
        var defaults = Map.<String, Object>of("actions", Map.of(
                "update", Map.of(
                        "method", "PATCH",
                        "path-template", "/{id}",
                        "body-schema", Map.of("type", "object"))));

        var resource = McpResourceParser.fromMerged(defaults, null).buildSingle(ctx(Map.of()));
        var toMap = resource.toMap();

        @SuppressWarnings("unchecked")
        var actionsJson = (Map<String, Object>) toMap.get("actions");
        @SuppressWarnings("unchecked")
        var updateJson = (Map<String, Object>) actionsJson.get("update");

        assertEquals("/{id}", updateJson.get("path_template"));
        assertEquals(Map.of("type", "object"), updateJson.get("body_schema"));
    }

    @Test
    public void examples_parsedWithDescriptionActionAndArgs() {
        var defaults = Map.<String, Object>of("examples", List.of(
                Map.of("description", "Find low-stock", "action", "query", "args", Map.of("filter", Map.of("quantity", 5)))));

        var resource = McpResourceParser.fromMerged(defaults, null).buildSingle(ctx(Map.of()));

        assertEquals(1, resource.examples().size());
        var example = resource.examples().get(0);
        assertEquals("Find low-stock", example.description());
        assertEquals("query", example.action());
        assertEquals(Map.of("quantity", 5), example.args().get("filter"));
    }

    @Test
    public void undeclaredKind_defaultsToService() {
        var resource = McpResourceParser.fromMerged(Map.of("description", "x"), null).buildSingle(ctx(Map.of()));
        assertEquals("service", resource.kind());
    }

    @Test
    public void uri_joinsBaseUrlAndPluginUri_regardlessOfSlashes() {
        var ctxNoBaseSlash = new McpContext(null, "https://host.example.com", "p", "orders", Map.of());
        var resource = McpResourceParser.fromMerged(null, null).buildSingle(ctxNoBaseSlash);
        assertEquals("https://host.example.com/orders", resource.uri());
    }
}
