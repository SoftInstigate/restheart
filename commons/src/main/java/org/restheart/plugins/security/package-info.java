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

/**
 * Security plugin framework for RESTHeart authentication, authorization, and token management.
 * <p>
 * This package contains the core interfaces and implementations for RESTHeart's security system,
 * enabling developers to create custom authentication mechanisms, authenticators, authorizers,
 * and token managers. The security framework provides a flexible, extensible architecture
 * that supports various authentication schemes and authorization policies.
 * </p>
 * 
 * <h2>Security Plugin Types</h2>
 * <p>
 * The security framework defines four main types of security plugins that work together
 * to provide comprehensive security coverage:
 * </p>
 * 
 * <h3>Authentication Mechanisms</h3>
 * <p>
 * Authentication mechanisms extract credentials from HTTP requests and determine whether
 * authentication challenges should be sent. They implement the {@link org.restheart.plugins.security.AuthMechanism}
 * interface and handle various authentication schemes:
 * </p>
 * <ul>
 *   <li><strong>Basic Authentication</strong> - Username/password via HTTP Basic auth</li>
 *   <li><strong>Bearer Token</strong> - Token-based authentication via Authorization header</li>
 *   <li><strong>Custom Headers</strong> - Authentication via custom HTTP headers</li>
 *   <li><strong>Cookies</strong> - Session-based authentication</li>
 *   <li><strong>Client Certificates</strong> - Certificate-based authentication</li>
 * </ul>
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "myAuthMechanism",
 *     description = "Custom authentication mechanism"
 * )
 * public class MyAuthMechanism implements AuthMechanism {
 *     &#64;Override
 *     public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {
 *         // Extract and validate credentials
 *         return AuthenticationMechanismOutcome.AUTHENTICATED;
 *     }
 *     
 *     &#64;Override
 *     public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
 *         // Send authentication challenge
 *         return new ChallengeResult(true, 401);
 *     }
 * }
 * </pre>
 * 
 * <h3>Authenticators</h3>
 * <p>
 * Authenticators verify credentials extracted by authentication mechanisms and create user accounts.
 * They implement the {@link org.restheart.plugins.security.Authenticator} interface and can integrate
 * with various identity stores:
 * </p>
 * <ul>
 *   <li><strong>Database Authenticators</strong> - Verify against database user tables</li>
 *   <li><strong>LDAP Authenticators</strong> - Authenticate against LDAP/Active Directory</li>
 *   <li><strong>File-based Authenticators</strong> - Use configuration files for users</li>
 *   <li><strong>JWT Authenticators</strong> - Validate and decode JWT tokens</li>
 *   <li><strong>External API Authenticators</strong> - Delegate to external services</li>
 * </ul>
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "myAuthenticator",
 *     description = "Custom credential authenticator"
 * )
 * public class MyAuthenticator implements Authenticator {
 *     &#64;Override
 *     public Account verify(String id, Credential credential) {
 *         // Verify credentials and return user account
 *         return new UserAccount(id, roles);
 *     }
 * }
 * </pre>
 * 
 * <h3>Authorizers</h3>
 * <p>
 * Authorizers make access control decisions after authentication is complete. They implement
 * the {@link org.restheart.plugins.security.Authorizer} interface and support two types:
 * </p>
 * <ul>
 *   <li><strong>ALLOWER</strong> - Can grant access to resources (positive authorization)</li>
 *   <li><strong>VETOER</strong> - Can deny access to resources (negative authorization)</li>
 * </ul>
 * <p>
 * <strong>Authorization Logic:</strong> A request is allowed when no VETOER denies it AND at least one ALLOWER allows it.
 * </p>
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "roleAuthorizer",
 *     description = "Role-based access control",
 *     authorizerType = Authorizer.TYPE.ALLOWER
 * )
 * public class RoleAuthorizer implements Authorizer {
 *     &#64;Override
 *     public boolean isAllowed(Request&lt;?&gt; request) {
 *         Account account = request.getAuthenticatedAccount();
 *         return account != null && account.getRoles().contains("admin");
 *     }
 *     
 *     &#64;Override
 *     public boolean isAuthenticationRequired(Request&lt;?&gt; request) {
 *         return !request.getPath().startsWith("/public");
 *     }
 * }
 * </pre>
 * 
 * <h3>Token Managers</h3>
 * <p>
 * Token managers provide stateless authentication through cryptographic tokens. They extend
 * the {@link org.restheart.plugins.security.Authenticator} interface with token-specific functionality:
 * </p>
 * <ul>
 *   <li><strong>Token Generation</strong> - Create secure tokens for authenticated users</li>
 *   <li><strong>Token Validation</strong> - Verify token authenticity and extract user info</li>
 *   <li><strong>Token Invalidation</strong> - Revoke tokens for logout or security</li>
 *   <li><strong>Token Updates</strong> - Refresh tokens with updated user information</li>
 * </ul>
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "jwtTokenManager",
 *     description = "JWT-based token management"
 * )
 * public class JwtTokenManager implements TokenManager {
 *     &#64;Override
 *     public PasswordCredential get(Account account) {
 *         String token = generateJWT(account);
 *         return new PasswordCredential(token.toCharArray());
 *     }
 *     
 *     &#64;Override
 *     public void injectTokenHeaders(HttpServerExchange exchange, PasswordCredential token) {
 *         exchange.getResponseHeaders().put(AUTH_TOKEN_HEADER, new String(token.getPassword()));
 *     }
 * }
 * </pre>
 * 
 * <h2>Security Pipeline</h2>
 * <p>
 * The security components work together in a well-defined pipeline:
 * </p>
 * <ol>
 *   <li><strong>Authentication Mechanism</strong> - Extracts credentials from HTTP request</li>
 *   <li><strong>Authenticator</strong> - Verifies credentials and creates user account</li>
 *   <li><strong>Authorizer</strong> - Makes access control decisions</li>
 *   <li><strong>Request Processing</strong> - Service or proxy handles the request</li>
 *   <li><strong>Token Manager</strong> - Manages tokens for subsequent requests (if applicable)</li>
 * </ol>
 * 
 * <h2>Configuration</h2>
 * <p>
 * Security plugins are configured through the RESTHeart configuration file:
 * </p>
 * <pre>
 * # restheart.yml
 * plugins-args:
 *   myAuthenticator:
 *     enabled: true
 *     database: "userdb"
 *     collection: "users"
 *   
 *   myAuthorizer:
 *     enabled: true
 *     adminRoles: ["admin", "superuser"]
 * </pre>
 * 
 * <h2>Multiple Plugins</h2>
 * <p>
 * RESTHeart supports multiple security plugins of each type:
 * </p>
 * <ul>
 *   <li><strong>Authentication Mechanisms</strong> - Tried in priority order until one succeeds</li>
 *   <li><strong>Authenticators</strong> - Used by authentication mechanisms for credential verification</li>
 *   <li><strong>Authorizers</strong> - All are consulted for authorization decisions</li>
 *   <li><strong>Token Managers</strong> - Typically one active token manager per deployment</li>
 * </ul>
 * 
 * <h2>Integration with Main Framework</h2>
 * <p>
 * Security plugins integrate seamlessly with the main RESTHeart plugin framework:
 * </p>
 * <ul>
 *   <li>Use {@link org.restheart.plugins.RegisterPlugin} for registration</li>
 *   <li>Support {@link org.restheart.plugins.ConfigurablePlugin} for configuration</li>
 *   <li>Participate in dependency injection via {@link org.restheart.plugins.Inject}</li>
 *   <li>Follow the same lifecycle management as other plugins</li>
 * </ul>
 * 
 * <h2>Best Practices</h2>
 * <ul>
 *   <li><strong>Fail Securely</strong> - Default to denying access when in doubt</li>
 *   <li><strong>Validate Input</strong> - Always validate credentials and authorization data</li>
 *   <li><strong>Log Security Events</strong> - Log authentication and authorization decisions</li>
 *   <li><strong>Use Standard Algorithms</strong> - Prefer established cryptographic standards</li>
 *   <li><strong>Handle Errors Gracefully</strong> - Provide appropriate error responses without leaking information</li>
 * </ul>
 * 
 * <h2>Examples and Documentation</h2>
 * <p>
 * For complete examples and detailed documentation, see:
 * <a href="https://restheart.org/docs/plugins/security-plugins/">https://restheart.org/docs/plugins/security-plugins/</a>
 * </p>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see org.restheart.plugins.security.AuthMechanism
 * @see org.restheart.plugins.security.Authenticator
 * @see org.restheart.plugins.security.Authorizer
 * @see org.restheart.plugins.security.TokenManager
 * @see org.restheart.plugins
 */
package org.restheart.plugins.security;
