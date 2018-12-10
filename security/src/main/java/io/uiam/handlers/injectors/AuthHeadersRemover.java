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

import io.uiam.handlers.PipedHttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 *
 * injects the context authenticatedAccount
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthHeadersRemover extends PipedHttpHandler {
    /**
     * Creates a new instance of AccountInjectorHandler
     *
     * @param next
     */
    public AuthHeadersRemover(PipedHttpHandler next) {
        super(next);
    }

    /**
     * before proxyng the request the authentication headers are removed
     * 
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.getRequestHeaders().remove("Authorization");

        if (getNext() != null) {
            next(exchange);
        }
    }
}
