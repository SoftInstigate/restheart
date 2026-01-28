/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.restheart.configuration.Configuration;
import org.restheart.configuration.ConfigurationException;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Abstract helper class that simplifies loading plugin configuration from external files.
 * <p>
 * FileConfigurablePlugin extends the basic {@link ConfigurablePlugin} functionality by
 * providing automatic loading and parsing of plugin configuration from external YAML
 * files. This is particularly useful for plugins that require complex configuration
 * that is better managed in separate files rather than inline in the main RESTHeart
 * configuration.
 * </p>
 * <p>
 * This class handles:
 * <ul>
 *   <li><strong>File Path Resolution</strong> - Resolves configuration file paths relative to RESTHeart config</li>
 *   <li><strong>YAML Parsing</strong> - Automatically parses YAML configuration files</li>
 *   <li><strong>Configuration Injection</strong> - Provides parsed configuration to subclass implementations</li>
 *   <li><strong>Error Handling</strong> - Provides meaningful error messages for configuration issues</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Usage Pattern:</strong><br>
 * Plugins extending this class must specify a 'conf-file' argument in their plugin configuration
 * that points to a YAML file containing the detailed plugin configuration:
 * <pre>
 * plugins-args:
 *   myPlugin:
 *     conf-file: "config/my-plugin-config.yml"
 * </pre>
 * </p>
 * <p>
 * The external configuration file should contain a section named after the plugin type:
 * <pre>
 * # my-plugin-config.yml
 * myPluginType:
 *   - name: "config1"
 *     setting1: "value1"
 *     setting2: 42
 *   - name: "config2"
 *     setting1: "value2"
 *     setting2: 24
 * </pre>
 * </p>
 * <p>
 * Example implementation:
 * <pre>
 * &#64;RegisterPlugin(name = "myPlugin", description = "Plugin with file configuration")
 * public class MyPlugin extends FileConfigurablePlugin implements Service&lt;JsonRequest, JsonResponse&gt; {
 *     private List&lt;MyConfig&gt; configurations = new ArrayList&lt;&gt;();
 *     
 *     &#64;Override
 *     public Consumer&lt;? super Map&lt;String, Object&gt;&gt; consumeConfiguration() throws ConfigurationException {
 *         return config -&gt; {
 *             MyConfig myConfig = new MyConfig(
 *                 (String) config.get("name"),
 *                 (String) config.get("setting1"),
 *                 (Integer) config.get("setting2")
 *             );
 *             configurations.add(myConfig);
 *         };
 *     }
 *     
 *     &#64;Inject("config")
 *     public void initConfig(Map&lt;String, Object&gt; args) throws ConfigurationException {
 *         init(args, "myPluginType");
 *     }
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>File Path Resolution:</strong><br>
 * Configuration file paths can be specified as:
 * <ul>
 *   <li><strong>Absolute paths</strong> - Starting with "/" (Unix) or drive letter (Windows)</li>
 *   <li><strong>Relative to RESTHeart config</strong> - When RESTHeart config file is specified</li>
 *   <li><strong>Relative to plugins directory</strong> - When using default configuration</li>
 * </ul>
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 * @see ConfigurablePlugin
 * @see org.restheart.configuration.Configuration
 */
abstract public class FileConfigurablePlugin implements ConfigurablePlugin {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FileConfigurablePlugin.class);

    /**
     * Returns a Consumer function that processes individual configuration entries from the file.
     * <p>
     * This abstract method must be implemented by subclasses to define how each configuration
     * entry from the external YAML file should be processed. The method returns a Consumer
     * that will be called for each configuration item found in the specified section of
     * the configuration file.
     * </p>
     * <p>
     * The Consumer receives a Map representing a single configuration entry parsed from
     * the YAML file. For example, if the YAML contains:
     * <pre>
     * myPluginType:
     *   - name: "config1"
     *     host: "localhost"
     *     port: 8080
     *   - name: "config2"
     *     host: "remote"
     *     port: 9090
     * </pre>
     * The Consumer will be called twice, once for each configuration entry.
     * </p>
     * <p>
     * Example implementation:
     * <pre>
     * &#64;Override
     * public Consumer&lt;? super Map&lt;String, Object&gt;&gt; consumeConfiguration() throws ConfigurationException {
     *     return config -&gt; {
     *         String name = (String) config.get("name");
     *         String host = (String) config.get("host");
     *         Integer port = (Integer) config.get("port");
     *         
     *         // Validate configuration
     *         if (name == null || host == null || port == null) {
     *             throw new IllegalArgumentException("Missing required configuration");
     *         }
     *         
     *         // Store or use configuration
     *         addServerConfig(new ServerConfig(name, host, port));
     *     };
     * }
     * </pre>
     * </p>
     * <p>
     * <strong>Error Handling:</strong><br>
     * The Consumer implementation should validate configuration values and throw
     * appropriate exceptions for invalid or missing required parameters. These
     * exceptions will be caught and handled by the file loading mechanism.
     * </p>
     *
     * @return a Consumer function that processes each configuration entry from the file
     * @throws ConfigurationException if there are issues setting up the configuration consumer
     */
    public abstract Consumer<? super Map<String, Object>> consumeConfiguration() throws ConfigurationException;

    /**
     * Initializes the plugin by loading and parsing configuration from the specified file.
     * <p>
     * This method loads the configuration file specified in the 'conf-file' argument,
     * parses it as YAML, extracts the specified configuration section, and processes
     * each configuration entry using the Consumer returned by {@link #consumeConfiguration()}.
     * </p>
     * <p>
     * The method performs the following steps:
     * <ol>
     *   <li>Extracts the 'conf-file' path from the plugin arguments</li>
     *   <li>Resolves the file path (absolute or relative)</li>
     *   <li>Loads and parses the YAML file</li>
     *   <li>Extracts the configuration section specified by the 'type' parameter</li>
     *   <li>Applies the configuration consumer to each entry in the section</li>
     * </ol>
     * </p>
     * <p>
     * Example usage in plugin initialization:
     * <pre>
     * &#64;Inject("config")
     * public void initConfig(Map&lt;String, Object&gt; args) throws ConfigurationException {
     *     try {
     *         init(args, "databaseConnections");
     *     } catch (FileNotFoundException e) {
     *         throw new ConfigurationException("Database configuration file not found", e);
     *     }
     * }
     * </pre>
     * </p>
     * <p>
     * <strong>File Path Resolution:</strong><br>
     * The configuration file path is resolved in the following order:
     * <ul>
     *   <li>If absolute path (starts with "/"), use as-is</li>
     *   <li>If RESTHeart config file is specified, resolve relative to its directory</li>
     *   <li>Otherwise, resolve relative to the plugins directory</li>
     * </ul>
     * </p>
     *
     * @param arguments the plugin arguments map containing the 'conf-file' parameter
     * @param type the configuration section name to extract from the YAML file
     * @throws FileNotFoundException if the specified configuration file cannot be found
     * @throws ConfigurationException if the configuration file format is invalid or required sections are missing
     */
    @SuppressWarnings("unchecked")
    public void init(Map<String, Object> arguments, String type) throws FileNotFoundException, ConfigurationException {
        InputStream is = null;
        try {
            final String confFilePath = extractConfigFilePath(arguments);
            is = new FileInputStream(new File(java.net.URLDecoder.decode(confFilePath, "utf-8")));

            final Map<String, Object> conf = (Map<String, Object>) new Yaml(new SafeConstructor(new LoaderOptions())).load(is);

            List<Map<String, Object>> confItems = extractConfArgs(conf, type);
            confItems.stream().forEach(consumeConfiguration());
        } catch (FileNotFoundException ex) {
            LOGGER.error("*** cannot find the file {} " + "specified in the configuration.", extractConfigFilePath(arguments));

            LOGGER.error("*** note that the path must be either "
              + "absolute "
              + "or relative to the restheart configuration file (if specified) "
              + "or relative to the plugins directory (if using the default configuration)");
            throw ex;
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(uee);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException ex) {
                LOGGER.warn("Can't close the InputStream", ex);
            }
        }
    }

    /**
     * Extracts and resolves the absolute path of the 'conf-file' argument.
     * <p>
     * This method takes the 'conf-file' parameter from the plugin arguments and resolves
     * it to an absolute file system path. The path can be specified as either an absolute
     * path or a relative path that will be resolved relative to the RESTHeart configuration
     * file directory.
     * </p>
     * <p>
     * Path resolution logic:
     * <ul>
     *   <li><strong>Absolute paths</strong> - Paths starting with "/" are used as-is</li>
     *   <li><strong>Relative paths with RESTHeart config</strong> - Resolved relative to the directory containing the RESTHeart configuration file</li>
     *   <li><strong>Relative paths without RESTHeart config</strong> - Resolved relative to the JAR file location or classes directory</li>
     * </ul>
     * </p>
     * <p>
     * Example path resolutions:
     * <pre>
     * // Absolute path
     * conf-file: "/etc/restheart/my-plugin.yml" → "/etc/restheart/my-plugin.yml"
     * 
     * // Relative to RESTHeart config (if config is in /opt/restheart/restheart.yml)
     * conf-file: "config/my-plugin.yml" → "/opt/restheart/config/my-plugin.yml"
     * 
     * // Relative to JAR location (if no RESTHeart config specified)
     * conf-file: "my-plugin.yml" → "/path/to/jar/my-plugin.yml"
     * </pre>
     * </p>
     *
     * @param arguments the plugin arguments map that should contain the 'conf-file' parameter
     * @return the absolute path to the configuration file
     * @throws IllegalArgumentException if the 'conf-file' argument is missing or invalid
     */
    String extractConfigFilePath(Map<String, Object> arguments) throws IllegalArgumentException {
        if (arguments == null) {
            throw new IllegalArgumentException("missing required arguments conf-file");
        }

        Object _confFilePath = arguments.getOrDefault("conf-file", null);

        if (_confFilePath == null || !(_confFilePath instanceof String)) {
            throw new IllegalArgumentException("missing required arguments conf-file");
        }

        Path restheartConfFilePath = Configuration.getPath();

        String confFilePath = (String) _confFilePath;
        if (!confFilePath.startsWith("/")) {
            if (restheartConfFilePath != null) {
                confFilePath = restheartConfFilePath.toAbsolutePath().getParent().resolve(confFilePath).toString();
            } else {
                // this is to allow specifying the configuration file path
                // relative to the jar (also working when running from classes)
                URL location = this.getClass().getProtectionDomain().getCodeSource().getLocation();

                File locationFile = new File(location.getPath());
                confFilePath = locationFile.getParent() + File.separator + confFilePath;
            }
        }
        return confFilePath;
    }

    /**
     * Extracts the configuration section specified by type from the parsed YAML configuration.
     * <p>
     * This method locates and returns the configuration section with the given name from
     * the parsed YAML file. The section is expected to contain a list of configuration
     * entries, where each entry is a Map of configuration parameters.
     * </p>
     * <p>
     * Expected YAML structure:
     * <pre>
     * sectionName:
     *   - key1: value1
     *     key2: value2
     *   - key1: value3
     *     key2: value4
     * </pre>
     * </p>
     * <p>
     * The method validates that:
     * <ul>
     *   <li>The specified section exists in the configuration</li>
     *   <li>The section contains a List (array) of configuration entries</li>
     *   <li>Each entry in the list is a Map of configuration parameters</li>
     * </ul>
     * </p>
     *
     * @param conf the complete parsed YAML configuration as a Map
     * @param type the name of the configuration section to extract
     * @return a List of Maps, where each Map represents a configuration entry
     * @throws IllegalArgumentException if the configuration format is invalid or the specified section is missing
     */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> extractConfArgs(final Map<String, Object> conf, String type) throws IllegalArgumentException {
        Object args = conf.get(type);

        if (args == null || !(args instanceof List)) {
            throw new IllegalArgumentException("wrong configuration file format. missing mandatory '" + type + "' section.");
        }

        List<Map<String, Object>> users = (List<Map<String, Object>>) args;
        return users;
    }
}
