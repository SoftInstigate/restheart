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
 * Interface for implementing authorization logic in RESTHeart.
 * 
 * <p>Authorizer plugins determine whether authenticated users have permission to access
 * specific resources or perform certain operations. They work in conjunction with
 * {@link AuthMechanism} and {@link Authenticator} to provide complete security coverage.</p>
 * 
 * <h2>Purpose</h2>
 * <p>Authorizers are responsible for:</p>
 * <ul>
 *   <li>Evaluating access control policies</li>
 *   <li>Checking user roles and permissions</li>
 *   <li>Implementing resource-level security</li>
 *   <li>Determining if authentication is required for specific resources</li>
 * </ul>
 * 
 * <h2>Authorization Model</h2>
 * <p>RESTHeart uses a two-type authorization model:</p>
 * <ul>
 *   <li><strong>ALLOWER:</strong> Grants access to resources</li>
 *   <li><strong>VETOER:</strong> Denies access to resources</li>
 * </ul>
 * <p>A request is authorized when no VETOER denies it AND at least one ALLOWER allows it.</p>
 * 
 * <h2>Implementation Example</h2>
 * <pre>{@code
 * @RegisterPlugin(
 *     name = "roleBasedAuthorizer",
 *     description = "Authorizes based on user roles",
 *     authorizerType = Authorizer.TYPE.ALLOWER
 * )
 * public class RoleBasedAuthorizer implements Authorizer {
 *     @Inject("config")
 *     private Map<String, Object> config;
 *     
 *     private Map<String, Set<String>> rolePermissions;
 *     
 *     @OnInit
 *     public void init() {
 *         rolePermissions = loadRolePermissions(config);
 *     }
 *     
 *     @Override
 *     public boolean isAllowed(Request<?> request) {
 *         var account = request.getAuthenticatedAccount();
 *         if (account == null) {
 *             return false;
 *         }
 *         
 *         String method = request.getMethod();
 *         String path = request.getPath();
 *         
 *         // Check if any user role has permission for this request
 *         return account.getRoles().stream()
 *             .map(role -> rolePermissions.get(role))
 *             .filter(Objects::nonNull)
 *             .flatMap(Set::stream)
 *             .anyMatch(permission -> matchesPermission(permission, method, path));
 *     }
 *     
 *     @Override
 *     public boolean isAuthenticationRequired(Request<?> request) {
 *         // Require authentication for all requests except public endpoints
 *         return !request.getPath().startsWith("/public");
 *     }
 * }
 * }</pre>
 * 
 * <h2>VETOER Example</h2>
 * <pre>{@code
 * @RegisterPlugin(
 *     name = "ipBlocker",
 *     description = "Blocks requests from blacklisted IPs",
 *     authorizerType = Authorizer.TYPE.VETOER
 * )
 * public class IpBlockerAuthorizer implements Authorizer {
 *     private Set<String> blockedIps;
 *     
 *     @Override
 *     public boolean isAllowed(Request<?> request) {
 *         String clientIp = request.getRemoteIp();
 *         // VETOER returns false to deny access
 *         return !blockedIps.contains(clientIp);
 *     }
 *     
 *     @Override
 *     public boolean isAuthenticationRequired(Request<?> request) {
 *         // This VETOER doesn't affect authentication requirements
 *         return false;
 *     }
 * }
 * }</pre>
 * 
 * <h2>Multiple Authorizers</h2>
 * <p>RESTHeart supports multiple authorizers working together:</p>
 * <ol>
 *   <li>All VETOER authorizers are checked first - any denial blocks access</li>
 *   <li>If no VETOER denies, at least one ALLOWER must grant access</li>
 *   <li>Authentication is required if ANY authorizer requires it</li>
 * </ol>
 * 
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Use ALLOWER for positive permission grants (role-based, attribute-based)</li>
 *   <li>Use VETOER for blocklists, rate limiting, or security rules</li>
 *   <li>Keep authorization logic simple and performant</li>
 *   <li>Cache authorization decisions when appropriate</li>
 *   <li>Log authorization failures for security monitoring</li>
 * </ul>
 * 
 * @see AuthMechanism
 * @see Authenticator
 * @see TYPE
 * @see <a href="https://restheart.org/docs/plugins/security-plugins/#authorizers">Authorizer Documentation</a>
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface Authorizer extends ConfigurablePlugin {
    /**
     * Defines the authorization behavior type.
     * 
     * <p>This enum determines how an authorizer's decision affects the overall
     * authorization outcome. RESTHeart uses a two-phase authorization process
     * where VETOERs can deny access and ALLOWERs can grant access.</p>
     * 
     * <h3>Authorization Logic</h3>
     * <p>A request is authorized when:</p>
     * <ol>
     *   <li>No VETOER denies it (all VETOERs return true)</li>
     *   <li>At least one ALLOWER allows it (any ALLOWER returns true)</li>
     * </ol>
     * 
     * <h3>Usage Example</h3>
     * <pre>{@code
     * @RegisterPlugin(
     *     name = "myAuthorizer",
     *     authorizerType = Authorizer.TYPE.ALLOWER  // or TYPE.VETOER
     * )
     * public class MyAuthorizer implements Authorizer {
     *     // Implementation
     * }
     * }</pre>
     */
    public enum TYPE { 
        /**
         * Grants access to resources.
         * 
         * <p>ALLOWER authorizers implement positive authorization logic. They grant
         * access based on roles, permissions, or other attributes. At least one
         * ALLOWER must return true for a request to be authorized.</p>
         * 
         * <p>Common uses:</p>
         * <ul>
         *   <li>Role-based access control (RBAC)</li>
         *   <li>Attribute-based access control (ABAC)</li>
         *   <li>Resource ownership checks</li>
         *   <li>Group membership verification</li>
         * </ul>
         */
        ALLOWER, 
        
        /**
         * Denies access to resources.
         * 
         * <p>VETOER authorizers implement negative authorization logic. They can
         * block access regardless of what ALLOWERs say. If any VETOER returns
         * false, the request is denied.</p>
         * 
         * <p>Common uses:</p>
         * <ul>
         *   <li>IP address blocklists</li>
         *   <li>Rate limiting</li>
         *   <li>Time-based restrictions</li>
         *   <li>Security policy enforcement</li>
         *   <li>Compliance rules</li>
         * </ul>
         */
        VETOER 
    }

    /**
     * Determines if the request is allowed based on this authorizer's logic.
     * 
     * <p>The meaning of the return value depends on the authorizer type:</p>
     * <ul>
     *   <li><strong>ALLOWER:</strong> Returns true to grant access</li>
     *   <li><strong>VETOER:</strong> Returns false to deny access</li>
     * </ul>
     * 
     * <p>The request object contains all information needed for authorization decisions:</p>
     * <ul>
     *   <li>Authenticated account with roles and properties</li>
     *   <li>HTTP method and URI path</li>
     *   <li>Request headers and parameters</li>
     *   <li>Client IP address</li>
     * </ul>
     * 
     * @param request the request to authorize, containing authentication info and request details
     * @return true if the request is allowed by this authorizer's logic
     */
    boolean isAllowed(final Request<?> request);

    /**
     * Determines if authentication is required for the given request.
     * 
     * <p>This method is called before authentication occurs to determine if the request
     * can proceed without authentication. If any authorizer returns true, authentication
     * will be enforced.</p>
     * 
     * <p>Common patterns:</p>
     * <ul>
     *   <li>Return false for public endpoints (e.g., /public/*, /health)</li>
     *   <li>Return true for protected resources</li>
     *   <li>Check request method (e.g., require auth for POST but not GET)</li>
     * </ul>
     * 
     * <p>Example implementation:</p>
     * <pre>{@code
     * @Override
     * public boolean isAuthenticationRequired(Request<?> request) {
     *     // Allow anonymous access to public endpoints
     *     if (request.getPath().startsWith("/public")) {
     *         return false;
     *     }
     *     // Allow anonymous GET to documentation
     *     if (request.isGet() && request.getPath().equals("/docs")) {
     *         return false;
     *     }
     *     // All other requests require authentication
     *     return true;
     * }
     * }</pre>
     * 
     * @param request the request to check
     * @return true if authentication is required for this request
     */
    boolean isAuthenticationRequired(final Request<?> request);
}
