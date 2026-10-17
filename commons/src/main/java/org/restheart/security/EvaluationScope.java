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
package org.restheart.security;

/**
 * Declares when a predicate can be evaluated — implemented by a {@code PredicateBuilder}, in
 * addition to Undertow's own interface, which cannot be extended.
 *
 * <p>The MCP catalog decides what to list by reading the caller's permissions rather than by
 * putting a fabricated request to the authorization chain. Reading them means evaluating what is
 * already determined when a listing is composed — the candidate resource's path and method, the
 * session — and leaving undetermined whatever belongs to a call that has not happened. A predicate
 * that says nothing is left undetermined too, which never hides a resource but never discriminates
 * either.
 *
 * <p>A predicate does not have a class: it carries the rule by which the class of each of its
 * occurrences is worked out. {@code path('/a')} is always determined, {@code qparams-contain(page)}
 * never is, and {@code equals(...)} is whichever its arguments are.
 */
public interface EvaluationScope {

    enum Scope {
        /**
         * Reads only what a listing already has. For a predicate of RESTHeart's own — {@code path},
         * {@code method} — that includes the candidate resource; for one registered by a plugin it
         * means <strong>the session only</strong>, since the only way to evaluate it is to invoke it
         * on the live request, which carries the MCP path and not the candidate's.
         */
        LISTING,

        /**
         * Reads something of the call that has not happened yet: a query parameter, the body, a
         * request header. Always undetermined when a listing is composed.
         */
        CALL,

        /**
         * Takes the class of its arguments, as {@code equals}, {@code in}, {@code gte},
         * {@code lte}, {@code regex}, {@code contains} and {@code exists} do.
         */
        ARGUMENTS
    }

    /**
     * @return when occurrences of this predicate can be evaluated. A builder that does not
     *         implement this interface is treated as opaque, which is the safe reading: undetermined,
     *         hence satisfiable, hence the resource is listed.
     */
    Scope scope();
}
