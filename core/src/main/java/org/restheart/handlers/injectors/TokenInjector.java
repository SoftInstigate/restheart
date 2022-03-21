/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
package org.restheart.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.plugins.security.TokenManager;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class TokenInjector extends PipelinedHandler {
    private final TokenManager tokenManager;

    /**
     * Creates a new instance of TokenInjector
     *
     * @param next
     * @param tokenManager
     */
    public TokenInjector(PipelinedHandler next, TokenManager tokenManager) {
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
            var authenticatedAccount = exchange.getSecurityContext().getAuthenticatedAccount();

            var token = tokenManager.get(authenticatedAccount);

            tokenManager.injectTokenHeaders(exchange, token);
        }

        next(exchange);
    }
}
