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
 * Security plugin framework for implementing authentication and authorization in RESTHeart.
 *
 * <p>This package provides the core interfaces and components for building security plugins
 * that protect RESTHeart resources. The security framework supports pluggable authentication
 * mechanisms, flexible authorization policies, and token-based session management.</p>
 *
 * <h2>Security Architecture</h2>
 * <p>RESTHeart's security is based on a pipeline model:</p>
 * <ol>
 *   <li><strong>Authentication:</strong> Verify user identity</li>
 *   <li><strong>Authorization:</strong> Check access permissions</li>
 *   <li><strong>Token Management:</strong> Handle session tokens</li>
 * </ol>
 *
 * <h2>Security Plugin Types</h2>
 *
 * <h3>{@link org.restheart.plugins.security.AuthMechanism}</h3>
 * <p>Handles the authentication protocol (e.g., Basic Auth, JWT, OAuth2). Mechanisms extract
 * credentials from requests and pass them to authenticators for verification.</p>
 *
 * <h3>{@link org.restheart.plugins.security.Authenticator}</h3>
 * <p>Verifies credentials and creates authenticated accounts. Authenticators can validate
 * against various backends like databases, LDAP, or external services.</p>
 *
 * <h3>{@link org.restheart.plugins.security.Authorizer}</h3>
 * <p>Determines if authenticated users can access requested resources. Authorizers implement
 * access control policies based on roles, permissions, or custom logic.</p>
 *
 * <h3>{@link org.restheart.plugins.security.TokenManager}</h3>
 * <p>Manages authentication tokens for session handling. Token managers can create, validate,
 * and revoke tokens for stateless authentication.</p>
 *
 * <h2>Security Flow</h2>
 * <pre>
 * 1. Request arrives with credentials (e.g., Authorization header)
 * 2. AuthMechanism extracts credentials
 * 3. Authenticator verifies credentials and creates Account
 * 4. Authorizer checks if Account can access the resource
 * 5. TokenManager may issue/validate session tokens
 * 6. Request proceeds if authorized, returns 401/403 if not
 * </pre>
 *
 * <h2>Example Security Plugin</h2>
 * <pre>{@code
 * @RegisterPlugin(
 *     name = "custom-authenticator",
 *     description = "Authenticates users against custom database"
 * )
 * public class CustomAuthenticator implements Authenticator {
 *     @Inject("mclient")
 *     private MongoClient mongoClient;
 *
 *     @Override
 *     public Account verify(Credential credential) {
 *         if (credential instanceof PasswordCredential pwd) {
 *             // Check password against database
 *             var user = findUser(pwd.getAccount());
 *             if (user != null && checkPassword(pwd.getPassword(), user)) {
 *                 return new SimpleAccount(
 *                     user.getString("username"),
 *                     user.getList("roles", String.class)
 *                 );
 *             }
 *         }
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * <h2>Built-in Security Plugins</h2>
 * <p>RESTHeart includes several security plugins:</p>
 * <ul>
 *   <li><strong>BasicAuthMechanism:</strong> HTTP Basic Authentication</li>
 *   <li><strong>TokenBasicAuthMechanism:</strong> Token-based authentication</li>
 *   <li><strong>JwtAuthenticationMechanism:</strong> JWT token authentication</li>
 *   <li><strong>MongoRealmAuthenticator:</strong> MongoDB-based user authentication</li>
 *   <li><strong>FileRealmAuthenticator:</strong> File-based user authentication</li>
 *   <li><strong>MongoAclAuthorizer:</strong> MongoDB-based access control</li>
 *   <li><strong>FileAclAuthorizer:</strong> File-based access control</li>
 *   <li><strong>FullAuthorizer:</strong> Grants full access (development only)</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Security plugins are configured in the RESTHeart configuration file:</p>
 * <pre>{@code
 * auth-mechanisms:
 *   - name: basicAuthMechanism
 *     enabled: true
 *
 * authenticators:
 *   - name: mongoRealmAuthenticator
 *     enabled: true
 *
 * authorizers:
 *   - name: mongoAclAuthorizer
 *     enabled: true
 *
 * token-managers:
 *   - name: rndTokenManager
 *     enabled: true
 * }</pre>
 *
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Always use HTTPS in production for secure credential transmission</li>
 *   <li>Implement proper password hashing (e.g., bcrypt, argon2)</li>
 *   <li>Use token expiration for session management</li>
 *   <li>Follow the principle of least privilege in authorization</li>
 *   <li>Log security events for auditing</li>
 *   <li>Handle authentication failures without revealing user existence</li>
 * </ul>
 *
 * @see org.restheart.plugins.Plugin
 * @see org.restheart.security
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
package org.restheart.plugins.security;
