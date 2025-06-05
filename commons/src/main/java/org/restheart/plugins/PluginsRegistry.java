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

import java.util.List;
import java.util.Set;

import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.PipelineInfo;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.plugins.security.Authenticator;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.TokenManager;
import org.restheart.security.BaseAclPermissionTransformer;

import io.undertow.server.handlers.PathHandler;

/**
 * Central registry for managing all RESTHeart plugins.
 * 
 * <p>PluginsRegistry is the core component that maintains references to all loaded plugins,
 * manages their lifecycle, and provides access to plugin instances. It serves as the
 * central point for plugin discovery, registration, and retrieval.</p>
 * 
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Maintain registry of all plugin types (services, interceptors, security plugins)</li>
 *   <li>Provide lookup methods for retrieving plugins by type or name</li>
 *   <li>Manage request routing and pipeline configuration</li>
 *   <li>Handle plugin binding to URI paths</li>
 *   <li>Support dynamic plugin registration and removal</li>
 * </ul>
 * 
 * <h2>Plugin Categories</h2>
 * <p>The registry manages several categories of plugins:</p>
 * <ul>
 *   <li><strong>Services:</strong> Handle HTTP requests at specific URIs</li>
 *   <li><strong>Interceptors:</strong> Process requests/responses in the pipeline</li>
 *   <li><strong>Security Plugins:</strong> Authentication mechanisms, authenticators, authorizers</li>
 *   <li><strong>Initializers:</strong> Perform startup tasks</li>
 *   <li><strong>Providers:</strong> Supply dependencies for injection</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Access the registry (usually injected)
 * @Inject("registry")
 * private PluginsRegistry registry;
 * 
 * // Retrieve all services
 * Set<PluginRecord<Service<?, ?>>> services = registry.getServices();
 * 
 * // Get a specific authenticator
 * PluginRecord<Authenticator> auth = registry.getAuthenticator("basic");
 * 
 * // Get interceptors for a service
 * List<Interceptor<?, ?>> interceptors = registry.getServiceInterceptors(
 *     myService, InterceptPoint.REQUEST_AFTER_AUTH
 * );
 * }</pre>
 * 
 * <h2>Thread Safety</h2>
 * <p>The PluginsRegistry implementation must be thread-safe as it is accessed
 * concurrently by multiple request handling threads.</p>
 * 
 * @see PluginRecord
 * @see Plugin
 * @see Service
 * @see Interceptor
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface PluginsRegistry {
    /**
     * Gets all registered authentication mechanisms.
     * 
     * <p>Authentication mechanisms handle the protocol-specific aspects of authentication,
     * such as extracting credentials from requests (e.g., Basic, Digest, JWT tokens).</p>
     * 
     * @return an unmodifiable set of all registered authentication mechanism plugins
     */
    public Set<PluginRecord<AuthMechanism>> getAuthMechanisms();

    /**
     * Gets all registered authenticators.
     * 
     * <p>Authenticators verify credentials and create user accounts. Multiple authenticators
     * can be registered to support different authentication sources (e.g., file, database, LDAP).</p>
     * 
     * @return an unmodifiable set of all registered authenticator plugins
     */
    public Set<PluginRecord<Authenticator>> getAuthenticators();

    /**
     * Gets a specific authenticator by name.
     * 
     * <p>Retrieves an authenticator plugin by its registered name. This is useful when
     * a specific authenticator is required, such as when configuring authentication chains.</p>
     * 
     * @param name the name of the authenticator as specified in its @RegisterPlugin annotation
     * @return the authenticator plugin record
     * @throws ConfigurationException if no authenticator with the given name is found
     */
    public PluginRecord<Authenticator> getAuthenticator(String name) throws ConfigurationException;

    /**
     * Gets the registered token manager.
     * 
     * <p>The token manager handles creation, validation, and renewal of authentication tokens.
     * Only one token manager can be active at a time.</p>
     * 
     * @return the token manager plugin record, or null if none is registered
     */
    public PluginRecord<TokenManager> getTokenManager();

    /**
     * Gets all registered authorizers.
     * 
     * <p>Authorizers determine whether authenticated users have permission to access
     * specific resources. Multiple authorizers can work together to implement complex
     * authorization policies.</p>
     * 
     * @return an unmodifiable set of all registered authorizer plugins
     */
    public Set<PluginRecord<Authorizer>> getAuthorizers();

    /**
     * Gets all registered permission transformers.
     * 
     * <p>Permission transformers modify or enhance ACL permissions before they are evaluated.
     * They can be used to implement dynamic permissions based on request context.</p>
     * 
     * @return an unmodifiable set of all registered permission transformer instances
     */
    public Set<BaseAclPermissionTransformer> getPermissionTransformers();

    /**
     * Gets all registered initializers.
     * 
     * <p>Initializers perform one-time setup tasks during RESTHeart startup. They are
     * executed in order based on their {@link InitPoint} and priority settings.</p>
     * 
     * @return an unmodifiable set of all registered initializer plugins
     */
    public Set<PluginRecord<Initializer>> getInitializers();

    /**
     * Gets all registered providers.
     * 
     * <p>Providers supply instances for dependency injection. They enable plugins to
     * share complex objects or services that require special initialization.</p>
     * 
     * @return an unmodifiable set of all registered provider plugins
     */
    public Set<PluginRecord<Provider<?>>> getProviders();

    /**
     * Dynamically adds an interceptor to the registry.
     * 
     * <p>This method allows runtime registration of interceptors, useful for programmatically
     * adding interceptors based on configuration or runtime conditions.</p>
     * 
     * @param i the interceptor plugin record to add
     */
    public void addInterceptor(PluginRecord<Interceptor<?, ?>> i);

    /**
     * Removes interceptors that match the given predicate.
     * 
     * <p>This method enables dynamic removal of interceptors based on runtime conditions.
     * All interceptors matching the predicate will be removed from the registry.</p>
     * 
     * @param filter predicate to test which interceptors should be removed
     * @return true if any interceptors were removed
     */
    public boolean removeInterceptorIf(java.util.function.Predicate<? super PluginRecord<Interceptor<?, ?>>> filter);

    /**
     * Gets all registered services.
     * 
     * <p>Services are the main request handlers in RESTHeart. Each service is bound to
     * one or more URI paths and handles specific types of requests and responses.</p>
     * 
     * @return an unmodifiable set of all registered service plugins
     */
    public Set<PluginRecord<Service<?, ?>>> getServices();

    /**
     * Gets all registered interceptors.
     * 
     * <p>Returns all interceptors regardless of their type or interception point.
     * Use {@link #getServiceInterceptors} or {@link #getProxyInterceptors} for
     * filtered lists based on context.</p>
     * 
     * @return an unmodifiable set of all registered interceptor plugins
     */
    public Set<PluginRecord<Interceptor<?, ?>>> getInterceptors();

    /**
     * Gets interceptors applicable to a specific service at a given interception point.
     * 
     * <p>This method returns only the interceptors that:</p>
     * <ul>
     *   <li>Are registered for the specified interception point</li>
     *   <li>Resolve to true for the given service (based on their resolve() method)</li>
     *   <li>Are enabled in the configuration</li>
     * </ul>
     * 
     * <p>The returned list is ordered by interceptor priority.</p>
     * 
     * @param srv the service to get interceptors for
     * @param interceptPoint the point in the request lifecycle
     * @return an ordered list of applicable interceptors
     */
    public List<Interceptor<?, ?>> getServiceInterceptors(Service<?, ?> srv, InterceptPoint interceptPoint);

    /**
     * Gets interceptors applicable to proxy requests at a given interception point.
     * 
     * <p>Returns interceptors that are specifically designed to work with proxy
     * requests and are registered for the specified interception point.</p>
     * 
     * @param interceptPoint the point in the request lifecycle
     * @return an ordered list of applicable proxy interceptors
     */
    public List<Interceptor<?, ?>> getProxyInterceptors(InterceptPoint interceptPoint);

    /**
     * Gets the RESTHeart root handler.
     * 
     * <p>The root handler is the entry point for all HTTP requests. It routes
     * requests to appropriate pipelines based on the request path.</p>
     * 
     * <p><strong>Important:</strong> Avoid adding handlers using PathHandler.addPrefixPath() or
     * PathHandler.addExactPath() directly. Instead use {@link #plugPipeline} or
     * {@link #plugService} which properly configure the PipelineInfo.</p>
     * 
     * @return the RESTHeart root handler
     */
    public PathHandler getRootPathHandler();

    /**
     * Plugs a pipeline into the root handler binding it to the path.
     * 
     * <p>This method properly configures both the request routing and the pipeline
     * metadata. The PipelineInfo is associated with the path for request handling.</p>
     * 
     * <p>Example usage:</p>
     * <pre>{@code
     * PipelinedHandler pipeline = new MyCustomPipeline();
     * PipelineInfo info = new PipelineInfo(PipelineInfo.Type.SERVICE, "/api/custom");
     * registry.plugPipeline("/api/custom", pipeline, info);
     * }</pre>
     * 
     * @param path    the URI path pattern to match (exact or prefix based on handler type)
     * @param handler the handler pipeline to execute for matching requests
     * @param info    the PipelineInfo describing the handling pipeline
     */
    public void plugPipeline(String path, PipelinedHandler handler, PipelineInfo info);

    /**
     * Unplugs a handler from the root handler.
     * 
     * <p>Removes a previously plugged pipeline or service from the request routing.
     * The match policy must correspond to how the handler was originally plugged.</p>
     * 
     * @param uri the URI path to unplug
     * @param mp the match policy (EXACT or PREFIX) used when the handler was plugged
     */
    public void unplug(String uri, MATCH_POLICY mp);

    /**
     * Plugs a service into the root handler binding it to a URI path.
     * 
     * <p>This method creates a complete request handling pipeline for the service,
     * including security checks if required. The service will handle requests matching
     * the specified URI according to the match policy.</p>
     * 
     * <p>Example usage:</p>
     * <pre>{@code
     * PluginRecord<Service<?, ?>> myService = ...;
     * registry.plugService(myService, "/api/data", MATCH_POLICY.PREFIX, true);
     * // Handles all requests starting with /api/data with security enabled
     * }</pre>
     * 
     * @param srv     the service plugin record to plug
     * @param uri     the URI path to bind the service to
     * @param mp      the match policy - EXACT for exact path matching, PREFIX for prefix matching
     * @param secured true to require authentication and authorization for this service
     */
    public void plugService(PluginRecord<Service<? extends ServiceRequest<?>, ? extends ServiceResponse<?>>> srv, final String uri, MATCH_POLICY mp, boolean secured);

    /**
     * Gets the PipelineInfo for the pipeline handling a specific path.
     * 
     * <p>This method looks up which pipeline would handle a request to the given path
     * and returns its associated PipelineInfo. This is useful for determining request
     * routing and understanding the processing pipeline.</p>
     * 
     * @param path the request path to look up
     * @return the PipelineInfo of the matching pipeline, or null if no pipeline matches
     */
    public PipelineInfo getPipelineInfo(String path);

}
