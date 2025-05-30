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

import java.util.Map;

import org.restheart.configuration.ConfigurationException;
import org.restheart.utils.PluginUtils;

/**
 * Interface for plugins that can be configured through external configuration files.
 * <p>
 * ConfigurablePlugin extends the base Plugin interface to provide configuration support
 * for plugins that need external configuration parameters. It offers utility methods
 * for accessing and validating configuration arguments passed from the RESTHeart
 * configuration system.
 * </p>
 * <p>
 * Plugins implementing this interface can receive configuration arguments through
 * the RESTHeart configuration file, typically in YAML format. The configuration
 * arguments are provided as a Map&lt;String, ?&gt; where keys are argument names
 * and values can be of various types (String, Integer, Boolean, List, Map, etc.).
 * </p>
 * <p>
 * Example configuration in restheart.yml:
 * <pre>
 * plugins-args:
 *   myPlugin:
 *     enabled: true
 *     apiKey: "secret-key"
 *     timeout: 30
 *     endpoints:
 *       - "/api/v1"
 *       - "/api/v2"
 * </pre>
 * </p>
 * <p>
 * Example plugin implementation:
 * <pre>
 * &#64;RegisterPlugin(name = "myPlugin", description = "Example configurable plugin")
 * public class MyPlugin implements Service&lt;JsonRequest, JsonResponse&gt;, ConfigurablePlugin {
 *     private String apiKey;
 *     private int timeout;
 *     
 *     &#64;Inject("config")
 *     public void init(Map&lt;String, Object&gt; args) throws ConfigurationException {
 *         this.apiKey = arg(args, "apiKey");
 *         this.timeout = argOrDefault(args, "timeout", 60);
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Plugin
 * @see RegisterPlugin
 * @see org.restheart.configuration.ConfigurationException
 */
public interface ConfigurablePlugin extends Plugin {
    /**
     * Static utility method to retrieve a required configuration argument value.
     * <p>
     * This method extracts a configuration argument value from the provided arguments map
     * and throws a ConfigurationException if the argument is not found. It provides type-safe
     * access to configuration values with automatic casting to the expected type.
     * </p>
     * <p>
     * Example usage:
     * <pre>
     * String apiKey = ConfigurablePlugin.argValue(args, "apiKey");
     * Integer timeout = ConfigurablePlugin.argValue(args, "timeout");
     * List&lt;String&gt; endpoints = ConfigurablePlugin.argValue(args, "endpoints");
     * </pre>
     * </p>
     *
     * @param <V> the expected return type of the configuration argument
     * @param args the configuration arguments map provided by RESTHeart
     * @param argKey the key of the configuration argument to retrieve
     * @return the configuration argument value cast to type V
     * @throws ConfigurationException if the argument is not found in the configuration
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
     * Static utility method to retrieve a configuration argument value with a default fallback.
     * <p>
     * This method extracts a configuration argument value from the provided arguments map
     * and returns a default value if the argument is not found. It provides safe access
     * to optional configuration parameters with automatic type casting.
     * </p>
     * <p>
     * Example usage:
     * <pre>
     * // Use default timeout of 30 seconds if not configured
     * Integer timeout = ConfigurablePlugin.argValueOrDefault(args, "timeout", 30);
     * 
     * // Use default enabled state of true if not configured
     * Boolean enabled = ConfigurablePlugin.argValueOrDefault(args, "enabled", true);
     * </pre>
     * </p>
     *
     * @param <V> the expected return type of the configuration argument
     * @param args the configuration arguments map provided by RESTHeart
     * @param argKey the key of the configuration argument to retrieve
     * @param value the default value to return if the argument is not found
     * @return the configuration argument value cast to type V, or the default value if not found
     * @throws ConfigurationException if type casting fails
     */
    public static <V extends Object> V argValueOrDefault(final Map<String, ?> args, final String argKey, V value) throws ConfigurationException {
        if (args == null || !args.containsKey(argKey)) {
            return value;
        } else {
            return argValue(args, argKey);
        }
    }

    /**
     * Instance method to retrieve a required configuration argument value.
     * <p>
     * This method extracts a configuration argument value from the provided arguments map
     * and throws a ConfigurationException with plugin-specific context if the argument is not found.
     * The error message includes the plugin name for better debugging.
     * </p>
     * <p>
     * Example usage in plugin initialization:
     * <pre>
     * &#64;Inject("config")
     * public void init(Map&lt;String, Object&gt; args) throws ConfigurationException {
     *     this.apiKey = arg(args, "apiKey");
     *     this.maxConnections = arg(args, "maxConnections");
     * }
     * </pre>
     * </p>
     *
     * @param <V> the expected return type of the configuration argument
     * @param args the configuration arguments map provided by RESTHeart
     * @param argKey the key of the configuration argument to retrieve
     * @return the configuration argument value cast to type V
     * @throws ConfigurationException if the argument is not found, with plugin-specific error message
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
     * Instance method to retrieve a configuration argument value with a default fallback.
     * <p>
     * This method extracts a configuration argument value from the provided arguments map
     * and returns a default value if the argument is not found. It provides safe access
     * to optional configuration parameters with automatic type casting and plugin context.
     * </p>
     * <p>
     * Example usage in plugin initialization:
     * <pre>
     * &#64;Inject("config")
     * public void init(Map&lt;String, Object&gt; args) throws ConfigurationException {
     *     // Required argument
     *     this.apiKey = arg(args, "apiKey");
     *     
     *     // Optional arguments with defaults
     *     this.timeout = argOrDefault(args, "timeout", 30);
     *     this.retryCount = argOrDefault(args, "retryCount", 3);
     *     this.enabled = argOrDefault(args, "enabled", true);
     * }
     * </pre>
     * </p>
     *
     * @param <V> the expected return type of the configuration argument
     * @param args the configuration arguments map provided by RESTHeart
     * @param argKey the key of the configuration argument to retrieve
     * @param value the default value to return if the argument is not found
     * @return the configuration argument value cast to type V, or the default value if not found
     * @throws ConfigurationException if type casting fails
     */
    default public <V extends Object> V argOrDefault(final Map<String, ?> args, final String argKey, V value) throws ConfigurationException {
        if (args == null || !args.containsKey(argKey)) {
            return value;
        } else {
            return arg(args, argKey);
        }
    }
}
