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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for dependency injection in RESTHeart plugins.
 *
 * <p>The {@code @Inject} annotation marks fields in plugin classes for automatic dependency
 * injection by the RESTHeart plugin framework. This enables plugins to access shared
 * resources, configuration, and other objects hold by Providers without manual instantiation.</p>
 *
 * <h2>Supported Injection Types</h2>
 * <p>The following values can be used with {@code @Inject}:</p>
 * <ul>
 *   <li><strong>"config"</strong> - Injects plugin-specific configuration from the plugins-args section</li>
 *   <li><strong>"rh-config"</strong> - Injects the global RESTHeart {@link org.restheart.configuration.Configuration} object</li>
 *   <li><strong>"registry"</strong> - Injects the {@link PluginsRegistry} instance</li>
 *   <li><strong>"mclient"</strong> - Injects the MongoDB client (if MongoDB is enabled)</li>
 *   <li><strong>"acl-registry"</strong> - Injects the {@link org.restheart.security.ACLRegistry} for programmatic permission definition</li>
 *   <li><strong>"gql-app-definition-cache"</strong> - Injects the GraphQL app definition cache as {@code LoadingCache<String, GraphQLApp>} (available from v8.0.9)</li>
 * </ul>
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
 * <h2>Injection Timing</h2>
 * <p>Field injection occurs after plugin instantiation but before {@link OnInit} methods
 * are called. This ensures all dependencies are available during initialization.</p>
 *
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Declare injected fields as private to maintain encapsulation</li>
 *   <li>Avoid circular dependencies between plugins</li>
 *   <li>Check for null values when injecting optional dependencies</li>
 *   <li>Use specific types rather than Object when possible</li>
 *   <li>Document which injections your plugin requires</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * <p>If an injected dependency cannot be resolved, RESTHeart will:</p>
 * <ul>
 *   <li>Log an error message with details</li>
 *   <li>Skip the plugin initialization</li>
 *   <li>Continue loading other plugins</li>
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
     * <p>Common values include:</p>
     * <ul>
     *   <li>"config" - Plugin-specific configuration from plugins-args</li>
     *   <li>"rh-config" - Global RESTHeart configuration</li>
     *   <li>"registry" - Plugins registry</li>
     *   <li>"mclient" - MongoDB client</li>
     *   <li>"acl-registry" - ACL registry</li>
     *   <li>"gql-app-definition-cache" - GraphQL app definition cache</li>
     * </ul>
     *
     * @return the identifier of the dependency to inject
     */
    String value();
}
