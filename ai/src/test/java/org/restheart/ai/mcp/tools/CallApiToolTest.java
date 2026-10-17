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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.ai.mcp.transport.DescriptorRenderer;
import org.restheart.plugins.mcp.McpResource;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

/**
 * The request {@code call_api} runs in-process is the request {@code how_to_call} would have
 * described: same descriptor, turned into method, target, headers and body.
 */
public class CallApiToolTest {
    private static final McpJsonMapper MAPPER = new JacksonMcpJsonMapperSupplier().get();

    @Test
    public void anHttpDescriptor_becomesTheRequestItDescribes() throws Exception {
        var resource = McpResource.builder()
                .uri("https://c0ffee.example.com:8443/db/todos")
                .action("update", a -> a.method("PATCH").pathTemplate("/{id}"))
                .build();
        var args = new LinkedHashMap<String, Object>();
        args.put("id", "42");
        args.put("wm", "upsert");
        args.put("body", Map.of("done", true));

        var descriptor = DescriptorRenderer.render(resource, "update", args, null);
        var request = CallApiTool.InProcessRequest.of(descriptor, MAPPER);

        assertEquals(Methods.PATCH, request.method());
        assertEquals("/db/todos/42?wm=upsert", request.target());
        // the URL's authority, port included: on a multi-tenant node it selects the tenant
        assertEquals("c0ffee.example.com:8443", request.headers().getFirst(Headers.HOST));
        assertEquals("application/json", request.headers().getFirst(Headers.CONTENT_TYPE));
        assertEquals("{\"done\":true}", new String(request.body(), StandardCharsets.UTF_8));
        // the descriptor's credential placeholder never travels: identity is attached, not sent
        assertNull(request.headers().getFirst(Headers.AUTHORIZATION));
    }

    @Test
    public void aBodilessAction_sendsNoBodyAndNoContentType() throws Exception {
        var resource = McpResource.builder()
                .uri("http://localhost:8080/db/todos")
                .action("size", a -> a.method("GET").pathTemplate("/_size"))
                .build();

        var request = CallApiTool.InProcessRequest.of(DescriptorRenderer.render(resource, "size", Map.of(), null), MAPPER);

        assertEquals(Methods.GET, request.method());
        assertEquals("/db/todos/_size", request.target());
        assertNull(request.body());
        assertNull(request.headers().getFirst(Headers.CONTENT_TYPE));
    }

    @Test
    public void aStreamDescriptor_isRefused_pointingAtTheChannelThatCarriesStreams() {
        var resource = McpResource.builder()
                .uri("http://localhost:8080/db/todos/_streams/all")
                .transport(McpResource.Transport.SSE, "subscribe")
                .action("subscribe", a -> a.method("GET"))
                .build();
        var descriptor = DescriptorRenderer.render(resource, "subscribe", Map.of(), null);

        var refused = assertThrows(ValidationFailedException.class, () -> CallApiTool.InProcessRequest.of(descriptor, MAPPER));

        assertTrue(refused.getMessage().contains("resources/subscribe"), refused.getMessage());
    }
}
