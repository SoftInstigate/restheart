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
package org.restheart.security.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PipedWrappingHandler extends PipedHttpHandler {

    private final HttpHandler wrapped;

    /**
     * Creates a new instance of PipedWrappingHandler
     *
     * @param next
     * @param handler
     */
    private PipedWrappingHandler(PipedHttpHandler next, HttpHandler handler) {
        super(next);
        wrapped = handler;
    }

    /**
     * Creates a new instance of PipedWrappingHandler
     *
     * @param handler
     */
    private PipedWrappingHandler(HttpHandler handler) {
        super(null);
        wrapped = handler;
    }
    
    /**
     * 
     * @param next
     * @param handler
     * @return the wrapping handler
     */
    public static PipedWrappingHandler wrap(HttpHandler handler) {
        return wrap(null, handler);
    }
    
    /**
     * 
     * @param next
     * @param handler
     * @return the wrapping handler 
     */
    public static PipedWrappingHandler wrap(PipedHttpHandler next, HttpHandler handler) {
        return new PipedWrappingHandler(next, handler);
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
