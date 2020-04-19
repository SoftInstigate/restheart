/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.mongodb.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import org.restheart.exchange.BsonRequest;
import org.restheart.handlers.PipelinedHandler;

/**
 *
 * injects the context authenticatedAccount
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AccountInjector extends PipelinedHandler {
    /**
     * Creates a new instance of AccountInjectorHandler
     *
     */
    public AccountInjector() {
        super(null);
    }
    
    /**
     * Creates a new instance of AccountInjectorHandler
     *
     * @param next
     */
    public AccountInjector(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.of(exchange);
        
        // inject authenticatedAccount
        if (exchange.getSecurityContext() != null) {
            request.setAuthenticatedAccount(exchange.getSecurityContext()
                    .getAuthenticatedAccount());
        }
        
        next(exchange);
    }
}
