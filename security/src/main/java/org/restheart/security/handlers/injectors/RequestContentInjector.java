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
package org.restheart.security.handlers.injectors;

import io.undertow.server.HttpHandler;
import static org.restheart.handlers.exchange.AbstractExchange.MAX_BUFFERS;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.RequestBufferingHandler;
import io.undertow.util.AttachmentKey;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.plugins.security.RequestInterceptor;
import static org.restheart.plugins.security.RequestInterceptor.IPOINT.AFTER_AUTH;
import static org.restheart.plugins.security.RequestInterceptor.IPOINT.BEFORE_AUTH;
import static org.restheart.security.handlers.injectors.RequestContentInjector.Policy.ALWAYS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.restheart.security.handlers.injectors.RequestContentInjector.Policy.ON_REQUIRES_CONTENT_BEFORE_AUTH;
import static org.restheart.security.handlers.injectors.RequestContentInjector.Policy.ON_REQUIRES_CONTENT_AFTER_AUTH;
import org.restheart.security.plugins.PluginsRegistry;

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
public class RequestContentInjector extends PipedHttpHandler {
    public enum Policy {
        ALWAYS,
        ON_REQUIRES_CONTENT_BEFORE_AUTH,
        ON_REQUIRES_CONTENT_AFTER_AUTH
    }

    private static final Logger LOGGER = LoggerFactory
            .getLogger(RequestContentInjector.class);

    private final Policy policy;

    private HttpHandler bufferingHandler;

    /**
     * @param next
     * @param policy set the injection policy
     */
    public RequestContentInjector(PipedHttpHandler next, Policy policy) {
        super(next);
        this.bufferingHandler = new RequestBufferingHandler(next, MAX_BUFFERS);
        this.policy = policy;
    }

    /**
     * @param policy set the injection policy
     * ù
     */
    public RequestContentInjector(Policy policy) {
        super();
        this.bufferingHandler = null;
        this.policy = policy;
    }

    @Override
    protected void setNext(PipedHttpHandler next) {
        super.setNext(next);
        this.bufferingHandler = new RequestBufferingHandler(next, MAX_BUFFERS);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (shallInject(exchange, this.policy)) {

            LOGGER.trace("Request content available for Request.getContent()");

            markInjected(exchange);
            bufferingHandler.handleRequest(exchange);
        } else {
            LOGGER.trace("Request content is not available for Request.getContent()");
            next(exchange);
        }
    }

    private boolean shallInject(HttpServerExchange exchange, Policy policy) {
        return !isAlreadyInjected(exchange) && (policy == ALWAYS
                || (policy == ON_REQUIRES_CONTENT_AFTER_AUTH
                && isContentRequired(exchange, AFTER_AUTH))
                || (policy == ON_REQUIRES_CONTENT_BEFORE_AUTH
                && isContentRequired(exchange, BEFORE_AUTH)));
    }

    private boolean isContentRequired(HttpServerExchange exchange, RequestInterceptor.IPOINT interceptPoint) {
        return PluginsRegistry
                .getInstance()
                .getRequestInterceptors().stream()
                .filter(ri -> ri.isEnabled())
                .filter(ri -> ri.getInstance().resolve(exchange))
                .filter(ri -> interceptPoint.equals(ri.getInstance().interceptPoint()))
                .anyMatch(ri -> ri.getInstance().requiresContent());
    }
    
    private static final AttachmentKey<Boolean> INJECTED_KEY
            = AttachmentKey.create(Boolean.class);
    
    private void markInjected(HttpServerExchange exchange) {
        exchange
                .putAttachment(INJECTED_KEY, true);
    }
    
    private boolean isAlreadyInjected(HttpServerExchange exchange) {
        return exchange.getAttachment(INJECTED_KEY) != null
                && exchange.getAttachment(INJECTED_KEY);
    }
  
}
