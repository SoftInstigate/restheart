/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * =========================LICENSE_END==================================
 */
package org.restheart.security.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.restheart.security.EvaluationScope.Scope;

class ListingEvaluatorTest {

    /** A caller of `/orders` with the roles of a customer, whose account says the plan is gold. */
    private static ListingContext ctx(String path, String method, Map<String, String> session) {
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
                return Optional.ofNullable(SCOPES.get(name));
            }
        };
    }

    private static final Map<String, Scope> SCOPES = Map.of(
            "path", Scope.LISTING,
            "path-prefix", Scope.LISTING,
            "path-template", Scope.LISTING,
            "method", Scope.LISTING,
            "regex", Scope.ARGUMENTS,
            "equals", Scope.ARGUMENTS,
            "in", Scope.ARGUMENTS,
            "qparams-contain", Scope.CALL,
            "bson-request-contains", Scope.CALL);

    private static Truth evaluate(String predicate, String path, String method, Map<String, String> session) {
        return ListingEvaluator.evaluate(predicate, ctx(path, method, session));
    }

    private static Truth evaluate(String predicate, String path, String method) {
        return evaluate(predicate, path, method, Map.of());
    }

    // ------------------------------------------------------------ the path is an input

    @Test
    void aPathAtomIsDecidedAgainstTheCandidate() {
        assertEquals(Truth.TRUE, evaluate("path('/orders')", "/orders", "GET"));
        assertEquals(Truth.FALSE, evaluate("path('/orders')", "/invoices", "GET"));
    }

    @Test
    void aPrefixIsDecidedToo() {
        assertEquals(Truth.TRUE, evaluate("path-prefix('/orders')", "/orders/42", "GET"));
        assertEquals(Truth.FALSE, evaluate("path-prefix('/orders')", "/invoices", "GET"));
    }

    /**
     * The sub-resources of a collection — {@code _meta}, {@code _indexes}, and the {@code *} a bulk
     * write addresses — are ordinary paths, selected by a prefix and missed by an exact path. The
     * switches that gate them are a separate condition, on the permission and not in its predicate.
     */
    @Test
    void aPrefixReachesTheSubResourcesOfACollection_anExactPathDoesNot() {
        for (var path : new String[]{"/coll/_meta", "/coll/_indexes", "/coll/_indexes/byQty", "/coll/*"}) {
            assertEquals(Truth.TRUE, evaluate("path-prefix('/coll')", path, "GET"), path);
            assertEquals(Truth.FALSE, evaluate("path('/coll')", path, "GET"), path);
        }
    }

    /** The prefix an owner writes to mean "everything" reaches every resource. */
    @Test
    void theRootPrefixMatchesEverything() {
        assertEquals(Truth.TRUE, evaluate("path-prefix('/')", "/orders", "GET"));
        assertEquals(Truth.TRUE, evaluate("path-prefix('/')", "/a/b/c", "DELETE"));
    }

    /** A regular expression is applied like any other path atom: it reads the relative path. */
    @Test
    void aRegexOverThePathIsDecided() {
        assertEquals(Truth.TRUE, evaluate("regex('/t-[^/]+/(orders|invoices)')", "/t-acme/orders", "GET"));
        assertEquals(Truth.FALSE, evaluate("regex('/t-[^/]+/(orders|invoices)')", "/pippo", "GET"));
    }

    @Test
    void theMethodIsDecidedAgainstTheActionsOwn() {
        assertEquals(Truth.TRUE, evaluate("method(GET)", "/orders", "GET"));
        assertEquals(Truth.FALSE, evaluate("method(POST)", "/orders", "GET"));
        assertEquals(Truth.TRUE, evaluate("method(value={GET, POST})", "/orders", "POST"));
    }

    // ------------------------------------------------------------ the session

    @Test
    void anIdentityConditionIsDecidedExactly() {
        var gold = Map.of("@user.plan", "gold");
        var free = Map.of("@user.plan", "free");

        assertEquals(Truth.TRUE,
                evaluate("path-prefix('/orders') and equals(@user.plan, 'gold')", "/orders", "GET", gold));
        assertEquals(Truth.FALSE,
                evaluate("path-prefix('/orders') and equals(@user.plan, 'gold')", "/orders", "GET", free));
    }

    @Test
    void aVariableThatDoesNotResolveLeavesTheAtomUndetermined() {
        assertEquals(Truth.UNDETERMINED, evaluate("equals(@user.plan, 'gold')", "/orders", "GET"));
    }

    // ------------------------------------------------------------ the call

    /** #743: a condition on a query parameter must not hide anything. */
    @Test
    void aConditionOnTheCallIsUndeterminedAndShows() {
        var truth = evaluate("path('/market_events') and method(POST) and equals(%{q,secret}, 'xxx')",
                "/market_events", "POST");

        assertEquals(Truth.UNDETERMINED, truth);
        assertTrue(truth.mayBeTrue());
    }

    @Test
    void aNegatedUndeterminedStaysUndetermined() {
        assertEquals(Truth.UNDETERMINED, evaluate("not qparams-contain(export)", "/orders", "GET"));
    }

    @Test
    void aFalsePathIsNotRescuedByAnUndeterminedConjunct() {
        assertEquals(Truth.FALSE,
                evaluate("path('/orders') and qparams-contain(page)", "/invoices", "GET"));
    }

    // ------------------------------------------------------------ opaque

    @Test
    void anUndeclaredPredicateIsUndetermined() {
        assertEquals(Truth.UNDETERMINED, evaluate("is-gold-customer()", "/orders", "GET"));
    }

    @Test
    void anUndeclaredPredicateNeverHidesWhatThePathAllows() {
        assertTrue(evaluate("path('/orders') and is-gold-customer()", "/orders", "GET").mayBeTrue());
    }

    // ------------------------------------------------------------ path templates

    @Test
    void aTemplateBindsItsVariablesForTheAtomsThatFollow() {
        var acme = Map.of("@user.tenant", "acme");
        var other = Map.of("@user.tenant", "other");

        assertEquals(Truth.TRUE, evaluate(
                "path-template('/t-{tenant}/orders') and equals(@user.tenant, ${tenant})", "/t-acme/orders", "GET", acme));
        assertEquals(Truth.FALSE, evaluate(
                "path-template('/t-{tenant}/orders') and equals(@user.tenant, ${tenant})", "/t-acme/orders", "GET", other));
    }

    /** A catalog entry can be templated itself, and a variable compared with a literal decides nothing. */
    @Test
    void aTemplatedCandidateLeavesTheComparisonUndetermined() {
        assertEquals(Truth.UNDETERMINED, evaluate("path('/orders/42')", "/orders/{id}", "GET"));
    }

    @Test
    void aTemplatedCandidateStillFailsOnALengthMismatch() {
        assertEquals(Truth.FALSE, evaluate("path('/invoices')", "/orders/{id}", "GET"));
    }

    // ------------------------------------------------------------ the whole thing

    @Test
    void theResultIsAnUpperBound() {
        // every shape that could be allowed by some call answers "may be true"
        for (var predicate : new String[]{
                "path('/orders')",
                "path('/orders') and equals(%{q,token}, 'x')",
                "path('/orders') and not qparams-contain(export)",
                "path('/orders') and is-whatever()",
                "path('/orders') or path('/invoices')"}) {
            assertTrue(evaluate(predicate, "/orders", "GET").mayBeTrue(), predicate);
        }
    }

    @Test
    void literalsCompose() {
        assertEquals(Truth.TRUE, evaluate("path('/orders') and true", "/orders", "GET"));
        assertEquals(Truth.FALSE, evaluate("path('/orders') and false", "/orders", "GET"));
    }
}
