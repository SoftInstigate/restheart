/*
 * RESTHeart Security
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
package org.restheart.security.plugins.tokens;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.Sets;
import org.restheart.security.cache.Cache;
import org.restheart.security.cache.CacheFactory;
import org.restheart.security.handlers.exchange.JsonRequest;

import org.restheart.security.plugins.ConfigurablePlugin;
import org.restheart.security.utils.URLUtils;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import java.util.Arrays;
import org.restheart.security.ConfigurationException;
import org.restheart.security.plugins.TokenManager;
import org.restheart.security.plugins.authenticators.PwdCredentialAccount;

public class RndTokenManager implements TokenManager {

    private static SecureRandom RND_GENERATOR = new SecureRandom();

    private static Cache<String, PwdCredentialAccount> CACHE;

    private final int ttl;
    private final String srvURI;

    public RndTokenManager(String name, Map<String, Object> args)
            throws ConfigurationException {
        this.ttl = ConfigurablePlugin.argValue(args, "ttl");

        this.srvURI = ConfigurablePlugin.argValue(args, "srv-uri");

        this.CACHE = CacheFactory.createLocalCache(Long.MAX_VALUE,
                Cache.EXPIRE_POLICY.AFTER_READ,
                ttl * 60 * 1_000);
    }

    @Override
    public Account verify(final Account account) {
        return account;
    }

    @Override
    public Account verify(final String id, final Credential credential) {
        final Optional<PwdCredentialAccount> _account = CACHE.get(id);

        return _account != null
                && _account.isPresent()
                && verifyToken(_account.get(), credential)
                ? _account.get()
                : null;
    }

    @Override
    public Account verify(final Credential credential) {
        return null;
    }

    private boolean verifyToken(final PwdCredentialAccount account,
            final Credential credential) {
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
        Optional<PwdCredentialAccount> cachedAccount = this.CACHE
                .get(account.getPrincipal().getName());

        if (cachedAccount != null && cachedAccount.isPresent()) {
            return cachedAccount.get().getCredentials();
        } else {
            char[] token = nextToken();
            PwdCredentialAccount newCachedTokenAccount = new PwdCredentialAccount(
                    account.getPrincipal().getName(),
                    token,
                    Sets.newTreeSet(account.getRoles()));

            this.CACHE.put(account.getPrincipal().getName(),
                    newCachedTokenAccount);

            return newCachedTokenAccount.getCredentials();
        }
    }

    @Override
    public void invalidate(Account account) {
        this.CACHE.invalidate(account.getPrincipal().getName());
    }

    @Override
    public void update(Account account) {
        String id = account.getPrincipal().getName();

        Optional<PwdCredentialAccount> _authTokenAccount
                = this.CACHE.get(id);

        if (_authTokenAccount != null && _authTokenAccount.isPresent()) {
            PwdCredentialAccount authTokenAccount = _authTokenAccount.get();

            PwdCredentialAccount updatedAuthTokenAccount
                    = new PwdCredentialAccount(
                            id,
                            authTokenAccount.getCredentials().getPassword(),
                            account.getRoles());

            this.CACHE.put(id, updatedAuthTokenAccount);
        }
    }

    @Override
    public void injectTokenHeaders(HttpServerExchange exchange,
            PasswordCredential token) {
        exchange.getResponseHeaders().add(AUTH_TOKEN_HEADER,
                new String(token.getPassword()));

        exchange.getResponseHeaders().add(AUTH_TOKEN_VALID_HEADER,
                Instant.now().plus(ttl, ChronoUnit.MINUTES).toString());

        var request = JsonRequest.wrap(exchange);

        if (request.getAuthenticatedAccount() != null
                && request.getAuthenticatedAccount().getPrincipal() != null
                && request.getAuthenticatedAccount().getPrincipal().getName() != null) {
            String cid = request
                    .getAuthenticatedAccount()
                    .getPrincipal()
                    .getName();

            exchange.getResponseHeaders().add(AUTH_TOKEN_LOCATION_HEADER,
                    URLUtils.removeTrailingSlashes(srvURI)
                            .concat("/")
                            .concat(cid));
        }
    }

    private static char[] nextToken() {
        return new BigInteger(256, RND_GENERATOR)
                .toString(Character.MAX_RADIX).toCharArray();
    }
}
