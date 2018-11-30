/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.uiam.plugins.authentication.impl;

import com.google.common.collect.Sets;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.PasswordCredential;
import java.security.Principal;
import java.util.Collections;
import java.util.Set;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RolesAccount implements Account {
    
    private final static long serialVersionUID = 5146445L;

    final private Principal principal;
    final private PasswordCredential credential;
    final private Set<String> roles;

    /**
     *
     * @param name
     * @param password
     * @param roles
     */
    public RolesAccount(String name, char[] password, Set<String> roles) {
        if (name == null) {
            throw new IllegalArgumentException("argument principal cannot be null");
        }

        if (password == null) {
            throw new IllegalArgumentException("argument password cannot be null");
        }

        if (roles == null || roles.isEmpty()) {
            roles = Sets.newHashSet();
        }

        this.principal = new NamedPrincipal(name);
        this.credential = new PasswordCredential(password);
        this.roles = roles;
    }

    /**
     *
     * @return
     */
    @Override
    public Principal getPrincipal() {
        return principal;
    }

    /**
     *
     * @return
     */
    public PasswordCredential getCredentials() {
        return credential;
    }

    @Override
    public Set<String> getRoles() {
        return Collections.unmodifiableSet(roles);
    }
}
