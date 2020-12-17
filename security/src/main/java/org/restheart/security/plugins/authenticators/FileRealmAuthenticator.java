/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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
package org.restheart.security.plugins.authenticators;

import com.google.common.collect.Sets;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.DigestCredential;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.util.HexConverter;
import java.io.FileNotFoundException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.restheart.ConfigurationException;
import org.restheart.idm.PwdCredentialAccount;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
import org.restheart.plugins.FileConfigurablePlugin;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authenticator;
import org.restheart.utils.LambdaUtils;

/**
 *
 * Authenticator with permission defined in a yml configuration file
 *
 * supports PasswordCredentials and DigestCredentials
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "fileRealmAuthenticator",
        description = "authenticates clients credentials defined in a configuration file",
        enabledByDefault = false)
public class FileRealmAuthenticator
        extends FileConfigurablePlugin
        implements Authenticator {

    private final Map<String, PwdCredentialAccount> accounts = new HashMap<>();

    @InjectConfiguration
    public void init(Map<String, Object> confArgs)
            throws FileNotFoundException, ConfigurationException {
        init(confArgs, "users");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Consumer<? super Map<String, Object>> consumeConfiguration() {
        return u -> {
            try {
                String userid = argValue(u, "userid");
                String _password = argValue(u, "password");
                char[] password = ((String) _password).toCharArray();

                @SuppressWarnings("rawtypes")
                List _roles = argValue(u, "roles");

                if (((Collection<?>) _roles).stream().anyMatch(i -> !(i instanceof String))) {
                    throw new IllegalArgumentException(
                            "wrong configuration file format. a roles entry is wrong. they all must be strings");
                }

                Set<String> roles = Sets.newLinkedHashSet((Collection<String>) _roles);

                PwdCredentialAccount a = new PwdCredentialAccount(userid, password, roles);

                this.accounts.put(userid, a);
            } catch (ConfigurationException pce) {
                LambdaUtils.throwsSneakyExcpetion(pce);
            }
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
