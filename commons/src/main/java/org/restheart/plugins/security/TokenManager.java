/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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
 * Interface for managing authentication tokens in RESTHeart.
 * 
 * <p>TokenManager extends {@link Authenticator} to provide token-based authentication
 * capabilities. It handles the lifecycle of authentication tokens including generation,
 * validation, invalidation, and renewal. This enables stateless authentication for
 * RESTful APIs.</p>
 * 
 * <h2>Purpose</h2>
 * <p>Token managers are responsible for:</p>
 * <ul>
 *   <li>Generating authentication tokens after successful login</li>
 *   <li>Validating tokens on subsequent requests</li>
 *   <li>Managing token expiration and renewal</li>
 *   <li>Invalidating tokens on logout</li>
 *   <li>Injecting token information into HTTP responses</li>
 * </ul>
 * 
 * <h2>Token Flow</h2>
 * <ol>
 *   <li>User authenticates with credentials (username/password)</li>
 *   <li>TokenManager generates a token via {@link #get(Account)}</li>
 *   <li>Token is returned to client in response headers</li>
 *   <li>Client includes token in subsequent requests</li>
 *   <li>TokenManager validates token via {@link #verify(Credential)}</li>
 *   <li>Token can be invalidated via {@link #invalidate(Account)}</li>
 * </ol>
 * 
 * <h2>Implementation Example</h2>
 * <pre>{@code
 * @RegisterPlugin(
 *     name = "jwtTokenManager",
 *     description = "JWT-based token manager"
 * )
 * public class JwtTokenManager implements TokenManager {
 *     private final String secret = "your-secret-key";
 *     private final long ttl = 3600000; // 1 hour
 *     
 *     @Override
 *     public PasswordCredential get(Account account) {
 *         String token = JWT.create()
 *             .withSubject(account.getPrincipal().getName())
 *             .withClaim("roles", account.getRoles())
 *             .withExpiresAt(new Date(System.currentTimeMillis() + ttl))
 *             .sign(Algorithm.HMAC256(secret));
 *             
 *         return new PasswordCredential(token.toCharArray());
 *     }
 *     
 *     @Override
 *     public Account verify(Credential credential) {
 *         if (!(credential instanceof PasswordCredential)) {
 *             return null;
 *         }
 *         
 *         String token = new String(((PasswordCredential) credential).getPassword());
 *         
 *         try {
 *             DecodedJWT jwt = JWT.require(Algorithm.HMAC256(secret))
 *                 .build()
 *                 .verify(token);
 *                 
 *             String username = jwt.getSubject();
 *             Set<String> roles = new HashSet<>(jwt.getClaim("roles").asList(String.class));
 *             
 *             return new JwtAccount(username, roles);
 *         } catch (JWTVerificationException e) {
 *             return null;
 *         }
 *     }
 *     
 *     @Override
 *     public void injectTokenHeaders(HttpServerExchange exchange, PasswordCredential token) {
 *         exchange.getResponseHeaders()
 *             .put(AUTH_TOKEN_HEADER, new String(token.getPassword()))
 *             .put(AUTH_TOKEN_VALID_HEADER, String.valueOf(System.currentTimeMillis() + ttl))
 *             .put(AUTH_TOKEN_LOCATION_HEADER, "/tokens/" + account.getPrincipal().getName());
 *     }
 * }
 * }</pre>
 * 
 * <h2>Token Types</h2>
 * <p>Common token implementations include:</p>
 * <ul>
 *   <li><strong>JWT:</strong> Self-contained tokens with embedded claims</li>
 *   <li><strong>UUID:</strong> Random tokens stored in cache or database</li>
 *   <li><strong>Signed Cookies:</strong> Tokens stored in HTTP cookies</li>
 *   <li><strong>OAuth 2.0:</strong> Standards-based token management</li>
 * </ul>
 * 
 * <h2>Security Considerations</h2>
 * <ul>
 *   <li>Use strong secrets for token signing</li>
 *   <li>Implement appropriate token expiration times</li>
 *   <li>Store sensitive tokens securely (not in localStorage)</li>
 *   <li>Use HTTPS to prevent token interception</li>
 *   <li>Implement token rotation for enhanced security</li>
 *   <li>Consider implementing refresh tokens for long-lived sessions</li>
 * </ul>
 * 
 * @see Authenticator
 * @see Account
 * @see <a href="https://restheart.org/docs/plugins/security-plugins/#token-managers">Token Manager Documentation</a>
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface TokenManager extends Authenticator {
    public static final HttpString AUTH_TOKEN_HEADER = HttpString.tryFromString("Auth-Token");
    public static final HttpString AUTH_TOKEN_VALID_HEADER = HttpString.tryFromString("Auth-Token-Valid-Until");
    public static final HttpString AUTH_TOKEN_LOCATION_HEADER = HttpString.tryFromString("Auth-Token-Location");
    public static final HttpString ACCESS_CONTROL_EXPOSE_HEADERS = HttpString.tryFromString("Access-Control-Expose-Headers");
    /**
     * Retrieves or generates a token valid for the account.
     * 
     * <p>This method is called after successful authentication to create a token
     * that the client can use for subsequent requests. The token should encode
     * enough information to identify the user and their permissions.</p>
     * 
     * <p>Implementation considerations:</p>
     * <ul>
     *   <li>Include user identity and roles in the token</li>
     *   <li>Set appropriate expiration time</li>
     *   <li>Sign or encrypt tokens to prevent tampering</li>
     *   <li>Consider including a unique token ID for revocation</li>
     * </ul>
     * 
     * @param account the authenticated account to generate a token for
     * @return a PasswordCredential containing the token as a char array
     */
    public PasswordCredential get(final Account account);

    /**
     * Invalidates the token bound to the account.
     * 
     * <p>This method is typically called during logout or when revoking access.
     * After invalidation, any requests using the invalidated token should be
     * rejected.</p>
     * 
     * <p>Implementation strategies:</p>
     * <ul>
     *   <li>For JWT: Add token to a blacklist until expiration</li>
     *   <li>For UUID tokens: Remove from token store</li>
     *   <li>For stateful tokens: Mark as invalid in database</li>
     * </ul>
     * 
     * @param account the account whose token should be invalidated
     */
    public void invalidate(final Account account);

    /**
     * Updates the account information bound to a token.
     * 
     * <p>This method is called when account details change (e.g., roles modified,
     * profile updated) and the token needs to reflect these changes. Depending on
     * the token type, this might involve issuing a new token or updating stored
     * token metadata.</p>
     * 
     * <p>Use cases:</p>
     * <ul>
     *   <li>User role changes require new permissions in token</li>
     *   <li>Profile updates that affect token claims</li>
     *   <li>Extending token expiration time</li>
     * </ul>
     * 
     * @param account the account with updated information
     */
    public void update(final Account account);

    /**
     * Injects the token headers in the HTTP response.
     * 
     * <p>This method adds token-related headers to the response after successful
     * authentication. The standard headers include the token itself, expiration time,
     * and location for token management.</p>
     * 
     * <p>Standard headers set by this method:</p>
     * <ul>
     *   <li>{@link #AUTH_TOKEN_HEADER} - The authentication token</li>
     *   <li>{@link #AUTH_TOKEN_VALID_HEADER} - Token expiration timestamp</li>
     *   <li>{@link #AUTH_TOKEN_LOCATION_HEADER} - URI for token management</li>
     *   <li>{@link #ACCESS_CONTROL_EXPOSE_HEADERS} - CORS header to expose custom headers</li>
     * </ul>
     * 
     * <p>Example response headers:</p>
     * <pre>
     * Auth-Token: eyJhbGciOiJIUzI1NiIs...
     * Auth-Token-Valid-Until: 1634567890000
     * Auth-Token-Location: /tokens/current
     * Access-Control-Expose-Headers: Auth-Token, Auth-Token-Valid-Until, Auth-Token-Location
     * </pre>
     * 
     * @param exchange the HTTP server exchange to add headers to
     * @param token the token credential to include in the response
     */
    public void injectTokenHeaders(final HttpServerExchange exchange, final PasswordCredential token);
}
