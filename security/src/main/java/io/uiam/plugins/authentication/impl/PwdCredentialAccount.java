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

import io.undertow.security.idm.PasswordCredential;
import java.util.SortedSet;

/**
 * Account implementation that holds PasswordCredential
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PwdCredentialAccount extends BaseAccount {
    final transient private PasswordCredential credential;

    /**
     *
     * @param name
     * @param password
     * @param roles
     */
    public PwdCredentialAccount(final String name,
            final char[] password,
            final SortedSet<String> roles) {
        super(name, roles);

        if (password == null) {
            throw new IllegalArgumentException("argument password cannot be null");
        }

        this.credential = new PasswordCredential(password);
    }

    /**
     *
     * @return
     */
    public PasswordCredential getCredentials() {
        return credential;
    }
}
