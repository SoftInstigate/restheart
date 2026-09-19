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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.restheart.ai.mcp.McpAwareRegistry;
import org.restheart.ai.mcp.RegisteredMcpAware;
import org.restheart.plugins.mcp.McpScopeProvider;
import org.restheart.plugins.mcp.McpAware;
import org.restheart.plugins.mcp.McpContext;
import org.restheart.plugins.mcp.McpResource;

public class ListApisToolTest {

    /** These tests are about listing, not about who may see what — everything is visible. */
    /** every action is invokable: what an unauthenticated deployment answers, and what these tests are not about */
    private static final Function<McpResource, Set<String>> ALL_ACTIONS = resource -> resource.actions().keySet();

    private static final Predicate<McpResource> VISIBLE = r -> true;

    private static CachedResourceLookup lookup(RegisteredMcpAware... entries) {
        return new CachedResourceLookup(McpAwareRegistry.of(List.of(entries)), Duration.ofMinutes(5), key -> {
        });
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
    public void aResourceIsDescribedWithOnlyTheActionsTheCallerCanInvoke() {
        var resource = McpResource.builder()
                .uri("https://host/ledger")
                .action("query", a -> a.method("GET").readable(true))
                .action("create", a -> a.method("POST"))
                .action("delete", a -> a.method("DELETE").pathTemplate("/{id}"))
                .example("append an event", "create", Map.of("body", Map.of("type", "offer")))
                .example("drop one", "delete", Map.of("id", "x"))
                .build();
        var tool = toolWith(new RegisteredMcpAware(fixed(resource), "p1", "/x", Map.of()));

        var described = tool.list(null, "https://host", McpScopeProvider.UNPARTITIONED, "https://host/ledger",
                null, null, null, null, VISIBLE, r -> Set.of("query", "create"));

        @SuppressWarnings("unchecked")
        var actions = (Map<String, Object>) described.get("actions");
        assertEquals(Set.of("query", "create"), actions.keySet(), "an append-only ledger must not offer delete");

        @SuppressWarnings("unchecked")
        var examples = (List<Map<String, Object>>) described.get("examples");
        assertEquals(1, examples.size(), "an example of a call that is not on the table demonstrates nothing");
        assertEquals("create", examples.get(0).get("action"));
    }

    /**
     * The transports list names the same actions from the other side. Filtered in one place and
     * not the other, a single listing says both that {@code create} is available and that it is
     * not — which is exactly what an agent reported before this was fixed.
     */
    @Test
    public void theTransportsListTheSameActionsAsTheActionsMap() {
        var resource = McpResource.builder()
                .uri("https://host/ledger")
                .action("query", a -> a.method("GET").readable(true))
                .action("create", a -> a.method("POST"))
                .action("delete", a -> a.method("DELETE").pathTemplate("/{id}"))
                .build();
        var tool = toolWith(new RegisteredMcpAware(fixed(resource), "p1", "/x", Map.of()));

        var described = tool.list(null, "https://host", McpScopeProvider.UNPARTITIONED, "https://host/ledger",
                null, null, null, null, VISIBLE, r -> Set.of("query"));

        @SuppressWarnings("unchecked")
        var actions = (Map<String, Object>) described.get("actions");

        @SuppressWarnings("unchecked")
        var transports = (List<Map<String, Object>>) described.get("transports");

        assertEquals(Set.of("query"), actions.keySet());
        assertFalse(transports.isEmpty(), "a transport carrying one action is still a transport");
        transports.forEach(transport -> {
            @SuppressWarnings("unchecked")
            var names = (List<String>) transport.get("actions");
            assertEquals(List.of("query"), names,
                    "the transports must not offer what the actions map withholds");
        });
    }

    @Test
    public void catalog_flattensResourcesAcrossPlugins() {
        var tool = toolWith(
                new RegisteredMcpAware(fixed(resource("https://host/a", "service", "A")), "p1", "/a", Map.of()),
                new RegisteredMcpAware(fixed(resource("https://host/b", "service", "B"), resource("https://host/c", "service", "C")), "p2", "/b", Map.of()));

        var result = tool.list(null, "https://host", McpScopeProvider.UNPARTITIONED, null, null, null, null, null, VISIBLE, ALL_ACTIONS);

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

        var result = tool.list(null, "https://host", McpScopeProvider.UNPARTITIONED, "https://host/a", "irrelevant", "irrelevant", 1, "irrelevant", VISIBLE, ALL_ACTIONS);

        assertEquals("https://host/a", result.get("uri"));
        assertEquals("collection", result.get("kind"));
    }

    @Test
    public void resourceMode_unknownUri_throws() {
        var tool = toolWith(new RegisteredMcpAware(fixed(resource("https://host/a", "service", "A")), "p1", "/a", Map.of()));
        assertThrows(UnknownResourceException.class, () -> tool.list(null, "https://host", McpScopeProvider.UNPARTITIONED, "https://host/does-not-exist", null, null, null, null, VISIBLE, ALL_ACTIONS));
    }

    @Test
    public void query_filtersCaseInsensitiveAcrossUriKindDescription() {
        var tool = toolWith(new RegisteredMcpAware(
                fixed(resource("https://host/orders", "collection", "Order management"),
                        resource("https://host/products", "collection", "Catalog items")),
                "p1", "/x", Map.of()));

        var result = tool.list(null, "https://host", McpScopeProvider.UNPARTITIONED, null, "ORDER", null, null, null, VISIBLE, ALL_ACTIONS);

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

        var result = tool.list(null, "https://host", McpScopeProvider.UNPARTITIONED, null, null, "GraphQL-App", null, null, VISIBLE, ALL_ACTIONS);

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

        var firstPage = tool.list(null, "https://host", McpScopeProvider.UNPARTITIONED, null, null, null, 2, null, VISIBLE, ALL_ACTIONS);
        @SuppressWarnings("unchecked")
        var firstResources = (List<Map<String, Object>>) firstPage.get("resources");
        assertEquals(2, firstResources.size());
        assertEquals("https://host/a", firstResources.get(0).get("uri"));
        assertEquals("2", firstPage.get("next_cursor"));

        var secondPage = tool.list(null, "https://host", McpScopeProvider.UNPARTITIONED, null, null, null, 2, (String) firstPage.get("next_cursor"), VISIBLE, ALL_ACTIONS);
        @SuppressWarnings("unchecked")
        var secondResources = (List<Map<String, Object>>) secondPage.get("resources");
        assertEquals(1, secondResources.size());
        assertEquals("https://host/c", secondResources.get(0).get("uri"));
        assertNull(secondPage.get("next_cursor"));
    }

    @Test
    public void catalog_omitsWhatTheCallerCannotSee() {
        var tool = toolWith(new RegisteredMcpAware(
                fixed(resource("https://host/allowed", "collection", "A"), resource("https://host/denied", "collection", "B")),
                "p1", "/x", Map.of()));

        var result = tool.list(null, "https://host", McpScopeProvider.UNPARTITIONED, null, null, null, null, null,
                r -> r.uri().endsWith("/allowed"), ALL_ACTIONS);

        @SuppressWarnings("unchecked")
        var resources = (List<Map<String, Object>>) result.get("resources");
        assertEquals(1, resources.size());
        assertEquals("https://host/allowed", resources.get(0).get("uri"));
    }

    /**
     * Asking for one resource by URI must obey the same filter as the catalog: otherwise the
     * drill-down hands back in full what the listing was careful to leave out.
     */
    @Test
    public void resourceMode_hiddenResourceIsUnknown() {
        var tool = toolWith(new RegisteredMcpAware(fixed(resource("https://host/denied", "collection", "B")), "p1", "/x", Map.of()));

        assertThrows(UnknownResourceException.class,
                () -> tool.list(null, "https://host", McpScopeProvider.UNPARTITIONED, "https://host/denied", null, null, null, null, r -> false, ALL_ACTIONS));
    }

    @Test
    public void emptyRegistry_emptyCatalog() {
        var tool = new ListApisTool(lookup());
        var result = tool.list(null, "https://host", McpScopeProvider.UNPARTITIONED, null, null, null, null, null, VISIBLE, ALL_ACTIONS);

        @SuppressWarnings("unchecked")
        var resources = (List<Map<String, Object>>) result.get("resources");
        assertEquals(0, resources.size());
    }
}
