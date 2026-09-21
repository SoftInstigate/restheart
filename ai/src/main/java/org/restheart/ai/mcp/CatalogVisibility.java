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
import java.util.Map;
import java.util.Optional;

import org.restheart.exchange.Request;
import org.restheart.plugins.mcp.CatalogCondition;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.plugins.security.AclPermissions;
import org.restheart.security.BaseAclPermission;
import org.restheart.security.EvaluationScope.Scope;
import org.restheart.security.MongoPermissions;
import org.restheart.security.analysis.ListingContext;
import org.restheart.security.analysis.ListingEvaluator;
import org.restheart.security.analysis.MongoGates;
import org.restheart.security.analysis.PermissionHints;
import org.restheart.security.analysis.PredicateExpression;
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

        return isEntryVisible(permissions, request, resource, pathOf(resource.uri()), methodOf(action));
    }

    /**
     * Whether one entry of a listing should appear for this caller.
     *
     * <p>A listing carries more entries than the catalog has resources: {@code /coll/_size} and
     * {@code /coll/{id}} are entries in their own right, synthesized from a collection's actions.
     * They are decided on their own path — a permission naming {@code path('/coll')} exactly does
     * not cover {@code _size}, and a listing must say so — but they follow the declaration of the
     * resource they were synthesized from, which is the one somebody wrote it on.
     *
     * @param owner the resource the entry belongs to, or {@code null} when the catalog has none
     */
    static boolean isEntryVisible(AclPermissions permissions, Request<?> request, McpResource owner,
            String path, String method) {
        var context = new RequestListingContext(request, path, method);
        var declared = declaredVisibility(owner, request, context);

        if (declared.isPresent()) {
            return declared.get();
        }

        // a resource with no action is visible unconditionally: there is nothing to decide
        if (owner != null && owner.actions().isEmpty()) {
            return true;
        }

        return isReadable(permissions.of(request), context);
    }

    /**
     * What the resource says about itself, when it says anything.
     *
     * <p>{@code hide_from_roles} is answered first and is final: it is the one thing the publisher
     * stated outright. {@code show_if} then replaces the reading of the permissions altogether —
     * that is what declaring it is for.
     *
     * @return empty when the resource declares nothing and the permissions decide
     */
    private static Optional<Boolean> declaredVisibility(McpResource resource, Request<?> request,
            ListingContext context) {
        if (resource == null) {
            return Optional.empty();
        }

        if (hiddenFromCaller(resource, request)) {
            return Optional.of(false);
        }

        return resource.showIf() == null ? Optional.empty() : Optional.of(declared(resource, context));
    }

    /**
     * Whether the resource names one of this caller's roles in {@code hide_from_roles}.
     */
    private static boolean hiddenFromCaller(McpResource resource, Request<?> request) {
        if (resource.hideFromRoles().isEmpty() || !request.isAuthenticated()) {
            return false;
        }

        return request.getAuthenticatedAccount().getRoles().stream().anyMatch(resource.hideFromRoles()::contains);
    }

    /**
     * A resource whose {@code mcp} block declares {@code show_if}: the condition decides.
     *
     * <p>It may only read what a listing has. One that does not is an authoring mistake, and the
     * resource is not announced: a condition that cannot be evaluated is not a reason to announce
     * something its author meant to keep quiet.
     */
    private static boolean declared(McpResource resource, ListingContext context) {
        var problems = CatalogCondition.problemsWith(resource.showIf());

        if (!problems.isEmpty()) {
            LOGGER.warn("{} declares a catalogue condition that cannot be evaluated, so it is not announced: {}",
                    resource.uri(), String.join("; ", problems));

            return false;
        }

        return ListingEvaluator.evaluate(resource.showIf(), context) == Truth.TRUE;
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
        // No rule to read is not the same as no rule that matches. The caller reached /mcp, so
        // something authorized them; if none of it is a rule we can enumerate — an authorizer whose
        // logic is Java, which is how a hosting platform grants an administrator its own node —
        // then the analysis knows nothing at all, and claiming "not allowed" would be inventing an
        // answer. Nothing can be ruled out, so nothing is hidden. What should stay quiet anyway
        // says so in its own mcp metadata.
        if (permissions.isEmpty()) {
            return true;
        }

        for (var permission : permissions) {
            if (predicateTruth(permission, context).and(gates(permission, MongoGates.Action.ORDINARY)).mayBeTrue()) {
                return true;
            }
        }

        return false;
    }

    /**
     * What a listing can say about this caller performing one action.
     *
     * <p>Three answers. {@code yes} and {@code unknown} are both offered — never hide what could be
     * allowed — and only {@code no} withholds it, which is the same rule by which the resource
     * itself is listed or not. The third answer is not a hedge: a rule may decide on something the
     * call carries, which a listing does not have, and an action that might work is one worth
     * offering.
     *
     * <p>Withholding is not blocking. A caller who knows the action can still ask for it, and the
     * ACL refuses it then, exactly as it refuses a resource that never appeared in a listing.
     */
    static Map<String, Object> verdict(AclPermissions permissions, Request<?> request, McpResource resource,
            String actionName) {
        var action = resource.actions().get(actionName);

        if (action == null) {
            return Map.of();
        }

        var path = pathOf(resource.uri()) + (action.pathTemplate() == null ? "" : action.pathTemplate());

        return verdict(permissions.of(request), new RequestListingContext(request, path, methodOf(action)), action, path);
    }

    /** The same question with the rules and the context already in hand. Visible for testing. */
    static Map<String, Object> verdict(Collection<BaseAclPermission> rules, ListingContext context,
            McpResource.Action action, String path) {
        var asked = askedOf(action);

        if (rules.isEmpty()) {
            return undecided("no rule that applies to you could be read, so this was not decided here: "
                    + "try it, and read the status");
        }

        var best = Truth.FALSE;
        var bestIgnoringSwitches = Truth.FALSE;

        for (var permission : rules) {
            var predicate = predicateTruth(permission, context);

            bestIgnoringSwitches = bestIgnoringSwitches.or(predicate);
            best = best.or(predicate.and(gates(permission, asked)));
        }

        return switch (best) {
            case TRUE -> Map.of("permitted", "yes");
            case UNDETERMINED -> undecided("whether this is permitted depends on what the call carries, "
                    + "which a catalogue does not have: try it, and read the status");
            case FALSE -> refused(action, bestIgnoringSwitches, path);
        };
    }

    private static Map<String, Object> undecided(String why) {
        return Map.of("permitted", "unknown", "note", why);
    }

    /**
     * Why it is refused, told apart: a rule covers the request and a switch of that rule withholds
     * it, or no rule covers the request at all. The two are fixed in different places.
     */
    private static Map<String, Object> refused(McpResource.Action action, Truth ignoringSwitches, String path) {
        if (ignoringSwitches.mayBeTrue() && !action.requires().isEmpty()) {
            return Map.of("permitted", "no",
                    "note", "a rule of yours covers this request, but it does not grant "
                            + String.join(" and ", action.requires()));
        }

        return Map.of("permitted", "no",
                "note", "no rule that applies to you covers " + methodOf(action) + " on " + path);
    }

    /** What the action asks of MongoDB, as far as the switches of a permission are concerned. */
    private static MongoGates.Action askedOf(McpResource.Action action) {
        return action.requires().contains("mongo.allowManagementRequests")
                ? MongoGates.Action.managementRequest()
                : MongoGates.Action.ORDINARY;
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
    private static Truth predicateTruth(BaseAclPermission permission, ListingContext context) {
        if (!PermissionHints.publishes(permission)) {
            return Truth.FALSE;
        }

        var source = permission.predicateSource().orElse(null);

        if (source == null) {
            return Truth.UNDETERMINED;
        }

        try {
            return ListingEvaluator.evaluate(PredicateSyntax.parse(source),
                    withHints(context, PermissionHints.resolved(permission)));
        } catch (PredicateSyntax.SyntaxException e) {
            // A permission the server accepted but this cannot read: leave it open, rather than
            // decide anything on the strength of our own parser disagreeing with Undertow's.
            LOGGER.debug("could not read the predicate of a permission for a catalog listing, "
                    + "leaving it undecided: {}", e.getMessage());

            return Truth.UNDETERMINED;
        }
    }

    /**
     * The context, plus whatever rules this permission declares for atoms nobody else does.
     *
     * <p>Per permission, because the declaration is: the same opaque predicate may be one the
     * author of this rule knows and the author of another does not.
     */
    private static ListingContext withHints(ListingContext context, Map<String, Scope> hints) {
        if (hints.isEmpty()) {
            return context;
        }

        return new ListingContext() {
            @Override
            public String path() {
                return context.path();
            }

            @Override
            public String method() {
                return context.method();
            }

            @Override
            public Optional<String> attribute(String token) {
                return context.attribute(token);
            }

            @Override
            public Optional<String> variable(String token) {
                return context.variable(token);
            }

            @Override
            public Optional<Scope> scopeOf(String predicateName) {
                var declared = hints.get(predicateName);

                return declared != null ? Optional.of(declared) : context.scopeOf(predicateName);
            }

            @Override
            public Optional<Boolean> evaluate(PredicateExpression.Atom atom) {
                return context.evaluate(atom);
            }
        };
    }

    /** The {@code mongo} switches of a permission, for what the action asks. */
    private static Truth gates(BaseAclPermission permission, MongoGates.Action asked) {
        try {
            return MongoGates.allows(MongoPermissions.from(permission), asked);
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
