/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.utils;

import org.bson.BsonValue;
import org.restheart.exchange.Request;

/**
 * Service Provider Interface for pluggable {@link VarsInterpolator} operators.
 *
 * <p>A {@code CustomOperator} lets a plugin contribute a new {@code $}-prefixed
 * operator (e.g. {@code $vectorize}) usable inline in stored aggregation pipeline
 * {@code stages} and GraphQL mappings, resolved wherever {@link VarsInterpolator} /
 * {@link StagesInterpolator} already interpolate {@code $var} / {@code $arg}. This is
 * the aggregation-pipeline counterpart to {@code org.restheart.security.VarResolver},
 * which does the analogous job for {@code @}-prefixed variables in ACL predicates and
 * {@code readFilter}/{@code mergeRequest} documents — a different subsystem, evaluated
 * at a different point in the request lifecycle; the two are not interchangeable.
 *
 * <h2>Registration</h2>
 * <pre>{@code
 * @Inject("custom-operator-registry")
 * private CustomOperatorRegistry operators;
 *
 * @OnInit
 * public void init() {
 *     operators.register(new MyOperator());
 * }
 * }</pre>
 *
 * <p>There are no built-in custom operators — {@code $var}/{@code $arg} themselves are
 * handled directly by {@link VarsInterpolator}, not through this registry. Registering
 * a name that collides with an already-registered custom operator fails at startup with
 * a {@link org.restheart.configuration.ConfigurationException}. Registering a name that
 * shadows a genuine MongoDB operator (e.g. {@code "gt"}) is the registrant's own
 * responsibility to avoid — nothing here checks against MongoDB's operator set.
 *
 * <h2>Resolution order</h2>
 * <p>The operator's argument — the value under the {@code $name} key — is interpolated
 * first (so a nested {@code {"$var": "..."}}, or a nested call to another custom
 * operator, is already resolved to its bound value), then passed to {@link #resolve}.
 * A document like {@code {"$vectorize": {"$var": "query"}}} therefore has {@code arg}
 * already equal to the bound {@code query} value, not the raw {@code $var} node.
 *
 * <h2>Failure semantics</h2>
 * <p>Unlike {@code VarResolver} (where an unresolved variable degrades gracefully to a
 * denied predicate), an operator that cannot be resolved should throw rather than
 * return a placeholder — consistent with how an unbound {@code $var} without a default
 * already fails the whole request with {@link org.restheart.exchange.QueryVariableNotBoundException}
 * rather than silently resolving to {@code null}. A custom operator producing a
 * misleading value (e.g. a null or zero vector for {@code $vectorize}) would otherwise
 * make a query silently wrong instead of visibly failing.
 *
 * @see CustomOperatorRegistry
 * @see VarsInterpolator
 */
public interface CustomOperator {
    /**
     * The operator name, without the leading {@code $} — e.g. {@code "vectorize"}. Must
     * be unique across all registered custom operators.
     */
    String name();

    /**
     * Resolves this operator's value for the given request and (already-interpolated)
     * argument.
     *
     * @param request the current request; may be {@code null} for contexts without one
     *        (e.g. GraphQL mapping interpolation) — implementations needing per-request
     *        overrides should handle a {@code null} request as "use the static default"
     * @param arg the value under the {@code $name} key, after {@code $var}/{@code $arg}
     *        and any nested custom-operator interpolation has already been applied
     * @return the resolved value
     * @throws RuntimeException if the operator cannot be resolved — see the class
     *         javadoc's failure semantics; this propagates as an aggregation/mapping
     *         execution error rather than silently degrading the pipeline
     */
    BsonValue resolve(Request<?> request, BsonValue arg);
}
