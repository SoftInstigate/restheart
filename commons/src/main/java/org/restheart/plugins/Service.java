/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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
package org.restheart.plugins;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.restheart.exchange.CORSHeaders;
import org.restheart.exchange.Response;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.utils.HttpStatus;

import io.undertow.server.HttpServerExchange;

/**
 * Interface for implementing RESTHeart services that handle HTTP requests.
 * <p>
 * Services are plugins that extend the RESTHeart API by providing custom web service
 * endpoints. They can handle any HTTP method and implement custom business logic,
 * data processing, or integration with external systems.
 * </p>
 * <p>
 * Services are registered with the RESTHeart framework using the {@link RegisterPlugin}
 * annotation, which defines their URI mapping, security requirements, and execution
 * parameters. Each service is responsible for:
 * </p>
 * <ul>
 *   <li>Processing incoming HTTP requests</li>
 *   <li>Generating appropriate HTTP responses</li>
 *   <li>Handling request and response object initialization</li>
 *   <li>Implementing CORS headers if needed</li>
 * </ul>
 * <p>
 * Example implementation:
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "myService",
 *     description = "A custom service",
 *     defaultURI = "/api/custom"
 * )
 * public class MyService implements Service&lt;JsonRequest, JsonResponse&gt; {
 *     &#64;Override
 *     public void handle(JsonRequest request, JsonResponse response) throws Exception {
 *         // Service logic here
 *         response.setContent(JsonObject.of("message", "Hello World"));
 *     }
 *     
 *     // Other required methods...
 * }
 * </pre>
 * </p>
 *
 * @param <R> the request type, must extend ServiceRequest
 * @param <S> the response type, must extend ServiceResponse
 * @see RegisterPlugin
 * @see HandlingPlugin
 * @see ConfigurablePlugin
 * @see CORSHeaders
 * @see https://restheart.org/docs/plugins/core-plugins/#services
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface Service<R extends ServiceRequest<?>, S extends ServiceResponse<?>> extends HandlingPlugin<R, S>, ConfigurablePlugin, CORSHeaders {
        /**
         * Handles the incoming HTTP request and generates the response.
         * <p>
         * This is the main entry point for service processing. Implementations should:
         * <ul>
         *   <li>Extract data from the request object</li>
         *   <li>Perform business logic or data processing</li>
         *   <li>Set appropriate content and headers on the response</li>
         *   <li>Handle any exceptions appropriately</li>
         * </ul>
         * </p>
         * <p>
         * The handling logic can alternatively be provided by overriding the
         * {@link #handle()} method that returns a BiConsumer for functional-style
         * request processing.
         * </p>
         *
         * @param request the service request containing HTTP request data and content
         * @param response the service response for setting HTTP response data and content
         * @throws Exception if an error occurs during request processing
         */
        public default void handle(final R request, final S response) throws Exception {
            handle().accept(request, response);
        }

        /**
         * Returns a BiConsumer that handles the request in functional style.
         * <p>
         * This method provides an alternative way to implement request handling
         * using functional programming patterns. Services can override this method
         * instead of the {@link #handle(ServiceRequest, ServiceResponse)} method
         * to provide handling logic as a BiConsumer.
         * </p>
         * <p>
         * Example functional implementation:
         * <pre>
         * &#64;Override
         * public BiConsumer&lt;JsonRequest, JsonResponse&gt; handle() {
         *     return (request, response) -&gt; {
         *         // Handle request and set response
         *         response.setContent(processRequest(request));
         *     };
         * }
         * </pre>
         * </p>
         *
         * @return the BiConsumer that handles the request and response
         * @throws Exception if an error occurs during BiConsumer creation or initialization
         */
        public default BiConsumer<R, S> handle() throws Exception {
            return (r,s) -> {
                throw new UnsupportedOperationException("handle function not implemented");
            };
        }

        /**
         * Returns the Consumer function used to initialize request objects.
         * <p>
         * This method must return a Consumer that properly initializes the specific
         * request type for this service. The Consumer typically calls a static
         * initialization method on the request class to create and attach the
         * request object to the HTTP exchange.
         * </p>
         * <p>
         * Example implementation:
         * <pre>
         * &#64;Override
         * public Consumer&lt;HttpServerExchange&gt; requestInitializer() {
         *     return JsonRequest::init;
         * }
         * </pre>
         * </p>
         *
         * @return the Consumer function for request object instantiation
         */
        public Consumer<HttpServerExchange> requestInitializer();

        /**
         * Returns the Consumer function used to initialize response objects.
         * <p>
         * This method must return a Consumer that properly initializes the specific
         * response type for this service. The Consumer typically calls a static
         * initialization method on the response class to create and attach the
         * response object to the HTTP exchange.
         * </p>
         * <p>
         * Example implementation:
         * <pre>
         * &#64;Override
         * public Consumer&lt;HttpServerExchange&gt; responseInitializer() {
         *     return JsonResponse::init;
         * }
         * </pre>
         * </p>
         *
         * @return the Consumer function for response object instantiation
         */
        public Consumer<HttpServerExchange> responseInitializer();

        /**
         * Returns the Function used to retrieve the request object from the exchange.
         * <p>
         * This method must return a Function that retrieves the service request
         * object that was previously attached to the HTTP exchange during the
         * initialization phase. The Function typically calls a static factory
         * method on the request class.
         * </p>
         * <p>
         * Example implementation:
         * <pre>
         * &#64;Override
         * public Function&lt;HttpServerExchange, JsonRequest&gt; request() {
         *     return JsonRequest::of;
         * }
         * </pre>
         * </p>
         *
         * @return the Function for retrieving the request object from the exchange
         */
        public Function<HttpServerExchange, R> request();

        /**
         * Returns the Function used to retrieve the response object from the exchange.
         * <p>
         * This method must return a Function that retrieves the service response
         * object that was previously attached to the HTTP exchange during the
         * initialization phase. The Function typically calls a static factory
         * method on the response class.
         * </p>
         * <p>
         * Example implementation:
         * <pre>
         * &#64;Override
         * public Function&lt;HttpServerExchange, JsonResponse&gt; response() {
         *     return JsonResponse::of;
         * }
         * </pre>
         * </p>
         *
         * @return the Function for retrieving the response object from the exchange
         */
        public Function<HttpServerExchange, S> response();

        /**
         * Helper method to handle HTTP OPTIONS requests for CORS support.
         * <p>
         * This method provides a standard implementation for handling OPTIONS
         * requests by setting appropriate CORS headers and returning a 200 OK
         * status. It's commonly used for CORS preflight request handling.
         * </p>
         * <p>
         * The method sets the following CORS headers:
         * <ul>
         *   <li>Access-Control-Allow-Methods</li>
         *   <li>Access-Control-Allow-Headers</li>
         * </ul>
         * </p>
         * <p>
         * Services can call this method directly in their request handling logic
         * when they detect an OPTIONS request, or use the functional version
         * {@link #handleOptions()}.
         * </p>
         *
         * @param request the service request for the OPTIONS method
         */
        default void handleOptions(final R request) {
            var exchange = request.getExchange();
            var response = Response.of(exchange);

            response.getHeaders()
                .put(ACCESS_CONTROL_ALLOW_METHODS, accessControlAllowMethods(request))
                .put(ACCESS_CONTROL_ALLOW_HEADERS, accessControlAllowHeaders(request));

            response.setStatusCode(HttpStatus.SC_OK);
            exchange.endExchange();
        }

        /**
         * Returns a BiConsumer helper for handling HTTP OPTIONS requests in functional style.
         * <p>
         * This method provides a functional interface version of {@link #handleOptions(ServiceRequest)}
         * that can be used in functional-style request handling implementations.
         * It provides the same CORS handling functionality but in a BiConsumer format.
         * </p>
         * <p>
         * Example usage in functional handling:
         * <pre>
         * &#64;Override
         * public BiConsumer&lt;JsonRequest, JsonResponse&gt; handle() {
         *     return (request, response) -&gt; {
         *         if (request.isOptions()) {
         *             handleOptions().accept(request, response);
         *         } else {
         *             // Handle other methods
         *         }
         *     };
         * }
         * </pre>
         * </p>
         *
         * @return a BiConsumer that handles OPTIONS requests for CORS support
         */
        default BiConsumer<R, S> handleOptions() {
            return (r,s) -> handleOptions(r);
        }
}
