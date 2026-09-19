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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.restheart.plugins.mcp.McpResource;
import org.restheart.security.BaseAccount;

/**
 * Handles the {@code list_apis} tool: with a {@code resource} URI, the full context for
 * that one resource; otherwise the (optionally filtered, paged) catalog. Catalog data comes
 * from {@link CachedResourceLookup} — at most its configured TTL stale, since none of the
 * current {@code McpAware} implementations filter by principal (see #616's amended "ACL
 * filtering" design: exposure is controlled by each resource's own {@code mcp.enabled}, not
 * by the caller's identity).
 */
public final class ListApisTool {
    static final int DEFAULT_PAGE_SIZE = 50;

    private final CachedResourceLookup lookup;

    public ListApisTool(CachedResourceLookup lookup) {
        this.lookup = lookup;
    }

    /**
     * @param resourceUri optional; when given, {@code query}/{@code kind}/{@code limit}/{@code cursor} are ignored
     * @throws UnknownResourceException if {@code resourceUri} matches no known resource
     */
    public Map<String, Object> list(BaseAccount principal, String baseUrl, String scope, String resourceUri,
                                    String query, String kind, Integer limit, String cursor,
                                    Predicate<McpResource> visible, Function<McpResource, Set<String>> invokableActions) {
        if (resourceUri != null) {
            return lookup.find(principal, baseUrl, scope, resourceUri)
                    .filter(visible)
                    .map(resource -> describe(resource, invokableActions.apply(resource)))
                    .orElseThrow(() -> new UnknownResourceException(resourceUri));
        }

        return catalog(principal, baseUrl, scope, query, kind, limit, cursor, visible);
    }

    private Map<String, Object> catalog(BaseAccount principal, String baseUrl, String scope, String query, String kind,
                                        Integer limit, String cursor, Predicate<McpResource> visible) {
        var resources = new ArrayList<>(lookup.all(principal, baseUrl, scope));
        resources.sort(Comparator.comparing(McpResource::uri));

        var filtered = resources.stream()
                .filter(visible)
                .filter(r -> kind == null || kind.equalsIgnoreCase(r.kind()))
                .filter(r -> query == null || matches(r, query))
                .toList();

        var start = parseCursor(cursor);
        var pageSize = limit != null ? limit : DEFAULT_PAGE_SIZE;
        var page = filtered.stream().skip(start).limit(pageSize).toList();
        var nextCursor = start + page.size() < filtered.size() ? String.valueOf(start + page.size()) : null;

        var result = new LinkedHashMap<String, Object>();
        result.put("resources", page.stream().map(ListApisTool::catalogEntry).toList());
        result.put("next_cursor", nextCursor);
        return result;
    }

    /**
     * One resource in full, carrying only the actions this caller could invoke.
     *
     * <p>A catalogue that offers what the ACL refuses reads as an offer and costs an agent a turn
     * to find out otherwise: a ledger that is append-only by permission should not list
     * {@code delete} beside {@code create}. Examples go the same way, or the ones left behind would
     * demonstrate a call that is not on the table.
     */
    private static Map<String, Object> describe(McpResource resource, Set<String> invokable) {
        var described = resource.toMap();

        if (described.get("actions") instanceof Map<?, ?> actions) {
            var kept = new LinkedHashMap<String, Object>();
            actions.forEach((name, action) -> {
                if (invokable.contains(String.valueOf(name))) {
                    kept.put(String.valueOf(name), action);
                }
            });
            described.put("actions", kept);
        }

        // The transports list names the same actions from the other side, and left alone it
        // contradicts the map above: one entry saying `create` is available and the next leaving
        // it out. A transport that ends up carrying nothing goes with them.
        if (described.get("transports") instanceof List<?> transports) {
            described.put("transports", transports.stream()
                    .map(transport -> transport instanceof Map<?, ?> t ? filterTransport(t, invokable) : transport)
                    .filter(transport -> !(transport instanceof Map<?, ?> t)
                            || !(t.get("actions") instanceof List<?> names) || !names.isEmpty())
                    .toList());
        }

        if (described.get("examples") instanceof List<?> examples) {
            described.put("examples", examples.stream()
                    .filter(example -> !(example instanceof Map<?, ?> m)
                            || m.get("action") == null
                            || invokable.contains(String.valueOf(m.get("action"))))
                    .toList());
        }

        return described;
    }

    /** One transport entry with only the actions this caller could invoke listed on it. */
    private static Map<String, Object> filterTransport(Map<?, ?> transport, Set<String> invokable) {
        var out = new LinkedHashMap<String, Object>();
        transport.forEach((key, value) -> out.put(String.valueOf(key), value));

        if (out.get("actions") instanceof List<?> names) {
            out.put("actions", names.stream().filter(name -> invokable.contains(String.valueOf(name))).toList());
        }

        return out;
    }

    private static Map<String, Object> catalogEntry(McpResource resource) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("uri", resource.uri());
        entry.put("kind", resource.kind());
        if (resource.description() != null) {
            entry.put("description", resource.description());
        }
        return entry;
    }

    private static boolean matches(McpResource resource, String query) {
        var q = query.toLowerCase();
        return contains(resource.uri(), q) || contains(resource.kind(), q) || contains(resource.description(), q);
    }

    private static boolean contains(String value, String lowerCaseQuery) {
        return value != null && value.toLowerCase().contains(lowerCaseQuery);
    }

    private static int parseCursor(String cursor) {
        if (cursor == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(cursor));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
