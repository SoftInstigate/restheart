/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2022 SoftInstigate
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
package org.restheart.security.tokens;

import com.google.common.collect.Sets;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import org.restheart.ConfigurationException;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.exchange.JsonProxyRequest;
import org.restheart.security.FileRealmAccount;
import org.restheart.security.MongoRealmAccount;
import org.restheart.security.PwdCredentialAccount;
import org.restheart.plugins.ConfigurablePlugin;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.TokenManager;
import org.restheart.security.interceptors.TokenCORSResponseInterceptor;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(name = "rndTokenManager",
                description = "generates random auth tokens",
                enabledByDefault = false)
public class RndTokenManager implements TokenManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RndTokenManager.class);

    private static final SecureRandom RND_GENERATOR = new SecureRandom();

    private static Cache<String, PwdCredentialAccount> CACHE = null;

    private int ttl = -1;
    private String srvURI = null;

    @InjectConfiguration
    @InjectPluginsRegistry
    public void init(Map<String, Object> confArgs, PluginsRegistry pluginsRegistry) throws ConfigurationException {
        this.ttl = ConfigurablePlugin.argValue(confArgs, "ttl");

        this.srvURI = ConfigurablePlugin.argValue(confArgs, "srv-uri");

        CACHE = CacheFactory.createLocalCache(Long.MAX_VALUE, Cache.EXPIRE_POLICY.AFTER_READ, ttl * 60 * 1_000);

        // add the auth token header to CORS header Access-Control-Expose-Headers
        // using helper interceptor tokenCORSResponseInterceptor
        String[] headers = {AUTH_TOKEN_HEADER.toString(), AUTH_TOKEN_VALID_HEADER.toString(), AUTH_TOKEN_LOCATION_HEADER.toString()};

        var ti = pluginsRegistry.getInterceptors()
                .stream().filter(i -> "tokenCORSResponseInterceptor".equals(i.getName()))
                .findFirst();

        if (ti.isPresent()) {
            ((TokenCORSResponseInterceptor) ti.get().getInstance()).setHeaders(headers);
        } else {
            LOGGER.warn("Cound not find tokenCORSResponseInterceptor. Auth token headers are not added to CORS");
        }
    }

    @Override
    public Account verify(final Account account) {
        return account;
    }

    @Override
    public Account verify(final String id, final Credential credential) {
        final var _account = CACHE.get(id);

        return _account != null && _account.isPresent() && verifyToken(_account.get(), credential)
                ? _account.get()
                : null;
    }

    @Override
    public Account verify(final Credential credential) {
        return null;
    }

    private boolean verifyToken(final PwdCredentialAccount account, final Credential credential) {
        if (credential instanceof PasswordCredential) {
            char[] token = ((PasswordCredential) credential).getPassword();
            char[] expectedToken = account.getCredentials().getPassword();

            return Arrays.equals(token, expectedToken);
        }
        return false;
    }

    public Cache<String, PwdCredentialAccount> getCACHE() {
        return CACHE;
    }

    @Override
    public PasswordCredential get(Account account) {
        var cachedAccount = CACHE.get(account.getPrincipal().getName());

        if (cachedAccount != null && cachedAccount.isPresent()) {
            return cachedAccount.get().getCredentials();
        } else {
            var newCachedTokenAccount = cloneWithToken(account, nextToken());

            CACHE.put(account.getPrincipal().getName(), newCachedTokenAccount);

            return newCachedTokenAccount.getCredentials();
        }
    }

    private PwdCredentialAccount cloneWithToken(Account account, char[] token) {
        PwdCredentialAccount ret;

        if (account instanceof MongoRealmAccount) {
            var _account = (MongoRealmAccount) account;

            ret = new MongoRealmAccount( account.getPrincipal().getName(), token, Sets.newTreeSet(account.getRoles()), _account.getAccountDocument());
        } else if (account instanceof FileRealmAccount) {
            var _account = (FileRealmAccount) account;

            ret = new FileRealmAccount(account.getPrincipal().getName(), token, Sets.newTreeSet(account.getRoles()), _account.getAccountProperties());
        } else {
            ret = new PwdCredentialAccount(account.getPrincipal().getName(), token, Sets.newTreeSet(account.getRoles()));
        }

        return ret;
    }

    @Override
    public void invalidate(Account account) {
        CACHE.invalidate(account.getPrincipal().getName());
    }

    @Override
    public void update(Account account) {
        String id = account.getPrincipal().getName();

        var _authTokenAccount = CACHE.get(id);

        if (_authTokenAccount != null && _authTokenAccount.isPresent()) {
            var authTokenAccount = _authTokenAccount.get();

            var updatedAuthTokenAccount = cloneWithToken(account, authTokenAccount.getCredentials().getPassword());

            CACHE.put(id, updatedAuthTokenAccount);
        }
    }

    @Override
    public void injectTokenHeaders(HttpServerExchange exchange, PasswordCredential token) {
        exchange.getResponseHeaders().add(AUTH_TOKEN_HEADER, new String(token.getPassword()));

        exchange.getResponseHeaders().add(AUTH_TOKEN_VALID_HEADER, Instant.now().plus(ttl, ChronoUnit.MINUTES).toString());

        var request = JsonProxyRequest.of(exchange);

        if (request.getAuthenticatedAccount() != null
                && request.getAuthenticatedAccount().getPrincipal() != null
                && request.getAuthenticatedAccount().getPrincipal().getName() != null) {
            var cid = request.getAuthenticatedAccount().getPrincipal().getName();

            exchange.getResponseHeaders().add(AUTH_TOKEN_LOCATION_HEADER, URLUtils.removeTrailingSlashes(srvURI).concat("/").concat(cid));
        }
    }

    private static char[] nextToken() {
        return new BigInteger(256, RND_GENERATOR).toString(Character.MAX_RADIX).toCharArray();
    }
}
