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

import io.undertow.security.idm.PasswordCredential;
import java.util.Set;

/**
 * Account implementation that securely stores password credentials for authentication.
 * 
 * <p>This class extends {@link BaseAccount} to add password-based authentication capabilities.
 * It encapsulates a {@link PasswordCredential} object that securely holds the user's password
 * as a char array, following security best practices for handling sensitive authentication data.</p>
 * 
 * <h2>Security Features</h2>
 * <ul>
 *   <li><strong>Char Array Storage:</strong> Passwords are stored as char arrays rather than
 *       Strings, allowing them to be explicitly cleared from memory after use</li>
 *   <li><strong>Transient Credential:</strong> The credential field is marked transient to
 *       prevent accidental serialization of passwords</li>
 *   <li><strong>Immutable Design:</strong> Once created, the password credential cannot be changed</li>
 * </ul>
 * 
 * <h2>Usage Guidelines</h2>
 * <p>When using this class, follow these security practices:</p>
 * <ol>
 *   <li>Clear the password char array as soon as it's no longer needed</li>
 *   <li>Avoid storing instances longer than necessary</li>
 *   <li>Never log or display the password credential</li>
 *   <li>Use secure communication channels when transmitting</li>
 * </ol>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create account with password
 * char[] password = getPasswordFromSecureSource();
 * try {
 *     PwdCredentialAccount account = new PwdCredentialAccount(
 *         "john.doe",
 *         password,
 *         Set.of("user", "admin")
 *     );
 *     
 *     // Use account for authentication
 *     authenticateUser(account);
 * } finally {
 *     // Clear password from memory
 *     Arrays.fill(password, ' ');
 * }
 * }</pre>
 * 
 * <h2>Subclasses</h2>
 * <p>This class serves as the base for specific password-based account implementations:</p>
 * <ul>
 *   <li>{@link FileRealmAccount} - For file-based authentication</li>
 *   <li>{@link MongoRealmAccount} - For MongoDB-based authentication</li>
 * </ul>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 5.0.0
 * @see BaseAccount
 * @see io.undertow.security.idm.PasswordCredential
 */
public class PwdCredentialAccount extends BaseAccount {
    private static final long serialVersionUID = -5840334837968478775L;
    final transient private PasswordCredential credential;

    /**
     * Constructs a new PwdCredentialAccount with the specified name, password, and roles.
     * 
     * <p>Creates an account that stores password credentials for authentication. The password
     * is wrapped in a {@link PasswordCredential} object for secure handling.</p>
     * 
     * <p><strong>Security Note:</strong> The password char array is not copied; the original
     * array is used directly. This means:</p>
     * <ul>
     *   <li>Modifications to the original array will affect the stored credential</li>
     *   <li>The caller should not clear the array until the account is no longer needed</li>
     *   <li>For maximum security, create a copy of the password array if the original
     *       needs to be cleared immediately</li>
     * </ul>
     * 
     * @param name The username for this account. Must not be null
     * @param password The password as a char array. Must not be null. The array should be
     *                 cleared by the caller when the account is no longer needed
     * @param roles The set of roles assigned to this account. Can be null or empty
     * @throws IllegalArgumentException if name or password is null
     */
    public PwdCredentialAccount(final String name, final char[] password, final Set<String> roles) {
        super(name, roles);

        if (password == null) {
            throw new IllegalArgumentException("argument password cannot be null");
        }

        this.credential = new PasswordCredential(password);
    }

    /**
     * Returns the password credential for this account.
     * 
     * <p>The returned {@link PasswordCredential} contains the password as a char array.
     * This method is typically used by authentication mechanisms to verify the user's
     * password during login.</p>
     * 
     * <p><strong>Security Considerations:</strong></p>
     * <ul>
     *   <li>The returned credential object contains sensitive data</li>
     *   <li>Avoid storing references to the credential</li>
     *   <li>Never log or display the credential contents</li>
     *   <li>The password can be accessed via {@code credential.getPassword()}</li>
     *   <li>Clear the password array when done: {@code Arrays.fill(credential.getPassword(), ' ')}</li>
     * </ul>
     * 
     * @return The PasswordCredential containing the account's password, never null
     */
    public PasswordCredential getCredentials() {
        return credential;
    }
}
