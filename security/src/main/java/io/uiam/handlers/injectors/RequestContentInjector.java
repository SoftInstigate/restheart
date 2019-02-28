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
package io.uiam.handlers.injectors;

import io.uiam.Bootstrapper;
import io.uiam.Configuration;
import io.uiam.plugins.PluginsRegistry;
import static io.uiam.handlers.exchange.AbstractExchange.MAX_BUFFERS;

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
