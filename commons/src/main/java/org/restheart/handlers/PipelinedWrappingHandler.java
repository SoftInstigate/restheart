/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.handlers;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.logging.RequestPhaseContext;
import org.restheart.logging.RequestPhaseContext.Phase;
import org.restheart.plugins.Service;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * A specialized PipelinedHandler that wraps standard HttpHandler or Service instances
 * to integrate them into a handler pipeline.
 * 
 * <p>This class acts as an adapter, allowing non-pipelined handlers (standard Undertow
 * {@link HttpHandler} implementations or RESTHeart {@link Service} implementations)
 * to be used within a {@link PipelinedHandler} chain. This is particularly useful when:</p>
 * <ul>
 *   <li>Integrating existing HttpHandler implementations into a pipeline</li>
 *   <li>Using RESTHeart services as part of a processing chain</li>
 *   <li>Mixing pipelined and non-pipelined handlers in the same flow</li>
 * </ul>
 * 
 * <p>The wrapper ensures that after the wrapped handler completes its processing,
 * the request is automatically forwarded to the next handler in the pipeline
 * (unless the response has already been completed).</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Wrap a standard HttpHandler
 * HttpHandler standardHandler = exchange -> {
 *     exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "text/plain");
 *     exchange.getResponseSender().send("Hello");
 * };
 * 
 * PipelinedHandler pipeline = PipelinedHandler.pipe(
 *     new AuthHandler(),
 *     PipelinedWrappingHandler.wrap(standardHandler),
 *     new LoggingHandler()
 * );
 * }</pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see PipelinedHandler
 * @see HttpHandler
 * @see Service
 */
public class PipelinedWrappingHandler extends PipelinedHandler {

    /**
     * The wrapped handler that will be executed when this handler processes a request.
     */
    private final HttpHandler wrapped;

    /**
     * Creates a new instance of PipelinedWrappingHandler with a specified next handler.
     * 
     * <p>This constructor wraps an HttpHandler and links it to the next handler
     * in the pipeline chain.</p>
     *
     * @param next the next handler in the pipeline to execute after the wrapped handler
     * @param handler the HttpHandler to wrap and integrate into the pipeline
     */
    private PipelinedWrappingHandler(PipelinedHandler next, HttpHandler handler) {
        super(next);
        wrapped = handler;
    }

    /**
     * Creates a new instance of PipelinedWrappingHandler that wraps a Service.
     * 
     * <p>This constructor wraps a RESTHeart Service implementation and links it
     * to the next handler in the pipeline chain. The Service is internally
     * wrapped in a ServiceWrapper that adapts it to the HttpHandler interface.</p>
     *
     * @param <R> the type of ServiceRequest the service handles
     * @param <S> the type of ServiceResponse the service produces
     * @param next the next handler in the pipeline to execute after the service
     * @param service the Service to wrap and integrate into the pipeline
     */
    private <R extends ServiceRequest<?>, S extends ServiceResponse<?>> PipelinedWrappingHandler(PipelinedHandler next, Service<R, S> service) {
        super(next);
        wrapped = new ServiceWrapper<>(service);
    }

    /**
     * Creates a new instance of PipelinedWrappingHandler without a next handler.
     * 
     * <p>This constructor creates a terminal wrapper that doesn't forward
     * requests to any subsequent handler after the wrapped handler completes.</p>
     *
     * @param handler the HttpHandler to wrap
     */
    private PipelinedWrappingHandler(HttpHandler handler) {
        super(null);
        wrapped = handler;
    }

    /**
     * Creates a PipelinedWrappingHandler that wraps an HttpHandler.
     * 
     * <p>This factory method creates a wrapper with no next handler,
     * making it suitable as the last handler in a pipeline.</p>
     *
     * @param handler the HttpHandler to wrap
     * @return a new PipelinedWrappingHandler wrapping the provided handler
     */
    public static PipelinedWrappingHandler wrap(HttpHandler handler) {
        return wrap(null, handler);
    }

    /**
     * Creates a PipelinedWrappingHandler that wraps a Service.
     * 
     * <p>This factory method creates a wrapper with no next handler,
     * making it suitable as the last handler in a pipeline. The Service
     * is adapted to work within the HttpHandler interface.</p>
     *
     * @param <R> the type of ServiceRequest the service handles
     * @param <S> the type of ServiceResponse the service produces
     * @param service the Service to wrap
     * @return a new PipelinedWrappingHandler wrapping the provided service
     */
    public static <R extends ServiceRequest<?>, S extends ServiceResponse<?>> PipelinedWrappingHandler wrap(Service<R, S> service) {
        return wrap(null, service);
    }

    /**
     * Creates a PipelinedWrappingHandler that wraps an HttpHandler with a specified next handler.
     * 
     * <p>This factory method creates a wrapper that will forward requests to the
     * specified next handler after the wrapped handler completes (unless the
     * response is already complete).</p>
     *
     * @param next the next handler in the pipeline
     * @param handler the HttpHandler to wrap
     * @return a new PipelinedWrappingHandler wrapping the provided handler
     */
    public static PipelinedWrappingHandler wrap(PipelinedHandler next, HttpHandler handler) {
        return new PipelinedWrappingHandler(next, handler);
    }

    /**
     * Creates a PipelinedWrappingHandler that wraps a Service with a specified next handler.
     * 
     * <p>This factory method creates a wrapper that will forward requests to the
     * specified next handler after the service completes (unless the response
     * is already complete). The Service is adapted to work within the
     * HttpHandler interface.</p>
     *
     * @param <R> the type of ServiceRequest the service handles
     * @param <S> the type of ServiceResponse the service produces
     * @param next the next handler in the pipeline
     * @param service the Service to wrap
     * @return a new PipelinedWrappingHandler wrapping the provided service
     */
    public static <R extends ServiceRequest<?>, S extends ServiceResponse<?>> PipelinedWrappingHandler wrap(PipelinedHandler next, Service<R, S> service) {
        return new PipelinedWrappingHandler(next, service);
    }

    /**
     * Handles the HTTP request by delegating to the wrapped handler.
     * 
     * <p>This method executes the wrapped handler and then, if the response
     * is not already complete, forwards the request to the next handler
     * in the pipeline. This allows wrapped handlers to:</p>
     * <ul>
     *   <li>Process and modify the request before it continues down the pipeline</li>
     *   <li>Complete the response entirely, stopping further processing</li>
     *   <li>Add headers or other modifications that subsequent handlers can use</li>
     * </ul>
     * 
     * <p>The response is considered complete if the wrapped handler has
     * already sent response data or explicitly marked it as complete.</p>
     *
     * @param exchange the HTTP server exchange to process
     * @throws Exception if the wrapped handler or next handler throws an exception
     */
    /**
     * Handles the request by converting it to the service's expected types.
     * 
     * <p>This method:
     * <ol>
     *   <li>Applies the service's request transformer to create a typed request object</li>
     *   <li>Applies the service's response transformer to create a typed response object</li>
     *   <li>Invokes the service's handle method with the typed objects</li>
     * </ol>
     * 
     * @param exchange the HTTP server exchange to process
     * @throws Exception if the service or its transformers throw an exception
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

/**
 * Internal adapter class that wraps a RESTHeart Service as a PipelinedHandler.
 * 
 * <p>This class bridges the gap between the Service interface (which uses
 * typed request/response objects) and the HttpHandler interface (which uses
 * HttpServerExchange). It applies the service's request and response
 * transformers to convert between the two models.</p>
 * 
 * @param <R> the type of ServiceRequest the service handles
 * @param <S> the type of ServiceResponse the service produces
 */
class ServiceWrapper<R extends ServiceRequest<?>, S extends ServiceResponse<?>> extends PipelinedHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceWrapper.class);
    
    /**
     * The wrapped service instance.
     */
    final Service<R, S> service;

    /**
     * Creates a new ServiceWrapper for the specified service.
     * 
     * @param service the service to wrap
     */
    ServiceWrapper(Service<R, S> service) {
        this.service = service;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var path = exchange.getRequestPath();
        var method = exchange.getRequestMethod().toString();
        var serviceName = PluginUtils.name(service);
        var serviceClass = service.getClass().getSimpleName();
        var startTime = System.currentTimeMillis();
        
        RequestPhaseContext.setPhase(Phase.PHASE_START);
        LOGGER.debug("SERVICE: {} ({})", serviceName, serviceClass);
            
        try {
            // Apply service transformers and execute
            var serviceRequest = service.request().apply(exchange);
            var serviceResponse = service.response().apply(exchange);
            
            RequestPhaseContext.setPhase(Phase.INFO);
            LOGGER.debug("Request/Response: {} → {}", 
                serviceRequest.getClass().getSimpleName(), serviceResponse.getClass().getSimpleName());
            
            service.handle(serviceRequest, serviceResponse);
            
            var duration = System.currentTimeMillis() - startTime;
            var statusCode = serviceResponse.getStatusCode();
            
            RequestPhaseContext.setPhase(Phase.PHASE_END);
            LOGGER.debug("✓ SERVICE COMPLETED - Status: {} ({}ms)", statusCode, duration);
            RequestPhaseContext.reset();
                
        } catch (Exception ex) {
            var duration = System.currentTimeMillis() - startTime;
            RequestPhaseContext.setPhase(Phase.PHASE_END);
            LOGGER.error("Service execution failed: {} for {} {} after {}ms - Thread: {}", 
                serviceName, method, path, duration, Thread.currentThread().getName(), ex);
            RequestPhaseContext.reset();
            throw ex;
        }
    }
}
