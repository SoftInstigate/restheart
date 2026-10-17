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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.security.analysis;

import java.util.Optional;

import org.restheart.security.EvaluationScope.Scope;
import org.restheart.security.analysis.PredicateExpression.Atom;

/**
 * What a {@link ListingEvaluator} may read: the resource being considered, and the session asking.
 *
 * <p>Deliberately small. Everything here is available before any call is made — the candidate
 * resource is an input of the question, not an unknown to be worked out of the predicate, and the
 * session comes from the live MCP request. Anything else is undetermined, and saying so is the
 * point: an empty {@link Optional} is an answer, not a failure.
 */
public interface ListingContext {

    /** The path of the resource being considered. */
    String path();

    /** The method of the action the listing is about — a resource's read, for a catalog. */
    String method();

    /**
     * The value of an exchange attribute, as written: {@code %{RELATIVE_PATH}}, {@code %m},
     * {@code %u}, {@code %{q,page}}.
     *
     * @return empty when the attribute belongs to the call rather than to the session
     */
    Optional<String> attribute(String token);

    /**
     * The value of an ACL variable, as written: {@code @user.dept}, {@code @roles},
     * {@code @qparams['id']}.
     *
     * @return empty when the variable belongs to the call, when its resolver declares nothing, or
     *         when it does not resolve
     */
    Optional<String> variable(String token);

    /**
     * The rule declared for a predicate name.
     *
     * @return empty when nothing is declared, which makes the occurrence opaque
     */
    Optional<Scope> scopeOf(String predicateName);

    /**
     * Evaluates an atom this evaluator has no semantics for — a predicate registered by a plugin
     * that declared {@link Scope#LISTING} — by invoking it on the live request.
     *
     * @return empty when it cannot be evaluated, which leaves the atom undetermined
     */
    default Optional<Boolean> evaluate(Atom atom) {
        return Optional.empty();
    }
}
