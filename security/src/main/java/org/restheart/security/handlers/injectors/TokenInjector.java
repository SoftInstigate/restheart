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
package org.restheart.security.handlers.injectors;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import org.restheart.plugins.security.TokenManager;
import org.restheart.security.handlers.PipedHttpHandler;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class TokenInjector extends PipedHttpHandler {
    private final TokenManager tokenManager;

    /**
     * Creates a new instance of AuthTokenInjecterHandler
     *
     * @param next
     * @param tokenManager
     */
    public TokenInjector(PipedHttpHandler next, TokenManager tokenManager) {
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
