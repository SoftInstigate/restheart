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

import io.uiam.Bootstrapper;
import io.uiam.Configuration;
import io.uiam.plugins.PluginsRegistry;

import io.undertow.server.HttpServerExchange;
import java.util.List;
import io.uiam.plugins.interceptors.PluggableRequestInterceptor;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.RequestBufferingHandler;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * injects in the exchange the request content if the request involves a Service
 * or a Request Interceptor whose requiresContent() returns true
 *
 * Note that getting the content has significant performance overhead for
 * proxied resources. To mitigate DoS attacks the injector limits the size of
 * the content to MAX_CONTENT_SIZE bytes
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestContentInjector extends RequestBufferingHandler {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(RequestContentInjector.class);

    public static final int MAX_CONTENT_SIZE = 16 * 1024 * 1024; // 16byte

    public static final int MAX_BUFFERS;

    static {
        MAX_BUFFERS = 1 + (MAX_CONTENT_SIZE / (Bootstrapper.getConfiguration() != null
                ? Bootstrapper.getConfiguration().getBufferSize()
                : 1024));

        LOGGER.info("The maximum size for request content "
                + "is {} bytes.",
                MAX_CONTENT_SIZE,
                MAX_BUFFERS,
                Bootstrapper.getConfiguration() != null
                ? Bootstrapper.getConfiguration().getBufferSize()
                : 16384);
    }

    private HttpHandler next;

    /**
     * @param next
     */
    public RequestContentInjector(HttpHandler next) {
        super(next, MAX_BUFFERS);
        this.next = new BlockingHandler(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (isContentRequiredByAnyRequestInterceptor(exchange)
                || isServiceRequested(exchange)) {

            LOGGER.trace("Request content available for Request.getContent()");
            super.handleRequest(exchange);
        } else {
            LOGGER.trace("Request content is not available for Request.getContent()");
            next.handleRequest(exchange);
        }
    }

    private boolean isContentRequiredByAnyRequestInterceptor(HttpServerExchange exchange) {
        List<PluggableRequestInterceptor> interceptors = PluginsRegistry
                .getInstance()
                .getRequestInterceptors();

        return interceptors.stream()
                .filter(t -> t.resolve(exchange))
                .anyMatch(t -> t.requiresContent());
    }

    private boolean isServiceRequested(HttpServerExchange exchange) {
        return Bootstrapper.getConfiguration().getServices().stream()
                .filter(c -> getServiceURI(c) != null)
                .map(c -> getServiceURI(c))
                .anyMatch(uri
                        -> exchange.getRequestPath().startsWith(uri));
    }

    private String getServiceURI(Map<String, Object> conf) {
        Object _uri = conf.get(Configuration.SERVICE_URI_KEY);

        if (_uri != null && (_uri instanceof String)) {
            return (String) _uri;
        } else {
            return null;
        }
    }
}
