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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for dependency injection in RESTHeart plugins.
 *
 * <p>The {@code @Inject} annotation marks fields in plugin classes for automatic dependency
 * injection by the RESTHeart plugin framework. This enables plugins to access shared
 * resources, configuration, and other objects provided by {@link Provider} implementations
 * without manual instantiation.</p>
 *
 * <h2>How It Works</h2>
 * <p>When RESTHeart initializes a plugin, it scans for fields annotated with {@code @Inject}
 * and automatically populates them with objects from registered {@link Provider}s or built-in
 * providers. The injection happens after plugin instantiation but before {@link OnInit} methods
 * are called.</p>
 *
 * <h2>Built-in Injection Types</h2>
 * <p>RESTHeart provides several built-in injection values:</p>
 * <ul>
 *   <li><strong>"config"</strong> - Injects plugin-specific configuration from the plugins-args section as {@code Map<String, Object>}</li>
 *   <li><strong>"rh-config"</strong> - Injects the global RESTHeart {@link org.restheart.configuration.Configuration} object</li>
 *   <li><strong>"registry"</strong> - Injects the {@link PluginsRegistry} instance for accessing other plugins</li>
 *   <li><strong>"mclient"</strong> - Injects the MongoDB client as {@code MongoClient} (requires MongoDB to be enabled)</li>
 *   <li><strong>"acl-registry"</strong> - Injects the {@link org.restheart.security.ACLRegistry} for programmatic permission definition</li>
 *   <li><strong>"gql-app-definition-cache"</strong> - Injects the GraphQL app definition cache as {@code LoadingCache<String, GraphQLApp>} (available from v8.0.9)</li>
 * </ul>
 *
 * <h2>Custom Providers</h2>
 * <p>In addition to built-in types, you can inject objects from custom {@link Provider} implementations.
 * Simply use the provider's name as the injection value:</p>
 * <pre>{@code
 * @Inject("my-custom-provider")
 * private MyCustomService service;
 * }</pre>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * @RegisterPlugin(name = "my-service")
 * public class MyService implements JsonService {
 *     // Inject plugin-specific configuration
 *     @Inject("config")
 *     private Map<String, Object> config;
 *
 *     // Inject the plugins registry
 *     @Inject("registry")
 *     private PluginsRegistry registry;
 *
 *     // Inject MongoDB client
 *     @Inject("mclient")
 *     private MongoClient mongoClient;
 *
 *     @OnInit
 *     public void init() {
 *         // Injected fields are available here
 *         String dbName = config.get("database").toString();
 *     }
 * }
 * }</pre>
 *
 * <h2>Injection Lifecycle</h2>
 * <p>The injection process follows this sequence:</p>
 * <ol>
 *   <li>Plugin class is instantiated via no-arg constructor</li>
 *   <li>Fields annotated with {@code @Inject} are populated</li>
 *   <li>Methods annotated with {@link OnInit} are called</li>
 *   <li>Plugin is registered and ready for use</li>
 * </ol>
 * <p>This ensures all dependencies are available during the initialization phase.</p>
 *
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Declare injected fields as private to maintain encapsulation</li>
 *   <li>Avoid circular dependencies between plugins and providers</li>
 *   <li>Check for null values when injecting optional dependencies</li>
 *   <li>Use specific types rather than Object for better type safety</li>
 *   <li>Document which injections your plugin requires in its class Javadoc</li>
 *   <li>Initialize injected fields to null to make dependencies explicit</li>
 *   <li>Use final fields when the injected value won't change</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * <p>If an injected dependency cannot be resolved, RESTHeart will:</p>
 * <ul>
 *   <li>Log an error message with the plugin name and missing dependency</li>
 *   <li>Skip the plugin initialization to prevent runtime errors</li>
 *   <li>Continue loading other plugins to maintain system stability</li>
 *   <li>Make the failed plugin unavailable in the registry</li>
 * </ul>
 *
 * <p>Common injection failures include:</p>
 * <ul>
 *   <li>No provider registered with the specified name</li>
 *   <li>Type mismatch between field and provided object</li>
 *   <li>Provider throwing an exception during object creation</li>
 *   <li>Circular dependency between providers</li>
 * </ul>
 *
 * @see Provider
 * @see RegisterPlugin
 * @see OnInit
 * @see PluginsRegistry
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Inject {
    /**
     * Specifies the name or type of the dependency to inject.
     *
     * <p>This value is used to look up the appropriate {@link Provider} or built-in
     * injection source. The lookup is case-sensitive.</p>
     *
     * <p>Built-in values include:</p>
     * <ul>
     *   <li>"config" - Plugin-specific configuration from plugins-args</li>
     *   <li>"rh-config" - Global RESTHeart configuration</li>
     *   <li>"registry" - Plugins registry for accessing other plugins</li>
     *   <li>"mclient" - MongoDB client instance</li>
     *   <li>"acl-registry" - ACL registry for permission management</li>
     *   <li>"gql-app-definition-cache" - GraphQL app definition cache</li>
     * </ul>
     *
     * <p>For custom providers, use the provider's registered name.</p>
     *
     * @return the identifier of the dependency to inject, must not be null or empty
     */
    String value();
}
