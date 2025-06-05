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

/**
 * Container class that holds metadata and runtime information about a loaded plugin.
 * 
 * <p>PluginRecord encapsulates all the information about a plugin instance, including
 * its metadata from the {@link RegisterPlugin} annotation, configuration arguments,
 * and the actual plugin instance. This class is used by the {@link PluginsRegistry}
 * to manage plugin lifecycle and access.</p>
 * 
 * <h2>Purpose</h2>
 * <p>PluginRecord serves as a complete descriptor for a plugin, containing:</p>
 * <ul>
 *   <li>Plugin metadata (name, description, class name)</li>
 *   <li>Security and enablement status</li>
 *   <li>Configuration arguments from the RESTHeart configuration file</li>
 *   <li>The actual plugin instance</li>
 * </ul>
 * 
 * <h2>Configuration Override</h2>
 * <p>Plugin behavior can be controlled through configuration:</p>
 * <pre>{@code
 * plugins-args:
 *   my-plugin:
 *     enabled: true      # Override the enabledByDefault setting
 *     secured: false     # Override the secure setting
 *     custom-arg: value  # Plugin-specific configuration
 * }</pre>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Accessing plugin information from the registry
 * PluginRecord<JsonService> record = registry.getPluginRecord("my-service");
 * 
 * if (record.isEnabled()) {
 *     JsonService service = record.getInstance();
 *     String name = record.getName();
 *     Map<String, Object> config = record.getConfArgs();
 *     
 *     logger.info("Using plugin: {} ({})", name, record.getClassName());
 * }
 * }</pre>
 * 
 * <h2>Thread Safety</h2>
 * <p>PluginRecord instances are immutable and thread-safe. The plugin instance
 * itself must handle its own thread safety requirements.</p>
 * 
 * @param <T> the type of plugin this record contains, must extend {@link Plugin}
 * @see Plugin
 * @see RegisterPlugin
 * @see PluginsRegistry
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
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
     * Configuration key to enable or disable a plugin.
     * 
     * <p>When present in the plugin configuration, this overrides the
     * {@code enabledByDefault} setting from the {@link RegisterPlugin} annotation.</p>
     * 
     * <p>Example configuration:</p>
     * <pre>{@code
     * plugins-args:
     *   my-plugin:
     *     enabled: false  # Disable this plugin
     * }</pre>
     */
    public static final String PLUGIN_ENABLED_KEY = "enabled";

    /**
     * Configuration key to control plugin security.
     * 
     * <p>When present in the plugin configuration, this overrides the
     * {@code secure} setting from the {@link RegisterPlugin} annotation.
     * Secure plugins are only accessible over HTTPS connections.</p>
     * 
     * <p>Example configuration:</p>
     * <pre>{@code
     * plugins-args:
     *   my-plugin:
     *     secured: true  # Force HTTPS-only access
     * }</pre>
     */
    public static final String PLUGIN_SECURE_KEY = "secured";

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
     * Gets the plugin name.
     * 
     * <p>This is the unique identifier for the plugin, as specified in the
     * {@link RegisterPlugin#name()} annotation attribute.</p>
     * 
     * @return the plugin name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the plugin description.
     * 
     * <p>This is the human-readable description of the plugin's purpose,
     * as specified in the {@link RegisterPlugin#description()} annotation attribute.</p>
     * 
     * @return the plugin description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the fully qualified class name of the plugin.
     * 
     * <p>This is useful for logging, debugging, and reflection operations.</p>
     * 
     * @return the plugin's class name
     */
    public String getClassName() {
        return className;
    }

    /**
     * Checks if the plugin requires secure (HTTPS) connections.
     * 
     * <p>This method considers both the default security setting from the
     * {@link RegisterPlugin#secure()} annotation and any configuration override
     * via the {@link #PLUGIN_SECURE_KEY} configuration key.</p>
     * 
     * @return true if the plugin should only be accessible over HTTPS
     */
    public boolean isSecure() {
        return isSecure(secure, getConfArgs());
    }

    /**
     * Checks if the plugin is enabled.
     * 
     * <p>This method considers both the default enablement setting from the
     * {@link RegisterPlugin#enabledByDefault()} annotation and any configuration
     * override via the {@link #PLUGIN_ENABLED_KEY} configuration key.</p>
     * 
     * @return true if the plugin is enabled and should be active
     */
    public boolean isEnabled() {
        return isEnabled(enabledByDefault, getConfArgs());
    }

    /**
     * Determines if a plugin should be enabled based on default and configuration settings.
     * 
     * <p>This static utility method checks for the {@link #PLUGIN_ENABLED_KEY} in the
     * configuration arguments. If present and valid, it overrides the default setting.</p>
     * 
     * @param enabledByDefault the default enablement status from the plugin annotation
     * @param confArgs the configuration arguments map, may be null
     * @return true if the plugin should be enabled
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
     * Determines if a plugin should require secure connections based on default and configuration settings.
     * 
     * <p>This static utility method checks for the {@link #PLUGIN_SECURE_KEY} in the
     * configuration arguments. If present and valid, it overrides the default setting.</p>
     * 
     * @param secure the default security requirement from the plugin annotation
     * @param confArgs the configuration arguments map, may be null
     * @return true if the plugin should require HTTPS connections
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
     * Gets the configuration arguments for this plugin.
     * 
     * <p>These are the plugin-specific configuration values from the RESTHeart
     * configuration file under the {@code plugins-args} section. The map may
     * contain any custom configuration needed by the plugin, in addition to
     * the standard {@link #PLUGIN_ENABLED_KEY} and {@link #PLUGIN_SECURE_KEY} keys.</p>
     * 
     * <p>Example configuration that would be returned:</p>
     * <pre>{@code
     * {
     *   "enabled": true,
     *   "secured": false,
     *   "database": "mydb",
     *   "collection": "users",
     *   "cache-ttl": 3600
     * }
     * }</pre>
     * 
     * @return the configuration arguments map, may be null if no configuration provided
     */
    public Map<String, Object> getConfArgs() {
        return confArgs;
    }

    /**
     * Gets the actual plugin instance.
     * 
     * <p>This returns the instantiated and initialized plugin object that can be
     * used to handle requests or perform plugin-specific operations. The instance
     * is created once during plugin loading and reused for all requests (singleton pattern).</p>
     * 
     * @return the plugin instance
     */
    public T getInstance() {
        return instance;
    }
}
