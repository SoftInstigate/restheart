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

import com.google.common.collect.Sets;
import io.undertow.security.idm.Account;
import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Base concrete Account implementation providing core authentication account functionality.
 * 
 * <p>This class serves as the foundation for all account types in the RESTHeart security framework.
 * It implements the Undertow {@link Account} interface and provides a simple, immutable representation
 * of an authenticated user with their associated roles.</p>
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><strong>Immutability:</strong> Once created, the account's principal and roles cannot be modified</li>
 *   <li><strong>Null Safety:</strong> Constructor validates that the principal name is never null</li>
 *   <li><strong>Role Management:</strong> Roles are stored in a LinkedHashSet to maintain order and uniqueness</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create a basic account with roles
 * Set<String> roles = Set.of("user", "admin");
 * BaseAccount account = new BaseAccount("john.doe", roles);
 * 
 * // Access account information
 * String username = account.getPrincipal().getName();  // "john.doe"
 * boolean isAdmin = account.getRoles().contains("admin");  // true
 * }</pre>
 * 
 * <h2>Extension Points</h2>
 * <p>This class is designed to be extended by more specific account implementations:</p>
 * <ul>
 *   <li>{@link PwdCredentialAccount} - Adds password credential support</li>
 *   <li>{@link FileRealmAccount} - Adds support for file-based authentication</li>
 *   <li>{@link MongoRealmAccount} - Adds support for MongoDB-based authentication</li>
 *   <li>{@link JwtAccount} - Adds support for JWT token authentication</li>
 * </ul>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 5.0.0
 * @see io.undertow.security.idm.Account
 * @see BasePrincipal
 */
public class BaseAccount implements Account {
    private static final long serialVersionUID = 4199620709967413442L;
    final private Principal principal;
    final private LinkedHashSet<String> roles;

    /**
     * Constructs a new BaseAccount with the specified name and roles.
     * 
     * <p>Creates an immutable account instance with a {@link BasePrincipal} for the given name
     * and a defensive copy of the provided roles. If roles are null or empty, an empty
     * LinkedHashSet is created to ensure the account always has a valid roles collection.</p>
     * 
     * @param name The principal name for this account. Must not be null
     * @param roles The set of roles assigned to this account. Can be null or empty,
     *              in which case an empty set will be used
     * @throws IllegalArgumentException if the name parameter is null
     */
    public BaseAccount(final String name, final Set<String> roles) {
        if (name == null) {
            throw new IllegalArgumentException("argument principal cannot be null");
        }

        if (roles == null || roles.isEmpty()) {
            this.roles = Sets.newLinkedHashSet();
        } else {
            this.roles = Sets.newLinkedHashSet(roles);
        }

        this.principal = new BasePrincipal(name);
    }

    /**
     * Returns the principal associated with this account.
     * 
     * <p>The principal represents the identity of the authenticated user and is
     * guaranteed to be non-null with a valid name.</p>
     * 
     * @return The principal for this account, never null
     */
    @Override
    public Principal getPrincipal() {
        return principal;
    }

    /**
     * Returns the roles assigned to this account.
     * 
     * <p>The returned set maintains insertion order (LinkedHashSet) and is the actual
     * internal collection. Modifications to the returned set will affect this account's
     * roles. For immutability, callers should not modify the returned set.</p>
     * 
     * @return The set of roles for this account, never null but may be empty
     */
    @Override
    public Set<String> getRoles() {
        return roles;
    }

    /**
     * Returns a string representation of this account.
     * 
     * <p>The string format includes the username and all assigned roles, useful for
     * debugging and logging purposes. Format: {@code username=<name> roles=[role1, role2, ...]}</p>
     * 
     * @return A string representation of this account
     */
    @Override
    public String toString() {
        return "username="
                .concat(principal != null ? principal.getName() : "null")
                .concat(" roles=")
                .concat(roles != null ? roles.toString() : "null");
    }
}
