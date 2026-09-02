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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class McpManifestBuilderTest {

    @Test
    public void exactlyTwoToolsListed() {
        @SuppressWarnings("unchecked")
        var tools = (List<Map<String, Object>>) McpManifestBuilder.toolsList().get("tools");

        assertEquals(2, tools.size());
        var names = Set.of((String) tools.get(0).get("name"), (String) tools.get(1).get("name"));
        assertEquals(Set.of("list_apis", "how_to_call"), names);
    }

    @Test
    public void everyToolHasNameDescriptionAndInputSchema() {
        @SuppressWarnings("unchecked")
        var tools = (List<Map<String, Object>>) McpManifestBuilder.toolsList().get("tools");

        for (var tool : tools) {
            assertNotNull(tool.get("name"));
            assertTrue(tool.get("description") instanceof String desc && !desc.isBlank());
            assertNotNull(tool.get("inputSchema"));
        }
    }

    @Test
    public void howToCallTool_requiresResourceAndAction() {
        @SuppressWarnings("unchecked")
        var tools = (List<Map<String, Object>>) McpManifestBuilder.toolsList().get("tools");
        var howToCall = tools.stream().filter(t -> "how_to_call".equals(t.get("name"))).findFirst().orElseThrow();

        @SuppressWarnings("unchecked")
        var inputSchema = (Map<String, Object>) howToCall.get("inputSchema");
        assertEquals(List.of("resource", "action"), inputSchema.get("required"));
    }

    @Test
    public void listApisTool_hasFilterAndPagingParams() {
        @SuppressWarnings("unchecked")
        var tools = (List<Map<String, Object>>) McpManifestBuilder.toolsList().get("tools");
        var listApis = tools.stream().filter(t -> "list_apis".equals(t.get("name"))).findFirst().orElseThrow();

        @SuppressWarnings("unchecked")
        var inputSchema = (Map<String, Object>) listApis.get("inputSchema");
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) inputSchema.get("properties");
        assertEquals(Set.of("resource", "query", "kind", "limit", "cursor"), properties.keySet());
    }

    @Test
    public void isCached_sameInstanceAcrossCalls() {
        assertSame(McpManifestBuilder.toolsList(), McpManifestBuilder.toolsList());
    }
}
