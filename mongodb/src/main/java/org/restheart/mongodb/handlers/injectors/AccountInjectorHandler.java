/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart.mongodb.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;

/**
 *
 * injects the context authenticatedAccount
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AccountInjectorHandler extends PipelinedHandler {
    /**
     * Creates a new instance of AccountInjectorHandler
     *
     * @param next
     */
    public AccountInjectorHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);
        
        // inject authenticatedAccount
        if (exchange.getSecurityContext() != null) {
            request.setAuthenticatedAccount(exchange.getSecurityContext()
                    .getAuthenticatedAccount());
        }
        
        next(exchange);
    }
}
