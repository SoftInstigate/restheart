/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.handlers.injectors;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.RequestBufferingHandler;
import io.undertow.util.AttachmentKey;
import static org.restheart.exchange.AbstractExchange.MAX_BUFFERS;
import org.restheart.exchange.AbstractRequest;
import org.restheart.exchange.AbstractResponse;
import org.restheart.exchange.BufferedByteArrayRequest;
import org.restheart.exchange.BufferedByteArrayResponse;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.handlers.PipelinedHandler;
import static org.restheart.handlers.injectors.RequestContentInjector.Policy.ALWAYS;
import static org.restheart.handlers.injectors.RequestContentInjector.Policy.ON_REQUIRES_CONTENT_AFTER_AUTH;
import static org.restheart.handlers.injectors.RequestContentInjector.Policy.ON_REQUIRES_CONTENT_BEFORE_AUTH;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.utils.PluginUtils;
import static org.restheart.utils.PluginUtils.interceptPoint;
import static org.restheart.utils.PluginUtils.requiresContent;
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
public class RequestContentInjector extends PipelinedHandler {
    public enum Policy {
        ALWAYS,
        ON_REQUIRES_CONTENT_BEFORE_AUTH,
        ON_REQUIRES_CONTENT_AFTER_AUTH
    }

    private static final Logger LOGGER = LoggerFactory
            .getLogger(RequestContentInjector.class);

    private final Policy policy;

    private HttpHandler bufferingHandler = null;

    /**
     * @param next
     * @param policy set the injection policy
     */
    public RequestContentInjector(PipelinedHandler next, Policy policy) {
        super(next);
        this.bufferingHandler = new RequestBufferingHandler(next, MAX_BUFFERS);
        this.policy = policy;
    }

    /**
     * @param policy set the injection policy
     */
    public RequestContentInjector(Policy policy) {
        super();
        this.bufferingHandler = null;
        this.policy = policy;
    }

    @Override
    protected void setNext(PipelinedHandler next) {
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
        if (this.bufferingHandler == null) {
            throw new IllegalStateException("Cannot invoke handleRequest next "
                    + "if not set via setNext()");
        }

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
                && isContentRequired(exchange, InterceptPoint.REQUEST_AFTER_AUTH))
                || (policy == ON_REQUIRES_CONTENT_BEFORE_AUTH
                && isContentRequired(exchange, InterceptPoint.REQUEST_BEFORE_AUTH)));
    }

    @SuppressWarnings("unchecked")
    private boolean isContentRequired(HttpServerExchange exchange,
            InterceptPoint interceptPoint) {
        AbstractRequest request;
        AbstractResponse response;

        var handlingService = PluginUtils.handlingService(
                PluginsRegistryImpl.getInstance(),
                exchange);

        if (handlingService != null) {
            request = Request.of(exchange, Request.class);
            response = Response.of(exchange, Response.class);
        } else {
            request = BufferedByteArrayRequest.of(exchange);
            response = BufferedByteArrayResponse.of(exchange);
        }
        
        return PluginsRegistryImpl
                .getInstance()
                .getInterceptors().stream()
                .filter(ri -> ri.isEnabled())
                .map(ri -> ri.getInstance())
                .filter(ri -> interceptPoint == interceptPoint(ri))
                // IMPORTANT: An interceptor can intercept 
                // - requests handled by a Service when its request and response 
                //   types are equal to the ones declared by the Service
                // - request handled by a Proxy when its request and response 
                //   are BufferedByteArrayRequest and BufferedByteArrayResponse
                .filter(ri -> 
                        (handlingService == null
                && ri.requestType().equals(BufferedByteArrayRequest.type())
                && ri.responseType().equals(BufferedByteArrayResponse.type()))
                || (handlingService != null
                && ri.requestType().equals(handlingService.requestType())
                && ri.responseType().equals(handlingService.responseType())))
                .filter(ri -> {
                    try {
                        return ri.resolve(request, response);
                    } catch (Exception e) {
                        LOGGER.warn("Error resolving interceptor {} for {} on intercept point {}",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath(),
                                interceptPoint,
                                e);

                        return false;
                    }
                })
                .anyMatch(ri -> requiresContent(ri));
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
