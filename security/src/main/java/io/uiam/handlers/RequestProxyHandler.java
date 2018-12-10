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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uiam.Bootstrapper;
import io.uiam.Configuration;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestProxyHandler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestProxyHandler.class);

    private final Configuration configuration = Bootstrapper.getConfiguration();

    /**
     * Creates a new instance of RequestLoggerHandler
     *
     * @param next
     */
    public RequestProxyHandler() {
        super(null);
    }
    
    /**
     * Creates a new instance of RequestLoggerHandler
     *
     * @param next
     */
    public RequestProxyHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     * Creates a new instance of RequestLoggerHandler
     *
     * @param handler
     */
    public RequestProxyHandler(HttpHandler handler) {
        super(null);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        LOGGER.info("should proxy request {}", exchange.getRequestURI());
        
        if (getNext() != null) {
            getNext().handleRequest(exchange, context);
        }
    }
}
