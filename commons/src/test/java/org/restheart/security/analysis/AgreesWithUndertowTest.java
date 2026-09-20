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

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.restheart.security.EvaluationScope.Scope;

import io.undertow.predicate.PredicateParser;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * The analysis reads a predicate instead of running it, so its understanding of the language is
 * its own — and that is a copy of Undertow's, which can drift from it without anything saying so.
 *
 * <p>This is where the two are held together. For a predicate whose atoms are all determined, our
 * three-valued evaluation must answer exactly what Undertow's own parser and predicate answer on
 * the matching request. A divergence shows up here rather than as a resource quietly missing from
 * somebody's catalogue.
 *
 * <p>Only determined predicates can be compared: one reading the call has no answer on our side by
 * design, which is the whole point of the analysis and not a disagreement.
 */
class AgreesWithUndertowTest {

    /** A request Undertow can answer about: a path and a method, which is all these predicates read. */
    private static HttpServerExchange exchange(String path, String method) {
        var exchange = new HttpServerExchange();
        exchange.setRequestPath(path);
        exchange.setRelativePath(path);
        exchange.setRequestMethod(new HttpString(method));
        return exchange;
    }

    private static ListingContext listing(String path, String method) {
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
                return Optional.empty();
            }

            @Override
            public Optional<String> variable(String token) {
                return Optional.empty();
            }

            @Override
            public Optional<Scope> scopeOf(String name) {
                return PredicateScopes.of(name);
            }
        };
    }

    /** Both sides asked the same thing about the same request. */
    private static void agree(String predicate, String path, String method) {
        var undertow = PredicateParser.parse(predicate, AgreesWithUndertowTest.class.getClassLoader())
                .resolve(exchange(path, method));

        var ours = ListingEvaluator.evaluate(predicate, listing(path, method));

        assertEquals(Truth.of(undertow), ours,
                () -> predicate + " on " + method + " " + path + ": Undertow says " + undertow + ", we say " + ours);
    }

    @Test
    @DisplayName("path, in every shape an ACL writes it")
    void paths() {
        for (var request : Map.of("/orders", "GET", "/orders/42", "GET", "/invoices", "GET",
                "/", "GET", "/orders/_size", "GET").entrySet()) {
            for (var predicate : new String[] {
                    "path('/orders')",
                    "path('/orders', '/invoices')",
                    "path-prefix('/orders')",
                    "path-prefix('/')",
                    "path-prefix('/ord')",
                    "path-suffix('/_size')",
                    "path-template('/orders/{id}')",
                    "regex('/orders.*')",
                    "regex(pattern='/orders', full-match=true)" }) {
                agree(predicate, request.getKey(), request.getValue());
            }
        }
    }

    @Test
    @DisplayName("method and the predicates derived from it")
    void methods() {
        for (var method : new String[] { "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS" }) {
            agree("method(GET)", "/orders", method);
            agree("method(GET, POST)", "/orders", method);
            agree("idempotent", "/orders", method);
        }
    }

    @Test
    @DisplayName("the boolean structure, precedence included")
    void structure() {
        for (var predicate : new String[] {
                "path('/orders') and method(GET)",
                "path('/orders') or path('/invoices')",
                "not path('/orders')",
                "not path('/orders') and method(GET)",
                "path('/orders') and (method(POST) or method(GET))",
                "(path('/orders') or path('/invoices')) and method(GET)",
                "true",
                "false",
                "path('/orders') and true",
                "path('/orders') and false" }) {
            agree(predicate, "/orders", "GET");
            agree(predicate, "/invoices", "POST");
        }
    }

    @Test
    @DisplayName("comparisons over attributes the request answers")
    void comparisons() {
        agree("equals(%{RELATIVE_PATH}, '/orders')", "/orders", "GET");
        agree("equals(%{RELATIVE_PATH}, '/invoices')", "/orders", "GET");
        agree("contains(value=%{RELATIVE_PATH}, search={ord})", "/orders", "GET");
        agree("contains(value=%{RELATIVE_PATH}, search={xyz})", "/orders", "GET");
        agree("exists(%{RELATIVE_PATH})", "/orders", "GET");
    }

    @Test
    @DisplayName("a quoted operator is data, not syntax")
    void quoting() {
        agree("path('/and/or/not')", "/and/or/not", "GET");
        agree("path('/and/or/not')", "/orders", "GET");
    }
}
