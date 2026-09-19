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
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.restheart.ai.mcp.transport.DescriptorRenderer;
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
        return isReadable(authorization, identity, path, method, Map.of());
    }

    /**
     * The same question, asked with the query parameters the request will carry.
     *
     * <p>A listing has none to offer, and answers for the bare path. A call has them, and must be
     * judged on them: an ACL rule may decide on a query parameter, and asking without it produces
     * a refusal the real request would never have got — the resource then looks absent to a caller
     * holding exactly what would have opened it.
     */
    static boolean isReadable(DescriptorAuthorization authorization,
                              RequestDescriptor identity,
                              String path,
                              String method,
                              Map<String, Deque<String>> queryParameters) {
        var probe = new RequestDescriptor(identity.principal(), method, path,
                queryParameters, identity.headers(), identity.cookies(), identity.remoteAddress(), identity.scheme(),
                identity.attachedParams());

        return authorization.isAllowed(probe);
    }

    /**
     * Whether this caller may invoke one action of a resource <em>with these arguments</em>.
     *
     * <p>Used where a specific call is being answered rather than a catalogue composed: the path
     * carries the arguments it addresses, and the query string the ones the ACL may read. It is
     * the same question the pipeline will ask, so the two cannot disagree.
     */
    static boolean canInvoke(DescriptorAuthorization authorization,
                             RequestDescriptor identity,
                             McpResource resource,
                             String actionName,
                             Map<String, Object> args) {
        var action = resource.actions().get(actionName);

        if (action == null) {
            return false;
        }

        return isReadable(authorization, identity,
                pathOf(resource.uri()) + DescriptorRenderer.pathFor(action, args),
                methodOf(action),
                DescriptorRenderer.queryParametersOf(action, args));
    }

    /**
     * The actions of {@code resource} this caller could actually invoke, by the same rule that
     * decides whether the resource is listed at all: each one probed with its own method and path.
     *
     * <p>Listing an action the ACL refuses is not a leak — the resource is already visible, and its
     * description says more than the action name does — but it wastes an agent's turn and reads as
     * an offer. A ledger that is append-only by permission should not advertise {@code delete}.
     *
     * <p>An action whose path carries a variable ({@code /{id}}) is probed on the literal part
     * before it, because there is no document id to probe with. Against the path-prefix rules ACLs
     * are usually written with this is exact; against a rule naming one document it is generous,
     * and generous is the right way to be wrong here: an action wrongly shown costs a {@code 403}
     * the caller can read, an action wrongly hidden costs them an API they were entitled to.
     */
    static Set<String> invokableActions(DescriptorAuthorization authorization,
                                        RequestDescriptor identity,
                                        McpResource resource) {
        var basePath = pathOf(resource.uri());

        return resource.actions().entrySet().stream()
                .filter(entry -> isReadable(authorization, identity,
                        basePath + literalPath(entry.getValue().pathTemplate()), methodOf(entry.getValue())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** The part of a path template before its first variable, without a trailing slash: {@code /{id}} is nothing, {@code /_size} is itself. */
    private static String literalPath(String pathTemplate) {
        if (pathTemplate == null || pathTemplate.isBlank()) {
            return "";
        }

        var brace = pathTemplate.indexOf('{');
        var literal = brace < 0 ? pathTemplate : pathTemplate.substring(0, brace);

        return literal.endsWith("/") ? literal.substring(0, literal.length() - 1) : literal;
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
