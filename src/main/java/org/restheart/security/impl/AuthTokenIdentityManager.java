/*
 * RESTHeart - the data REST API server
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
import java.util.Arrays;
import java.util.Optional;
import org.restheart.Bootstrapper;

import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public final class AuthTokenIdentityManager implements IdentityManager {
    private final Cache<String, SimpleAccount> cachedAccounts;
    
    private static final long TTL = Bootstrapper.getConfiguration().getAuthTokenTtl();
    private static final boolean enabled = Bootstrapper.getConfiguration().isAuthTokenEnabled();
    
    /**
     *
     * @param next
     */
    private AuthTokenIdentityManager() {
        this.cachedAccounts = CacheFactory.createLocalCache(Long.MAX_VALUE, Cache.EXPIRE_POLICY.AFTER_READ, TTL*60*1000);
    }


    @Override
    public Account verify(Account account) {
        if (!enabled)
            return null;
        
        return account;
    }

    @Override
    public Account verify(String id, Credential credential) {
         if (!enabled)
            return null;
        
        final Optional<SimpleAccount> _account = cachedAccounts.get(id);
        
        return _account != null && _account.isPresent() && verifyToken(_account.get(), credential) ? _account.get() : null;
    }

    @Override
    public Account verify(Credential credential) {
        return null;
    }

    private boolean verifyToken(Account account, Credential credential) {
        if (credential instanceof PasswordCredential && account instanceof SimpleAccount) {
            char[] token = ((PasswordCredential) credential).getPassword();
            char[] expectedToken = cachedAccounts.get(account.getPrincipal().getName()).get().getCredentials().getPassword();

            return Arrays.equals(token, expectedToken);
        }
        return false;
    }
    
    public Cache<String, SimpleAccount> getCachedAccounts() {
        return cachedAccounts;
    }
    
    /**
     *
     * @return
     */
    public static AuthTokenIdentityManager getInstance() {
        return SessionTokenIdentityManagerHolder.INSTANCE;
    }

    private static class SessionTokenIdentityManagerHolder {
        private static final AuthTokenIdentityManager INSTANCE = new AuthTokenIdentityManager();
    }
}
