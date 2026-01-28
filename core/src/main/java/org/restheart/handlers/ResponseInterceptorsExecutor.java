/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

import java.util.List;

import org.restheart.exchange.ByteArrayProxyRequest;
import org.restheart.exchange.ByteArrayProxyResponse;
import org.restheart.exchange.Exchange;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.logging.RequestPhaseContext;
import org.restheart.logging.RequestPhaseContext.Phase;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.InterceptorException;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.plugins.Service;
import org.restheart.utils.LambdaUtils;
import org.restheart.utils.PluginUtils;
import static org.restheart.utils.PluginUtils.requiresContent;
import org.restheart.utils.ThreadsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.undertow.server.HttpServerExchange;

/**
 * Executes response interceptors
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseInterceptorsExecutor extends PipelinedHandler {
    static final Logger LOGGER = LoggerFactory.getLogger(ResponseInterceptorsExecutor.class);

    private final boolean filterRequiringContent;

    private final PluginsRegistry pluginsRegistry = PluginsRegistryImpl.getInstance();

    public ResponseInterceptorsExecutor() {
        this(null, false);
    }

    public ResponseInterceptorsExecutor(boolean filterRequiringContent) {
        this(null, filterRequiringContent);
    }

    /**
     * Construct a new instance.
     *
     * @param next
     * @param filterRequiringContent if true does not execute the interceptors that
     *                               require content
     */
    public ResponseInterceptorsExecutor(PipelinedHandler next, boolean filterRequiringContent) {
        super(next);
        this.filterRequiringContent = filterRequiringContent;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        Request request;
        Response response;

        var handlingService = PluginUtils.handlingService(pluginsRegistry, exchange);

        if (handlingService != null) {
            request = ServiceRequest.of(exchange, ServiceRequest.class);
            response = ServiceResponse.of(exchange, ServiceResponse.class);
        } else {
            request = ByteArrayProxyRequest.of(exchange);
            response = ByteArrayProxyResponse.of(exchange);
        }

        if (!Exchange.responseInterceptorsExecuted(exchange)) {
            Exchange.setResponseInterceptorsExecuted(exchange);
            executeAsyncResponseInterceptor(exchange, handlingService, request, response);
            executeResponseInterceptor(exchange, handlingService, request, response);
        }

        next(exchange);
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private void executeResponseInterceptor(HttpServerExchange exchange, Service handlingService, Request request, Response response) {

        Exchange.setResponseInterceptorsExecuted(exchange);
        
        var requestPath = exchange.getRequestPath();
        var requestMethod = exchange.getRequestMethod().toString();
        var statusCode = response.getStatusCode();

        List<Interceptor<?,?>> inteceptors;

        if (handlingService != null) {
            inteceptors = this.pluginsRegistry.getServiceInterceptors(handlingService, InterceptPoint.RESPONSE);
        } else {
            inteceptors = this.pluginsRegistry.getProxyInterceptors(InterceptPoint.RESPONSE);
        }

        var applicableInterceptors = inteceptors.stream()
            .filter(ri -> ri instanceof Interceptor)
            .map(ri -> (Interceptor) ri)
            .filter(ri -> !this.filterRequiringContent || !requiresContent(ri))
            .filter(ri -> {
                try {
                    return ri.resolve(request, response);
                } catch (Exception ex) {
                    LOGGER.warn("Error resolving response interceptor {} for {} {}", 
                        ri.getClass().getSimpleName(), requestMethod, requestPath, ex);
                    return false;
                }
            })
            .collect(java.util.stream.Collectors.toList());

        if (applicableInterceptors.isEmpty()) {
            RequestPhaseContext.setPhase(Phase.PHASE_START);
            LOGGER.debug("RESPONSE INTERCEPTORS");
            RequestPhaseContext.setPhase(Phase.INFO);
            LOGGER.debug("No applicable interceptors");
            RequestPhaseContext.setPhase(Phase.PHASE_END);
            LOGGER.debug("RESPONSE COMPLETED in 0ms");
            RequestPhaseContext.reset();
            return;
        }

        RequestPhaseContext.setPhase(Phase.PHASE_START);
        LOGGER.debug("RESPONSE INTERCEPTORS");
        RequestPhaseContext.setPhase(Phase.INFO);
        LOGGER.debug("Found {} applicable interceptors", applicableInterceptors.size());

        var executionStartTime = System.currentTimeMillis();
        
        applicableInterceptors.forEach(ri -> {
            var interceptorStartTime = System.currentTimeMillis();
            var interceptorName = PluginUtils.name(ri);
            
            RequestPhaseContext.setPhase(Phase.ITEM);
            LOGGER.debug("{} (priority: {})", interceptorName, PluginUtils.priority(ri));

            try {
                ri.handle(request, response);
                
                var interceptorDuration = System.currentTimeMillis() - interceptorStartTime;
                RequestPhaseContext.setPhase(Phase.SUBITEM);
                LOGGER.debug("✓ {}ms", interceptorDuration);
            } catch (Exception ex) {
                var interceptorDuration = System.currentTimeMillis() - interceptorStartTime;
                RequestPhaseContext.setPhase(Phase.SUBITEM);
                LOGGER.error("✗ FAILED after {}ms: {}", interceptorDuration, ex.getMessage());

                Exchange.setInError(exchange);
                LambdaUtils.throwsSneakyException(new InterceptorException("Error executing interceptor " + ri.getClass().getSimpleName(), ex));
            }
        });
            
        var totalDuration = System.currentTimeMillis() - executionStartTime;
        RequestPhaseContext.setPhase(Phase.PHASE_END);
        LOGGER.debug("RESPONSE COMPLETED in {}ms", totalDuration);
        RequestPhaseContext.reset();
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private void executeAsyncResponseInterceptor(HttpServerExchange exchange, Service handlingService, Request request, Response response) {

        var requestPath = exchange.getRequestPath();
        var requestMethod = exchange.getRequestMethod().toString();
        var statusCode = response.getStatusCode();

        List<Interceptor<?, ?>> inteceptors;

        if (handlingService != null) {
            inteceptors = this.pluginsRegistry.getServiceInterceptors(handlingService, InterceptPoint.RESPONSE_ASYNC);
        } else {
            inteceptors = this.pluginsRegistry.getProxyInterceptors(InterceptPoint.RESPONSE_ASYNC);
        }

        var applicableAsyncInterceptors = inteceptors.stream()
            .filter(ri -> ri instanceof Interceptor)
            .map(ri -> (Interceptor) ri)
            .filter(ri -> !this.filterRequiringContent || !requiresContent(ri))
            .filter(ri -> {
                try {
                    return ri.resolve(request, response);
                } catch (Exception ex) {
                    LOGGER.warn("Error resolving async interceptor {} for {} {}", 
                        ri.getClass().getSimpleName(), requestMethod, requestPath, ex);
                    return false;
                }
            })
            .collect(java.util.stream.Collectors.toList());

        if (!applicableAsyncInterceptors.isEmpty()) {
            RequestPhaseContext.setPhase(Phase.PHASE_START);
            LOGGER.debug("RESPONSE_ASYNC INTERCEPTORS");
            RequestPhaseContext.setPhase(Phase.INFO);
            LOGGER.debug("Found {} applicable async interceptors", applicableAsyncInterceptors.size());
        }

        Exchange.setResponseInterceptorsExecuted(exchange);

        applicableAsyncInterceptors.forEach(ri -> {
            // Retrieve MDC context from the exchange attachment (set by TracingInstrumentationHandler)
            var mdcContext = response.getMDCContext();
            
            ThreadsUtils.virtualThreadsExecutor().execute(() -> {
                // Restore MDC context in the virtual thread
                if (mdcContext != null) {
                    MDC.setContextMap(mdcContext);
                }
                
                try {
                    // Use ScopedValue to propagate exchange to the async virtual thread
                    RequestPhaseContext.callWithCurrentExchange(() -> {
                        var interceptorStartTime = System.currentTimeMillis();
                        var interceptorName = PluginUtils.name(ri);
                        
                        RequestPhaseContext.setPhase(Phase.ITEM);
                        LOGGER.debug("ASYNC {} (priority: {}, thread: {})", 
                            interceptorName, PluginUtils.priority(ri), Thread.currentThread().getName());

                        try {
                            ri.handle(request, response);
                            
                            var interceptorDuration = System.currentTimeMillis() - interceptorStartTime;
                            RequestPhaseContext.setPhase(Phase.SUBITEM);
                            LOGGER.debug("✓ {}ms (async)", interceptorDuration);
                        } catch (Exception ex) {
                            var interceptorDuration = System.currentTimeMillis() - interceptorStartTime;
                            RequestPhaseContext.setPhase(Phase.SUBITEM);
                            LOGGER.error("✗ ASYNC FAILED after {}ms: {}", interceptorDuration, ex.getMessage());

                            Exchange.setInError(exchange);
                            LambdaUtils.throwsSneakyException(new InterceptorException("Error executing async interceptor " + ri.getClass().getSimpleName(), ex));
                        }
                        RequestPhaseContext.reset();
                    });
                } finally {
                    // Clean up MDC context after execution
                    MDC.clear();
                }
            });
        });
        
        if (!applicableAsyncInterceptors.isEmpty()) {
            RequestPhaseContext.setPhase(Phase.PHASE_END);
            LOGGER.debug("RESPONSE_ASYNC dispatched to {} virtual threads", applicableAsyncInterceptors.size());
            RequestPhaseContext.reset();
        }
    }
}
