/*
 * uIAM - the IAM for microservices
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
package io.uiam.handlers;


import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestNotManagedHandler extends PipedHttpHandler {
    /**
     * Creates a new instance of RequestProxyHandler
     *
     */
    public RequestNotManagedHandler() {
        super(null);
    }

    /**
     * Creates a new instance of RequestProxyHandler
     *
     * @param next
     */
    public RequestNotManagedHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     * Creates a new instance of RequestProxyHandler
     *
     * @param handler
     */
    public RequestNotManagedHandler(HttpHandler handler) {
        super(null);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.setStatusCode(StatusCodes.BAD_GATEWAY);
        exchange.endExchange();
    }
}
