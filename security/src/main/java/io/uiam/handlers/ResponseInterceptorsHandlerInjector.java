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
package io.uiam.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.ResponseCommitListener;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseInterceptorsHandlerInjector extends PipedHttpHandler {

    private ResponseInterceptorsHandler rish = new ResponseInterceptorsHandler();

    /**
     *
     */
    public ResponseInterceptorsHandlerInjector() {
        super(null);
    }

    /**
     * @param next
     */
    public ResponseInterceptorsHandlerInjector(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.addResponseCommitListener(new ResponseCommitListener() {
            @Override
            public void beforeCommit(HttpServerExchange exchange) {
                try {
                    rish.handleRequest(exchange);
                }
                catch (Exception ce) {
                    throw new RuntimeException(ce);
                }
            }
        });

        next(exchange);
    }
}
