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

/**
 * Core RESTHeart plugin framework providing interfaces, annotations, and utilities for extending RESTHeart functionality.
 * <p>
 * This package contains the foundational components of the RESTHeart plugin system, enabling developers to create
 * custom services, interceptors, authenticators, authorizers, and other plugin types that seamlessly integrate
 * with the RESTHeart framework. The plugin system provides a modular, extensible architecture that allows
 * RESTHeart to be customized for specific application requirements.
 * </p>
 * 
 * <h2>Plugin Types</h2>
 * <p>
 * RESTHeart supports several types of plugins, each serving different purposes in the request processing pipeline:
 * </p>
 * 
 * <h3>Services</h3>
 * <p>
 * Services are plugins that handle HTTP requests and generate responses, effectively extending RESTHeart's API
 * with custom endpoints. They implement the {@link org.restheart.plugins.Service} interface and can handle
 * any HTTP method (GET, POST, PUT, DELETE, etc.).
 * </p>
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "myService",
 *     description = "Custom REST API service",
 *     defaultURI = "/api/custom"
 * )
 * public class MyService implements JsonService {
 *     &#64;Override
 *     public void handle(JsonRequest request, JsonResponse response) throws Exception {
 *         // Service implementation
 *     }
 * }
 * </pre>
 * 
 * <h3>Interceptors</h3>
 * <p>
 * Interceptors provide cross-cutting functionality by intercepting requests and responses at specific points
 * in the processing pipeline. They implement the {@link org.restheart.plugins.Interceptor} interface and
 * can be used for authentication, authorization, logging, content transformation, and more.
 * </p>
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "myInterceptor",
 *     description = "Custom request interceptor",
 *     interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH
 * )
 * public class MyInterceptor implements Interceptor&lt;JsonRequest, JsonResponse&gt; {
 *     &#64;Override
 *     public void handle(JsonRequest request, JsonResponse response) throws Exception {
 *         // Interceptor logic
 *     }
 *     
 *     &#64;Override
 *     public boolean resolve(JsonRequest request, JsonResponse response) {
 *         return true; // Determine if this interceptor should run
 *     }
 * }
 * </pre>
 * 
 * <h3>Initializers</h3>
 * <p>
 * Initializers perform setup tasks during RESTHeart startup, such as database schema creation, index setup,
 * or external service registration. They implement the {@link org.restheart.plugins.Initializer} interface.
 * </p>
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "myInitializer",
 *     description = "Startup initialization tasks",
 *     initPoint = InitPoint.BEFORE_STARTUP
 * )
 * public class MyInitializer implements Initializer {
 *     &#64;Override
 *     public void init() {
 *         // Initialization logic
 *     }
 * }
 * </pre>
 * 
 * <h3>Providers</h3>
 * <p>
 * Providers enable dependency injection by supplying configured objects to other plugins. They implement
 * the {@link org.restheart.plugins.Provider} interface and participate in RESTHeart's dependency injection system.
 * </p>
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "myProvider",
 *     description = "Custom dependency provider"
 * )
 * public class MyProvider implements Provider&lt;DatabaseService&gt; {
 *     &#64;Override
 *     public DatabaseService get(PluginRecord&lt;?&gt; caller) {
 *         return new DatabaseService();
 *     }
 * }
 * </pre>
 * 
 * <h2>Plugin Registration</h2>
 * <p>
 * All plugins must be annotated with {@link org.restheart.plugins.RegisterPlugin} to provide metadata
 * for registration, configuration, and execution:
 * </p>
 * <ul>
 *   <li><strong>name</strong> - Unique plugin identifier</li>
 *   <li><strong>description</strong> - Human-readable description</li>
 *   <li><strong>priority</strong> - Execution order (lower values = higher priority)</li>
 *   <li><strong>enabledByDefault</strong> - Whether the plugin is enabled by default</li>
 *   <li><strong>secure</strong> - Whether authentication/authorization is required</li>
 * </ul>
 * 
 * <h2>Configuration</h2>
 * <p>
 * Plugins can receive configuration through the {@link org.restheart.plugins.ConfigurablePlugin} interface
 * and the RESTHeart configuration file:
 * </p>
 * <pre>
 * # restheart.yml
 * plugins-args:
 *   myPlugin:
 *     enabled: true
 *     apiKey: "secret-key"
 *     timeout: 30
 * </pre>
 * 
 * <h2>Dependency Injection</h2>
 * <p>
 * The {@link org.restheart.plugins.Inject} annotation enables dependency injection of framework services
 * and custom providers:
 * </p>
 * <pre>
 * public class MyPlugin implements Service&lt;JsonRequest, JsonResponse&gt; {
 *     &#64;Inject("config")
 *     private Map&lt;String, Object&gt; config;
 *     
 *     &#64;Inject("mongo-client")
 *     private MongoClient mongoClient;
 * }
 * </pre>
 * 
 * <h2>Lifecycle Management</h2>
 * <p>
 * Plugin lifecycle is managed automatically by RESTHeart:
 * </p>
 * <ol>
 *   <li><strong>Discovery</strong> - Plugins are discovered through classpath scanning</li>
 *   <li><strong>Instantiation</strong> - Plugin classes are instantiated</li>
 *   <li><strong>Configuration</strong> - Configuration arguments are injected</li>
 *   <li><strong>Dependency Injection</strong> - Dependencies are resolved and injected</li>
 *   <li><strong>Initialization</strong> - {@link org.restheart.plugins.OnInit} methods are called</li>
 *   <li><strong>Registration</strong> - Plugins are registered with appropriate handlers</li>
 *   <li><strong>Execution</strong> - Plugins are invoked during request processing</li>
 * </ol>
 * 
 * <h2>Type Safety</h2>
 * <p>
 * The plugin system uses generics to ensure type safety between services and interceptors.
 * The {@link org.restheart.plugins.ExchangeTypeResolver} interface provides runtime type information
 * to enable proper plugin matching and execution.
 * </p>
 * 
 * <h2>Error Handling</h2>
 * <p>
 * Plugins can use {@link org.restheart.plugins.InterceptorException} to signal errors with specific
 * HTTP status codes, allowing for proper error responses to clients.
 * </p>
 * 
 * <h2>Specialized Interfaces</h2>
 * <p>
 * The package includes specialized service interfaces for common use cases:
 * </p>
 * <ul>
 *   <li>{@link org.restheart.plugins.JsonService} - For JSON-based REST APIs</li>
 *   <li>{@link org.restheart.plugins.ByteArrayService} - For binary data handling</li>
 *   <li>{@link org.restheart.plugins.StringService} - For text-based services</li>
 *   <li>{@link org.restheart.plugins.BsonService} - For MongoDB BSON document handling</li>
 * </ul>
 * 
 * <h2>Security Integration</h2>
 * <p>
 * Security-related plugins are defined in the {@link org.restheart.plugins.security} subpackage,
 * providing authentication mechanisms, authenticators, authorizers, and token managers.
 * </p>
 * 
 * <h2>Examples and Best Practices</h2>
 * <p>
 * For complete examples and best practices, see the RESTHeart documentation at:
 * <a href="https://restheart.org/docs/plugins/">https://restheart.org/docs/plugins/</a>
 * </p>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see org.restheart.plugins.Plugin
 * @see org.restheart.plugins.Service
 * @see org.restheart.plugins.Interceptor
 * @see org.restheart.plugins.RegisterPlugin
 * @see org.restheart.plugins.ConfigurablePlugin
 * @see org.restheart.plugins.security
 */
package org.restheart.plugins;