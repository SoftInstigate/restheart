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

import org.restheart.exchange.Request;
import org.restheart.exchange.Response;

/**
 * Interface for implementing RESTHeart interceptors that can modify requests and responses.
 * <p>
 * Interceptors allow developers to snoop and modify requests and responses at different
 * stages of the request lifecycle as defined by the {@code interceptPoint} parameter
 * of the {@link RegisterPlugin} annotation. They provide a powerful mechanism for
 * cross-cutting concerns such as logging, security, content transformation, and monitoring.
 * </p>
 * <p>
 * Interceptors can operate on two types of requests:
 * </p>
 * <ul>
 *   <li><strong>Service Requests</strong> - Intercept requests handled by Services when
 *       the interceptor's request and response types match those declared by the Service</li>
 *   <li><strong>Proxied Requests</strong> - Intercept proxied requests when the interceptor's
 *       request and response types extend BufferedRequest and BufferedResponse</li>
 * </ul>
 * <p>
 * Interceptors are executed at specific points in the request processing pipeline:
 * </p>
 * <ul>
 *   <li>{@code REQUEST_BEFORE_AUTH} - Before authentication</li>
 *   <li>{@code REQUEST_AFTER_AUTH} - After authentication but before request handling</li>
 *   <li>{@code RESPONSE} - After response generation</li>
 *   <li>{@code RESPONSE_ASYNC} - Asynchronously after response is sent</li>
 * </ul>
 * <p>
 * Example implementation:
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "myInterceptor",
 *     description = "Custom request/response interceptor",
 *     interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
 *     priority = 100
 * )
 * public class MyInterceptor implements Interceptor&lt;JsonRequest, JsonResponse&gt; {
 *     &#64;Override
 *     public void handle(JsonRequest request, JsonResponse response) throws Exception {
 *         // Add custom header
 *         response.setHeader("X-Custom-Header", "intercepted");
 *     }
 *     
 *     &#64;Override
 *     public boolean resolve(JsonRequest request, JsonResponse response) {
 *         // Only intercept requests to /api path
 *         return request.getPath().startsWith("/api");
 *     }
 * }
 * </pre>
 * </p>
 *
 * @param <R> the request type that this interceptor can handle
 * @param <S> the response type that this interceptor can handle
 * @see RegisterPlugin
 * @see InterceptPoint
 * @see ConfigurablePlugin
 * @see ExchangeTypeResolver
 * @see https://restheart.org/docs/plugins/core-plugins/#interceptors
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface Interceptor<R extends Request<?>, S extends Response<?>> extends ConfigurablePlugin, ExchangeTypeResolver<R, S> {
    /**
     * Handles the interception of the request and response.
     * <p>
     * This method is called when the interceptor is executed at its configured
     * intercept point. Implementations can:
     * </p>
     * <ul>
     *   <li>Examine and modify request headers, parameters, or content</li>
     *   <li>Examine and modify response headers, status, or content</li>
     *   <li>Perform logging, auditing, or monitoring</li>
     *   <li>Implement security checks or content validation</li>
     *   <li>Transform request or response data</li>
     * </ul>
     * <p>
     * The specific request and response data available depends on the intercept point:
     * </p>
     * <ul>
     *   <li><strong>REQUEST_BEFORE_AUTH / REQUEST_AFTER_AUTH</strong> - Request data is available, response may be minimal</li>
     *   <li><strong>RESPONSE / RESPONSE_ASYNC</strong> - Both request and response data are available</li>
     * </ul>
     * <p>
     * If this method throws an exception, the request processing will be terminated
     * and an error response will be sent to the client.
     * </p>
     *
     * @param request the request object containing HTTP request data and content
     * @param response the response object for modifying HTTP response data and content
     * @throws Exception if an error occurs during interception that should terminate request processing
     */
    public void handle(final R request, final S response) throws Exception;

    /**
     * Determines if this interceptor should handle the given request and response.
     * <p>
     * This method is called by the RESTHeart framework to determine whether this
     * interceptor should be executed for a particular request. It provides a way
     * to conditionally apply interceptors based on request characteristics such as:
     * </p>
     * <ul>
     *   <li>Request path or URI patterns</li>
     *   <li>HTTP method (GET, POST, etc.)</li>
     *   <li>Request headers or parameters</li>
     *   <li>Authentication status</li>
     *   <li>Content type or other request properties</li>
     * </ul>
     * <p>
     * The method is called before {@link #handle(Request, Response)} and only
     * if this method returns {@code true} will the interceptor be executed.
     * This allows for fine-grained control over when interceptors are applied
     * without requiring complex conditional logic in the handle method.
     * </p>
     * <p>
     * Example implementations:
     * <pre>
     * // Only intercept JSON requests
     * public boolean resolve(JsonRequest request, JsonResponse response) {
     *     return request.isContentTypeJson();
     * }
     * 
     * // Only intercept requests to specific paths
     * public boolean resolve(Request request, Response response) {
     *     return request.getPath().startsWith("/api/secure");
     * }
     * </pre>
     * </p>
     *
     * @param request the request object to evaluate
     * @param response the response object to evaluate
     * @return true if this interceptor should handle the request, false to skip it
     */
    public boolean resolve(final R request, final S response);
}