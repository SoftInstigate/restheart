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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.common.collect.Sets;

import io.uiam.plugins.AbstractConfiFileConsumer;
import io.uiam.plugins.authentication.PluggableIdentityManager;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.DigestCredential;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.util.HexConverter;

/**
 *
 * Identity Manager with RolesAccounts defined in a yml configuration file
 *
 * supports PasswordCredentials and DigestCredentials
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SimpleFileIdentityManager extends AbstractConfiFileConsumer implements PluggableIdentityManager {

    private final Map<String, PwdCredentialAccount> accounts = new HashMap<>();

    /**
     *
     * @param arguments
     * @throws java.io.FileNotFoundException
     */
    public SimpleFileIdentityManager(String name, Map<String, Object> arguments)
            throws FileNotFoundException, UnsupportedEncodingException {
        init(arguments, "users");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Consumer<? super Map<String, Object>> consumeConfiguration() {
        return u -> {
            Object _userid = u.get("userid");
            Object _password = u.get("password");
            Object _roles = u.get("roles");

            if (_userid == null || !(_userid instanceof String)) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. a user entry is missing the userid");
            }

            if (_password == null || !(_password instanceof String)) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. a user entry is missing the password");
            }

            if (_roles == null) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. a user entry is missing the roles");
            }

            if (!(_roles instanceof List)) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. a user entry roles argument is not an array");
            }

            String userid = (String) _userid;
            char[] password = ((String) _password).toCharArray();

            if (((Collection<?>) _roles).stream().anyMatch(i -> !(i instanceof String))) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. a roles entry is wrong. they all must be strings");
            }

            Set<String> roles = Sets.newLinkedHashSet((Collection<String>) _roles);

            PwdCredentialAccount a = new PwdCredentialAccount(userid, password, roles);

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
        if (account instanceof PwdCredentialAccount) {
            if (credential instanceof PasswordCredential) {
                return verifyPasswordCredential(account, credential);
            } else if (credential instanceof DigestCredential) {
                return verifyDigestCredential(account, credential);
            }
        }

        return false;
    }

    private boolean verifyPasswordCredential(Account account, Credential credential) {
        char[] password = ((PasswordCredential) credential).getPassword();
        char[] expectedPassword = accounts.get(account.getPrincipal().getName()).getCredentials().getPassword();

        return Arrays.equals(password, expectedPassword);
    }

    private boolean verifyDigestCredential(Account account, Credential credential) {
        try {
            DigestCredential dc = (DigestCredential) credential;

            MessageDigest digest = dc.getAlgorithm().getMessageDigest();

            String expectedPassword = new String(
                    accounts.get(account.getPrincipal().getName()).getCredentials().getPassword());

            digest.update(account.getPrincipal().getName().getBytes(UTF_8));
            digest.update((byte) ':');
            digest.update(dc.getRealm().getBytes(UTF_8));
            digest.update((byte) ':');
            digest.update(expectedPassword.getBytes(UTF_8));

            byte[] ha1 = HexConverter.convertToHexBytes(digest.digest());

            return dc.verifyHA1(ha1);
        } catch (NoSuchAlgorithmException ne) {
            return false;
        }
    }
}
