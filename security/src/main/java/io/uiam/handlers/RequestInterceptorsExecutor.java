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

import io.uiam.handlers.exchange.AbstractExchange;
import io.uiam.handlers.exchange.ByteArrayRequest;
import io.uiam.plugins.PluginsRegistry;

import io.undertow.server.HttpServerExchange;
import java.util.List;
import io.uiam.plugins.interceptors.PluggableRequestInterceptor;
import io.uiam.utils.BuffersUtils;
import io.uiam.utils.LambdaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestInterceptorsExecutor extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(RequestInterceptorsExecutor.class);

    /**
     *
     */
    public RequestInterceptorsExecutor() {
        super(null);
    }

    /**
     * @param next
     */
    public RequestInterceptorsExecutor(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<PluggableRequestInterceptor> interceptors = PluginsRegistry
                .getInstance()
                .getRequestInterceptors();

        interceptors.stream()
                .filter(ri -> ri.resolve(exchange))
                .forEachOrdered(ri -> {
                    try {
                        LOGGER.debug("Executing request interceptor {} for {}",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath());
                        
                        ri.handleRequest(exchange);
                    }
                    catch (Exception ex) {
                        LOGGER.error("Error executing request interceptor {} for {}",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath(),
                                ex);
                        AbstractExchange.setInError(exchange);
                        LambdaUtils.throwsSneakyExcpetion(ex);
                    }
                });

        next(exchange);
    }
}
