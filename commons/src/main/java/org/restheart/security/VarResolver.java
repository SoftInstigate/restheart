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

import org.bson.BsonValue;
import org.restheart.exchange.Request;

/**
 * Service Provider Interface for pluggable {@link AclVarsInterpolator} variables.
 *
 * <p>A {@code VarResolver} lets a plugin contribute a new {@code @}-prefixed variable
 * (e.g. {@code @subscription}) that becomes usable everywhere {@link AclVarsInterpolator}
 * is consulted: ACL {@code predicate} strings ({@code MongoAclPermission},
 * {@code FileAclPermission}), and the MongoDB {@code readFilter}/{@code mergeRequest}
 * permission properties. One namespace, one place to look.
 *
 * <h2>Registration</h2>
 * <pre>{@code
 * @Inject("acl-vars-registry")
 * private AclVarsRegistry vars;
 *
 * @OnInit
 * public void init() {
 *     vars.register(new SubscriptionVarResolver());
 * }
 * }</pre>
 *
 * <p>Built-in resolvers ({@code @user}, {@code @request}, {@code @now}, {@code @rnd}, ...)
 * register first, at class-loading time, and therefore win any name collision: registering
 * a name that is already taken fails at startup with a {@code ConfigurationException} rather
 * than being silently ignored.
 *
 * <h2>Dispatch</h2>
 * <p>The framework routes to a resolver the expressions starting with {@code @} + {@link #name()}
 * followed by end-of-string, {@code .}, {@code (} or {@code [}. The resolver receives the whole
 * expression as written in the predicate or filter and is responsible for parsing its own suffix
 * or argument — e.g. a resolver named {@code "user"} receives {@code "@user"} as well as
 * {@code "@user.profile.name"}.
 *
 * <h2>Failure semantics</h2>
 * <p>Returning {@code null} (or a {@link org.bson.BsonNull}) is treated the same as an unresolved
 * variable: in a {@code readFilter}/{@code mergeRequest} document the value becomes
 * {@code BsonNull.VALUE}; in an ACL {@code predicate} string the expression is replaced with a
 * random, unguessable token, so any comparison against it fails and the predicate is not
 * satisfied. A broken resolver denies access, it never widens it. Throwing from {@link #resolve}
 * is handled the same way as returning {@code null}.
 *
 * <h2>Resolver contract</h2>
 * <ol>
 *   <li><b>The cache belongs to the resolver.</b> The framework memoizes the value of a given
 *       full expression once per request (see {@link #cacheable()}), but if resolving one
 *       sub-property is expensive and a predicate accesses several (e.g. {@code @subscription.plan}
 *       and {@code @subscription.status}), caching the underlying document across those calls is
 *       the resolver's own responsibility — invalidation policy is implementation-specific and
 *       only the registrant knows it.</li>
 *   <li><b>No secrets in the returned value.</b> A registered variable also flows into
 *       {@code readFilter}, hence into MongoDB queries — never resolve to a credential, hash, or
 *       other sensitive value.</li>
 *   <li><b>Deterministic, request-scoped.</b> {@link #resolve} must be a pure function of the
 *       request and the authenticated identity, taking no input from whoever writes the predicate
 *       being evaluated.</li>
 * </ol>
 *
 * @see AclVarsRegistry
 * @see AclVarsInterpolator
 * @since 9.7.0
 */
public interface VarResolver {

    /**
     * The variable name, without the leading {@code @} — e.g. {@code "user"}, {@code "now"},
     * {@code "rnd"}, {@code "subscription"}. Must be unique across all registered resolvers,
     * built-in or not.
     */
    String name();

    /**
     * Resolves the value of this variable for the given request.
     *
     * @param request the current request
     * @param var     the full expression exactly as written in the predicate or filter document —
     *                e.g. {@code "@user.profile.name"}, {@code "@rnd(256)"}, {@code "@qparams['id']"},
     *                or just {@code "@now"}
     * @return the resolved value, or {@code null} (or {@link org.bson.BsonNull#VALUE}) if the
     *         variable cannot be resolved for this request — see the class javadoc for failure
     *         semantics
     */
    BsonValue resolve(Request<?> request, String var);

    /**
     * Whether the value resolved for a given full expression can be memoized for the rest of the
     * current request. Defaults to {@code true}.
     *
     * <p>Set to {@code false} for non-deterministic variables such as {@code @rnd}, where every
     * occurrence in the predicate or filter must yield an independent value — memoizing it would
     * change the semantics of the variable, not just optimize its computation.
     */
    default boolean cacheable() {
        return true;
    }
}
