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
package org.restheart.ai.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.ai.mcp.McpAwareRegistry;
import org.restheart.ai.mcp.RegisteredMcpAware;
import org.restheart.plugins.mcp.McpAware;
import org.restheart.plugins.mcp.McpContext;
import org.restheart.plugins.mcp.McpResource;

public class HowToCallToolTest {

    private static McpAware fixed(McpResource resource) {
        return new McpAware() {
            @Override
            public List<McpResource> describeMcp(McpContext ctx) {
                return List.of(resource);
            }
        };
    }

    private static HowToCallTool toolFor(McpResource resource) {
        var registry = McpAwareRegistry.of(List.of(new RegisteredMcpAware(fixed(resource), "p1", "/x", Map.of())));
        return new HowToCallTool(registry);
    }

    @Test
    public void unknownResource_throws() {
        var resource = McpResource.builder().uri("https://host/a").action("query", a -> a.method("GET")).build();
        var tool = toolFor(resource);

        assertThrows(UnknownResourceException.class,
                () -> tool.call(null, "https://host", "https://host/does-not-exist", "query", Map.of(), null, null));
    }

    @Test
    public void unknownAction_throwsWithValidActionsListed() {
        var resource = McpResource.builder().uri("https://host/a").action("query", a -> a.method("GET")).build();
        var tool = toolFor(resource);

        var ex = assertThrows(UnknownActionException.class,
                () -> tool.call(null, "https://host", "https://host/a", "delete", Map.of(), null, null));
        assertTrue(ex.validActions().contains("query"));
    }

    @Test
    public void missingRequiredParam_throwsValidationFailed() {
        var resource = McpResource.builder()
                .uri("https://host/a")
                .action("get", a -> a.method("GET").param("id", "string", true))
                .build();
        var tool = toolFor(resource);

        var ex = assertThrows(ValidationFailedException.class,
                () -> tool.call(null, "https://host", "https://host/a", "get", Map.of(), null, null));
        assertEquals(1, ex.errors().size());
    }

    @Test
    public void bodySchemaViolation_throwsValidationFailed() {
        var resource = McpResource.builder()
                .uri("https://host/echo")
                .action("echo", a -> a.method("POST").bodySchema(Map.of(
                        "type", "object",
                        "required", List.of("message"))))
                .build();
        var tool = toolFor(resource);

        var ex = assertThrows(ValidationFailedException.class,
                () -> tool.call(null, "https://host", "https://host/echo", "echo", Map.of("body", Map.of("other", "x")), null, null));
        assertTrue(!ex.errors().isEmpty());
    }

    @Test
    public void validCall_returnsDescriptor() {
        var resource = McpResource.builder()
                .uri("https://host/echo")
                .action("echo", a -> a.method("POST"))
                .build();
        var tool = toolFor(resource);

        var descriptor = tool.call(null, "https://host", "https://host/echo", "echo", Map.of("body", Map.of("message", "hi")), null, null);

        assertEquals("http", descriptor.get("transport"));
        assertEquals(Map.of("message", "hi"), descriptor.get("body"));
    }
}
