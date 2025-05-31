/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
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

import java.util.Map;
import java.util.Set;


/**
 * Account implementation for file-based authentication realms.
 * 
 * <p>This class extends {@link PwdCredentialAccount} to provide account management for users
 * authenticated via file-based authentication mechanisms. It stores user credentials along with
 * arbitrary properties that can be used for authorization decisions or application-specific needs.</p>
 * 
 * <h2>Key Features</h2>
 * <ul>
 *   <li><strong>Password Storage:</strong> Inherits secure password handling from PwdCredentialAccount</li>
 *   <li><strong>Property Support:</strong> Stores additional user properties as a flexible Map</li>
 *   <li><strong>File Realm Integration:</strong> Designed for use with FileRealmAuthenticator</li>
 * </ul>
 * 
 * <h2>Property Storage</h2>
 * <p>Properties are stored as a Map with String keys and Object values, allowing for:</p>
 * <ul>
 *   <li>User metadata (email, full name, department)</li>
 *   <li>Application-specific attributes</li>
 *   <li>Authorization-related data (permissions, quotas)</li>
 *   <li>Account settings (preferences, configuration)</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create account with properties
 * Map<String, Object> properties = new HashMap<>();
 * properties.put("email", "user@example.com");
 * properties.put("department", "Engineering");
 * properties.put("maxRequests", 1000);
 * 
 * FileRealmAccount account = new FileRealmAccount(
 *     "john.doe",
 *     "password123".toCharArray(),
 *     Set.of("user", "developer"),
 *     properties
 * );
 * 
 * // Access properties
 * String email = (String) account.properties().get("email");
 * Integer maxRequests = (Integer) account.properties().get("maxRequests");
 * }</pre>
 * 
 * <h2>File Realm Configuration</h2>
 * <p>When used with FileRealmAuthenticator, accounts are typically loaded from configuration files
 * in formats like YAML or JSON, where properties can be defined alongside credentials and roles.</p>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 5.0.0
 * @see PwdCredentialAccount
 * @see WithProperties
 */
public class FileRealmAccount extends PwdCredentialAccount implements WithProperties<Map<String, ? super Object>> {
    private static final long serialVersionUID = -5840534832968478775L;

    private final Map<String, ? super Object> properties;

    /**
     * Constructs a new FileRealmAccount with the specified credentials, roles, and properties.
     * 
     * <p>Creates an account suitable for file-based authentication with support for arbitrary
     * user properties. The password is stored securely as a PasswordCredential.</p>
     * 
     * <p><strong>Security Note:</strong> The password char array should be cleared after use
     * to minimize the time sensitive data remains in memory.</p>
     * 
     * @param name The username for this account. Must not be null
     * @param password The password as a char array. Must not be null. Should be cleared after use
     * @param roles The set of roles assigned to this account. Can be null or empty
     * @param properties Additional properties for this account. Can be null, in which case
     *                   an empty properties map will be available
     * @throws IllegalArgumentException if name or password is null
     */
    public FileRealmAccount(final String name, final char[] password, final Set<String> roles, Map<String, ? super Object> properties) {
        super(name, password, roles);

        if (password == null) {
            throw new IllegalArgumentException("argument password cannot be null");
        }

        this.properties = properties;
    }

    /**
     * Returns the additional properties associated with this account.
     * 
     * <p>The returned map contains any custom properties defined for this user in the
     * file realm configuration. These properties can be used for authorization decisions,
     * user preferences, or application-specific data.</p>
     * 
     * <p><strong>Note:</strong> The returned map is the internal properties object.
     * Modifications to this map will affect the account's properties.</p>
     * 
     * @return The properties map for this account, may be null if no properties were provided
     */
    @Override
    public Map<String, ? super Object> properties() {
        return this.properties;
    }

    /**
     * Returns the account properties as a Map.
     * 
     * <p>For FileRealmAccount, this method returns the same object as {@link #properties()}
     * since properties are already stored as a Map. This method exists to satisfy the
     * {@link WithProperties} interface contract.</p>
     * 
     * @return The properties map for this account, may be null if no properties were provided
     */
    @Override
    public Map<String, ? super Object> propertiesAsMap() {
        return this.properties;
    }
}
