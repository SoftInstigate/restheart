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

/**
 * Data container that holds metadata and configuration information for a registered plugin.
 * <p>
 * PluginRecord serves as a comprehensive descriptor for plugins registered with the RESTHeart
 * framework. It encapsulates all the metadata from the {@link RegisterPlugin} annotation
 * along with the plugin instance, configuration arguments, and runtime state information.
 * This record is used throughout the plugin lifecycle for registration, configuration,
 * and execution management.
 * </p>
 * <p>
 * The record contains:
 * <ul>
 *   <li><strong>Plugin Metadata</strong> - Name, description, class information</li>
 *   <li><strong>Security Settings</strong> - Authentication and authorization requirements</li>
 *   <li><strong>Lifecycle State</strong> - Enabled/disabled status</li>
 *   <li><strong>Plugin Instance</strong> - The actual instantiated plugin object</li>
 *   <li><strong>Configuration Arguments</strong> - Runtime configuration parameters</li>
 * </ul>
 * </p>
 * <p>
 * Configuration override behavior allows runtime modification of plugin settings:
 * <ul>
 *   <li>The 'enabled' configuration parameter can override the default enabled state</li>
 *   <li>The 'secured' configuration parameter can override the default security requirements</li>
 *   <li>These overrides take precedence over annotation-defined defaults</li>
 * </ul>
 * </p>
 * <p>
 * Example configuration override in restheart.yml:
 * <pre>
 * plugins-args:
 *   myPlugin:
 *     enabled: false        # Override enabledByDefault from annotation
 *     secured: true         # Override secure from annotation
 *     customParam: "value"  # Plugin-specific configuration
 * </pre>
 * </p>
 * <p>
 * The PluginRecord is immutable after creation and provides thread-safe access to
 * plugin information. It is used by the plugin registry, dependency injection system,
 * and request processing pipeline to manage plugin execution.
 * </p>
 *
 * @param <T> the type of plugin this record contains, must extend Plugin
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Plugin
 * @see RegisterPlugin
 * @see PluginsRegistry
 */
public class PluginRecord<T extends Plugin> {
    private final String name;
    private final String description;
    private final boolean secure;
    private final boolean enabledByDefault;
    private final String className;
    private final T instance;
    private final Map<String, Object> confArgs;

    /**
     * Configuration key used to override the enabled state of a plugin.
     * <p>
     * When this key is present in the plugin's configuration arguments with a Boolean value,
     * it overrides the {@code enabledByDefault} setting from the {@link RegisterPlugin}
     * annotation. This allows administrators to enable or disable plugins at runtime
     * without modifying code or recompiling.
     * </p>
     */
    public static final String PLUGIN_ENABLED_KEY = "enabled";

    /**
     * Configuration key used to override the security requirements of a plugin.
     * <p>
     * When this key is present in the plugin's configuration arguments with a Boolean value,
     * it overrides the {@code secure} setting from the {@link RegisterPlugin} annotation.
     * This allows administrators to modify authentication/authorization requirements
     * for plugins at runtime.
     * </p>
     */
    public static final String PLUGIN_SECURE_KEY = "secured";

    /**
     * Creates a new PluginRecord with the specified plugin metadata and configuration.
     * <p>
     * This constructor is typically called by the plugin registry during plugin
     * registration to create a comprehensive record of the plugin's metadata,
     * configuration, and runtime instance.
     * </p>
     *
     * @param name the unique name of the plugin from {@link RegisterPlugin#name()}
     * @param description the plugin description from {@link RegisterPlugin#description()}
     * @param secure the default security requirement from {@link RegisterPlugin#secure()}
     * @param enabledByDefault the default enabled state from {@link RegisterPlugin#enabledByDefault()}
     * @param className the fully qualified class name of the plugin implementation
     * @param instance the instantiated plugin object
     * @param confArgs the configuration arguments map from the RESTHeart configuration
     */
    public PluginRecord(String name,
            String description,
            final boolean secure,
            boolean enabledByDefault,
            String className,
            T instance,
            Map<String, Object> confArgs) {
        this.name = name;
        this.description = description;
        this.secure = secure;
        this.enabledByDefault = enabledByDefault;
        this.instance = instance;
        this.className = className;
        this.confArgs = confArgs;
    }

    /**
     * Returns the unique name of this plugin.
     * <p>
     * The name serves as the primary identifier for the plugin within the RESTHeart
     * framework and is used for configuration references, dependency injection,
     * logging, and plugin lookup operations.
     * </p>
     *
     * @return the unique plugin name as specified in {@link RegisterPlugin#name()}
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the human-readable description of this plugin.
     * <p>
     * The description provides information about the plugin's functionality and
     * purpose, typically used for documentation, administration interfaces,
     * and debugging purposes.
     * </p>
     *
     * @return the plugin description as specified in {@link RegisterPlugin#description()}
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the fully qualified class name of the plugin implementation.
     * <p>
     * The class name is used for debugging, logging, and reflection operations.
     * It provides the complete package and class name of the plugin implementation.
     * </p>
     *
     * @return the fully qualified class name of the plugin implementation
     */
    public String getClassName() {
        return className;
    }

    /**
     * Returns whether this plugin requires authentication and authorization.
     * <p>
     * This method checks both the default security setting from the plugin annotation
     * and any configuration overrides. If the configuration contains a 'secured' key
     * with a Boolean value, that takes precedence over the annotation default.
     * </p>
     * <p>
     * Secure plugins will only be executed if:
     * <ul>
     *   <li>The request is successfully authenticated</li>
     *   <li>The authenticated user is authorized to access the plugin</li>
     * </ul>
     * </p>
     *
     * @return true if the plugin requires authentication and authorization, false otherwise
     */
    public boolean isSecure() {
        return isSecure(secure, getConfArgs());
    }

    /**
     * Returns whether this plugin is currently enabled.
     * <p>
     * This method checks both the default enabled setting from the plugin annotation
     * and any configuration overrides. If the configuration contains an 'enabled' key
     * with a Boolean value, that takes precedence over the annotation default.
     * </p>
     * <p>
     * Disabled plugins are not registered with the framework and will not be
     * invoked during request processing.
     * </p>
     *
     * @return true if the plugin is enabled and should be active, false otherwise
     */
    public boolean isEnabled() {
        return isEnabled(enabledByDefault, getConfArgs());
    }

    /**
     * Static utility method to determine if a plugin is enabled based on default setting and configuration.
     * <p>
     * This method implements the logic for resolving the enabled state of a plugin by
     * checking for configuration overrides. It prioritizes explicit configuration
     * values over annotation defaults.
     * </p>
     * <p>
     * Resolution logic:
     * <ol>
     *   <li>If confArgs is null, return enabledByDefault</li>
     *   <li>If confArgs contains 'enabled' key with Boolean value, return that value</li>
     *   <li>Otherwise, return enabledByDefault</li>
     * </ol>
     * </p>
     *
     * @param enabledByDefault the default enabled state from the plugin annotation
     * @param confArgs the configuration arguments map that may contain override values
     * @return true if the plugin should be enabled, false otherwise
     */
    public static boolean isEnabled(boolean enabledByDefault, Map<String, Object> confArgs) {
        return confArgs == null
                ? enabledByDefault
                : confArgs.containsKey(PLUGIN_ENABLED_KEY)
                && confArgs.get(PLUGIN_ENABLED_KEY) != null
                && confArgs.get(PLUGIN_ENABLED_KEY) instanceof Boolean
                ? (Boolean) confArgs.get(PLUGIN_ENABLED_KEY)
                : enabledByDefault;
    }

    /**
     * Static utility method to determine if a plugin is secure based on default setting and configuration.
     * <p>
     * This method implements the logic for resolving the security requirements of a plugin
     * by checking for configuration overrides. It prioritizes explicit configuration
     * values over annotation defaults.
     * </p>
     * <p>
     * Resolution logic:
     * <ol>
     *   <li>If confArgs is null, return secure default</li>
     *   <li>If confArgs contains 'secured' key with Boolean value, return that value</li>
     *   <li>Otherwise, return secure default</li>
     * </ol>
     * </p>
     *
     * @param secure the default security requirement from the plugin annotation
     * @param confArgs the configuration arguments map that may contain override values
     * @return true if the plugin should require authentication/authorization, false otherwise
     */
    public static boolean isSecure(boolean secure, Map<String, Object> confArgs) {
        return confArgs == null
                ? secure
                : confArgs.containsKey(PLUGIN_SECURE_KEY)
                && confArgs.get(PLUGIN_SECURE_KEY) != null
                && confArgs.get(PLUGIN_SECURE_KEY) instanceof Boolean
                ? (Boolean) confArgs.get(PLUGIN_SECURE_KEY)
                : secure;
    }

    /**
     * Returns the configuration arguments map for this plugin.
     * <p>
     * The configuration arguments are loaded from the RESTHeart configuration file
     * and contain plugin-specific settings, feature flags, and override values.
     * This map is used by {@link ConfigurablePlugin} implementations to access
     * their runtime configuration.
     * </p>
     * <p>
     * Common configuration arguments include:
     * <ul>
     *   <li>'enabled' - Override for the plugin's enabled state</li>
     *   <li>'secured' - Override for the plugin's security requirements</li>
     *   <li>Plugin-specific parameters as defined by the plugin implementation</li>
     * </ul>
     * </p>
     *
     * @return the configuration arguments map, may be null if no configuration is provided
     */
    public Map<String, Object> getConfArgs() {
        return confArgs;
    }

    /**
     * Returns the instantiated plugin object.
     * <p>
     * This is the actual plugin implementation instance that will be invoked
     * during request processing. The instance has been instantiated, configured,
     * and initialized by the RESTHeart plugin system and is ready for use.
     * </p>
     * <p>
     * The instance type corresponds to the generic parameter T and can be cast
     * to specific plugin interface types (Service, Interceptor, etc.) as needed.
     * </p>
     *
     * @return the instantiated and configured plugin object
     */
    public T getInstance() {
        return instance;
    }
}
