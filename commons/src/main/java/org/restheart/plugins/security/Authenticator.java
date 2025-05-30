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

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import org.restheart.plugins.ConfigurablePlugin;

/**
 * Interface for implementing RESTHeart authenticators that verify user credentials.
 * <p>
 * Authenticators are responsible for validating credentials extracted by authentication
 * mechanisms and determining whether they represent a valid user account. They serve
 * as the credential verification component in the RESTHeart security pipeline, working
 * in conjunction with AuthMechanisms to establish user identity.
 * </p>
 * <p>
 * Authenticators can integrate with various identity storage systems:
 * <ul>
 *   <li><strong>Database Authenticators</strong> - Verify credentials against database user tables</li>
 *   <li><strong>LDAP Authenticators</strong> - Authenticate against LDAP/Active Directory</li>
 *   <li><strong>File-based Authenticators</strong> - Use configuration files for user storage</li>
 *   <li><strong>JWT Authenticators</strong> - Validate and decode JWT tokens</li>
 *   <li><strong>External API Authenticators</strong> - Delegate verification to external services</li>
 * </ul>
 * </p>
 * <p>
 * The authenticator is responsible for:
 * <ol>
 *   <li>Receiving credentials from authentication mechanisms</li>
 *   <li>Validating credentials against the identity store</li>
 *   <li>Creating Account objects for authenticated users</li>
 *   <li>Populating user roles and permissions</li>
 *   <li>Handling credential refresh and validation</li>
 * </ol>
 * </p>
 * <p>
 * Example implementation:
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "myAuthenticator",
 *     description = "Custom database authenticator"
 * )
 * public class MyAuthenticator implements Authenticator {
 *     &#64;Override
 *     public Account verify(String id, Credential credential) {
 *         if (credential instanceof PasswordCredential) {
 *             PasswordCredential pc = (PasswordCredential) credential;
 *             User user = userService.authenticate(id, pc.getPassword());
 *             if (user != null) {
 *                 return new UserAccount(user);
 *             }
 *         }
 *         return null;
 *     }
 *     
 *     &#64;Override
 *     public Account verify(Account account) {
 *         // Re-verify existing account if needed
 *         return account.isValid() ? account : null;
 *     }
 *     
 *     &#64;Override
 *     public Account verify(Credential credential) {
 *         // Handle credentials without explicit ID (e.g., tokens)
 *         if (credential instanceof TokenCredential) {
 *             return verifyToken((TokenCredential) credential);
 *         }
 *         return null;
 *     }
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Integration with Security Pipeline:</strong>
 * <ol>
 *   <li>AuthMechanism extracts credentials from HTTP request</li>
 *   <li>AuthMechanism calls Authenticator.verify() with the credentials</li>
 *   <li>Authenticator validates credentials against identity store</li>
 *   <li>If valid, Authenticator returns an Account object with user details and roles</li>
 *   <li>Account is attached to the SecurityContext for authorization decisions</li>
 * </ol>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see org.restheart.plugins.security.AuthMechanism
 * @see io.undertow.security.idm.IdentityManager
 * @see io.undertow.security.idm.Account
 * @see io.undertow.security.idm.Credential
 * @see ConfigurablePlugin
 * @see RegisterPlugin
 * @see https://restheart.org/docs/plugins/security-plugins/#authenticators
 */
public interface Authenticator extends IdentityManager, ConfigurablePlugin {
    /**
     * Re-verifies an existing account to ensure it is still valid.
     * <p>
     * This method is called to validate that an existing Account object is still
     * valid and should continue to be trusted. This is useful for scenarios where:
     * <ul>
     *   <li>Account validity needs periodic refresh</li>
     *   <li>User permissions may have changed since initial authentication</li>
     *   <li>Session or token-based authentication requires revalidation</li>
     *   <li>Account status needs to be checked against external systems</li>
     * </ul>
     * </p>
     * <p>
     * Implementations should check:
     * <ul>
     *   <li>Whether the account is still active in the identity store</li>
     *   <li>If user roles or permissions have been modified</li>
     *   <li>Whether any security constraints require account invalidation</li>
     *   <li>If time-based expiration policies apply</li>
     * </ul>
     * </p>
     * <p>
     * This method is typically called by the security framework during request
     * processing to ensure that cached or session-based accounts remain valid.
     * </p>
     *
     * @param account the existing account to verify
     * @return the same account if still valid, a refreshed account with updated
     *         information, or null if the account is no longer valid
     */
    @Override
    public Account verify(final Account account);

    /**
     * Verifies user credentials using a provided user identifier and credential.
     * <p>
     * This is the primary authentication method that validates a user's identity
     * using their username/ID and accompanying credentials such as passwords,
     * tokens, or certificates. This method is typically called by authentication
     * mechanisms that extract both user identifiers and credentials from requests.
     * </p>
     * <p>
     * Common credential types include:
     * <ul>
     *   <li><strong>PasswordCredential</strong> - Username/password authentication</li>
     *   <li><strong>DigestCredential</strong> - Digest authentication credentials</li>
     *   <li><strong>X509CertificateCredential</strong> - Client certificate authentication</li>
     *   <li><strong>Custom Credentials</strong> - Application-specific credential types</li>
     * </ul>
     * </p>
     * <p>
     * Implementation considerations:
     * <ul>
     *   <li>Validate that the credential type is supported by this authenticator</li>
     *   <li>Perform secure credential comparison (e.g., constant-time for passwords)</li>
     *   <li>Handle account lockout and brute force protection if applicable</li>
     *   <li>Populate the returned Account with appropriate roles and permissions</li>
     *   <li>Log authentication attempts for security monitoring</li>
     * </ul>
     * </p>
     *
     * @param id the user identifier (username, email, etc.)
     * @param credential the credential to verify (password, token, certificate, etc.)
     * @return an Account object if authentication succeeds, null if authentication fails
     */
    @Override
    public Account verify(final String id, final Credential credential);

    /**
     * Verifies credentials that contain embedded identity information.
     * <p>
     * This method is used for credential types that include both identity and
     * authentication information within the credential itself, eliminating the
     * need for a separate user identifier. This is commonly used with token-based
     * authentication systems where the token contains user identity information.
     * </p>
     * <p>
     * Common use cases include:
     * <ul>
     *   <li><strong>JWT Tokens</strong> - Self-contained tokens with user claims</li>
     *   <li><strong>API Keys</strong> - Keys that identify both user and authorization</li>
     *   <li><strong>Session Tokens</strong> - Server-side session identifiers</li>
     *   <li><strong>OAuth Tokens</strong> - Bearer tokens from OAuth flows</li>
     * </ul>
     * </p>
     * <p>
     * Implementation should:
     * <ul>
     *   <li>Extract user identity from the credential</li>
     *   <li>Validate the credential's authenticity and integrity</li>
     *   <li>Check credential expiration and validity</li>
     *   <li>Create an Account with the embedded user information and roles</li>
     *   <li>Handle credential refresh if supported</li>
     * </ul>
     * </p>
     * <p>
     * This method is particularly useful for stateless authentication scenarios
     * where all necessary information is contained within the credential itself.
     * </p>
     *
     * @param credential the self-contained credential to verify
     * @return an Account object if the credential is valid and authentic, null otherwise
     */
    @Override
    public Account verify(final Credential credential);
}
