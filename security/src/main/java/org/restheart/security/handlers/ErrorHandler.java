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
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.ByteArrayResponse;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ErrorHandler implements HttpHandler {

    private final HttpHandler next;

    private final PipelinedHandler sender = new ResponseSender(null);

    private final Logger LOGGER = LoggerFactory.getLogger(ErrorHandler.class);

    /**
     * Creates a new instance of ErrorHandler
     *
     * @param next
     */
    public ErrorHandler(HttpHandler next) {
        this.next = next;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        try {
            next.handleRequest(exchange);
        } catch (Exception t) {
            LOGGER.error("Error handling the request", t);

            ByteArrayResponse.wrap(exchange).endExchangeWithMessage(
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Error handling the request, see log for more information", t);

            sender.handleRequest(exchange);
        }
    }
}
