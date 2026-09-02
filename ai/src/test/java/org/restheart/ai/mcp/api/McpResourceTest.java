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
package org.restheart.ai.mcp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class McpResourceTest {

    @Test
    public void noExplicitTransport_defaultsToHttpWithAllActions() {
        var resource = McpResource.builder()
                .uri("https://host/orders/_process")
                .description("Triggers order fulfillment.")
                .action("process", a -> a.method("POST"))
                .action("cancel", a -> a.method("DELETE"))
                .build();

        var json = resource.toMap();

        @SuppressWarnings("unchecked")
        var transports = (List<Map<String, Object>>) json.get("transports");
        assertEquals(1, transports.size());
        assertEquals("http", transports.get(0).get("name"));
        assertEquals(List.of("process", "cancel"), transports.get(0).get("actions"));
    }

    @Test
    public void explicitTransportWithNoActionNames_impliesAllActionsAtBuildTime() {
        var resource = McpResource.builder()
                .uri("https://host/x")
                .transport(McpResource.Transport.WEBSOCKET)
                .action("subscribe", a -> a.method("GET"))
                .build();

        var json = resource.toMap();
        @SuppressWarnings("unchecked")
        var transports = (List<Map<String, Object>>) json.get("transports");
        assertEquals("websocket", transports.get(0).get("name"));
        assertEquals(List.of("subscribe"), transports.get(0).get("actions"));
        assertEquals("wss", transports.get(0).get("url_scheme"));
    }

    @Test
    public void explicitTransportWithActionNames_usesExactlyThose() {
        var resource = McpResource.builder()
                .uri("https://host/x")
                .transport(McpResource.Transport.WEBSOCKET, "subscribe")
                .transport(McpResource.Transport.SSE, "subscribe")
                .action("subscribe", a -> a.method("GET"))
                .build();

        var json = resource.toMap();
        @SuppressWarnings("unchecked")
        var transports = (List<Map<String, Object>>) json.get("transports");
        assertEquals(2, transports.size());
        assertEquals("text/event-stream", transports.get(1).get("media_type"));
    }

    @Test
    public void kindDefaultsToService() {
        var resource = McpResource.builder().uri("https://host/x").build();
        assertEquals("service", resource.kind());
    }

    @Test
    public void noDescription_omittedFromJson() {
        var resource = McpResource.builder().uri("https://host/x").build();
        assertNull(resource.toMap().get("description"));
    }

    @Test
    public void examples_renderedInDeclarationOrder() {
        var resource = McpResource.builder()
                .uri("https://host/x")
                .example("first", "query", Map.of("a", 1))
                .example("second", "query", Map.of("a", 2))
                .build();

        assertEquals("first", resource.examples().get(0).description());
        assertEquals("second", resource.examples().get(1).description());
    }
}
