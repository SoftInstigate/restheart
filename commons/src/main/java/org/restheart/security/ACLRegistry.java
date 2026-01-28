/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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

import java.util.function.Predicate;

import org.restheart.exchange.Request;

/**
 * Registry for defining Access Control Lists (ACLs) programmatically.
 *
 * <p>This registry serves as a central repository for dynamic access control rules that can be
 * registered at runtime. It is utilized by the {@code ACLRegistryVetoer} and {@code ACLRegistryAllower}
 * authorizers to manage request permissions in a flexible, programmatic manner.</p>
 *
 * <h2>Authorization Model</h2>
 * <p>The registry implements a two-phase authorization model:</p>
 * <ol>
 *   <li><strong>Veto Phase:</strong> The {@code ACLRegistryVetoer} evaluates all registered veto
 *       predicates. If any predicate returns {@code true}, the request is immediately denied.</li>
 *   <li><strong>Allow Phase:</strong> If not vetoed, the {@code ACLRegistryAllower} checks if at
 *       least one allow predicate returns {@code true}. The request proceeds only if approved.</li>
 * </ol>
 *
 * <p>A request is permitted to proceed if and only if:</p>
 * <ul>
 *   <li>It is not denied by any {@code ACLRegistryVetoer} predicates, AND</li>
 *   <li>At least one {@code ACLRegistryAllower} predicate approves it</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @Inject("acl-registry")
 * ACLRegistry registry;
 *
 * @OnInit
 * public void init() {
 *     // Deny all requests to /admin from non-admin users
 *     registry.registerVeto(request -> 
 *         request.getPath().startsWith("/admin") && 
 *         !request.isAccountInRole("admin")
 *     );
 *     
 *     // Allow authenticated users to access /api
 *     registry.registerAllow(request -> 
 *         request.getPath().startsWith("/api") && 
 *         request.isAuthenticated()
 *     );
 *     
 *     // Require authentication for all /secure paths
 *     registry.registerAuthenticationRequirement(request ->
 *         request.getPath().startsWith("/secure")
 *     );
 * }
 * }</pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 6.0.0
 * @see org.restheart.security.plugins.authorizers.ACLRegistryVetoer
 * @see org.restheart.security.plugins.authorizers.ACLRegistryAllower
 */
public interface ACLRegistry {
    /**
     * Registers a veto predicate that determines if a request should be denied.
     * 
     * <p>When the predicate evaluates to {@code true}, the request is immediately forbidden (vetoed)
     * with no further evaluation. This provides a mechanism for implementing hard denials that cannot
     * be overridden by allow rules.</p>
     * 
     * <p>Common use cases for veto predicates include:</p>
     * <ul>
     *   <li>Blocking access to sensitive administrative endpoints</li>
     *   <li>Enforcing IP-based access restrictions</li>
     *   <li>Preventing access during maintenance windows</li>
     *   <li>Implementing rate limiting or abuse prevention</li>
     * </ul>
     * 
     * <p><strong>Note:</strong> Veto predicates are evaluated before allow predicates. A single
     * veto is sufficient to deny a request, regardless of any allow rules.</p>
     *
     * @param veto The veto predicate to register. This predicate should return {@code true} to veto
     *             (deny) the request, and {@code false} to let the decision be further evaluated
     *             by allow predicates or other authorizers
     * @throws NullPointerException if the veto predicate is null
     */
    public void registerVeto(Predicate<Request<?>> veto);

    /**
     * Registers an allow predicate that determines if a request should be authorized.
     * 
     * <p>The request is authorized if this predicate evaluates to {@code true}, provided that no
     * veto predicates or other active vetoer authorizers have denied the request. Multiple allow
     * predicates can be registered, and the request proceeds if any one of them returns {@code true}.</p>
     * 
     * <p>Common use cases for allow predicates include:</p>
     * <ul>
     *   <li>Granting access based on user roles or permissions</li>
     *   <li>Implementing path-based access control</li>
     *   <li>Allowing access based on request headers or parameters</li>
     *   <li>Implementing custom business logic for authorization</li>
     * </ul>
     * 
     * <p><strong>Important:</strong> Allow predicates are only evaluated if no veto predicates
     * have denied the request. At least one allow predicate must return {@code true} for the
     * request to be authorized.</p>
     *
     * @param allow The allow predicate to register. This predicate should return {@code true} to
     *              authorize the request, unless it is vetoed by any veto predicates or other
     *              vetoing conditions
     * @throws NullPointerException if the allow predicate is null
     */
    public void registerAllow(Predicate<Request<?>> allow);

    /**
     * Registers a predicate that determines whether requests require authentication.
     * 
     * <p>This method is used to specify conditions under which authentication is mandatory before
     * authorization rules are evaluated. When the predicate returns {@code true}, the request must
     * have valid authentication credentials; otherwise, it will be rejected with a 401 Unauthorized
     * status.</p>
     * 
     * <p>Authentication requirements are evaluated before authorization rules. This ensures that
     * sensitive endpoints can enforce authentication regardless of allow predicates.</p>
     * 
     * <p>Common use cases include:</p>
     * <ul>
     *   <li>Requiring authentication for all API endpoints</li>
     *   <li>Enforcing authentication for specific URL patterns</li>
     *   <li>Making authentication mandatory for certain HTTP methods</li>
     *   <li>Implementing mixed authentication models (some paths public, others protected)</li>
     * </ul>
     * 
     * <p>Example:</p>
     * <pre>{@code
     * // Require authentication for all paths except /public
     * registry.registerAuthenticationRequirement(request ->
     *     !request.getPath().startsWith("/public")
     * );
     * }</pre>
     *
     * @param authenticationRequired The predicate to determine if authentication is necessary.
     *                               It should return {@code true} if the request must be authenticated,
     *                               otherwise {@code false} if unauthenticated requests might be allowed
     * @throws NullPointerException if the authenticationRequired predicate is null
     */
    public void registerAuthenticationRequirement(Predicate<Request<?>> authenticationRequired);

}
