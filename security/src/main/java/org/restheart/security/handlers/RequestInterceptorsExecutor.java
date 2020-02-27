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

import org.restheart.handlers.exchange.AbstractExchange;
import org.restheart.security.plugins.PluginsRegistry;

import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.exchange.ByteArrayResponse;
import org.restheart.security.plugins.RequestInterceptor.IPOINT;
import org.restheart.utils.HttpStatus;
import org.restheart.security.utils.LambdaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestInterceptorsExecutor extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(RequestInterceptorsExecutor.class);

    private final ResponseSender sender = new ResponseSender();

    private final IPOINT interceptPoint;

    /**
     *
     */
    public RequestInterceptorsExecutor(IPOINT interceptPoint) {
        super(null);
        this.interceptPoint = interceptPoint;
    }

    /**
     * @param next
     */
    public RequestInterceptorsExecutor(PipedHttpHandler next, IPOINT interceptPoint) {
        super(next);
        this.interceptPoint = interceptPoint;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        PluginsRegistry
                .getInstance()
                .getRequestInterceptors()
                .stream()
                .filter(ri -> ri.isEnabled())
                .filter(ri -> ri.getInstance().resolve(exchange))
                .filter(ri -> interceptPoint.equals(ri.getInstance().interceptPoint()))
                .forEachOrdered(ri -> {
                    try {
                        LOGGER.debug("Executing request interceptor {} for {} on intercept point {}",
                                ri.getInstance().getClass().getSimpleName(),
                                exchange.getRequestPath(),
                                interceptPoint);

                        ri.getInstance().handleRequest(exchange);
                    } catch (Exception ex) {
                        LOGGER.error("Error executing request interceptor {} for {} on intercept point {}",
                                ri.getInstance().getClass().getSimpleName(),
                                exchange.getRequestPath(),
                                interceptPoint,
                                ex);
                        AbstractExchange.setInError(exchange);
                        LambdaUtils.throwsSneakyExcpetion(ex);
                    }
                });

        // if an interceptor sets the response as errored
        // stop processing the request and send the response
        if (AbstractExchange.isInError(exchange)) {
            var response = ByteArrayResponse.wrap(exchange);
            // if in error but no status code use 400 Bad Request
            if (response.getStatusCode() < 0) {
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            }

            sender.handleRequest(exchange);
        } else {
            next(exchange);
        }
    }
}
