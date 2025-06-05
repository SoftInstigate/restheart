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

import java.util.Map;

import org.restheart.configuration.ConfigurationException;
import org.restheart.utils.PluginUtils;

/**
 * Interface for plugins that can be configured via RESTHeart configuration file.
 * 
 * <p>ConfigurablePlugin extends the base {@link Plugin} interface to add configuration
 * capabilities. Plugins implementing this interface can receive configuration parameters
 * from RESTHeart's configuration file, allowing for flexible and customizable behavior
 * without code changes.</p>
 * 
 * <h2>Configuration Format</h2>
 * <p>Plugin configuration is typically provided in the RESTHeart configuration file as:</p>
 * <pre>{@code
 * plugins-args:
 *   my-plugin:
 *     param1: value1
 *     param2: value2
 *     nested:
 *       param3: value3
 * }</pre>
 * 
 * <h2>Usage Pattern</h2>
 * <p>Plugins receive configuration through dependency injection:</p>
 * <pre>{@code
 * @RegisterPlugin(name = "my-plugin")
 * public class MyPlugin implements ConfigurablePlugin, Service<Request<?>, Response<?>> {
 *     @Inject("config")
 *     private Map<String, Object> config;
 *     
 *     @OnInit
 *     public void init() {
 *         String param1 = arg(config, "param1");
 *         int param2 = argOrDefault(config, "param2", 100);
 *     }
 * }
 * }</pre>
 * 
 * <h2>Utility Methods</h2>
 * <p>This interface provides several utility methods for accessing configuration values:</p>
 * <ul>
 *   <li>{@link #arg(Map, String)} - Get required configuration value (instance method)</li>
 *   <li>{@link #argOrDefault(Map, String, Object)} - Get configuration with default (instance method)</li>
 *   <li>{@link #argValue(Map, String)} - Get required configuration value (static method)</li>
 *   <li>{@link #argValueOrDefault(Map, String, Object)} - Get configuration with default (static method)</li>
 * </ul>
 * 
 * <h2>Type Safety</h2>
 * <p>Configuration values are retrieved with generic type parameters, providing compile-time
 * type safety when possible. Runtime type checking may still be necessary for complex types.</p>
 * 
 * @see Plugin
 * @see FileConfigurablePlugin
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface ConfigurablePlugin extends Plugin {
    /**
     * Retrieves a required configuration value from the provided arguments map.
     * 
     * <p>This static utility method extracts a configuration value by its key. If the key
     * is not present in the configuration, a {@link ConfigurationException} is thrown.</p>
     * 
     * @param <V> the type of the configuration value
     * @param args the configuration arguments map
     * @param argKey the configuration key to retrieve
     * @return the configuration value cast to type V
     * @throws ConfigurationException if the required argument is not found
     */
    @SuppressWarnings("unchecked")
    public static <V extends Object> V argValue(final Map<String, ?> args, final String argKey) throws ConfigurationException {
        if (args == null || !args.containsKey(argKey)) {
            throw new ConfigurationException("Required configuration argument '" + argKey + "' non found");
        } else {
            return (V) args.get(argKey);
        }
    }

    /**
     * Retrieves a configuration value from the provided arguments map with a default fallback.
     * 
     * <p>This static utility method attempts to extract a configuration value by its key.
     * If the key is not present, the provided default value is returned instead.</p>
     * 
     * @param <V> the type of the configuration value
     * @param args the configuration arguments map
     * @param argKey the configuration key to retrieve
     * @param value the default value to return if the key is not found
     * @return the configuration value cast to type V, or the default value
     * @throws ConfigurationException if there's an error accessing the configuration
     */
    public static <V extends Object> V argValueOrDefault(final Map<String, ?> args, final String argKey, V value) throws ConfigurationException {
        if (args == null || !args.containsKey(argKey)) {
            return value;
        } else {
            return argValue(args, argKey);
        }
    }

    /**
     * Retrieves a required configuration value for this plugin instance.
     * 
     * <p>This instance method extracts a configuration value by its key. If the key
     * is not present, a {@link ConfigurationException} is thrown with a message
     * that includes the plugin name for better error diagnostics.</p>
     * 
     * <h3>Example Usage</h3>
     * <pre>{@code
     * String dbUrl = arg(config, "database-url");
     * Integer maxConnections = arg(config, "max-connections");
     * }</pre>
     * 
     * @param <V> the type of the configuration value
     * @param args the configuration arguments map
     * @param argKey the configuration key to retrieve
     * @return the configuration value cast to type V
     * @throws ConfigurationException if the required argument is not found
     */
    @SuppressWarnings("unchecked")
    default public <V extends Object> V arg(final Map<String, ?> args, final String argKey) throws ConfigurationException {
        if (args == null || !args.containsKey(argKey)) {
            throw new ConfigurationException("The plugin " + PluginUtils.name(this) + " requires the missing configuration argument '" + argKey + "'");
        } else {
            return (V) args.get(argKey);
        }
    }

    /**
     * Retrieves a configuration value for this plugin instance with a default fallback.
     * 
     * <p>This instance method attempts to extract a configuration value by its key.
     * If the key is not present, the provided default value is returned instead.
     * This is useful for optional configuration parameters.</p>
     * 
     * <h3>Example Usage</h3>
     * <pre>{@code
     * int timeout = argOrDefault(config, "timeout", 30);
     * boolean debug = argOrDefault(config, "debug", false);
     * String prefix = argOrDefault(config, "prefix", "default-");
     * }</pre>
     * 
     * @param <V> the type of the configuration value
     * @param args the configuration arguments map
     * @param argKey the configuration key to retrieve
     * @param value the default value to return if the key is not found
     * @return the configuration value cast to type V, or the default value
     * @throws ConfigurationException if there's an error accessing the configuration
     */
    default public <V extends Object> V argOrDefault(final Map<String, ?> args, final String argKey, V value) throws ConfigurationException {
        if (args == null || !args.containsKey(argKey)) {
            return value;
        } else {
            return arg(args, argKey);
        }
    }
}
