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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import org.restheart.security.EvaluationScope;
import org.restheart.security.EvaluationScope.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.predicate.PredicateBuilder;

/**
 * When each predicate of the language can be evaluated.
 *
 * <p>Two sources, in this order. A builder that implements {@link EvaluationScope} answers for
 * itself — that is how RESTHeart's own predicates and a plugin's declare. Undertow's cannot, since
 * we do not own those classes, so their rules are written here.
 *
 * <p>A name neither source knows is <strong>opaque</strong>: the analysis stops there and leaves
 * the atom undetermined, which shows the resource. That is the safe direction, and it is why
 * declaring nothing costs precision and not correctness.
 */
public final class PredicateScopes {

    private static final Logger LOGGER = LoggerFactory.getLogger(PredicateScopes.class);

    private PredicateScopes() {
    }

    /**
     * Undertow's own, which cannot implement the interface.
     *
     * <p>{@code auth-required} is among the call-dependent ones on purpose: it asks whether the
     * request being made needs authentication, which is a property of that request and not of the
     * session composing the listing.
     */
    private static final Map<String, Scope> UNDERTOW = Map.ofEntries(
            Map.entry("path", Scope.LISTING),
            Map.entry("path-prefix", Scope.LISTING),
            Map.entry("path-suffix", Scope.LISTING),
            Map.entry("path-template", Scope.LISTING),
            Map.entry("method", Scope.LISTING),
            Map.entry("secure", Scope.LISTING),
            Map.entry("idempotent", Scope.LISTING),
            Map.entry("equals", Scope.ARGUMENTS),
            Map.entry("regex", Scope.ARGUMENTS),
            Map.entry("contains", Scope.ARGUMENTS),
            Map.entry("exists", Scope.ARGUMENTS),
            Map.entry("auth-required", Scope.CALL),
            Map.entry("max-content-size", Scope.CALL),
            Map.entry("min-content-size", Scope.CALL),
            Map.entry("request-larger-than", Scope.CALL),
            Map.entry("request-smaller-than", Scope.CALL));

    private static volatile Map<String, Scope> declared;

    /**
     * @param predicateName the name as it is written in a predicate
     * @return its rule, or empty when nothing declares one — an opaque predicate
     */
    public static Optional<Scope> of(String predicateName) {
        var byBuilder = declared().get(predicateName);

        return Optional.ofNullable(byBuilder != null ? byBuilder : UNDERTOW.get(predicateName));
    }

    /** Every predicate whose builder declares a rule, loaded once. */
    private static Map<String, Scope> declared() {
        var known = declared;

        if (known == null) {
            synchronized (PredicateScopes.class) {
                known = declared;

                if (known == null) {
                    declared = known = load();
                }
            }
        }

        return known;
    }

    private static Map<String, Scope> load() {
        var scopes = new HashMap<String, Scope>();

        for (var builder : ServiceLoader.load(PredicateBuilder.class)) {
            if (builder instanceof EvaluationScope scoped) {
                scopes.put(builder.name(), scoped.scope());
            } else {
                LOGGER.debug("predicate {} declares no evaluation scope: the MCP catalog will treat "
                        + "it as opaque and list what it might allow", builder.name());
            }
        }

        return Map.copyOf(scopes);
    }
}
