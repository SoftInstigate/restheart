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

import java.net.URI;
import java.util.Map;

import org.restheart.plugins.mcp.McpResource;
import org.restheart.plugins.security.DescriptorAuthorization;
import org.restheart.plugins.security.RequestDescriptor;

/**
 * Decides whether a resource belongs in a catalog listing for the caller who asked.
 *
 * <p>Reads are authorized one by one — a {@code resources/read} the ACL does not cover is refused
 * — but the catalog used to be the same for everybody, so any caller allowed to reach {@code /mcp}
 * could enumerate every MCP-enabled resource on the server: its URI, its actions (including the
 * writing ones), its parameter names, and its {@code mcp.description}. The description is the part
 * that matters: it is prose written to orient an agent, so it is deliberately informative about
 * what the data is.
 *
 * <p>The decision is not taken here. It is delegated to {@link DescriptorAuthorization}, the
 * framework's own rule — the same one applied to a real request — so a listing can never disagree
 * with what a read would actually do.
 */
final class CatalogVisibility {

    private CatalogVisibility() {
    }

    /**
     * Whether {@code resource} should appear in a listing for the caller identified by
     * {@code identity}.
     *
     * <p>Only a resource that declares no action at all stays visible unconditionally: there is
     * nothing a caller could do with it, so there is nothing to decide.
     */
    static boolean isVisible(DescriptorAuthorization authorization,
                             RequestDescriptor identity,
                             McpResource resource) {
        var action = readAction(resource);

        return action == null
                || isReadable(authorization, identity, pathOf(resource.uri()), methodOf(action));
    }

    /**
     * Whether the caller could read the resource at {@code path} — always decided, never assumed.
     *
     * <p>{@code method} is the resource's own: probing everything with a {@code GET} would be wrong
     * for a GraphQL app, whose read is a {@code POST}, and a caller granted {@code POST} on it and
     * nothing else would watch it vanish from a catalog it can perfectly well use.
     *
     * <p>This is deliberately reached for every entry of a listing, including the ones the catalog
     * has no {@link McpResource} for. A listing carries more URIs than the catalog has resources:
     * {@code /coll/_size} and {@code /coll/{id}} are entries in their own right, synthesized from a
     * collection's actions. Treating "the catalog does not know this URI" as "show it" made
     * {@code /inventory/_size} survive a filter that had just removed {@code /inventory} — a filter
     * that fails open is worse than none, because it looks like it worked.
     */
    static boolean isReadable(DescriptorAuthorization authorization,
                              RequestDescriptor identity,
                              String path,
                              String method) {
        var probe = new RequestDescriptor(identity.principal(), method, path,
                Map.of(), identity.headers(), identity.cookies(), identity.remoteAddress(), identity.scheme());

        return authorization.isAllowed(probe);
    }

    static String methodOf(McpResource.Action action) {
        return action == null || action.method() == null ? "GET" : action.method();
    }

    /**
     * The action to probe the caller's access with: {@code query} when there is a readable one,
     * else the first readable action — an aggregation's is {@code execute} — else simply the first
     * action of any kind.
     *
     * <p>That last fallback is what keeps a GraphQL app and a service out of a catalog the caller
     * has no business seeing. Neither declares a {@code readable} action: a GraphQL app is queried
     * with a {@code POST} the caller sends itself, and a service is whatever it is. Treating "no
     * readable action" as "nothing to decide" left exactly the resources whose {@code description}
     * is the most revealing prose on the server visible to everybody who could reach it.
     */
    static McpResource.Action readAction(McpResource resource) {
        if (resource.actions().get("query") instanceof McpResource.Action query && query.readable()) {
            return query;
        }

        return resource.actions().values().stream()
                .filter(McpResource.Action::readable)
                .findFirst()
                .orElseGet(() -> resource.actions().values().stream().findFirst().orElse(null));
    }

    static String pathOf(String absoluteUri) {
        try {
            return new URI(absoluteUri).getPath();
        } catch (Exception e) {
            return absoluteUri;
        }
    }
}
