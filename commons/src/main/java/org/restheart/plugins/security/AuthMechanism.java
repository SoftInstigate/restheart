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

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;
import org.restheart.plugins.ConfigurablePlugin;
import org.restheart.utils.PluginUtils;

/**
 * Interface for implementing RESTHeart authentication mechanisms.
 * <p>
 * Authentication mechanisms are responsible for extracting authentication credentials
 * from HTTP requests and determining whether a request should be challenged for
 * authentication. They serve as the first step in the RESTHeart security pipeline,
 * working in conjunction with Authenticators to verify user identity.
 * </p>
 * <p>
 * Authentication mechanisms handle various authentication schemes such as:
 * <ul>
 *   <li><strong>Basic Authentication</strong> - Username/password via HTTP Basic auth</li>
 *   <li><strong>Bearer Token Authentication</strong> - Token-based authentication via Authorization header</li>
 *   <li><strong>Custom Header Authentication</strong> - Authentication via custom HTTP headers</li>
 *   <li><strong>Cookie Authentication</strong> - Session-based authentication via cookies</li>
 *   <li><strong>Certificate Authentication</strong> - Client certificate-based authentication</li>
 * </ul>
 * </p>
 * <p>
 * The authentication mechanism is responsible for:
 * <ol>
 *   <li>Examining incoming HTTP requests for authentication credentials</li>
 *   <li>Extracting credentials and creating appropriate Credential objects</li>
 *   <li>Delegating credential verification to the configured Authenticator</li>
 *   <li>Sending authentication challenges when credentials are missing or invalid</li>
 * </ol>
 * </p>
 * <p>
 * Example implementation:
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "myAuthMechanism",
 *     description = "Custom authentication mechanism"
 * )
 * public class MyAuthMechanism implements AuthMechanism {
 *     &#64;Override
 *     public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {
 *         // Extract credentials from request
 *         String token = extractToken(exchange);
 *         if (token != null) {
 *             // Attempt authentication
 *             Account account = securityContext.getIdentityManager().verify(new TokenCredential(token));
 *             if (account != null) {
 *                 securityContext.authenticationComplete(account, getMechanismName(), false);
 *                 return AuthenticationMechanismOutcome.AUTHENTICATED;
 *             }
 *         }
 *         return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
 *     }
 *     
 *     &#64;Override
 *     public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
 *         // Send appropriate challenge response
 *         exchange.getResponseHeaders().put(Headers.WWW_AUTHENTICATE, "Bearer realm=\"api\"");
 *         return new ChallengeResult(true, 401);
 *     }
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Integration with Security Pipeline:</strong>
 * <ol>
 *   <li>RESTHeart receives an HTTP request</li>
 *   <li>Authentication mechanisms are tried in priority order</li>
 *   <li>Each mechanism examines the request for its specific credential type</li>
 *   <li>If credentials are found, they are passed to the Authenticator for verification</li>
 *   <li>If authentication succeeds, the user account is attached to the security context</li>
 *   <li>If authentication fails or no credentials are found, challenges may be sent</li>
 * </ol>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see org.restheart.plugins.security.Authenticator
 * @see io.undertow.security.api.AuthenticationMechanism
 * @see ConfigurablePlugin
 * @see RegisterPlugin
 * @see https://restheart.org/docs/plugins/security-plugins/#authentication-mechanisms
 */
public interface AuthMechanism extends AuthenticationMechanism, ConfigurablePlugin {
    /**
     * Attempts to authenticate the current request using this authentication mechanism.
     * <p>
     * This method is called by the security framework for each incoming request to determine
     * if this authentication mechanism can handle the request and whether authentication
     * should succeed, fail, or be deferred to other mechanisms.
     * </p>
     * <p>
     * The method should:
     * <ol>
     *   <li>Examine the HTTP request for authentication credentials specific to this mechanism</li>
     *   <li>If credentials are found, extract and validate them</li>
     *   <li>Use the SecurityContext's IdentityManager to verify the credentials</li>
     *   <li>If verification succeeds, call securityContext.authenticationComplete()</li>
     *   <li>Return the appropriate AuthenticationMechanismOutcome</li>
     * </ol>
     * </p>
     * <p>
     * Return values indicate:
     * <ul>
     *   <li><strong>AUTHENTICATED</strong> - Authentication successful, user account attached to context</li>
     *   <li><strong>NOT_AUTHENTICATED</strong> - No credentials found or authentication failed</li>
     *   <li><strong>NOT_ATTEMPTED</strong> - This mechanism cannot handle this request type</li>
     * </ul>
     * </p>
     *
     * @param exchange the HTTP server exchange containing the request to authenticate
     * @param securityContext the security context for managing authentication state
     * @return the outcome of the authentication attempt
     */
    @Override
    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange, final SecurityContext securityContext);

    /**
     * Sends an authentication challenge to the client when authentication is required.
     * <p>
     * This method is called when the security framework determines that authentication
     * is required but no valid credentials were provided, or when authentication fails.
     * The mechanism should send an appropriate challenge response that instructs the
     * client how to provide authentication credentials.
     * </p>
     * <p>
     * Common challenge implementations:
     * <ul>
     *   <li><strong>Basic Auth</strong> - Set WWW-Authenticate: Basic realm="realm-name"</li>
     *   <li><strong>Bearer Token</strong> - Set WWW-Authenticate: Bearer realm="api"</li>
     *   <li><strong>Custom Schemes</strong> - Set appropriate challenge headers and status codes</li>
     * </ul>
     * </p>
     * <p>
     * The method should set appropriate response headers and status codes to inform
     * the client about the authentication requirements. Common status codes include:
     * <ul>
     *   <li><strong>401 Unauthorized</strong> - For most authentication challenges</li>
     *   <li><strong>403 Forbidden</strong> - When access is denied regardless of credentials</li>
     * </ul>
     * </p>
     *
     * @param exchange the HTTP server exchange for sending the challenge response
     * @param securityContext the security context containing authentication state
     * @return the result of sending the challenge, indicating success and status code
     */
    @Override
    public ChallengeResult sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext);

    /**
     * Returns the name of this authentication mechanism.
     * <p>
     * The mechanism name is used for identification in security contexts, logging,
     * and debugging. By default, this returns the plugin name as configured in
     * the {@link RegisterPlugin} annotation.
     * </p>
     * <p>
     * The name should be unique among all authentication mechanisms and should
     * clearly identify the authentication scheme being implemented.
     * </p>
     *
     * @return the unique name of this authentication mechanism
     */
    default String getMechanismName() {
        return PluginUtils.name(this);
    }
}
