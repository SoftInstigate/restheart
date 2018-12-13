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
package io.uiam.handlers.security;

import io.uiam.handlers.PipedHttpHandler;
import io.uiam.plugins.authentication.PluggableTokenManager;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthTokenInjecterHandler extends PipedHttpHandler {
    private final PluggableTokenManager tokenManager;

    /**
     * Creates a new instance of AuthTokenInjecterHandler
     *
     * @param next
     * @param tokenManager
     */
    public AuthTokenInjecterHandler(PipedHttpHandler next, PluggableTokenManager tokenManager) {
        super(next);
        this.tokenManager = tokenManager;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (this.tokenManager != null
                && exchange.getSecurityContext() != null
                && exchange.getSecurityContext().isAuthenticated()) {
            Account authenticatedAccount = exchange
                    .getSecurityContext().getAuthenticatedAccount();

            PasswordCredential token = tokenManager.get(authenticatedAccount);

            tokenManager.injectTokenHeaders(exchange, token);
        }

        next(exchange);
    }
}
