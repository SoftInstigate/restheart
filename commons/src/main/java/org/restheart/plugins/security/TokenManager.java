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

import io.undertow.security.idm.Account;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * Interface for implementing RESTHeart token managers that handle authentication tokens.
 * <p>
 * Token managers are specialized authenticators that provide stateless authentication
 * through the use of cryptographic tokens. They generate, validate, and manage tokens
 * that represent authenticated user sessions, eliminating the need for server-side
 * session storage and enabling scalable, distributed authentication.
 * </p>
 * <p>
 * Token managers extend the {@link Authenticator} interface and add token-specific
 * functionality for:
 * <ul>
 *   <li><strong>Token Generation</strong> - Creating secure tokens for authenticated users</li>
 *   <li><strong>Token Validation</strong> - Verifying token authenticity and extracting user information</li>
 *   <li><strong>Token Invalidation</strong> - Revoking tokens when users log out or sessions expire</li>
 *   <li><strong>Token Updates</strong> - Refreshing tokens with updated user information</li>
 *   <li><strong>Header Injection</strong> - Adding token-related headers to HTTP responses</li>
 * </ul>
 * </p>
 * <p>
 * Common token implementations include:
 * <ul>
 *   <li><strong>JWT (JSON Web Tokens)</strong> - Self-contained, cryptographically signed tokens</li>
 *   <li><strong>Random Tokens</strong> - Server-generated random strings with server-side validation</li>
 *   <li><strong>Encrypted Tokens</strong> - Tokens containing encrypted user information</li>
 *   <li><strong>Hybrid Tokens</strong> - Combination of different token strategies</li>
 * </ul>
 * </p>
 * <p>
 * Example implementation:
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "jwtTokenManager",
 *     description = "JWT-based token manager"
 * )
 * public class JwtTokenManager implements TokenManager {
 *     &#64;Override
 *     public PasswordCredential get(Account account) {
 *         String token = generateJWT(account);
 *         return new PasswordCredential(token.toCharArray());
 *     }
 *     
 *     &#64;Override
 *     public Account verify(String id, Credential credential) {
 *         if (credential instanceof PasswordCredential) {
 *             String token = new String(((PasswordCredential) credential).getPassword());
 *             return validateJWT(token);
 *         }
 *         return null;
 *     }
 *     
 *     &#64;Override
 *     public void injectTokenHeaders(HttpServerExchange exchange, PasswordCredential token) {
 *         exchange.getResponseHeaders()
 *             .put(AUTH_TOKEN_HEADER, new String(token.getPassword()))
 *             .put(AUTH_TOKEN_VALID_HEADER, getExpirationTime(token));
 *     }
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Integration with Security Pipeline:</strong>
 * <ol>
 *   <li>User authenticates with initial credentials (username/password)</li>
 *   <li>Token manager generates a token for the authenticated user</li>
 *   <li>Token is returned to client via response headers</li>
 *   <li>Client includes token in subsequent requests</li>
 *   <li>Token manager validates tokens and extracts user information</li>
 *   <li>Tokens can be refreshed, updated, or invalidated as needed</li>
 * </ol>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Authenticator
 * @see io.undertow.security.idm.Account
 * @see io.undertow.security.idm.PasswordCredential
 * @see https://restheart.org/docs/plugins/security-plugins/#token-managers
 */
public interface TokenManager extends Authenticator {
    /** HTTP header name for transmitting authentication tokens to clients */
    public static final HttpString AUTH_TOKEN_HEADER = HttpString.tryFromString("Auth-Token");
    
    /** HTTP header name for communicating token expiration time to clients */
    public static final HttpString AUTH_TOKEN_VALID_HEADER = HttpString.tryFromString("Auth-Token-Valid-Until");
    
    /** HTTP header name for indicating where clients should send tokens for validation */
    public static final HttpString AUTH_TOKEN_LOCATION_HEADER = HttpString.tryFromString("Auth-Token-Location");
    
    /** HTTP header name for exposing token-related headers in CORS scenarios */
    public static final HttpString ACCESS_CONTROL_EXPOSE_HEADERS = HttpString.tryFromString("Access-Control-Expose-Headers");
    /**
     * Retrieves or generates a valid authentication token for the specified user account.
     * <p>
     * This method is called when a user successfully authenticates and needs a token
     * for subsequent requests. The token manager can either:
     * <ul>
     *   <li>Generate a new token containing the user's identity and roles</li>
     *   <li>Retrieve an existing valid token for the user (if token reuse is supported)</li>
     *   <li>Refresh an expired token with updated information</li>
     * </ul>
     * </p>
     * <p>
     * The returned token should:
     * <ul>
     *   <li>Uniquely identify the user account</li>
     *   <li>Include necessary user roles and permissions</li>
     *   <li>Have appropriate expiration settings</li>
     *   <li>Be cryptographically secure and tamper-evident</li>
     * </ul>
     * </p>
     * <p>
     * Token format considerations:
     * <ul>
     *   <li><strong>Self-contained tokens (JWT)</strong> - Include all user information in the token</li>
     *   <li><strong>Reference tokens</strong> - Use token as a key to lookup user information</li>
     *   <li><strong>Encrypted tokens</strong> - Protect sensitive user data within the token</li>
     * </ul>
     * </p>
     *
     * @param account the authenticated user account for which to generate/retrieve a token
     * @return a PasswordCredential containing the token as a char array, or null if token generation fails
     */
    public PasswordCredential get(final Account account);

    /**
     * Invalidates any tokens associated with the specified user account.
     * <p>
     * This method is called when a user logs out, when their account is disabled,
     * or when tokens need to be revoked for security reasons. The token manager
     * should ensure that any existing tokens for this account are no longer valid
     * and will be rejected in future authentication attempts.
     * </p>
     * <p>
     * Invalidation strategies include:
     * <ul>
     *   <li><strong>Blacklist Management</strong> - Add tokens to a revocation blacklist</li>
     *   <li><strong>Database Updates</strong> - Mark tokens as invalid in persistent storage</li>
     *   <li><strong>Cache Invalidation</strong> - Remove tokens from validation caches</li>
     *   <li><strong>Key Rotation</strong> - Change signing keys to invalidate all tokens</li>
     * </ul>
     * </p>
     * <p>
     * For stateless tokens (like JWTs), invalidation may require maintaining
     * a blacklist or implementing short token lifespans with refresh mechanisms.
     * For stateful tokens, the token record can be directly removed or marked invalid.
     * </p>
     * <p>
     * This method should be idempotent - calling it multiple times for the same
     * account should not cause errors or side effects.
     * </p>
     *
     * @param account the user account whose tokens should be invalidated
     */
    public void invalidate(final Account account);

    /**
     * Updates the user information associated with existing tokens for the account.
     * <p>
     * This method is called when user account information changes (such as roles,
     * permissions, or profile data) and existing tokens need to reflect these
     * updates. The token manager should update or refresh tokens to include the
     * new account information without requiring the user to re-authenticate.
     * </p>
     * <p>
     * Update strategies include:
     * <ul>
     *   <li><strong>Token Refresh</strong> - Generate new tokens with updated information</li>
     *   <li><strong>Database Updates</strong> - Update stored token metadata</li>
     *   <li><strong>Cache Updates</strong> - Refresh cached user information for tokens</li>
     *   <li><strong>Lazy Updates</strong> - Update tokens on next validation attempt</li>
     * </ul>
     * </p>
     * <p>
     * Common scenarios for account updates:
     * <ul>
     *   <li>User role changes (promotion, demotion, role assignment)</li>
     *   <li>Permission modifications</li>
     *   <li>Profile information updates</li>
     *   <li>Security attribute changes</li>
     * </ul>
     * </p>
     * <p>
     * For self-contained tokens (like JWTs), this may require invalidating
     * existing tokens and issuing new ones. For reference tokens, the associated
     * user data can be updated in the backing store.
     * </p>
     *
     * @param account the user account with updated information to be reflected in tokens
     */
    public void update(final Account account);

    /**
     * Injects token-related headers into the HTTP response.
     * <p>
     * This method is called to add authentication token information to HTTP responses,
     * allowing clients to receive and store tokens for subsequent requests. The method
     * should set appropriate headers that inform clients about token usage, expiration,
     * and validation requirements.
     * </p>
     * <p>
     * Common headers to inject include:
     * <ul>
     *   <li><strong>Auth-Token</strong> - The actual token value</li>
     *   <li><strong>Auth-Token-Valid-Until</strong> - Token expiration timestamp</li>
     *   <li><strong>Auth-Token-Location</strong> - Endpoint for token validation</li>
     *   <li><strong>Access-Control-Expose-Headers</strong> - CORS header exposure for web clients</li>
     * </ul>
     * </p>
     * <p>
     * CORS considerations:
     * When serving web applications, ensure that token headers are exposed through
     * the Access-Control-Expose-Headers header so that JavaScript clients can
     * access the token information.
     * </p>
     * <p>
     * Security considerations:
     * <ul>
     *   <li>Use secure transmission (HTTPS) for token headers</li>
     *   <li>Set appropriate cache control headers</li>
     *   <li>Consider token sensitivity when logging or debugging</li>
     *   <li>Implement proper header sanitization</li>
     * </ul>
     * </p>
     * <p>
     * Example header injection:
     * <pre>
     * exchange.getResponseHeaders()
     *     .put(AUTH_TOKEN_HEADER, tokenValue)
     *     .put(AUTH_TOKEN_VALID_HEADER, expirationTime)
     *     .put(ACCESS_CONTROL_EXPOSE_HEADERS, "Auth-Token, Auth-Token-Valid-Until");
     * </pre>
     * </p>
     *
     * @param exchange the HTTP server exchange to add headers to
     * @param token the authentication token to include in the response headers
     */
    public void injectTokenHeaders(final HttpServerExchange exchange, final PasswordCredential token);
}
