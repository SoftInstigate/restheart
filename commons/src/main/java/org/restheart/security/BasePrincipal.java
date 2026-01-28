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

import java.security.Principal;

/**
 * Base concrete Principal implementation for representing authenticated user identities.
 * 
 * <p>This class provides a simple, immutable implementation of the {@link Principal} interface
 * from the Java security framework. It represents the identity of an authenticated user within
 * the RESTHeart security system.</p>
 * 
 * <h2>Design Characteristics</h2>
 * <ul>
 *   <li><strong>Immutability:</strong> Once created, the principal's name cannot be changed</li>
 *   <li><strong>Null Safety:</strong> Constructor validates that the name is never null</li>
 *   <li><strong>Simplicity:</strong> Provides only the essential identity information</li>
 *   <li><strong>Thread Safety:</strong> Immutable design ensures thread safety</li>
 * </ul>
 * 
 * <h2>Usage Context</h2>
 * <p>BasePrincipal is typically used within {@link BaseAccount} and its subclasses to represent
 * the authenticated user's identity. It serves as the foundation for more complex principal
 * implementations if needed.</p>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create a principal for a user
 * Principal userPrincipal = new BasePrincipal("john.doe@example.com");
 * 
 * // Use in an account
 * BaseAccount account = new BaseAccount(
 *     userPrincipal.getName(),
 *     Set.of("user", "developer")
 * );
 * 
 * // Access the principal name
 * String username = userPrincipal.getName();  // "john.doe@example.com"
 * }</pre>
 * 
 * <h2>Security Considerations</h2>
 * <p>The principal name should be a unique identifier for the user within the authentication
 * realm. Common patterns include:</p>
 * <ul>
 *   <li>Email addresses (for email-based authentication)</li>
 *   <li>Usernames (for traditional username/password authentication)</li>
 *   <li>Distinguished Names (for certificate-based authentication)</li>
 *   <li>Subject identifiers (for JWT/OAuth authentication)</li>
 * </ul>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 5.0.0
 * @see java.security.Principal
 * @see BaseAccount
 */
public class BasePrincipal implements Principal {

    private String name;

    /**
     * Constructs a new BasePrincipal with the specified name.
     * 
     * <p>The name represents the identity of the principal and must be non-null.
     * This value should be unique within the authentication realm to properly
     * identify the user.</p>
     * 
     * @param name The name identifying this principal. Must not be null
     * @throws IllegalArgumentException if the name parameter is null
     */
    public BasePrincipal(String name) {
        if (name == null) {
            throw new IllegalArgumentException("argument name cannot be null");
        }

        this.name = name;
    }

    /**
     * Returns the name of this principal.
     * 
     * <p>The name is the unique identifier for this principal within the
     * authentication realm. This method is guaranteed to return a non-null
     * value as enforced by the constructor.</p>
     * 
     * @return The name of this principal, never null
     */
    @Override
    public String getName() {
        return name;
    }
}
