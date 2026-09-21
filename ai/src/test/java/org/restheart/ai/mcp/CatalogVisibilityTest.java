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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.security.BaseAclPermission;
import org.restheart.security.EvaluationScope.Scope;
import org.restheart.security.analysis.ListingContext;
import org.restheart.security.analysis.PredicateScopes;

class CatalogVisibilityTest {

    /** A permission that is nothing but its predicate, which is all the catalog reads. */
    private static BaseAclPermission permission(String predicate) {
        return permission(predicate, null);
    }

    /** With the permission's whole document, for what its mongo block says. */
    private static BaseAclPermission permission(String predicate, String raw) {
        return new BaseAclPermission(request -> true, Set.of("user"), 100, raw == null ? null : org.bson.BsonDocument.parse(raw)) {
            @Override
            public Optional<String> predicateSource() {
                return Optional.ofNullable(predicate);
            }
        };
    }

    /** A caller of {@code path} with {@code session} resolved for its variables. */
    private static ListingContext listing(String path, String method, Map<String, String> session) {
        return new ListingContext() {
            @Override
            public String path() {
                return path;
            }

            @Override
            public String method() {
                return method;
            }

            @Override
            public Optional<String> attribute(String token) {
                return Optional.ofNullable(session.get(token));
            }

            @Override
            public Optional<String> variable(String token) {
                return Optional.ofNullable(session.get(token));
            }

            @Override
            public Optional<Scope> scopeOf(String name) {
                return PredicateScopes.of(name);
            }
        };
    }

    private static boolean visible(String predicate, String path, String method, Map<String, String> session) {
        return CatalogVisibility.isReadable(List.of(permission(predicate)), listing(path, method, session));
    }

    private static boolean visible(String predicate, String path, String method) {
        return visible(predicate, path, method, Map.of());
    }

    @Test
    void aRuleThatNamesTheResourceShowsIt() {
        assertTrue(visible("path('/orders') and method(GET)", "/orders", "GET"));
    }

    @Test
    void aRuleAboutSomewhereElseDoesNot() {
        assertFalse(visible("path('/orders') and method(GET)", "/invoices", "GET"));
    }

    /**
     * #743: a rule deciding on a query parameter used to answer no to a question asked without one,
     * and the resource disappeared for the caller holding exactly what would open it.
     */
    @Test
    void aRuleThatDecidesOnTheCallStillShowsTheResource() {
        assertTrue(visible("path('/market_events') and method(POST) and equals(%{q,secret}, 'xxx')",
                "/market_events", "POST"));
    }

    @Test
    void aConditionOnTheCallerIsDecidedExactly() {
        var gate = "path-prefix('/orders') and equals(@user.plan, 'gold')";

        assertTrue(visible(gate, "/orders", "GET", Map.of("@user.plan", "gold")));
        assertFalse(visible(gate, "/orders", "GET", Map.of("@user.plan", "free")));
    }

    @Test
    void aPredicateNobodyDeclaresNeverHides() {
        assertTrue(visible("path('/orders') and is-gold-customer()", "/orders", "GET"));
    }

    /**
     * No rule to read is not the same as no rule that matches.
     *
     * <p>A caller with no enumerable permission still reached {@code /mcp}, so something allowed
     * them — an authorizer whose logic is code, say. The analysis then knows nothing, and answering
     * "not allowed" would be inventing it: nothing can be ruled out, so nothing is hidden.
     */
    @Test
    void noRuleToReadShowsEverything() {
        assertTrue(CatalogVisibility.isReadable(List.of(), listing("/orders", "GET", Map.of())));
    }

    /** A rule that can be read and does not match is an answer, and it hides. */
    @Test
    void aRuleThatDoesNotMatchHides() {
        assertFalse(visible("path('/invoices')", "/orders", "GET"));
    }

    /**
     * A permission whose condition is code has nothing to read, and refusing what cannot be read
     * would hide resources nobody decided to hide.
     */
    @Test
    void aPermissionWithoutAPredicateIsTakenAsPossible() {
        assertTrue(CatalogVisibility.isReadable(List.of(permission(null)), listing("/orders", "GET", Map.of())));
    }

    // ------------------------------------------------------------ what an action says

    private static McpResource.Action action(java.util.function.Consumer<McpResource.Action> shape) {
        var resource = McpResource.builder().uri("https://host/todos").kind("collection")
                .description("d").action("it", shape).build();

        return resource.actions().get("it");
    }

    private static Map<String, Object> verdict(String predicate, McpResource.Action action, String path) {
        return CatalogVisibility.verdict(List.of(permission(predicate)),
                listing(path, action.method() == null ? "GET" : action.method(), Map.of()), action, path);
    }

    @Test
    void anActionARuleCoversIsPermitted() {
        var read = action(a -> a.method("GET").pathTemplate(""));

        assertEquals("yes", verdict("path('/todos') and method(GET)", read, "/todos").get("permitted"));
    }

    @Test
    void anActionNoRuleCoversIsRefused_andSaysWhichRequestWasNotCovered() {
        var drop = action(a -> a.method("DELETE").pathTemplate(""));
        var v = verdict("path('/todos') and method(GET)", drop, "/todos");

        assertEquals("no", v.get("permitted"));
        assertTrue(String.valueOf(v.get("note")).contains("DELETE on /todos"), v.toString());
    }

    /**
     * Two conditions, both necessary. A rule covering {@code DELETE /todos} still does not permit a
     * drop unless its own {@code mongo} block grants management requests, and the note says so
     * rather than leaving the author to guess which of the two is missing.
     */
    @Test
    void anActionASwitchWithholdsSaysWhichSwitch() {
        var drop = action(a -> a.method("DELETE").pathTemplate("").requires("mongo.allowManagementRequests"));
        var v = verdict("path('/todos') and method(DELETE)", drop, "/todos");

        assertEquals("no", v.get("permitted"));
        assertTrue(String.valueOf(v.get("note")).contains("allowManagementRequests"), v.toString());
    }

    @Test
    void anActionWhoseRuleDecidesOnTheCallIsNotDecidedHere() {
        var create = action(a -> a.method("POST").pathTemplate(""));
        var v = verdict("path('/todos') and method(POST) and equals(%{q,ticket}, 'golden')", create, "/todos");

        assertEquals("unknown", v.get("permitted"));
        // named, so an agent knows what to send rather than guessing it from prose (market game)
        assertTrue(String.valueOf(v.get("note")).contains("the query parameter `ticket`"), v.toString());
    }

    /**
     * The collection's schema describes the stored document and requires what a mergeRequest
     * writes; told to validate against it, agents sent {@code actor} the example said not to send.
     */
    @Test
    void aWriteSaysWhichFieldsTheServerSets_whicheverRuleApplies() {
        var create = action(a -> a.method("POST").pathTemplate(""));
        var both = "{mongo: {mergeRequest: {actor: 'trader1', ts: '@now'}}}";
        var actorOnly = "{mongo: {mergeRequest: {actor: 'trader2'}}}";

        var one = CatalogVisibility.verdict(List.of(permission("path('/ledger') and method(POST)", both)),
                listing("/ledger", "POST", Map.of()), create, "/ledger");
        assertEquals(List.of("actor", "ts"), one.get("server_sets"));

        // two rules could apply: only what both set is surely the server's
        var two = CatalogVisibility.verdict(List.of(
                permission("path('/ledger') and method(POST) and equals(%{q,trader}, 'trader1')", both),
                permission("path('/ledger') and method(POST) and equals(%{q,trader}, 'trader2')", actorOnly)),
                listing("/ledger", "POST", Map.of()), create, "/ledger");
        assertEquals(List.of("actor"), two.get("server_sets"));

        // a rule that cannot apply says nothing about this request
        var other = CatalogVisibility.verdict(List.of(
                permission("path('/ledger') and method(POST)", actorOnly),
                permission("path('/elsewhere') and method(POST)", both)),
                listing("/ledger", "POST", Map.of()), create, "/ledger");
        assertEquals(List.of("actor"), other.get("server_sets"));
    }

    @Test
    void aReadHasNoFieldsTheServerSets() {
        var read = action(a -> a.method("GET").pathTemplate(""));
        var v = CatalogVisibility.verdict(List.of(permission("path('/ledger') and method(GET)", "{mongo: {mergeRequest: {actor: 'x'}}}")),
                listing("/ledger", "GET", Map.of()), read, "/ledger");

        assertNull(v.get("server_sets"));
    }

    @Test
    void theNoteNamesEveryParameterAndTheBodyTheUndecidedRulesRead() {
        assertEquals(List.of("the query parameter `trader`", "the query parameter `secret`"),
                CatalogVisibility.callInputsOf("path('/ledger') and method(POST) and equals(%{q,trader}, 'trader1') "
                        + "and equals(%{q,secret}, 'x')"));
        assertEquals(List.of("the query parameter `plan`"),
                CatalogVisibility.callInputsOf("equals(@qparams['plan'], 'gold')"));
        assertEquals(List.of("the query parameter `export`", "the request body"),
                CatalogVisibility.callInputsOf("qparams-contain(export) and bson-request-contains(status)"));
        assertEquals(List.of(), CatalogVisibility.callInputsOf("path('/todos') and method(GET)"));
    }

    @Test
    void withNoRuleToReadNothingIsDecided() {
        var read = action(a -> a.method("GET").pathTemplate(""));

        assertEquals("unknown", CatalogVisibility.verdict(List.of(), listing("/todos", "GET", Map.of()), read, "/todos")
                .get("permitted"));
    }

    @Test
    void theMethodOfTheReadIsTheOneAsked() {
        assertTrue(visible("path('/graphql/app') and method(POST)", "/graphql/app", "POST"));
        assertFalse(visible("path('/graphql/app') and method(POST)", "/graphql/app", "GET"));
    }
}
