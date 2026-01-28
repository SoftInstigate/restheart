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
package org.restheart.plugins;

/**
 * Enumeration defining the intercept points where interceptors can be executed in the request processing pipeline.
 * <p>
 * InterceptPoint determines when an interceptor is invoked during the lifecycle of an HTTP request
 * in RESTHeart. Each intercept point provides access to different aspects of the request and response,
 * allowing interceptors to perform various types of processing at the most appropriate time.
 * </p>
 * <p>
 * The request processing pipeline follows this order:
 * <ol>
 *   <li>{@link #REQUEST_BEFORE_EXCHANGE_INIT} - Before request/response objects are created</li>
 *   <li>{@link #REQUEST_BEFORE_AUTH} - After initialization but before authentication</li>
 *   <li>{@link #REQUEST_AFTER_FAILED_AUTH} - After authentication fails (only on auth failure)</li>
 *   <li>{@link #REQUEST_AFTER_AUTH} - After authentication but before request handling</li>
 *   <li>Request handling by service or proxy</li>
 *   <li>{@link #RESPONSE} - After response generation (blocking)</li>
 *   <li>{@link #RESPONSE_ASYNC} - After response generation (asynchronous)</li>
 * </ol>
 * </p>
 * <p>
 * Each intercept point serves different use cases:
 * <ul>
 *   <li><strong>Security</strong> - Authentication and authorization checks</li>
 *   <li><strong>Logging</strong> - Request/response logging and monitoring</li>
 *   <li><strong>Content Transformation</strong> - Modifying request or response data</li>
 *   <li><strong>Validation</strong> - Input validation and constraint checking</li>
 *   <li><strong>Caching</strong> - Response caching and cache invalidation</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Interceptor
 * @see RegisterPlugin#interceptPoint()
 */
public enum InterceptPoint {
    /**
     * Intercepts the request before the exchange is initialized.
     * <p>
     * This is the earliest intercept point in the request processing pipeline, occurring
     * before service-specific request and response objects are created. Interceptors at
     * this point have access to the raw HTTP exchange and can influence how the request
     * and response objects are initialized.
     * </p>
     * <p>
     * <strong>Requirements:</strong>
     * <ul>
     *   <li>Interceptors must implement the {@code WildcardInterceptor} interface</li>
     *   <li>The {@code handle(request, response)} method receives {@code UninitializedRequest} and {@code UninitializedResponse}</li>
     *   <li>Content can only be accessed in raw format</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Use Cases:</strong>
     * <ul>
     *   <li>Custom request/response initialization logic</li>
     *   <li>Raw content modification before parsing</li>
     *   <li>Early request routing decisions</li>
     *   <li>Global request preprocessing</li>
     * </ul>
     * </p>
     * <p>
     * Interceptors can provide custom initializers using {@code PluginUtils.attachCustomRequestInitializer()}
     * or modify raw request content using {@code Request.setRawContent()}.
     * </p>
     */
    REQUEST_BEFORE_EXCHANGE_INIT,

    /**
     * Intercepts the request before authentication occurs.
     * <p>
     * This intercept point is executed after request/response objects are initialized
     * but before any authentication mechanisms are applied. It provides access to
     * fully parsed request and response objects while allowing modification before
     * security processing begins.
     * </p>
     * <p>
     * <strong>Available Data:</strong>
     * <ul>
     *   <li>Complete request object with parsed content</li>
     *   <li>Request headers, parameters, and URI</li>
     *   <li>Response object for early response generation</li>
     *   <li>No authentication context (user not yet authenticated)</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Use Cases:</strong>
     * <ul>
     *   <li>Request validation and preprocessing</li>
     *   <li>Content transformation before authentication</li>
     *   <li>Rate limiting and request filtering</li>
     *   <li>Early request routing and redirection</li>
     *   <li>Custom authentication preparation</li>
     * </ul>
     * </p>
     */
    REQUEST_BEFORE_AUTH,

    /**
     * Intercepts the request after authentication or authorization fails.
     * <p>
     * This intercept point is executed only when authentication or authorization fails,
     * occurring after the authentication process completes unsuccessfully but before
     * the error response is sent to the client. It allows interceptors to implement
     * custom security policies, logging, or response modification for failed authentication
     * attempts.
     * </p>
     * <p>
     * <strong>Available Data:</strong>
     * <ul>
     *   <li>Complete request object with parsed content</li>
     *   <li>Authentication context (may include attempted username)</li>
     *   <li>Request headers and client information (IP address, user agent)</li>
     *   <li>Response object for custom error response generation</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Use Cases:</strong>
     * <ul>
     *   <li>Brute force attack protection (blocking excessive failed attempts)</li>
     *   <li>Security event logging and monitoring</li>
     *   <li>Custom error response generation for failed authentication</li>
     *   <li>Rate limiting based on failed authentication attempts</li>
     *   <li>Notification triggers for suspicious activity</li>
     *   <li>Collecting authentication failure metrics</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Important Notes:</strong>
     * <ul>
     *   <li>This intercept point only executes on authentication/authorization failure</li>
     *   <li>The request phase is set to PHASE_END to prevent further processing</li>
     *   <li>Interceptors should avoid expensive operations to maintain performance</li>
     *   <li>The response status code is typically 401 (Unauthorized) or 429 (Too Many Requests)</li>
     * </ul>
     * </p>
     */
    REQUEST_AFTER_FAILED_AUTH,

    /**
     * Intercepts the request after authentication occurs but before request handling.
     * <p>
     * This intercept point is executed after authentication has been completed but
     * before the main request handling logic (service or proxy) is invoked. It has
     * access to authentication context and can perform authorization checks or
     * authenticated user-specific processing.
     * </p>
     * <p>
     * <strong>Available Data:</strong>
     * <ul>
     *   <li>Complete request object with parsed content</li>
     *   <li>Authentication context and user information</li>
     *   <li>User roles and permissions</li>
     *   <li>Response object for early response generation</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Use Cases:</strong>
     * <ul>
     *   <li>Authorization and access control</li>
     *   <li>User-specific content modification</li>
     *   <li>Audit logging with user context</li>
     *   <li>Request enrichment with user data</li>
     *   <li>Permission-based request filtering</li>
     * </ul>
     * </p>
     */
    REQUEST_AFTER_AUTH,

    /**
     * Intercepts the response after processing and executes synchronously.
     * <p>
     * This intercept point is executed after the main request handling logic has
     * completed and the response has been generated. The interceptor runs synchronously,
     * meaning the response is held until the interceptor completes, allowing for
     * response modification before it's sent to the client.
     * </p>
     * <p>
     * <strong>Available Data:</strong>
     * <ul>
     *   <li>Complete request object with all processing history</li>
     *   <li>Complete response object with generated content</li>
     *   <li>Authentication context and user information</li>
     *   <li>Processing results and metadata</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Use Cases:</strong>
     * <ul>
     *   <li>Response content transformation</li>
     *   <li>Response header modification</li>
     *   <li>Content filtering and sanitization</li>
     *   <li>Response validation</li>
     *   <li>Synchronous logging and monitoring</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Performance Note:</strong> Since this intercept point blocks the response,
     * interceptors should complete quickly to avoid impacting response times.
     * </p>
     */
    RESPONSE,

    /**
     * Intercepts the response after processing and executes asynchronously.
     * <p>
     * This intercept point is executed after the response has been sent to the client.
     * The interceptor runs asynchronously, meaning it does not block the response
     * delivery but can still access the complete request and response data for
     * post-processing tasks.
     * </p>
     * <p>
     * <strong>Available Data:</strong>
     * <ul>
     *   <li>Complete request object with all processing history</li>
     *   <li>Complete response object with final content</li>
     *   <li>Authentication context and user information</li>
     *   <li>Final processing results and metadata</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Use Cases:</strong>
     * <ul>
     *   <li>Asynchronous logging and auditing</li>
     *   <li>Metrics collection and monitoring</li>
     *   <li>Cache warming and invalidation</li>
     *   <li>Notification sending</li>
     *   <li>Background data processing</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Performance Note:</strong> Since this intercept point is asynchronous,
     * it does not impact response times and is ideal for heavy processing tasks.
     * </p>
     */
    RESPONSE_ASYNC,

    /**
     * Special value used exclusively in the dontIntercept attribute to disable all interceptors.
     * <p>
     * ANY is not a valid intercept point for interceptor execution but serves as a special
     * value in the {@code dontIntercept} attribute of the {@link RegisterPlugin} annotation.
     * When {@code dontIntercept=ANY} is specified, it indicates that the service should
     * not be intercepted at any intercept point by standard interceptors.
     * </p>
     * <p>
     * <strong>Usage:</strong>
     * <pre>
     * &#64;RegisterPlugin(
     *     name = "myService",
     *     dontIntercept = {InterceptPoint.ANY}
     * )
     * public class MyService implements Service&lt;JsonRequest, JsonResponse&gt; {
     *     // This service will skip all standard interceptors
     * }
     * </pre>
     * </p>
     * <p>
     * <strong>Important Notes:</strong>
     * <ul>
     *   <li>Setting {@code interceptPoint=ANY} in an interceptor registration results in {@code IllegalArgumentException}</li>
     *   <li>This only affects standard interceptors; interceptors with {@code requiredInterceptor=true} will still execute</li>
     *   <li>Use sparingly and only when interceptors would interfere with service functionality</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Use Cases:</strong>
     * <ul>
     *   <li>Services that implement custom security mechanisms</li>
     *   <li>High-performance services that need to minimize overhead</li>
     *   <li>Services that handle interceptor functionality internally</li>
     *   <li>Debug or test services that need isolated execution</li>
     * </ul>
     * </p>
     */
    ANY
}
