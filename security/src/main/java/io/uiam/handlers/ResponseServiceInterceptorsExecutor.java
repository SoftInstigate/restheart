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

import io.uiam.plugins.PluginsRegistry;

import io.undertow.server.HttpServerExchange;
import java.io.IOException;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseServiceInterceptorsExecutor extends PipedHttpHandler {
    /**
     * 
     */
    public ResponseServiceInterceptorsExecutor() {
        super(null);
    }

    /**
     * @param next
     */
    public ResponseServiceInterceptorsExecutor(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var interceptors = PluginsRegistry
                .getInstance()
                .getResponseInterceptors();
        
        interceptors.stream()
                .filter(t -> t.resolve(exchange))
                .forEachOrdered(t -> t.handleRequest(exchange));
        
        next(exchange);
    }
}
