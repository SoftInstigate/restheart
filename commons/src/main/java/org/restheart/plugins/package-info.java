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
/**
 * RESTHeart plugin framework for extending functionality through modular components.
 * 
 * <p>This package provides the core plugin architecture that enables RESTHeart to be
 * extended with custom services, interceptors, initializers, and providers. The plugin
 * system supports dynamic loading, dependency injection, and lifecycle management.</p>
 * 
 * <h2>Plugin Types</h2>
 * 
 * <h3>Services</h3>
 * <ul>
 *   <li>{@link org.restheart.plugins.Service} - Base interface for all services</li>
 *   <li>{@link org.restheart.plugins.ByteArrayService} - Services handling binary data</li>
 *   <li>{@link org.restheart.plugins.JsonService} - Services handling JSON data</li>
 *   <li>{@link org.restheart.plugins.BsonService} - Services handling BSON data</li>
 *   <li>{@link org.restheart.plugins.StringService} - Services handling text data</li>
 * </ul>
 * 
 * <h3>Interceptors</h3>
 * <ul>
 *   <li>{@link org.restheart.plugins.Interceptor} - Base interface for all interceptors</li>
 *   <li>{@link org.restheart.plugins.ByteArrayInterceptor} - Interceptors for binary data</li>
 *   <li>{@link org.restheart.plugins.JsonInterceptor} - Interceptors for JSON data</li>
 *   <li>{@link org.restheart.plugins.BsonInterceptor} - Interceptors for BSON data</li>
 *   <li>{@link org.restheart.plugins.StringInterceptor} - Interceptors for text data</li>
 *   <li>{@link org.restheart.plugins.MongoInterceptor} - Interceptors for MongoDB operations</li>
 *   <li>{@link org.restheart.plugins.GraphQLInterceptor} - Interceptors for GraphQL operations</li>
 *   <li>{@link org.restheart.plugins.ProxyInterceptor} - Interceptors for proxy operations</li>
 *   <li>{@link org.restheart.plugins.WildcardInterceptor} - Interceptors matching any exchange type</li>
 * </ul>
 * 
 * <h3>Other Plugin Types</h3>
 * <ul>
 *   <li>{@link org.restheart.plugins.Initializer} - Application initialization hooks</li>
 *   <li>{@link org.restheart.plugins.Provider} - Dependency providers for injection</li>
 * </ul>
 * 
 * <h2>Core Interfaces and Annotations</h2>
 * <ul>
 *   <li>{@link org.restheart.plugins.Plugin} - Base interface for all plugins</li>
 *   <li>{@link org.restheart.plugins.RegisterPlugin} - Annotation to register plugins</li>
 *   <li>{@link org.restheart.plugins.Inject} - Annotation for dependency injection</li>
 *   <li>{@link org.restheart.plugins.OnInit} - Annotation for initialization methods</li>
 * </ul>
 * 
 * <h2>Plugin Configuration</h2>
 * <ul>
 *   <li>{@link org.restheart.plugins.ConfigurablePlugin} - Interface for configurable plugins</li>
 *   <li>{@link org.restheart.plugins.FileConfigurablePlugin} - Interface for file-based configuration</li>
 * </ul>
 * 
 * <h2>Plugin Traits</h2>
 * <ul>
 *   <li>{@link org.restheart.plugins.HandlingPlugin} - Plugins that handle requests</li>
 *   <li>{@link org.restheart.plugins.ConsumingPlugin} - Plugins that consume request content</li>
 * </ul>
 * 
 * <h2>Interception Points</h2>
 * <ul>
 *   <li>{@link org.restheart.plugins.InterceptPoint} - Enum defining when interceptors execute</li>
 *   <li>{@link org.restheart.plugins.InitPoint} - Enum defining initialization order</li>
 * </ul>
 * 
 * <h2>Supporting Classes</h2>
 * <ul>
 *   <li>{@link org.restheart.plugins.PluginRecord} - Metadata about loaded plugins</li>
 *   <li>{@link org.restheart.plugins.PluginsRegistry} - Central registry for all plugins</li>
 *   <li>{@link org.restheart.plugins.ExchangeTypeResolver} - Resolves exchange types</li>
 *   <li>{@link org.restheart.plugins.InterceptorException} - Exception for interceptor errors</li>
 * </ul>
 * 
 * <h2>Dependency Injection</h2>
 * <p>RESTHeart provides dependency injection for plugins via the {@link org.restheart.plugins.Inject} annotation.
 * Available providers include:</p>
 * <ul>
 *   <li>{@code @Inject("config")} - Plugin-specific configuration from plugins-args</li>
 *   <li>{@code @Inject("rh-config")} - Global RESTHeart configuration</li>
 *   <li>{@code @Inject("registry")} - Plugins registry</li>
 *   <li>{@code @Inject("mclient")} - MongoDB client</li>
 *   <li>{@code @Inject("acl-registry")} - ACL registry</li>
 *   <li>{@code @Inject("gql-app-definition-cache")} - GraphQL app cache (v8.0.9+)</li>
 * </ul>
 * 
 * <h2>Plugin Development Guidelines</h2>
 * <p>When developing plugins:</p>
 * <ul>
 *   <li>Annotate plugin classes with {@code @RegisterPlugin}</li>
 *   <li>Implement the appropriate interface based on the data type handled</li>
 *   <li>Use {@code @Inject} for dependency injection</li>
 *   <li>Use {@code @OnInit} for initialization logic</li>
 *   <li>Implement {@code ConfigurablePlugin} for runtime configuration</li>
 *   <li>Choose appropriate {@code InterceptPoint} for interceptors</li>
 * </ul>
 * 
 * <h2>Example Plugin</h2>
 * <pre>{@code
 * @RegisterPlugin(
 *     name = "hello-service",
 *     description = "A simple greeting service"
 * )
 * public class HelloService implements JsonService {
 *     // Inject plugin-specific configuration from plugins-args section
 *     @Inject("config")
 *     private Map<String, Object> config;
 *     
 *     @OnInit
 *     public void init() {
 *         // Initialization logic
 *     }
 *     
 *     @Override
 *     public void handle(JsonRequest request, JsonResponse response) {
 *         response.setContent(new JsonObject()
 *             .put("message", "Hello, World!")
 *             .build());
 *     }
 * }
 * }</pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
package org.restheart.plugins;