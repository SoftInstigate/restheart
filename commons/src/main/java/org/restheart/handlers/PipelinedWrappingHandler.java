/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
package org.restheart.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.restheart.plugins.Service;

/**
 * wraps a HttpHandler into a PipelinedHttpHandler
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PipelinedWrappingHandler extends PipelinedHandler {

    private final HttpHandler wrapped;

    /**
     * Creates a new instance of PipedWrappingHandler
     *
     * @param next
     * @param handler
     */
    private PipelinedWrappingHandler(PipelinedHandler next, HttpHandler handler) {
        super(next);
        wrapped = handler;
    }
    
    /**
     * Creates a new instance of PipedWrappingHandler
     *
     * @param next
     * @param service
     */
    private PipelinedWrappingHandler(PipelinedHandler next, Service service) {
        super(next);
        wrapped = new ServiceWrapper(service);
    }

    /**
     * Creates a new instance of PipedWrappingHandler
     *
     * @param handler
     */
    private PipelinedWrappingHandler(HttpHandler handler) {
        super(null);
        wrapped = handler;
    }
    
    /**
     * 
     * @param handler
     * @return the wrapping handler
     */
    public static PipelinedWrappingHandler wrap(HttpHandler handler) {
        return wrap(null, handler);
    }
    
    /**
     * 
     * @param service
     * @return the wrapping handler
     */
    public static PipelinedWrappingHandler wrap(Service service) {
        return wrap(null, service);
    }
    
    /**
     * 
     * @param next
     * @param handler
     * @return the wrapping handler 
     */
    public static PipelinedWrappingHandler wrap(PipelinedHandler next, HttpHandler handler) {
        return new PipelinedWrappingHandler(next, handler);
    }
    
    /**
     * 
     * @param next
     * @param service
     * @return the wrapping handler 
     */
    public static PipelinedWrappingHandler wrap(PipelinedHandler next, Service service) {
        return new PipelinedWrappingHandler(next, service);
    }
    
    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (wrapped == null) {
            next(exchange);
        } else {
            wrapped.handleRequest(exchange);

            if (!exchange.isResponseComplete()) {
                next(exchange);
            }
        }
    }
}

class ServiceWrapper extends PipelinedHandler {
    final Service service;
    
    ServiceWrapper(Service service) {
        this.service = service;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        service.handle(exchange);
    }
}
