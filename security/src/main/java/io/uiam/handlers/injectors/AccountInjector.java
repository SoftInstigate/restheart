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
package io.uiam.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.handlers.RequestContext;

/**
 *
 * injects the context authenticatedAccount
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AccountInjector extends PipedHttpHandler {
    /**
     * Creates a new instance of AccountInjectorHandler
     *
     * @param next
     */
    public AccountInjector(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(
            final HttpServerExchange exchange,
            final RequestContext context)
            throws Exception {
        // inject authenticatedAccount
        if (exchange.getSecurityContext() != null) {
            context.setAuthenticatedAccount(exchange.getSecurityContext().getAuthenticatedAccount());
        }
        
        next(exchange, context);
    }
}
