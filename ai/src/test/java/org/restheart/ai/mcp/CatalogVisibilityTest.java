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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.restheart.security.BaseAclPermission;
import org.restheart.security.EvaluationScope.Scope;
import org.restheart.security.analysis.ListingContext;
import org.restheart.security.analysis.PredicateScopes;

class CatalogVisibilityTest {

    /** A permission that is nothing but its predicate, which is all the catalog reads. */
    private static BaseAclPermission permission(String predicate) {
        return new BaseAclPermission(request -> true, Set.of("user"), 100, null) {
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

    @Test
    void theMethodOfTheReadIsTheOneAsked() {
        assertTrue(visible("path('/graphql/app') and method(POST)", "/graphql/app", "POST"));
        assertFalse(visible("path('/graphql/app') and method(POST)", "/graphql/app", "GET"));
    }
}
