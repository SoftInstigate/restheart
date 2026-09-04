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
package org.restheart.plugins.security;

/**
 * Opt-in capability for an {@link Authorizer} that can evaluate a plain {@link RequestDescriptor}
 * instead of a full {@link org.restheart.exchange.Request} — see restheart#722.
 *
 * <p>Needed only by request-shaped (predicate/path/method-based) authorizers, such as an ACL
 * authorizer matching Undertow-style predicates. An authorizer with no notion of "path/method
 * shaped operations" (a pure role-based authorizer, an OAuth-scope authorizer, ...) simply does
 * not implement this interface — it is never consulted for a {@link RequestDescriptor}-based
 * check, and never has to handle input it can't meaningfully interpret via a made-up default
 * answer.
 *
 * <p>Consumed by {@code core}'s {@code AuthorizersHandler} only when the {@link
 * org.restheart.plugins.Service} handling a request overrides {@code operationsToAuthorize(...)}
 * to produce something other than the identity descriptor of the real incoming exchange — i.e.
 * only for single-endpoint protocol services (GraphQL, MCP), never for an ordinary REST service.
 * When invoked, the same VETOER/ALLOWER voting semantics as {@link
 * Authorizer#isAllowed(org.restheart.exchange.Request)} apply, evaluated per {@link
 * RequestDescriptor} rather than per real request; if no registered {@link Authorizer} implements
 * this interface at all, the check fails closed (denied) rather than allowing an unchecked read.
 */
public interface DescriptorAwareAuthorizer extends Authorizer {
    /**
     * Same VETOER/ALLOWER contract as {@link Authorizer#isAllowed(org.restheart.exchange.Request)},
     * evaluated against a {@link RequestDescriptor} instead of a real request/exchange.
     *
     * @param descriptor the operation to authorize
     * @return true if this authorizer allows the operation (ALLOWER) or has no objection
     *         (VETOER), false if it has no opinion (ALLOWER) or denies it (VETOER)
     */
    boolean isAllowed(RequestDescriptor descriptor);
}
