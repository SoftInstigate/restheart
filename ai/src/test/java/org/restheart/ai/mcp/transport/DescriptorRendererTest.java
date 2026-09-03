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
package org.restheart.ai.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.plugins.mcp.McpResource;

public class DescriptorRendererTest {

    @Test
    public void getAction_noPathTemplate_argsBecomeQueryString() {
        var resource = McpResource.builder()
                .uri("https://cloud.restheart.com/warehouse/inventory")
                .action("query", a -> a.method("GET"))
                .build();

        var descriptor = DescriptorRenderer.render(resource, "query", Map.of("filter", Map.of("quantity", 5)), null, null);

        assertEquals("http", descriptor.get("transport"));
        assertEquals("GET", descriptor.get("method"));
        assertTrue(((String) descriptor.get("url")).startsWith("https://cloud.restheart.com/warehouse/inventory?filter="));
        assertTrue(((String) descriptor.get("url")).contains("quantity"));
    }

    @Test
    public void postAction_bodyArgBecomesDescriptorBody() {
        var resource = McpResource.builder()
                .uri("https://cloud.restheart.com/echo")
                .action("echo", a -> a.method("POST"))
                .build();

        var descriptor = DescriptorRenderer.render(resource, "echo", Map.of("body", Map.of("message", "hello")), null, null);

        assertEquals("https://cloud.restheart.com/echo", descriptor.get("url"));
        assertEquals(Map.of("message", "hello"), descriptor.get("body"));
        @SuppressWarnings("unchecked")
        var headers = (Map<String, Object>) descriptor.get("headers");
        assertEquals("application/json", headers.get("Content-Type"));
    }

    @Test
    public void pathTemplate_substitutesPlaceholderAndExcludesItFromQueryString() {
        var resource = McpResource.builder()
                .uri("https://host/orders")
                .action("cancel", a -> a.method("DELETE").pathTemplate("/{orderId}"))
                .build();

        var descriptor = DescriptorRenderer.render(resource, "cancel", Map.of("orderId", "abc123"), null, null);

        assertEquals("https://host/orders/abc123", descriptor.get("url"));
    }

    @Test
    public void noToken_embedsPlaceholder() {
        var resource = McpResource.builder().uri("https://host/x").action("a", a -> a.method("GET")).build();
        var descriptor = DescriptorRenderer.render(resource, "a", Map.of(), null, null);

        @SuppressWarnings("unchecked")
        var headers = (Map<String, Object>) descriptor.get("headers");
        assertEquals("Bearer <token>", headers.get("Authorization"));
    }

    @Test
    public void tokenProvided_usedVerbatim() {
        var resource = McpResource.builder().uri("https://host/x").action("a", a -> a.method("GET")).build();
        var descriptor = DescriptorRenderer.render(resource, "a", Map.of(), null, "real-token");

        @SuppressWarnings("unchecked")
        var headers = (Map<String, Object>) descriptor.get("headers");
        assertEquals("Bearer real-token", headers.get("Authorization"));
    }

    @Test
    public void unknownAction_throws() {
        var resource = McpResource.builder().uri("https://host/x").build();
        assertThrows(IllegalArgumentException.class, () -> DescriptorRenderer.render(resource, "nope", Map.of(), null, null));
    }

    @Test
    public void websocketAction_rendersWssUrlAndMessageFormat() {
        var resource = McpResource.builder()
                .uri("https://host/db/coll/_streams/low-stock")
                .transport(McpResource.Transport.WEBSOCKET, "subscribe")
                .action("subscribe", a -> a.method("GET").description("Low-stock alerts."))
                .build();

        var descriptor = DescriptorRenderer.render(resource, "subscribe", Map.of(), null, null);

        assertEquals("websocket", descriptor.get("transport"));
        assertEquals("wss://host/db/coll/_streams/low-stock", descriptor.get("url"));
        assertEquals(Map.of("description", "Low-stock alerts."), descriptor.get("message_format"));
        assertNull(descriptor.get("method"));
    }

    @Test
    public void sseAction_rendersHttpsUrlWithAcceptHeader() {
        var resource = McpResource.builder()
                .uri("https://host/db/coll/_streams/low-stock")
                .transport(McpResource.Transport.SSE, "subscribe")
                .action("subscribe", a -> a.method("GET"))
                .build();

        var descriptor = DescriptorRenderer.render(resource, "subscribe", Map.of(), null, null);

        assertEquals("sse", descriptor.get("transport"));
        assertEquals("https://host/db/coll/_streams/low-stock", descriptor.get("url"));
        @SuppressWarnings("unchecked")
        var headers = (Map<String, Object>) descriptor.get("headers");
        assertEquals("text/event-stream", headers.get("Accept"));
    }

    @Test
    public void sseAction_argsBecomeQueryString() {
        // a change-stream's own $var bindings (avars) must reach the URL exactly like an
        // aggregation's do — confirmed missing live (#616): the query string used to be built
        // for HTTP only, so a composed SSE descriptor silently dropped avars
        var resource = McpResource.builder()
                .uri("https://host/db/coll/_streams/low-stock")
                .transport(McpResource.Transport.SSE, "subscribe")
                .action("subscribe", a -> a.method("GET"))
                .build();

        var descriptor = DescriptorRenderer.render(resource, "subscribe", Map.of("avars", Map.of("minAmount", 100)), null, null);

        var url = (String) descriptor.get("url");
        assertTrue(url.startsWith("https://host/db/coll/_streams/low-stock?avars="));
        assertTrue(url.contains("minAmount"));
    }

    @Test
    public void websocketAction_argsBecomeQueryString() {
        var resource = McpResource.builder()
                .uri("https://host/db/coll/_streams/low-stock")
                .transport(McpResource.Transport.WEBSOCKET, "subscribe")
                .action("subscribe", a -> a.method("GET"))
                .build();

        var descriptor = DescriptorRenderer.render(resource, "subscribe", Map.of("avars", Map.of("minAmount", 100)), null, null);

        var url = (String) descriptor.get("url");
        assertTrue(url.startsWith("wss://host/db/coll/_streams/low-stock?avars="));
        assertTrue(url.contains("minAmount"));
    }

    @Test
    public void transportPreference_selectsAmongDeclaredTransports() {
        var resource = McpResource.builder()
                .uri("https://host/db/coll/_streams/x")
                .transport(McpResource.Transport.WEBSOCKET, "subscribe")
                .transport(McpResource.Transport.SSE, "subscribe")
                .action("subscribe", a -> a.method("GET"))
                .build();

        var sse = DescriptorRenderer.render(resource, "subscribe", Map.of(), "sse", null);
        assertEquals("sse", sse.get("transport"));

        var ws = DescriptorRenderer.render(resource, "subscribe", Map.of(), "websocket", null);
        assertEquals("websocket", ws.get("transport"));
    }

    @Test
    public void noBodyArg_noBodyOrContentTypeInDescriptor() {
        var resource = McpResource.builder().uri("https://host/x").action("get", a -> a.method("GET")).build();
        var descriptor = DescriptorRenderer.render(resource, "get", Map.of(), null, null);

        assertFalse(descriptor.containsKey("body"));
        @SuppressWarnings("unchecked")
        var headers = (Map<String, Object>) descriptor.get("headers");
        assertFalse(headers.containsKey("Content-Type"));
    }
}
