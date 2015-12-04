/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.security.impl;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import java.io.FileNotFoundException;
import java.util.Arrays;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public final class SimpleFileIdentityManager extends AbstractSimpleSecurityManager implements IdentityManager {

    private final Map<String, SimpleAccount> accounts = new HashMap<>();

    /**
     *
     * @param arguments
     * @throws java.io.FileNotFoundException
     */
    public SimpleFileIdentityManager(Map<String, Object> arguments) throws FileNotFoundException {
        init(arguments, "users");
    }

    @Override
    Consumer<? super Map<String, Object>> consumeConfiguration() {
        return u -> {
            Object _userid = u.get("userid");
            Object _password = u.get("password");
            Object _roles = u.get("roles");

            if (_userid == null || !(_userid instanceof String)) {
                throw new IllegalArgumentException("wrong configuration file format. a user entry is missing the userid");
            }

            if (_password == null || !(_password instanceof String)) {
                throw new IllegalArgumentException("wrong configuration file format. a user entry is missing the password");
            }

            if (_roles == null) {
                throw new IllegalArgumentException("wrong configuration file format. a user entry is missing the roles");
            }

            if (!(_roles instanceof List)) {
                throw new IllegalArgumentException("wrong configuration file format. a user entry roles argument is not an array");
            }

            String userid = (String) _userid;
            char[] password = ((String) _password).toCharArray();

            if (((List) _roles).stream().anyMatch(i -> !(i instanceof String))) {
                throw new IllegalArgumentException("wrong configuration file format. a roles entry is wrong. they all must be strings");
            }

            Set<String> roles = new HashSet<>((List) _roles);

            SimpleAccount a = new SimpleAccount(userid, password, roles);

            this.accounts.put(userid, a);
        };
    }

    @Override
    public Account verify(Account account) {
        return account;
    }

    @Override
    public Account verify(String id, Credential credential) {
        final Account account = accounts.get(id);
        return account != null && verifyCredential(account, credential) ? account : null;
    }

    @Override
    public Account verify(Credential credential) {
        // Auto-generated method stub
        return null;
    }

    private boolean verifyCredential(Account account, Credential credential) {
        if (credential instanceof PasswordCredential && account instanceof SimpleAccount) {
            char[] password = ((PasswordCredential) credential).getPassword();
            char[] expectedPassword = accounts.get(account.getPrincipal().getName()).getCredentials().getPassword();

            return Arrays.equals(password, expectedPassword);
        }
        return false;
    }
}
