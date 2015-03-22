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
package org.restheart.security.handlers;

import io.undertow.security.idm.Account;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.restheart.Bootstrapper;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_HEADER;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_LOCATION_HEADER;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_VALID_HEADER;

import org.restheart.security.impl.AuthTokenIdentityManager;
import org.restheart.security.impl.SimpleAccount;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class AuthTokenInjecterHandler extends PipedHttpHandler {
    private static final boolean enabled = Bootstrapper.getConfiguration().isAuthTokenEnabled();
    private static final long TTL = Bootstrapper.getConfiguration().getAuthTokenTtl();

    /**
     * Creates a new instance of AuthTokenInjecterHandler
     *
     * @param next
     */
    public AuthTokenInjecterHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (enabled) {
            if (exchange.getSecurityContext() != null && exchange.getSecurityContext().isAuthenticated()) {
                Account authenticatedAccount = exchange.getSecurityContext().getAuthenticatedAccount();

                char[] token = cacheSessionToken(authenticatedAccount);

                injectTokenHeaders(exchange, new HeadersManager(exchange), token);
            }
        }

        if (getNext() != null) {
            getNext().handleRequest(exchange, context);
        }
    }

    private void injectTokenHeaders(HttpServerExchange exchange, HeadersManager headers, char[] token) {
        headers.addResponseHeader(AUTH_TOKEN_HEADER, new String(token));
        headers.addResponseHeader(AUTH_TOKEN_VALID_HEADER, Instant.now().plus(TTL, ChronoUnit.MINUTES).toString());
        headers.addResponseHeader(AUTH_TOKEN_LOCATION_HEADER, "/_authtokens/" + exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal().getName());
    }

    private char[] cacheSessionToken(Account authenticatedAccount) {
        String id = authenticatedAccount.getPrincipal().getName();
        Optional<SimpleAccount> cachedTokenAccount = AuthTokenIdentityManager.getInstance().getCachedAccounts().get(id);

        if (cachedTokenAccount == null) {
            char[] token = UUID.randomUUID().toString().toCharArray();
            SimpleAccount newCachedTokenAccount = new SimpleAccount(id, token, authenticatedAccount.getRoles());
            AuthTokenIdentityManager.getInstance().getCachedAccounts().put(id, newCachedTokenAccount);

            return token;
        } else {
            return cachedTokenAccount.get().getCredentials().getPassword();
        }
    }
}
