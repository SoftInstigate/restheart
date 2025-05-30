/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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

import java.util.Arrays;
import java.util.Objects;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Abstract base class for implementing handlers that can be chained together in a pipeline.
 * 
 * <p>PipelinedHandler extends Undertow's {@link HttpHandler} interface to provide
 * a chain-of-responsibility pattern implementation. Each handler in the pipeline
 * can process the request and optionally pass it to the next handler in the chain.</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Handlers can be linked together to form a processing pipeline</li>
 *   <li>Each handler can decide whether to pass control to the next handler</li>
 *   <li>Supports dynamic pipeline construction via the {@link #pipe(PipelinedHandler...)} method</li>
 *   <li>Null-safe chaining (null handlers are automatically filtered out)</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * PipelinedHandler pipeline = PipelinedHandler.pipe(
 *     new AuthenticationHandler(),
 *     new AuthorizationHandler(),
 *     new BusinessLogicHandler()
 * );
 * }</pre>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see HttpHandler
 * @see PipelinedWrappingHandler
 */
public abstract class PipelinedHandler implements HttpHandler {
    /**
     * Constant for content type attribute name.
     * Can be used as a key for storing content type information in exchange attributes.
     */
    protected static final String CONTENT_TYPE = "contentType";

    /**
     * Reference to the next handler in the pipeline chain.
     * Will be null if this is the last handler in the chain.
     */
    private PipelinedHandler next;

    /**
     * Creates a new PipelinedHandler with no next handler in the chain.
     * 
     * <p>This constructor creates a terminal handler that doesn't forward
     * requests to any subsequent handler. Use this when creating the last
     * handler in a pipeline or when the next handler will be set later
     * using {@link #setNext(PipelinedHandler)}.</p>
     */
    public PipelinedHandler() {
        this(null);
    }

    /**
     * Creates a new PipelinedHandler with a specified next handler in the chain.
     * 
     * <p>This constructor establishes the link to the next handler that will
     * receive the request after this handler completes its processing
     * (if it chooses to forward the request).</p>
     *
     * @param next the next handler in the pipeline chain, or null if this is the last handler
     */
    public PipelinedHandler(PipelinedHandler next) {
        this.next = next;
    }

    /**
     * Handles an HTTP request in the pipeline.
     * 
     * <p>Implementations should process the request and optionally forward it
     * to the next handler by calling {@link #next(HttpServerExchange)}.
     * The handler can choose to:</p>
     * <ul>
     *   <li>Process and forward the request to the next handler</li>
     *   <li>Process and complete the response without forwarding</li>
     *   <li>Conditionally forward based on request attributes</li>
     *   <li>Modify the request before forwarding</li>
     * </ul>
     * 
     * <p>Example implementation:</p>
     * <pre>{@code
     * public void handleRequest(HttpServerExchange exchange) throws Exception {
     *     // Process request
     *     exchange.getRequestHeaders().add(Headers.CUSTOM, "value");
     *     
     *     // Forward to next handler
     *     next(exchange);
     * }
     * }</pre>
     *
     * @param exchange the HTTP server exchange containing request and response information
     * @throws Exception if an error occurs during request processing
     */
    @Override
    public abstract void handleRequest(HttpServerExchange exchange) throws Exception;

    /**
     * Gets the next handler in the pipeline chain.
     * 
     * <p>This method provides access to the next handler reference,
     * which can be useful for conditional forwarding or pipeline introspection.</p>
     * 
     * @return the next handler in the chain, or null if this is the last handler
     */
    protected PipelinedHandler getNext() {
        return next;
    }

    /**
     * Sets the next handler in the pipeline chain.
     * 
     * <p>This method allows dynamic modification of the pipeline by changing
     * the next handler reference. It can be used to:</p>
     * <ul>
     *   <li>Build pipelines programmatically</li>
     *   <li>Insert handlers into an existing pipeline</li>
     *   <li>Remove handlers by bypassing them</li>
     * </ul>
     *
     * @param next the handler to set as the next in the chain, or null to make this the last handler
     */
    protected void setNext(PipelinedHandler next) {
        this.next = next;
    }

    /**
     * Forwards the request to the next handler in the pipeline.
     * 
     * <p>This convenience method safely forwards the request to the next handler
     * if one exists. If there is no next handler (i.e., this is the last handler
     * in the chain), the method returns without throwing an exception.</p>
     * 
     * <p>Handlers typically call this method at the end of their
     * {@link #handleRequest(HttpServerExchange)} implementation to continue
     * the pipeline processing.</p>
     *
     * @param exchange the HTTP server exchange to forward
     * @throws Exception if the next handler throws an exception during processing
     */
    protected void next(HttpServerExchange exchange) throws Exception {
        if (this.next != null) {
            this.next.handleRequest(exchange);
        }
    }

    /**
     * Creates a pipeline by chaining multiple handlers together in sequence.
     * 
     * <p>This static factory method constructs a pipeline from an array of handlers,
     * linking each handler to the next in the order provided. The method is null-safe
     * and automatically filters out any null handlers from the pipeline.</p>
     * 
     * <p>Example usage:</p>
     * <pre>{@code
     * PipelinedHandler pipeline = PipelinedHandler.pipe(
     *     new LoggingHandler(),
     *     new AuthenticationHandler(),
     *     new CompressionHandler(),
     *     new BusinessLogicHandler()
     * );
     * 
     * // The above creates a pipeline: 
     * // LoggingHandler -> AuthenticationHandler -> CompressionHandler -> BusinessLogicHandler
     * }</pre>
     * 
     * <p>Features:</p>
     * <ul>
     *   <li>Null handlers are automatically removed from the pipeline</li>
     *   <li>Returns null if all handlers are null or no handlers are provided</li>
     *   <li>Preserves the order of non-null handlers</li>
     *   <li>Each handler's next reference is set to point to the subsequent handler</li>
     * </ul>
     *
     * @param handlers varargs array of handlers to chain together
     * @return the first handler in the pipeline, or null if no valid handlers were provided
     */
    public static PipelinedHandler pipe(PipelinedHandler... handlers) {
        if (Objects.isNull(handlers)) {
            return null;
        }

        // remove null entries in handlers array
        handlers = Arrays.stream(handlers)
            .filter(s -> s != null)
            .toArray(PipelinedHandler[]::new);

        for (var idx = 0; idx < handlers.length - 1; idx++) {
            handlers[idx].setNext(handlers[idx + 1]);
        }

        return handlers[0];
    }
}
