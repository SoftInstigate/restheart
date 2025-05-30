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

import java.lang.reflect.Type;

import org.restheart.utils.PluginUtils;

import com.google.common.reflect.TypeToken;

/**
 * Interface for implementing dependency injection providers in RESTHeart.
 * <p>
 * Providers are plugins that supply dependencies to other plugins through the RESTHeart
 * dependency injection system. They enable loose coupling between plugins by allowing
 * plugins to declare their dependencies through the {@link Inject} annotation rather
 * than directly instantiating or looking up dependencies.
 * </p>
 * <p>
 * The provider pattern is particularly useful for:
 * <ul>
 *   <li><strong>Service Abstraction</strong> - Providing abstract interfaces to concrete implementations</li>
 *   <li><strong>Configuration Management</strong> - Supplying configured objects to dependent plugins</li>
 *   <li><strong>Resource Sharing</strong> - Sharing expensive resources like database connections</li>
 *   <li><strong>Test Mocking</strong> - Providing test implementations during unit testing</li>
 *   <li><strong>Dynamic Configuration</strong> - Providing objects that can change based on runtime conditions</li>
 * </ul>
 * </p>
 * <p>
 * Example provider implementation:
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "databaseProvider",
 *     description = "Provides configured database service"
 * )
 * public class DatabaseProvider implements Provider&lt;DatabaseService&gt; {
 *     &#64;Inject("config")
 *     private Map&lt;String, Object&gt; config;
 *     
 *     private DatabaseService dbService;
 *     
 *     &#64;OnInit
 *     public void init() {
 *         String connectionString = (String) config.get("connectionString");
 *         this.dbService = new DatabaseService(connectionString);
 *     }
 *     
 *     &#64;Override
 *     public DatabaseService get(PluginRecord&lt;?&gt; caller) {
 *         LOGGER.debug("Providing DatabaseService to {}", caller.getName());
 *         return dbService;
 *     }
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Using Providers:</strong><br>
 * Other plugins can use the provided dependency by injection:
 * <pre>
 * &#64;RegisterPlugin(name = "myService", description = "Service using database")
 * public class MyService implements Service&lt;JsonRequest, JsonResponse&gt; {
 *     &#64;Inject("databaseProvider")
 *     private DatabaseService dbService;
 *     
 *     // Service implementation using dbService...
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Lifecycle:</strong>
 * <ol>
 *   <li>Provider is instantiated and configured during plugin loading</li>
 *   <li>Provider dependencies (if any) are injected</li>
 *   <li>Provider initialization methods are called</li>
 *   <li>Other plugins declare dependencies on this provider</li>
 *   <li>RESTHeart calls {@link #get(PluginRecord)} to obtain dependencies</li>
 *   <li>Dependencies are injected into requesting plugins</li>
 * </ol>
 * </p>
 * <p>
 * <strong>Type Safety:</strong><br>
 * The generic parameter T ensures type safety at compile time. The provider methods
 * {@link #type()} and {@link #rawType()} use reflection to determine the actual type
 * being provided, enabling runtime type checking and proper dependency resolution.
 * </p>
 *
 * @param <T> the type of object this provider supplies
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Inject
 * @see ConfigurablePlugin
 * @see PluginRecord
 */
public interface Provider<T> extends ConfigurablePlugin {
    /**
     * Provides the dependency instance to the requesting plugin.
     * <p>
     * This method is called by the RESTHeart dependency injection system when a plugin
     * requires the dependency provided by this provider. The method should return an
     * instance of type T that will be injected into the requesting plugin's field
     * marked with the {@link Inject} annotation.
     * </p>
     * <p>
     * Implementation considerations:
     * <ul>
     *   <li><strong>Thread Safety</strong> - This method may be called concurrently from multiple threads</li>
     *   <li><strong>Lifecycle Management</strong> - Consider whether to return shared instances or new instances</li>
     *   <li><strong>Caller Context</strong> - Use the caller parameter to provide context-specific instances if needed</li>
     *   <li><strong>Error Handling</strong> - Throw appropriate exceptions if the dependency cannot be provided</li>
     * </ul>
     * </p>
     * <p>
     * Common implementation patterns:
     * <pre>
     * // Singleton pattern - return the same instance to all callers
     * public DatabaseService get(PluginRecord&lt;?&gt; caller) {
     *     return this.sharedDbService;
     * }
     * 
     * // Factory pattern - create new instances per caller
     * public DatabaseConnection get(PluginRecord&lt;?&gt; caller) {
     *     return connectionPool.getConnection();
     * }
     * 
     * // Caller-specific pattern - customize based on the requesting plugin
     * public Configuration get(PluginRecord&lt;?&gt; caller) {
     *     return configManager.getConfiguration(caller.getName());
     * }
     * </pre>
     * </p>
     *
     * @param caller the PluginRecord of the plugin that requires this dependency
     * @return the provided object instance of type T
     * @throws RuntimeException if the dependency cannot be provided
     */
    public T get(PluginRecord<?> caller);

    /**
     * Returns the name of this provider for dependency resolution.
     * <p>
     * The provider name is used by the dependency injection system to match
     * {@link Inject} annotations with their corresponding providers. By default,
     * this returns the plugin name as specified in the {@link RegisterPlugin}
     * annotation.
     * </p>
     * <p>
     * For example, if a provider is registered with name "databaseProvider",
     * other plugins can inject its dependency using:
     * <pre>
     * &#64;Inject("databaseProvider")
     * private DatabaseService dbService;
     * </pre>
     * </p>
     * <p>
     * The name should be unique among all providers in the system to avoid
     * conflicts during dependency resolution.
     * </p>
     *
     * @return the unique name of this provider
     */
    public default String name() {
        return PluginUtils.name(this);
    }

    /**
     * Returns the parameterized Type of the dependency provided by this provider.
     * <p>
     * This method uses reflection to determine the actual generic type T at runtime,
     * enabling the dependency injection system to perform type checking and ensure
     * that dependencies are injected into fields of compatible types.
     * </p>
     * <p>
     * The returned Type includes full generic information, which is useful for
     * complex generic types like {@code List<String>} or {@code Map<String, Object>}.
     * </p>
     * <p>
     * This method is typically used internally by the RESTHeart framework for
     * dependency resolution and should not need to be overridden in most cases.
     * </p>
     *
     * @return the parameterized Type of the generic parameter T
     */
    default Type type() {
        return new TypeToken<T>(getClass()) {
			private static final long serialVersionUID = 1363463867743712234L;
        }.getType();
    }

    /**
     * Returns the raw Class of the dependency provided by this provider.
     * <p>
     * This method uses reflection to determine the raw class type of the generic
     * parameter T, providing the base class without generic information. This is
     * useful for basic type checking and compatibility verification during
     * dependency injection.
     * </p>
     * <p>
     * For example, if the provider provides {@code List<String>}, this method
     * would return {@code List.class}, while {@link #type()} would return the
     * full parameterized type including the String generic parameter.
     * </p>
     * <p>
     * This method is typically used internally by the RESTHeart framework for
     * dependency resolution and should not need to be overridden in most cases.
     * </p>
     *
     * @return the raw Class of the generic parameter T
     */
    default Class<? super T> rawType() {
        return new TypeToken<T>(getClass()) {
			private static final long serialVersionUID = 1363463867743712234L;
        }.getRawType();
    }
}
