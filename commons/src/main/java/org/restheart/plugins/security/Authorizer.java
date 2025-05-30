/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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

import org.restheart.exchange.Request;
import org.restheart.plugins.ConfigurablePlugin;

/**
 * Interface for implementing RESTHeart authorizers that control access to resources.
 * <p>
 * Authorizers are responsible for making access control decisions after a user has been
 * successfully authenticated. They determine whether an authenticated user (or anonymous
 * user) should be allowed to perform a specific operation on a particular resource.
 * Authorizers form the authorization layer of the RESTHeart security pipeline.
 * </p>
 * <p>
 * RESTHeart supports two types of authorizers that work together to make authorization decisions:
 * <ul>
 *   <li><strong>ALLOWER</strong> - Can grant access to resources (positive authorization)</li>
 *   <li><strong>VETOER</strong> - Can deny access to resources (negative authorization)</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Authorization Decision Logic:</strong><br>
 * A request is allowed when:
 * <ol>
 *   <li>No VETOER authorizer denies the request, AND</li>
 *   <li>At least one ALLOWER authorizer allows the request</li>
 * </ol>
 * This dual-approach allows for flexible security policies where default permissions
 * can be granted while specific restrictions are enforced.
 * </p>
 * <p>
 * Common authorizer implementations include:
 * <ul>
 *   <li><strong>Role-based Authorizers</strong> - Check user roles against resource requirements</li>
 *   <li><strong>ACL Authorizers</strong> - Use access control lists to determine permissions</li>
 *   <li><strong>Path-based Authorizers</strong> - Control access based on request paths</li>
 *   <li><strong>Resource-based Authorizers</strong> - Make decisions based on resource content</li>
 *   <li><strong>Time-based Authorizers</strong> - Restrict access based on time conditions</li>
 * </ul>
 * </p>
 * <p>
 * Example implementation:
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "roleAuthorizer",
 *     description = "Role-based access control authorizer",
 *     authorizerType = Authorizer.TYPE.ALLOWER
 * )
 * public class RoleAuthorizer implements Authorizer {
 *     &#64;Override
 *     public boolean isAllowed(Request&lt;?&gt; request) {
 *         var account = request.getAuthenticatedAccount();
 *         if (account != null && account.getRoles().contains("admin")) {
 *             return true; // Admins can access everything
 *         }
 *         return request.getPath().startsWith("/public");
 *     }
 *     
 *     &#64;Override
 *     public boolean isAuthenticationRequired(Request&lt;?&gt; request) {
 *         return !request.getPath().startsWith("/public");
 *     }
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Integration with Security Pipeline:</strong>
 * <ol>
 *   <li>User authentication is completed (or determined to be anonymous)</li>
 *   <li>RESTHeart invokes all registered authorizers in priority order</li>
 *   <li>Each authorizer examines the request and user context</li>
 *   <li>VETOER authorizers can immediately deny access</li>
 *   <li>ALLOWER authorizers can grant access</li>
 *   <li>Final decision is made based on the combined results</li>
 * </ol>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see org.restheart.plugins.security.Authenticator
 * @see org.restheart.exchange.Request
 * @see ConfigurablePlugin
 * @see RegisterPlugin
 * @see https://restheart.org/docs/plugins/security-plugins/#authorizers
 */
public interface Authorizer extends ConfigurablePlugin {
    /**
     * Enumeration defining the two types of authorization behavior.
     * <p>
     * The authorization type determines how the authorizer participates in the
     * overall authorization decision process:
     * </p>
     * <ul>
     *   <li><strong>ALLOWER</strong> - This authorizer can grant access to resources.
     *       At least one ALLOWER must allow a request for it to be permitted.</li>
     *   <li><strong>VETOER</strong> - This authorizer can deny access to resources.
     *       If any VETOER denies a request, it will be rejected regardless of ALLOWERs.</li>
     * </ul>
     * <p>
     * <strong>Decision Logic:</strong> A secured request is allowed when no VETOER denies it
     * and at least one ALLOWER allows it. This allows for implementing both permissive
     * policies (via ALLOWERs) and restrictive policies (via VETOERs) in the same system.
     * </p>
     */
    public enum TYPE { 
        /** Authorizer can grant access - at least one ALLOWER must allow the request */
        ALLOWER, 
        /** Authorizer can deny access - any VETOER can reject the request */
        VETOER 
    }

    /**
     * Determines if the given request should be allowed access to the requested resource.
     * <p>
     * This method is the core authorization decision point where the authorizer evaluates
     * the request context, user identity, resource being accessed, and any other relevant
     * factors to determine if access should be granted.
     * </p>
     * <p>
     * The method has access to complete request information including:
     * <ul>
     *   <li>Authenticated user account (if any) via request.getAuthenticatedAccount()</li>
     *   <li>Request path and HTTP method</li>
     *   <li>Request headers and parameters</li>
     *   <li>Request content (for requests that include a body)</li>
     *   <li>Session and context information</li>
     * </ul>
     * </p>
     * <p>
     * Authorization logic can be based on various factors:
     * <ul>
     *   <li><strong>User Identity</strong> - User ID, roles, group membership</li>
     *   <li><strong>Resource Path</strong> - URL patterns, path hierarchies</li>
     *   <li><strong>HTTP Method</strong> - Different permissions for GET vs POST vs DELETE</li>
     *   <li><strong>Resource Content</strong> - Data-driven authorization decisions</li>
     *   <li><strong>Context</strong> - Time of day, IP address, session state</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Return Value Interpretation:</strong>
     * <ul>
     *   <li>For <strong>ALLOWER</strong> authorizers: true means "grant access", false means "no opinion"</li>
     *   <li>For <strong>VETOER</strong> authorizers: false means "deny access", true means "no objection"</li>
     * </ul>
     * </p>
     *
     * @param request the request to authorize, containing user context and resource information
     * @return true if this authorizer allows the request (ALLOWER) or has no objection (VETOER),
     *         false if this authorizer has no opinion (ALLOWER) or denies access (VETOER)
     */
    boolean isAllowed(final Request<?> request);

    /**
     * Determines if authentication is required for the given request.
     * <p>
     * This method allows authorizers to specify whether authentication is mandatory
     * for accessing particular resources. It provides fine-grained control over
     * which parts of the API require user authentication and which can be accessed
     * anonymously.
     * </p>
     * <p>
     * Use cases for authentication requirements:
     * <ul>
     *   <li><strong>Public Resources</strong> - Allow anonymous access to public content</li>
     *   <li><strong>Protected Resources</strong> - Require authentication for sensitive data</li>
     *   <li><strong>Mixed Access</strong> - Different requirements based on HTTP method or path</li>
     *   <li><strong>Conditional Access</strong> - Context-dependent authentication requirements</li>
     * </ul>
     * </p>
     * <p>
     * The method can examine the request to make context-aware decisions:
     * <ul>
     *   <li>Request path (e.g., /public/* vs /private/*)</li>
     *   <li>HTTP method (e.g., GET public, POST requires auth)</li>
     *   <li>Request headers or parameters</li>
     *   <li>Time-based or IP-based conditions</li>
     * </ul>
     * </p>
     * <p>
     * If multiple authorizers return different values for authentication requirements,
     * RESTHeart will require authentication if any authorizer indicates it is needed.
     * This ensures that security requirements are not accidentally bypassed.
     * </p>
     * <p>
     * Example implementations:
     * <pre>
     * // Require authentication for all non-public paths
     * public boolean isAuthenticationRequired(Request&lt;?&gt; request) {
     *     return !request.getPath().startsWith("/public");
     * }
     * 
     * // Require authentication for write operations only
     * public boolean isAuthenticationRequired(Request&lt;?&gt; request) {
     *     return !request.getMethod().equals("GET");
     * }
     * </pre>
     * </p>
     *
     * @param request the request to evaluate for authentication requirements
     * @return true if authentication is required for this request, false if anonymous access is permitted
     */
    boolean isAuthenticationRequired(final Request<?> request);
}
