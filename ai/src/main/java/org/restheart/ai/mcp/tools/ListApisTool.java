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
import java.util.Map;

import org.restheart.ai.mcp.McpAwareRegistry;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.security.BaseAccount;

/**
 * Handles the {@code list_apis} tool: with a {@code resource} URI, the full context for
 * that one resource; otherwise the (optionally filtered, paged) catalog. Both modes are
 * already ACL-filtered, since they only ever see what each plugin's {@code describeMcp(ctx)}
 * chooses to return for the calling principal.
 */
public final class ListApisTool {
    static final int DEFAULT_PAGE_SIZE = 50;

    private final McpAwareRegistry registry;

    public ListApisTool(McpAwareRegistry registry) {
        this.registry = registry;
    }

    /**
     * @param resourceUri optional; when given, {@code query}/{@code kind}/{@code limit}/{@code cursor} are ignored
     * @throws UnknownResourceException if {@code resourceUri} matches no resource visible to {@code principal}
     */
    public Map<String, Object> list(BaseAccount principal, String baseUrl, String resourceUri,
            String query, String kind, Integer limit, String cursor) {
        if (resourceUri != null) {
            return ResourceLookup.find(registry, principal, baseUrl, resourceUri)
                    .map(McpResource::toMap)
                    .orElseThrow(() -> new UnknownResourceException(resourceUri));
        }

        return catalog(principal, baseUrl, query, kind, limit, cursor);
    }

    private Map<String, Object> catalog(BaseAccount principal, String baseUrl, String query, String kind, Integer limit, String cursor) {
        var resources = new ArrayList<>(ResourceLookup.all(registry, principal, baseUrl));
        resources.sort(Comparator.comparing(McpResource::uri));

        var filtered = resources.stream()
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
