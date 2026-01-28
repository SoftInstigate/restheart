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
 * Central registry for all RESTHeart plugins including services, interceptors, 
 * security components, and other plugin types.
 * 
 * This interface provides methods to register, retrieve, and manage all types of
 * plugins in the RESTHeart framework. It serves as the main registry for:
 * - Authentication mechanisms and authenticators
 * - Authorization components (authorizers and permission transformers)
 * - Services and their interceptors
 * - Initializers and providers
 * - Pipeline handlers and routing information
 * 
 * The registry also manages the root path handler and provides methods to
 * dynamically plug and unplug services and pipelines at runtime.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see PluginRecord
 * @see Service
 * @see Interceptor
 * @see AuthMechanism
 * @see Authenticator
 * @see Authorizer
 */
public interface PluginsRegistry {
    /**
     * Retrieves all registered authentication mechanisms.
     * 
     * Authentication mechanisms are responsible for extracting credentials
     * from HTTP requests and creating authentication contexts.
     * 
     * @return a set of all registered authentication mechanism plugin records
     */
    public Set<PluginRecord<AuthMechanism>> getAuthMechanisms();

    /**
     * Retrieves all registered authenticators.
     * 
     * Authenticators are responsible for verifying user credentials and
     * establishing user identity within the security pipeline.
     * 
     * @return a set of all registered authenticator plugin records
     */
    public Set<PluginRecord<Authenticator>> getAuthenticators();

    /**
     * Retrieves a specific authenticator by its name.
     * 
     * @param name the name of the authenticator to retrieve
     * @return the authenticator plugin record with the specified name
     * @throws ConfigurationException if no authenticator with the given name is found
     *                               or if there's a configuration issue
     */
    public PluginRecord<Authenticator> getAuthenticator(String name) throws ConfigurationException;

    /**
     * Retrieves the registered token manager.
     * 
     * The token manager is responsible for creating, validating, and managing
     * authentication tokens used for stateless authentication.
     * 
     * @return the token manager plugin record, or null if none is registered
     */
    public PluginRecord<TokenManager> getTokenManager();

    /**
     * Retrieves all registered authorizers.
     * 
     * Authorizers are responsible for determining whether an authenticated
     * user has permission to perform a specific operation on a resource.
     * 
     * @return a set of all registered authorizer plugin records
     */
    public Set<PluginRecord<Authorizer>> getAuthorizers();

    /**
     * Retrieves all registered permission transformers.
     * 
     * Permission transformers modify and enhance ACL (Access Control List)
     * permissions before they are evaluated by authorizers.
     * 
     * @return a set of all registered permission transformers
     */
    public Set<BaseAclPermissionTransformer> getPermissionTransformers();

    /**
     * Retrieves all registered initializers.
     * 
     * Initializers are executed during the RESTHeart startup process to
     * perform initialization tasks such as setting up resources, connections,
     * or other prerequisites.
     * 
     * @return a set of all registered initializer plugin records
     */
    public Set<PluginRecord<Initializer>> getInitializers();

    /**
     * Retrieves all registered providers.
     * 
     * Providers supply instances of various objects and services that can be
     * injected into other plugins using the dependency injection mechanism.
     * 
     * @return a set of all registered provider plugin records
     */
    public Set<PluginRecord<Provider<?>>> getProviders();

    /**
     * Adds an interceptor to the registry.
     * 
     * Interceptors can be dynamically added to the registry after initial
     * startup to extend functionality at runtime.
     * 
     * @param i the interceptor plugin record to add
     */
    public void addInterceptor(PluginRecord<Interceptor<?, ?>> i);

    /**
     * Removes all interceptors that match the given filter predicate.
     * 
     * This method allows for conditional removal of interceptors based on
     * custom criteria defined by the filter predicate.
     * 
     * @param filter the predicate to test interceptors for removal
     * @return true if any interceptors were removed, false otherwise
     */
    public boolean removeInterceptorIf(java.util.function.Predicate<? super PluginRecord<Interceptor<?, ?>>> filter);

    /**
     * Retrieves all registered services.
     * 
     * Services are the main business logic components that handle HTTP requests
     * and generate responses in the RESTHeart framework.
     * 
     * @return a set of all registered service plugin records
     */
    public Set<PluginRecord<Service<?, ?>>> getServices();

    /**
     * Retrieves all registered interceptors.
     * 
     * Interceptors provide cross-cutting functionality that can be applied
     * to services or proxy requests at various points in the request/response
     * processing pipeline.
     * 
     * @return a set of all registered interceptor plugin records
     */
    public Set<PluginRecord<Interceptor<?, ?>>> getInterceptors();

    /**
     * Retrieves the interceptors that apply to a specific service at a given intercept point.
     * 
     * This method filters interceptors based on their configuration to determine
     * which ones should be executed for the specified service and intercept point.
     * 
     * @param srv the service for which to get interceptors
     * @param interceptPoint the specific point in the request/response cycle
     * @return a list of interceptors that apply to the service at the specified point
     */
    public List<Interceptor<?, ?>> getServiceInterceptors(Service<?, ?> srv, InterceptPoint interceptPoint);

    /**
     * Retrieves the interceptors that apply to proxy requests at a given intercept point.
     * 
     * Proxy interceptors are applied to requests that are proxied to external services
     * rather than handled by RESTHeart services.
     * 
     * @param interceptPoint the specific point in the request/response cycle
     * @return a list of interceptors that apply to proxy requests at the specified point
     */
    public List<Interceptor<?, ?>> getProxyInterceptors(InterceptPoint interceptPoint);

    /**
     * Gets the RESTHeart root path handler.
     *
     * The root handler is responsible for routing incoming requests to the
     * appropriate service or pipeline based on the request path.
     * 
     * <strong>Important:</strong> Avoid adding handlers directly using 
     * PathHandler.addPrefixPath() or PathHandler.addExactPath(). Instead use 
     * PluginsRegistry.plugPipeline() or PluginsRegistry.plugService() which 
     * also set the correct PipelineInfo.
     *
     * @return the RESTHeart root path handler
     */
    public PathHandler getRootPathHandler();

    /**
     * Plugs a pipeline into the root handler binding it to the specified path.
     * 
     * This method registers a handler pipeline to be executed when requests
     * match the specified path. It also sets the appropriate PipelineInfo
     * for the handler.
     *
     * @param path the request path that will trigger this pipeline
     * @param handler the pipelined handler to be activated upon path match
     * @param info the PipelineInfo describing the handling pipeline
     */
    public void plugPipeline(String path, PipelinedHandler handler, PipelineInfo info);

    /**
     * Unplugs a handler from the root path handler.
     * 
     * This method removes a previously registered handler from the routing
     * table, making the associated URI path no longer accessible.
     *
     * @param uri the URI path to unplug
     * @param mp the match policy used when the handler was originally plugged
     */
    public void unplug(String uri, MATCH_POLICY mp);

    /**
     * Plugs a service into the root handler binding it to the specified path.
     * 
     * This method registers a service to handle requests at the given URI.
     * The service can be configured to require authentication and authorization
     * before being invoked.
     *
     * @param srv the service plugin record to plug
     * @param uri the URI path to bind the service to
     * @param mp the match policy (exact or prefix matching)
     * @param secured true to invoke the service only after authentication 
     *                and authorization succeed, false to allow unauthenticated access
     */
    public void plugService(PluginRecord<Service<? extends ServiceRequest<?>, ? extends ServiceResponse<?>>> srv, final String uri, MATCH_POLICY mp, boolean secured);

    /**
     * Retrieves the pipeline information for the handler that processes requests to the given path.
     * 
     * This method returns metadata about the processing pipeline that handles
     * requests for the specified path, including information about the service
     * type and processing characteristics.
     * 
     * @param path the request path to look up
     * @return the PipelineInfo of the pipeline handling requests to the path,
     *         or null if no handler is registered for the path
     */
    public PipelineInfo getPipelineInfo(String path);

}
