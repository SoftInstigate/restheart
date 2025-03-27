/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.security.authenticators;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.FileNotFoundException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.restheart.configuration.ConfigurationException;
import org.restheart.plugins.FileConfigurablePlugin;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authenticator;
import org.restheart.security.FileRealmAccount;
import org.restheart.utils.LambdaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.DigestCredential;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.util.HexConverter;

/**
 *
 * Authenticator with permission defined in a yml configuration file
 *
 * supports PasswordCredentials and DigestCredentials
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "fileRealmAuthenticator", description = "authenticates clients credentials defined in a configuration file", enabledByDefault = false)
public class FileRealmAuthenticator extends FileConfigurablePlugin implements Authenticator {
    private static final String USERS = "users";

    private static final Logger LOGGER = LoggerFactory.getLogger(FileRealmAuthenticator.class);

    private final Map<String, FileRealmAccount> accounts = new HashMap<>();

    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void init() throws FileNotFoundException, ConfigurationException {
        if (config.containsKey("conf-file") && config.get("conf-file") != null) {
            // init from conf-file
            init(config, USERS);
        } else if (config.containsKey(USERS) && config.get(USERS) != null) {
            // init from users list property
            final List<Map<String, Object>> users = argOrDefault(config, USERS, new ArrayList<>());
            users.stream().forEach(consumeConfiguration());
        } else {
            throw new IllegalArgumentException("The configuration requires either 'conf-file' or 'users' parameter");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Consumer<? super Map<String, Object>> consumeConfiguration() {
        return u -> {
            try {
                final String userid = arg(u, "userid");
                final String _password = arg(u, "password");

                if (_password == null) {
                    LOGGER.warn(
                            "User {} has a null password, disabling it. You can set it with the environment variable RHO=\"/fileRealmAuthenticator/users[userid='admin']/password->'secret'\"",
                            userid);
                    return;
                }

                final char[] password = _password.toCharArray();

                @SuppressWarnings("rawtypes")
                final List _roles = arg(u, "roles");

                if (((Collection<?>) _roles).stream().anyMatch(i -> !(i instanceof String))) {
                    throw new IllegalArgumentException(
                            "wrong configuration. a roles entry is wrong. they all must be strings");
                }

                final Set<String> roles = Sets.newLinkedHashSet((Collection<String>) _roles);

                // remove the password before injecting the account properties in the account
                u.remove("password");
                final FileRealmAccount a = new FileRealmAccount(userid, password, roles, u);

                this.accounts.put(userid, a);
            } catch (final ConfigurationException pce) {
                LambdaUtils.throwsSneakyException(pce);
            }
        };
    }

    @Override
    public Account verify(final Account account) {
        return account;
    }

    @Override
    public Account verify(final String id, final Credential credential) {
        final Account account = accounts.get(id);
        return account != null && verifyCredential(account, credential) ? account : null;
    }

    @Override
    public Account verify(final Credential credential) {
        // Auto-generated method stub
        return null;
    }

    private boolean verifyCredential(final Account account, final Credential credential) {
        if (account instanceof FileRealmAccount) {
            if (credential instanceof PasswordCredential) {
                return verifyPasswordCredential(account, credential);
            } else if (credential instanceof DigestCredential) {
                return verifyDigestCredential(account, credential);
            }
        }

        return false;
    }

    private boolean verifyPasswordCredential(final Account account, final Credential credential) {
        final char[] password = ((PasswordCredential) credential).getPassword();
        final char[] expectedPassword = accounts.get(account.getPrincipal().getName()).getCredentials().getPassword();

        return Arrays.equals(password, expectedPassword);
    }

    private boolean verifyDigestCredential(final Account account, final Credential credential) {
        try {
            final DigestCredential dc = (DigestCredential) credential;

            final MessageDigest digest = dc.getAlgorithm().getMessageDigest();

            final char[] expectedPassword = accounts.get(account.getPrincipal().getName()).getCredentials()
                    .getPassword();

            digest.update(account.getPrincipal().getName().getBytes(UTF_8));
            digest.update((byte) ':');
            digest.update(dc.getRealm().getBytes(UTF_8));
            digest.update((byte) ':');
            digest.update(new String(expectedPassword).getBytes(UTF_8));

            final byte[] ha1 = HexConverter.convertToHexBytes(digest.digest());

            return dc.verifyHA1(ha1);
        } catch (final NoSuchAlgorithmException ne) {
            return false;
        }
    }
}
