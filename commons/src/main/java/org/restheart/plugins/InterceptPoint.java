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
package org.restheart.plugins;

/**
 * Defines the execution point in the request/response pipeline where an {@link Interceptor} executes.
 * 
 * <p>InterceptPoint determines when an interceptor will be invoked during the processing
 * of an HTTP request. This allows interceptors to modify requests and responses at
 * different stages of the pipeline, enabling powerful middleware capabilities.</p>
 * 
 * <h2>Request/Response Pipeline</h2>
 * <p>The RESTHeart pipeline processes requests in this order:</p>
 * <ol>
 *   <li>Raw request received (REQUEST_BEFORE_EXCHANGE_INIT)</li>
 *   <li>Exchange initialization (request/response objects created)</li>
 *   <li>Pre-authentication processing (REQUEST_BEFORE_AUTH)</li>
 *   <li>Authentication and authorization</li>
 *   <li>Post-authentication processing (REQUEST_AFTER_AUTH)</li>
 *   <li>Service handler execution</li>
 *   <li>Response processing (RESPONSE or RESPONSE_ASYNC)</li>
 * </ol>
 * 
 * <h2>Choosing an Intercept Point</h2>
 * <p>Select the appropriate intercept point based on your needs:</p>
 * <ul>
 *   <li>Need raw request access? Use REQUEST_BEFORE_EXCHANGE_INIT</li>
 *   <li>Need to modify requests before auth? Use REQUEST_BEFORE_AUTH</li>
 *   <li>Need authenticated user info? Use REQUEST_AFTER_AUTH</li>
 *   <li>Need to modify responses? Use RESPONSE or RESPONSE_ASYNC</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @RegisterPlugin(
 *     name = "request-logger",
 *     interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH
 * )
 * public class RequestLogger implements MongoInterceptor {
 *     @Override
 *     public void handle(MongoRequest request, MongoResponse response) {
 *         logger.info("User {} accessing {}", 
 *             request.getAuthenticatedAccount().getPrincipal(),
 *             request.getPath());
 *     }
 * }
 * }</pre>
 * 
 * @see Interceptor
 * @see RegisterPlugin
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public enum InterceptPoint {
   /**
     * Intercepts the request before the exchange is initialized.
     *
     * <p>Interceptors on {@code InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT}
     * must implement the interface {@code WildcardInterceptor}; in this case,
     * {@code Interceptor.handle(request, response)} receives the request as
     * {@code UninitializedRequest} and the response as {@code UninitializedResponse}.
     *
     * <p>Interceptors can provide a custom initializer with
     * {@code PluginUtils.attachCustomRequestInitializer()} or can modify the raw
     * request content using {@code Request.setRawContent()}.
     */
    REQUEST_BEFORE_EXCHANGE_INIT,

    /**
     * Intercepts the request before authentication occurs.
     * 
     * <p>At this point:</p>
     * <ul>
     *   <li>The exchange has been initialized with proper request/response types</li>
     *   <li>Request content has been parsed and is accessible</li>
     *   <li>No authentication has been performed yet</li>
     *   <li>User identity is not available</li>
     * </ul>
     * 
     * <p>Common use cases:</p>
     * <ul>
     *   <li>Request validation that doesn't require authentication</li>
     *   <li>Adding CORS headers</li>
     *   <li>Request transformation or enrichment</li>
     *   <li>Rate limiting based on IP address</li>
     *   <li>Rejecting requests before authentication overhead</li>
     * </ul>
     */
    REQUEST_BEFORE_AUTH,

    /**
     * Intercepts the request after authentication and authorization.
     * 
     * <p>At this point:</p>
     * <ul>
     *   <li>Authentication has been performed (if required)</li>
     *   <li>User identity is available via {@code request.getAuthenticatedAccount()}</li>
     *   <li>Authorization has been checked</li>
     *   <li>Request will proceed to the service handler</li>
     * </ul>
     * 
     * <p>Common use cases:</p>
     * <ul>
     *   <li>Audit logging with user information</li>
     *   <li>User-specific request enrichment</li>
     *   <li>Setting user context for downstream processing</li>
     *   <li>Enforcing additional business rules based on user roles</li>
     *   <li>Request metrics collection with user attribution</li>
     * </ul>
     */
    REQUEST_AFTER_AUTH,

    /**
     * Intercepts the response synchronously, blocking until completion.
     * 
     * <p>At this point:</p>
     * <ul>
     *   <li>The service handler has completed execution</li>
     *   <li>Response content is available and can be modified</li>
     *   <li>Response headers can be added or modified</li>
     *   <li>The interceptor blocks the response from being sent</li>
     * </ul>
     * 
     * <p>Common use cases:</p>
     * <ul>
     *   <li>Response transformation or filtering</li>
     *   <li>Adding security headers</li>
     *   <li>Response compression</li>
     *   <li>Removing sensitive data from responses</li>
     *   <li>Response caching logic</li>
     * </ul>
     * 
     * <p><strong>Performance Note:</strong> Use RESPONSE_ASYNC for long-running
     * operations to avoid blocking the response pipeline.</p>
     */
    RESPONSE,

    /**
     * Intercepts the response asynchronously without blocking the response.
     * 
     * <p>At this point:</p>
     * <ul>
     *   <li>The service handler has completed execution</li>
     *   <li>Response has been sent to the client</li>
     *   <li>Interceptor runs in parallel, cannot modify the response</li>
     *   <li>Useful for fire-and-forget operations</li>
     * </ul>
     * 
     * <p>Common use cases:</p>
     * <ul>
     *   <li>Asynchronous logging or metrics collection</li>
     *   <li>Triggering webhooks or notifications</li>
     *   <li>Updating caches or search indexes</li>
     *   <li>Background data processing</li>
     *   <li>Analytics event tracking</li>
     * </ul>
     * 
     * <p><strong>Important:</strong> Cannot modify the response as it has already
     * been sent to the client. Use RESPONSE for response modification.</p>
     */
    RESPONSE_ASYNC,

    /**
     * Special value used only in the {@code dontIntercept} attribute of {@link RegisterPlugin}.
     * 
     * <p><strong>IMPORTANT:</strong> This is not a valid intercept point for interceptors.
     * Setting {@code interceptPoint=ANY} will result in an {@link IllegalArgumentException}.</p>
     * 
     * <h3>Usage</h3>
     * <p>Use ANY in service plugins to exclude all interceptors:</p>
     * <pre>{@code
     * @RegisterPlugin(
     *     name = "performance-critical-service",
     *     dontIntercept = InterceptPoint.ANY
     * )
     * public class HighPerformanceService implements JsonService {
     *     // This service will skip all optional interceptors
     * }
     * }</pre>
     * 
     * <h3>Behavior</h3>
     * <ul>
     *   <li>Prevents all standard interceptors from processing the service's requests</li>
     *   <li>Interceptors marked with {@code requiredInterceptor=true} still execute</li>
     *   <li>Useful for performance-critical endpoints that need minimal overhead</li>
     *   <li>Security interceptors may still run if marked as required</li>
     * </ul>
     * 
     * @see RegisterPlugin#dontIntercept()
     */
    ANY
}
