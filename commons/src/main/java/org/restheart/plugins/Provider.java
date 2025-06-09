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
 * Base interface for dependency providers in RESTHeart's plugin system.
 *
 * <p>A Provider is responsible for creating and managing instances of objects that can be
 * injected into plugins using the {@link Inject} annotation. Providers enable a simple
 * dependency injection mechanism where plugins can declare dependencies without knowing
 * how to create them.</p>
 *
 * <h2>Purpose</h2>
 * <p>Providers serve as factories for injectable dependencies, allowing:</p>
 * <ul>
 *   <li>Centralized object creation and lifecycle management</li>
 *   <li>Lazy initialization of expensive resources</li>
 *   <li>Configuration-based object instantiation</li>
 *   <li>Sharing of singleton instances across plugins</li>
 *   <li>Abstraction of complex initialization logic</li>
 * </ul>
 *
 * <h2>How It Works</h2>
 * <p>When a plugin field is annotated with {@code @Inject("provider-name")}, RESTHeart:</p>
 * <ol>
 *   <li>Looks up a Provider with matching name in the registry</li>
 *   <li>Calls the Provider's {@link #get(PluginRecord)} method</li>
 *   <li>Injects the returned object into the plugin field</li>
 * </ol>
 *
 * <h2>Implementation Example</h2>
 * <pre>{@code
 * @RegisterPlugin(name = "database-provider")
 * public class DatabaseProvider implements Provider<Database> {
 *     private Database sharedInstance;
 *     
 *     @Inject("config")
 *     private Map<String, Object> config;
 *
 *     @Override
 *     public Database get(PluginRecord<?> caller) {
 *         if (sharedInstance == null) {
 *             // Create database connection using configuration
 *             String url = config.get("db.url").toString();
 *             sharedInstance = new Database(url);
 *         }
 *         return sharedInstance;
 *     }
 * }
 *
 * // Usage in a plugin
 * @RegisterPlugin(name = "my-service")
 * public class MyService implements JsonService {
 *     @Inject("database-provider")
 *     private Database database;
 *     
 *     @OnInit
 *     public void init() {
 *         // database is injected and ready to use
 *         database.query("SELECT * FROM users");
 *     }
 * }
 * }</pre>
 *
 * <h2>Built-in Providers</h2>
 * <p>RESTHeart includes several built-in providers:</p>
 * <ul>
 *   <li><strong>config</strong> - Provides plugin-specific configuration</li>
 *   <li><strong>rh-config</strong> - Provides global RESTHeart configuration</li>
 *   <li><strong>registry</strong> - Provides the PluginsRegistry</li>
 *   <li><strong>mclient</strong> - Provides MongoDB client instance</li>
 *   <li><strong>acl-registry</strong> - Provides ACL registry</li>
 * </ul>
 *
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Make providers thread-safe if they manage shared state</li>
 *   <li>Use lazy initialization for expensive resources</li>
 *   <li>Document what configuration your provider expects</li>
 *   <li>Consider implementing {@link ConfigurablePlugin} for configuration support</li>
 *   <li>Use meaningful provider names that indicate what they provide</li>
 *   <li>Handle initialization errors gracefully in {@link #get(PluginRecord)}</li>
 * </ul>
 *
 * @param <T> the type of object this provider creates
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Inject
 * @see RegisterPlugin
 * @see ConfigurablePlugin
 */
public interface Provider<T> extends ConfigurablePlugin {
    /**
     * Creates or retrieves an instance of the provided object.
     *
     * <p>This method is called by RESTHeart when a plugin requests injection
     * of this provider's object. The implementation can:</p>
     * <ul>
     *   <li>Return a singleton instance shared across all plugins</li>
     *   <li>Create a new instance for each caller</li>
     *   <li>Use the caller information to customize the returned object</li>
     * </ul>
     *
     * <p>The method should be thread-safe as it may be called concurrently
     * during plugin initialization.</p>
     *
     * @param caller the PluginRecord of the plugin that requires this dependency,
     *               can be used to customize the provided object or for access control
     * @return the provided object instance, must not be null unless the field
     *         being injected is explicitly nullable
     * @throws RuntimeException if the object cannot be created due to configuration
     *                          errors or initialization failures
     */
    public T get(PluginRecord<?> caller);

    /**
     * Returns the name of this provider.
     *
     * <p>The name is used to match {@code @Inject} annotations with providers.
     * For example, {@code @Inject("my-provider")} will look for a provider
     * with name "my-provider".</p>
     *
     * <p>By default, uses the name specified in the {@link RegisterPlugin}
     * annotation or derives it from the class name.</p>
     *
     * @return the name of the provider used for injection matching
     */
    public default String name() {
        return PluginUtils.name(this);
    }

    /**
     * Returns the Type of the generic parameter T.
     *
     * <p>This method uses reflection to determine the actual type argument
     * of the Provider implementation. It's used internally by RESTHeart
     * for type checking during injection.</p>
     *
     * @return the Type of objects this provider creates, including generic
     *         type information if applicable
     */
    default Type type() {
        return new TypeToken<T>(getClass()) {
			private static final long serialVersionUID = 1363463867743712234L;
        }.getType();
    }

    /**
     * Returns the Class of the generic parameter T.
     *
     * <p>This method returns the raw class type without generic parameters.
     * For example, if this is a {@code Provider<List<String>>}, this method
     * returns {@code List.class}.</p>
     *
     * @return the raw Class type of objects this provider creates
     */
    default Class<? super T> rawType() {
        return new TypeToken<T>(getClass()) {
			private static final long serialVersionUID = 1363463867743712234L;
        }.getRawType();
    }
}
