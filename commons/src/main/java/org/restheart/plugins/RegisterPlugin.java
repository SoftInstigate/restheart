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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.restheart.plugins.security.Authorizer;

/**
 * Annotation to register a plugin with the RESTHeart plugin system.
 * <p>
 * This annotation provides metadata about a plugin that is used by the RESTHeart
 * framework for plugin discovery, registration, and runtime management. All plugin
 * implementations must be annotated with this annotation to be recognized and
 * loaded by the plugin system.
 * </p>
 * <p>
 * The annotation supports configuration for different plugin types:
 * <ul>
 *   <li><strong>Services</strong> - URI mapping, security settings, match policies</li>
 *   <li><strong>Interceptors</strong> - Intercept points, content requirements, execution order</li>
 *   <li><strong>Authorizers</strong> - Authorization type (ALLOWER/VETOER)</li>
 *   <li><strong>Initializers</strong> - Initialization points in the startup sequence</li>
 *   <li><strong>All Plugins</strong> - Name, description, priority, enabled state</li>
 * </ul>
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "myService",
 *     description = "A custom service implementation",
 *     defaultURI = "/api/custom",
 *     priority = 100,
 *     secure = true
 * )
 * public class MyService implements Service&lt;JsonRequest, JsonResponse&gt; {
 *     // implementation
 * }
 * </pre>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Plugin
 * @see Service
 * @see Interceptor
 * @see org.restheart.plugins.security.Authorizer
 * @see Initializer
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterPlugin {
    /**
     * Defines the unique name of the plugin.
     * <p>
     * This name serves as the primary identifier for the plugin within the RESTHeart
     * framework. It is used for:
     * <ul>
     *   <li>Configuration file references to pass plugin-specific arguments</li>
     *   <li>Plugin lookup and dependency resolution</li>
     *   <li>Logging and monitoring identification</li>
     *   <li>Default URI generation for services (if defaultURI is not specified)</li>
     * </ul>
     * </p>
     * <p>
     * The name must be unique across all registered plugins and should follow
     * standard naming conventions (lowercase, no spaces, descriptive).
     * </p>
     *
     * @return the unique name of the plugin
     */
    String name();

    /**
     * Provides a human-readable description of the plugin's functionality.
     * <p>
     * This description is used for documentation, administration interfaces,
     * and debugging purposes. It should clearly explain what the plugin does
     * and its intended use case.
     * </p>
     *
     * @return a descriptive text explaining the plugin's purpose and functionality
     */
    String description();

    /**
     * Sets the execution priority order for the plugin.
     * <p>
     * Lower values indicate higher priority, meaning plugins with smaller priority
     * values are executed before those with larger values. This is particularly
     * important for interceptors where execution order can affect the final result.
     * </p>
     * <p>
     * Default value is 10, which provides a reasonable middle ground for most plugins.
     * System plugins typically use values below 10, while custom plugins should
     * use values of 10 or higher to ensure proper execution order.
     * </p>
     *
     * @return the execution priority (lower values = higher priority)
     */
    int priority() default 10;

    /**
     * Determines if the service requires authentication and authorization.
     * <p>
     * <strong>Only applicable to Services.</strong>
     * </p>
     * <p>
     * When set to true, the service will only be executed if:
     * <ul>
     *   <li>The request is successfully authenticated</li>
     *   <li>The authenticated user is authorized to access the service</li>
     * </ul>
     * </p>
     * <p>
     * This value can be overridden at runtime by setting the 'secure' configuration
     * argument in the plugin's configuration. This allows for flexible security
     * configuration without code changes.
     * </p>
     *
     * @return true if the service requires authentication and authorization, false otherwise
     */
    boolean secure() default false;

    /**
     * Determines if the plugin is enabled by default when RESTHeart starts.
     * <p>
     * When set to true, the plugin will be automatically enabled during startup.
     * When set to false, the plugin must be explicitly enabled by setting the
     * 'enabled' configuration argument to true in the plugin's configuration.
     * </p>
     * <p>
     * This provides a convenient way to control plugin activation without requiring
     * configuration file changes for commonly used plugins.
     * </p>
     *
     * @return true if the plugin should be enabled by default, false if it requires explicit enabling
     */
    boolean enabledByDefault() default true;

    /**
     * Sets the default URI path where the service will be mounted.
     * <p>
     * <strong>Only applicable to Services.</strong>
     * </p>
     * <p>
     * If not specified, the service will be mounted at a URI derived from the
     * plugin name (e.g., a plugin named "myService" will be mounted at "/myService").
     * </p>
     * <p>
     * The URI can include path parameters and supports various patterns depending
     * on the uriMatchPolicy setting. Examples:
     * <ul>
     *   <li>"/api/users" - exact path</li>
     *   <li>"/api/users/{id}" - path with parameter</li>
     *   <li>"/api/v1/*" - wildcard matching</li>
     * </ul>
     * </p>
     *
     * @return the URI path where the service should be mounted
     */
    String defaultURI() default "";

    /**
     * Sets the URI matching policy for the service.
     * <p>
     * <strong>Only applicable to Services.</strong>
     * </p>
     * <p>
     * Determines how the service's URI is matched against incoming requests:
     * <ul>
     *   <li><strong>EXACT</strong> - The request URI must exactly match the service URI</li>
     *   <li><strong>PREFIX</strong> - The request URI must start with the service URI</li>
     * </ul>
     * </p>
     * <p>
     * PREFIX matching is useful for services that handle multiple sub-paths,
     * while EXACT matching is appropriate for services with fixed endpoints.
     * </p>
     *
     * @return the URI matching policy for the service
     */
    MATCH_POLICY uriMatchPolicy() default MATCH_POLICY.PREFIX;

    /**
     * Enumeration defining URI matching policies for services.
     */
    public enum MATCH_POLICY {
        /** Requires exact URI match between request and service URI */
        EXACT, 
        /** Matches if request URI starts with service URI */
        PREFIX
    };

    /**
     * Specifies when the interceptor should be executed in the request processing pipeline.
     * <p>
     * <strong>Only applicable to Interceptors.</strong>
     * </p>
     * <p>
     * The intercept point determines at which stage of request processing the
     * interceptor will be invoked. Different intercept points provide access to
     * different aspects of the request/response and allow for various types of
     * processing and modification.
     * </p>
     *
     * @return the intercept point defining when the interceptor executes
     * @see InterceptPoint
     */
    InterceptPoint interceptPoint() default InterceptPoint.REQUEST_AFTER_AUTH;


    /**
     * Forces the interceptor to always execute, bypassing dontIntercept settings.
     * <p>
     * <strong>Only applicable to Interceptors.</strong>
     * </p>
     * <p>
     * When set to true, this interceptor will always be executed even if a service
     * or other plugin has configured dontIntercept settings that would normally
     * exclude it. This is useful for critical interceptors that must always run,
     * such as security or auditing interceptors.
     * </p>
     * <p>
     * Use this setting sparingly and only for interceptors that are essential
     * for system integrity, security, or compliance requirements.
     * </p>
     *
     * @return true if the interceptor should always execute regardless of dontIntercept settings
     */
    boolean requiredinterceptor() default false;


    /**
     * Specifies when the initializer should be executed during RESTHeart startup.
     * <p>
     * <strong>Only applicable to Initializers.</strong>
     * </p>
     * <p>
     * The init point determines at which stage of the RESTHeart startup sequence
     * the initializer will be invoked. This allows for ordered initialization
     * of components and dependencies.
     * </p>
     *
     * @return the initialization point defining when the initializer executes
     * @see InitPoint
     */
    InitPoint initPoint() default InitPoint.AFTER_STARTUP;

    /**
     * Indicates if the interceptor requires access to request or response content.
     * <p>
     * <strong>Only applicable to Interceptors of proxied resources.</strong>
     * Interceptors of Services always have content available.
     * </p>
     * <p>
     * When set to true, RESTHeart will buffer the content to make it available
     * to the interceptor:
     * <ul>
     *   <li>For REQUEST_BEFORE_AUTH or REQUEST_AFTER_AUTH: request content is buffered</li>
     *   <li>For RESPONSE or RESPONSE_ASYNC: response content is buffered</li>
     * </ul>
     * </p>
     * <p>
     * Content buffering has performance implications, so only set this to true
     * if the interceptor actually needs to access or modify the content.
     * </p>
     *
     * @return true if the interceptor needs access to request or response content
     */
    boolean requiresContent() default false;

    /**
     * Specifies which interceptors should be skipped for requests handled by this plugin.
     * <p>
     * This setting allows plugins to opt out of certain interceptor processing,
     * which can be useful for:
     * <ul>
     *   <li>Performance optimization by skipping unnecessary interceptors</li>
     *   <li>Avoiding conflicts with specific interceptor behavior</li>
     *   <li>Implementing custom processing pipelines</li>
     * </ul>
     * </p>
     * <p>
     * Note that interceptors marked with requiredinterceptor=true will still
     * execute regardless of this setting.
     * </p>
     *
     * @return an array of InterceptPoints indicating which interceptors should not execute
     * @see InterceptPoint
     */
    InterceptPoint[] dontIntercept() default {};

    /**
     * Specifies the authorization behavior type for the authorizer.
     * <p>
     * <strong>Only applicable to Authorizers.</strong>
     * </p>
     * <p>
     * Authorization decisions follow this logic:
     * <ul>
     *   <li><strong>VETOER</strong> - Can deny access (veto a request)</li>
     *   <li><strong>ALLOWER</strong> - Can grant access (allow a request)</li>
     * </ul>
     * </p>
     * <p>
     * A request is allowed when:
     * <ul>
     *   <li>No VETOER authorizer denies it, AND</li>
     *   <li>At least one ALLOWER authorizer allows it</li>
     * </ul>
     * </p>
     *
     * @return the authorizer type defining its authorization behavior
     * @see org.restheart.plugins.security.Authorizer.TYPE
     */
    Authorizer.TYPE authorizerType() default Authorizer.TYPE.ALLOWER;

    /**
     * Determines if the plugin should be executed on a worker thread or the IO thread.
     * <p>
     * When set to true (default), the plugin is dispatched to the worker thread pool,
     * which is appropriate for plugins that perform blocking operations such as:
     * <ul>
     *   <li>Database queries</li>
     *   <li>File system operations</li>
     *   <li>Network requests to external services</li>
     *   <li>CPU-intensive computations</li>
     * </ul>
     * </p>
     * <p>
     * When set to false, the plugin executes directly on the IO thread, which
     * provides better performance for non-blocking operations but should only
     * be used when the plugin performs only fast, non-blocking operations.
     * </p>
     * <p>
     * <strong>Warning:</strong> Blocking the IO thread can severely impact
     * RESTHeart's performance and should be avoided.
     * </p>
     *
     * @return true if the plugin performs blocking operations and should use worker threads
     */
    boolean blocking() default true;
}
