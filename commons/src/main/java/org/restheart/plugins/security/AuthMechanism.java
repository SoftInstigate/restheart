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

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;
import org.restheart.plugins.ConfigurablePlugin;
import org.restheart.utils.PluginUtils;

/**
 * Interface for implementing authentication mechanisms in RESTHeart.
 * 
 * <p>AuthMechanism is responsible for extracting credentials from HTTP requests and
 * coordinating with {@link Authenticator} plugins to verify user identity. It extends
 * Undertow's {@link AuthenticationMechanism} to integrate with the web server's
 * security infrastructure.</p>
 * 
 * <h2>Purpose</h2>
 * <p>Authentication mechanisms handle the protocol-specific aspects of authentication:</p>
 * <ul>
 *   <li>Extract credentials from requests (headers, cookies, query parameters)</li>
 *   <li>Challenge clients for credentials when missing or invalid</li>
 *   <li>Coordinate with authenticators to verify credentials</li>
 *   <li>Establish security context for authenticated requests</li>
 * </ul>
 * 
 * <h2>Authentication Flow</h2>
 * <ol>
 *   <li>{@link #authenticate} is called to extract and verify credentials</li>
 *   <li>If credentials are missing or invalid, {@link #sendChallenge} is called</li>
 *   <li>The client provides credentials in the challenge response</li>
 *   <li>The process repeats until authentication succeeds or fails definitively</li>
 * </ol>
 * 
 * <h2>Implementation Example</h2>
 * <pre>{@code
 * @RegisterPlugin(
 *     name = "basicAuthMechanism",
 *     description = "HTTP Basic Authentication"
 * )
 * public class BasicAuthMechanism implements AuthMechanism {
 *     @Override
 *     public AuthenticationMechanismOutcome authenticate(
 *             HttpServerExchange exchange, 
 *             SecurityContext securityContext) {
 *         
 *         String authHeader = exchange.getRequestHeaders()
 *             .getFirst("Authorization");
 *             
 *         if (authHeader == null || !authHeader.startsWith("Basic ")) {
 *             return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
 *         }
 *         
 *         try {
 *             String credentials = new String(Base64.getDecoder()
 *                 .decode(authHeader.substring(6)));
 *             String[] parts = credentials.split(":", 2);
 *             
 *             if (parts.length == 2) {
 *                 PasswordCredential credential = 
 *                     new PasswordCredential(parts[1].toCharArray());
 *                 
 *                 Account account = securityContext.getIdentityManager()
 *                     .verify(parts[0], credential);
 *                     
 *                 if (account != null) {
 *                     securityContext.authenticationComplete(account, 
 *                         getMechanismName(), false);
 *                     return AuthenticationMechanismOutcome.AUTHENTICATED;
 *                 }
 *             }
 *         } catch (Exception e) {
 *             logger.warn("Invalid Basic auth header", e);
 *         }
 *         
 *         securityContext.authenticationFailed("Invalid credentials", 
 *             getMechanismName());
 *         return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
 *     }
 *     
 *     @Override
 *     public ChallengeResult sendChallenge(
 *             HttpServerExchange exchange, 
 *             SecurityContext securityContext) {
 *         
 *         exchange.getResponseHeaders()
 *             .add("WWW-Authenticate", "Basic realm=\"RESTHeart\"");
 *         return new ChallengeResult(true, 401);
 *     }
 * }
 * }</pre>
 * 
 * <h2>Return Values</h2>
 * <p>The {@link #authenticate} method returns:</p>
 * <ul>
 *   <li>{@code AUTHENTICATED} - Credentials verified successfully</li>
 *   <li>{@code NOT_AUTHENTICATED} - Credentials present but invalid</li>
 *   <li>{@code NOT_ATTEMPTED} - No credentials for this mechanism found</li>
 * </ul>
 * 
 * <h2>Common Authentication Mechanisms</h2>
 * <ul>
 *   <li><strong>Basic:</strong> Username/password in Authorization header</li>
 *   <li><strong>Digest:</strong> Challenge-response with hashed credentials</li>
 *   <li><strong>Bearer Token:</strong> JWT or OAuth tokens</li>
 *   <li><strong>API Key:</strong> Keys in headers or query parameters</li>
 *   <li><strong>Client Certificate:</strong> TLS client certificates</li>
 * </ul>
 * 
 * <h2>Security Considerations</h2>
 * <ul>
 *   <li>Always use HTTPS to protect credentials in transit</li>
 *   <li>Implement proper error handling to avoid information leakage</li>
 *   <li>Consider rate limiting authentication attempts</li>
 *   <li>Log authentication failures for security monitoring</li>
 * </ul>
 * 
 * @see Authenticator
 * @see AuthenticationMechanism
 * @see SecurityContext
 * @see <a href="https://restheart.org/docs/plugins/security-plugins/#authentication-mechanisms">Authentication Mechanism Documentation</a>
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface AuthMechanism extends AuthenticationMechanism, ConfigurablePlugin {
    @Override
    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange, final SecurityContext securityContext);

    @Override
    public ChallengeResult sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext);

    default String getMechanismName() {
        return PluginUtils.name(this);
    }
}
