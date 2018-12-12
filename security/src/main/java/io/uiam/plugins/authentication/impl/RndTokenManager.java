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

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.Sets;
import io.uiam.cache.Cache;
import io.uiam.cache.CacheFactory;
import io.uiam.handlers.ExchangeHelper;

import io.uiam.plugins.ConfigurablePlugin;
import io.uiam.plugins.PluginConfigurationException;
import io.uiam.plugins.authentication.PluggableTokenManager;
import io.uiam.utils.URLUtils;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import java.util.Arrays;

public class RndTokenManager implements PluggableTokenManager {

    private static SecureRandom RND_GENERATOR = new SecureRandom();

    private static Cache<String, PwdCredentialAccount> CACHE;

    private final int ttl;
    private final String srvURI;

    public RndTokenManager(String name, Map<String, Object> args)
            throws PluginConfigurationException {
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
    public void invalidate(Account account, PasswordCredential token) {
        this.CACHE.invalidate(account.getPrincipal().getName());
    }

    @Override
    public void injectTokenHeaders(HttpServerExchange exchange,
            PasswordCredential token) {
        exchange.getResponseHeaders().add(AUTH_TOKEN_HEADER,
                new String(token.getPassword()));

        exchange.getResponseHeaders().add(AUTH_TOKEN_VALID_HEADER,
                Instant.now().plus(ttl, ChronoUnit.MINUTES).toString());

        ExchangeHelper eh = new ExchangeHelper(exchange);

        if (eh.getAuthenticatedAccount() != null
                && eh.getAuthenticatedAccount().getPrincipal() != null
                && eh.getAuthenticatedAccount().getPrincipal().getName() != null) {
            String cid = eh
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
