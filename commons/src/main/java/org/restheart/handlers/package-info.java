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

/**
 * Provides handler implementations for building HTTP request processing pipelines.
 * 
 * <p>This package contains the core handler infrastructure for RESTHeart's request
 * processing pipeline. It extends Undertow's handler model with a chain-of-responsibility
 * pattern that allows handlers to be composed into sophisticated processing pipelines.</p>
 * 
 * <h2>Core Concepts</h2>
 * 
 * <h3>Pipeline Architecture</h3>
 * <p>The package implements a pipeline pattern where HTTP requests flow through a series
 * of handlers, each performing specific processing tasks. Handlers can:</p>
 * <ul>
 *   <li>Process and modify requests</li>
 *   <li>Generate responses</li>
 *   <li>Conditionally forward requests to the next handler</li>
 *   <li>Short-circuit the pipeline by completing the response</li>
 * </ul>
 * 
 * <h3>Handler Types</h3>
 * <ul>
 *   <li><strong>{@link org.restheart.handlers.PipelinedHandler}:</strong> Abstract base class
 *       for handlers that can be chained together in a pipeline</li>
 *   <li><strong>{@link org.restheart.handlers.PipelinedWrappingHandler}:</strong> Adapter that
 *       wraps standard Undertow HttpHandlers or RESTHeart Services for use in pipelines</li>
 *   <li><strong>{@link org.restheart.handlers.QueryStringRebuilder}:</strong> Specialized handler
 *       for query string manipulation and encoding</li>
 * </ul>
 * 
 * <h2>Key Features</h2>
 * <ul>
 *   <li><strong>Composability:</strong> Handlers can be easily combined using the
 *       {@code PipelinedHandler.pipe()} method</li>
 *   <li><strong>Flexibility:</strong> Each handler decides whether to forward requests
 *       to the next handler in the chain</li>
 *   <li><strong>Integration:</strong> Seamless integration with existing Undertow handlers
 *       and RESTHeart services through wrapping</li>
 *   <li><strong>Null Safety:</strong> Automatic filtering of null handlers in pipelines</li>
 * </ul>
 * 
 * <h2>Usage Examples</h2>
 * 
 * <h3>Creating a Simple Pipeline</h3>
 * <pre>{@code
 * PipelinedHandler pipeline = PipelinedHandler.pipe(
 *     new AuthenticationHandler(),
 *     new AuthorizationHandler(),
 *     new LoggingHandler(),
 *     new BusinessLogicHandler()
 * );
 * }</pre>
 * 
 * <h3>Wrapping Existing Handlers</h3>
 * <pre>{@code
 * HttpHandler existingHandler = exchange -> {
 *     exchange.getResponseSender().send("Hello World");
 * };
 * 
 * PipelinedHandler pipeline = PipelinedHandler.pipe(
 *     new QueryStringRebuilder(),
 *     PipelinedWrappingHandler.wrap(existingHandler)
 * );
 * }</pre>
 * 
 * <h3>Custom Handler Implementation</h3>
 * <pre>{@code
 * public class CustomHandler extends PipelinedHandler {
 *     @Override
 *     public void handleRequest(HttpServerExchange exchange) throws Exception {
 *         // Process the request
 *         exchange.getRequestHeaders().add(Headers.CUSTOM, "value");
 *         
 *         // Conditionally forward to next handler
 *         if (shouldContinue(exchange)) {
 *             next(exchange);
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <h2>Best Practices</h2>
 * <ul>
 *   <li><strong>Single Responsibility:</strong> Each handler should focus on a single
 *       aspect of request processing</li>
 *   <li><strong>Stateless Design:</strong> Handlers should be stateless and thread-safe</li>
 *   <li><strong>Error Handling:</strong> Propagate exceptions to allow centralized
 *       error handling</li>
 *   <li><strong>Performance:</strong> Minimize processing in handlers that run for
 *       every request</li>
 * </ul>
 * 
 * <h2>Query String Handling</h2>
 * <p>The {@link org.restheart.handlers.QueryStringRebuilder} provides special handling
 * for query parameters, ensuring that:</p>
 * <ul>
 *   <li>Original query strings are preserved for reference</li>
 *   <li>Modified parameters are properly encoded</li>
 *   <li>The query string reflects any parameter changes made by interceptors</li>
 * </ul>
 * 
 * @since 1.0
 * @see org.restheart.handlers.PipelinedHandler
 * @see org.restheart.handlers.PipelinedWrappingHandler
 * @see org.restheart.handlers.QueryStringRebuilder
 * @see io.undertow.server.HttpHandler
 */
package org.restheart.handlers;