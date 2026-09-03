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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.ai.mcp.McpAwareRegistry;
import org.restheart.ai.mcp.RegisteredMcpAware;
import org.restheart.plugins.mcp.McpAware;
import org.restheart.plugins.mcp.McpContext;
import org.restheart.plugins.mcp.McpResource;

public class ListApisToolTest {

    private static CachedResourceLookup lookup(RegisteredMcpAware... entries) {
        return new CachedResourceLookup(McpAwareRegistry.of(List.of(entries)), Duration.ofMinutes(5), () -> {});
    }

    private static McpAware fixed(McpResource... resources) {
        return new McpAware() {
            @Override
            public List<McpResource> describeMcp(McpContext ctx) {
                return List.of(resources);
            }
        };
    }

    private static McpResource resource(String uri, String kind, String description) {
        return McpResource.builder().uri(uri).kind(kind).description(description).build();
    }

    private static ListApisTool toolWith(RegisteredMcpAware... entries) {
        return new ListApisTool(lookup(entries));
    }

    @Test
    public void catalog_flattensResourcesAcrossPlugins() {
        var tool = toolWith(
                new RegisteredMcpAware(fixed(resource("https://host/a", "service", "A")), "p1", "/a", Map.of()),
                new RegisteredMcpAware(fixed(resource("https://host/b", "service", "B"), resource("https://host/c", "service", "C")), "p2", "/b", Map.of()));

        var result = tool.list(null, "https://host", null, null, null, null, null);

        @SuppressWarnings("unchecked")
        var resources = (List<Map<String, Object>>) result.get("resources");
        assertEquals(3, resources.size());
        // sorted by uri
        assertEquals("https://host/a", resources.get(0).get("uri"));
        assertEquals("https://host/b", resources.get(1).get("uri"));
        assertEquals("https://host/c", resources.get(2).get("uri"));
        assertNull(result.get("next_cursor"));
    }

    @Test
    public void resourceMode_returnsFullContext_ignoresOtherFilters() {
        var tool = toolWith(new RegisteredMcpAware(fixed(resource("https://host/a", "collection", "A")), "p1", "/a", Map.of()));

        var result = tool.list(null, "https://host", "https://host/a", "irrelevant", "irrelevant", 1, "irrelevant");

        assertEquals("https://host/a", result.get("uri"));
        assertEquals("collection", result.get("kind"));
    }

    @Test
    public void resourceMode_unknownUri_throws() {
        var tool = toolWith(new RegisteredMcpAware(fixed(resource("https://host/a", "service", "A")), "p1", "/a", Map.of()));
        assertThrows(UnknownResourceException.class, () -> tool.list(null, "https://host", "https://host/does-not-exist", null, null, null, null));
    }

    @Test
    public void query_filtersCaseInsensitiveAcrossUriKindDescription() {
        var tool = toolWith(new RegisteredMcpAware(
                fixed(resource("https://host/orders", "collection", "Order management"),
                        resource("https://host/products", "collection", "Catalog items")),
                "p1", "/x", Map.of()));

        var result = tool.list(null, "https://host", null, "ORDER", null, null, null);

        @SuppressWarnings("unchecked")
        var resources = (List<Map<String, Object>>) result.get("resources");
        assertEquals(1, resources.size());
        assertEquals("https://host/orders", resources.get(0).get("uri"));
    }

    @Test
    public void kind_filtersExactMatchCaseInsensitive() {
        var tool = toolWith(new RegisteredMcpAware(
                fixed(resource("https://host/a", "collection", "A"), resource("https://host/b", "graphql-app", "B")),
                "p1", "/x", Map.of()));

        var result = tool.list(null, "https://host", null, null, "GraphQL-App", null, null);

        @SuppressWarnings("unchecked")
        var resources = (List<Map<String, Object>>) result.get("resources");
        assertEquals(1, resources.size());
        assertEquals("https://host/b", resources.get(0).get("uri"));
    }

    @Test
    public void limitAndCursor_pageThroughResults() {
        var tool = toolWith(new RegisteredMcpAware(
                fixed(resource("https://host/a", "s", null), resource("https://host/b", "s", null), resource("https://host/c", "s", null)),
                "p1", "/x", Map.of()));

        var firstPage = tool.list(null, "https://host", null, null, null, 2, null);
        @SuppressWarnings("unchecked")
        var firstResources = (List<Map<String, Object>>) firstPage.get("resources");
        assertEquals(2, firstResources.size());
        assertEquals("https://host/a", firstResources.get(0).get("uri"));
        assertEquals("2", firstPage.get("next_cursor"));

        var secondPage = tool.list(null, "https://host", null, null, null, 2, (String) firstPage.get("next_cursor"));
        @SuppressWarnings("unchecked")
        var secondResources = (List<Map<String, Object>>) secondPage.get("resources");
        assertEquals(1, secondResources.size());
        assertEquals("https://host/c", secondResources.get(0).get("uri"));
        assertNull(secondPage.get("next_cursor"));
    }

    @Test
    public void emptyRegistry_emptyCatalog() {
        var tool = new ListApisTool(lookup());
        var result = tool.list(null, "https://host", null, null, null, null, null);

        @SuppressWarnings("unchecked")
        var resources = (List<Map<String, Object>>) result.get("resources");
        assertEquals(0, resources.size());
    }
}
