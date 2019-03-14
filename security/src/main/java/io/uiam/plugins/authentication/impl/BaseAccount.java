/*
 * uIAM - the IAM for microservices
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uiam.plugins.authentication.impl;

import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.common.collect.Sets;

import io.undertow.security.idm.Account;

/**
 * Base concrete Account implementation
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BaseAccount implements Account {
    private static final long serialVersionUID = 4199620709967413442L;
    final private Principal principal;
    final private LinkedHashSet<String> roles;

    /**
     *
     * @param name
     * @param roles
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
     *
     * @return
     */
    @Override
    public Principal getPrincipal() {
        return principal;
    }

    @Override
    public Set<String> getRoles() {
        return roles;
    }
    
    @Override
    public String toString() {
        return "username="
                .concat(principal != null ? principal.getName() : "null")
                .concat(" roles=")
                .concat(roles != null ? roles.toString(): "null");
    }
}
