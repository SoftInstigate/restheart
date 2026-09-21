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
                                    Predicate<McpResource> visible, Verdicts verdicts) {
        if (resourceUri != null) {
            return lookup.find(principal, baseUrl, scope, resourceUri)
                    .filter(visible)
                    .map(resource -> describe(resource, verdicts))
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
     * One resource in full, with every action its kind affords.
     *
     * <p>The actions are not filtered by the ACL, and that is deliberate. A REST collection has
     * all of them, an aggregation only its execution, a GraphQL app its query: the set is a
     * property of the kind, not a secret, and a caller who knows what a collection is knows it
     * already. Filtering them bought a courtesy — an append-only ledger not advertising
     * {@code delete} — and cost far more: a listing is composed without arguments, so an action
     * whose rule reads one was dropped for the very caller entitled to it, and three matches of
     * {@code examples/market-game} ended with the agent concluding the service was read-only.
     *
     * <p>What each action says about <em>this</em> caller is added instead: whether the rules that
     * apply to them permit it, refuse it, or leave it undecided until the call carries its
     * arguments. That is information the agent can act on, where an omission is a silence it has
     * to guess at.
     */
    private static Map<String, Object> describe(McpResource resource, Verdicts verdicts) {
        var described = resource.toMap();

        if (verdicts == null || !(described.get("actions") instanceof Map<?, ?> actions)) {
            return described;
        }

        @SuppressWarnings("unchecked")
        var mutable = (Map<String, Object>) actions;
        var withheld = new ArrayList<String>();

        for (var name : List.copyOf(mutable.keySet())) {
            var verdict = verdicts.of(resource, name);

            if (Verdicts.REFUSED.equals(verdict.get("permitted"))) {
                withheld.add(name);
                mutable.remove(name);
            } else if (mutable.get(name) instanceof Map<?, ?> fields) {
                @SuppressWarnings("unchecked")
                var action = (Map<String, Object>) fields;
                verdict.forEach((key, value) -> {
                    if (!"permitted".equals(key)) {
                        action.put(key, value);
                    }
                });
            }
        }

        // The transports name the actions they carry: leaving a withheld one there would make the
        // two halves of the same description disagree about what this resource offers.
        if (!withheld.isEmpty() && described.get("transports") instanceof List<?> transports) {
            transports.forEach(t -> {
                if (t instanceof Map<?, ?> transport && transport.get("actions") instanceof List<?> names) {
                    @SuppressWarnings("unchecked")
                    var mutableTransport = (Map<String, Object>) transport;
                    mutableTransport.put("actions", names.stream().filter(n -> !withheld.contains(n)).toList());
                }
            });
        }

        return described;
    }

    /**
     * What a listing can say about this caller performing one action of one resource.
     *
     * <p>The same rule the resource itself is listed by, one level down: an action the caller's
     * rules refuse is not offered, and one they might allow is. There is no middle, and no
     * marking of what is not there — a catalogue says what can be asked for.
     *
     * <p>This does not bring back the defect that filtering actions once caused (#743). That was a
     * two-valued probe, which answered "refused" to a rule deciding on an argument it did not
     * have, and dropped {@code create} for the very caller entitled to it. The analysis answers
     * "undetermined" there, and undetermined is offered.
     */
    @FunctionalInterface
    public interface Verdicts {

        /** The verdict of {@link #REFUSED} withholds the action; anything else describes it. */
        String REFUSED = "no";

        /** What this caller may do with one action: {@code permitted}, and whatever explains it. */
        Map<String, Object> of(McpResource resource, String actionName);

        /** Says nothing, for a caller there is nothing to say about. */
        Verdicts NONE = (resource, actionName) -> Map.of();
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
