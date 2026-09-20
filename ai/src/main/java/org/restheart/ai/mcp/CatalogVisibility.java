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
import java.util.Collection;

import org.restheart.exchange.Request;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.plugins.security.AclPermissions;
import org.restheart.security.BaseAclPermission;
import org.restheart.security.MongoPermissions;
import org.restheart.security.analysis.ListingContext;
import org.restheart.security.analysis.ListingEvaluator;
import org.restheart.security.analysis.MongoGates;
import org.restheart.security.analysis.PredicateSyntax;
import org.restheart.security.analysis.RequestListingContext;
import org.restheart.security.analysis.Truth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides whether a resource belongs in a catalog listing for the caller who asked.
 *
 * <p>The catalog is not the same for everybody: a resource's {@code mcp.description} is prose
 * written to orient an agent, so it is deliberately informative about what the data is, and
 * announcing it to whoever can reach {@code /mcp} announces more than a name.
 *
 * <p>The question is asked of the caller's <strong>permissions</strong>, not of the authorization
 * chain: "could some call to this resource satisfy one of them". Putting the chain a request made
 * up for the occasion answers a different question — a rule that decides on the arguments of a call
 * refuses one that has none, and the resource vanishes for a caller holding exactly what would open
 * it (#743).
 *
 * <p>So each rule is read instead: the atoms already determined when a listing is composed are
 * evaluated, the ones belonging to the call are left undetermined, and a resource is listed as soon
 * as one rule <em>may</em> allow it. The result is an upper bound — it never hides what could be
 * allowed, and at worst lists something that will answer 403, which {@code call_api} tells the
 * agent to expect.
 */
final class CatalogVisibility {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogVisibility.class);

    private CatalogVisibility() {
    }

    /**
     * Whether {@code resource} should appear in a listing for the caller making {@code request}.
     *
     * <p>Only a resource that declares no action at all stays visible unconditionally: there is
     * nothing a caller could do with it, so there is nothing to decide.
     */
    static boolean isVisible(AclPermissions permissions, Request<?> request, McpResource resource) {
        var action = readAction(resource);

        return action == null
                || isReadable(permissions, request, pathOf(resource.uri()), methodOf(action));
    }

    /**
     * Whether some call could let this caller read the resource at {@code path}.
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
    static boolean isReadable(AclPermissions permissions, Request<?> request, String path, String method) {
        return isReadable(permissions.of(request), new RequestListingContext(request, path, method));
    }

    /** The same question with the rules and the context already in hand. Visible for testing. */
    static boolean isReadable(Collection<BaseAclPermission> permissions, ListingContext context) {
        for (var permission : permissions) {
            if (mayAllow(permission, context)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether one rule could allow a read of the resource {@code context} is about.
     *
     * <p>Two things decide it, and both are the permission's own. Its predicate, analysed; and the
     * switches of its {@code mongo} block, which the MongoDB plugins turn into additional
     * conditions at load time — a read is an ordinary request, so they only ever matter here for a
     * permission that gates nothing else.
     *
     * <p>A permission whose condition is not text — one built from code — is taken as possible:
     * there is nothing to read, and refusing what cannot be read would hide resources nobody
     * decided to hide.
     */
    private static boolean mayAllow(BaseAclPermission permission, ListingContext context) {
        var source = permission.predicateSource().orElse(null);

        if (source == null) {
            return true;
        }

        try {
            var possible = ListingEvaluator.evaluate(PredicateSyntax.parse(source), context);

            return possible.mayBeTrue() && gates(permission) == Truth.TRUE;
        } catch (PredicateSyntax.SyntaxException e) {
            // A permission the server accepted but this cannot read: show, rather than hide
            // something on the strength of our own parser disagreeing with Undertow's.
            LOGGER.debug("could not read the predicate of a permission for a catalog listing, "
                    + "treating it as possible: {}", e.getMessage());

            return true;
        }
    }

    /** The {@code mongo} switches, for the ordinary read a listing is about. */
    private static Truth gates(BaseAclPermission permission) {
        try {
            return MongoGates.allows(MongoPermissions.from(permission), MongoGates.Action.ORDINARY);
        } catch (Exception e) {
            return Truth.TRUE;
        }
    }

    static String methodOf(McpResource.Action action) {
        return action == null || action.method() == null ? "GET" : action.method();
    }

    /**
     * The action to decide the caller's access with: {@code query} when there is a readable one,
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
