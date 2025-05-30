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
package org.restheart.configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.jxpath.JXPathContext;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Option;
import io.undertow.UndertowOptions;
import io.undertow.Undertow.Builder;

/**
 * Utility class for configuration handling and parsing.
 * 
 * <p>This class provides helper methods for extracting values from configuration maps,
 * handling environment variable overrides, parsing configuration files, and setting
 * Undertow server options. It supports both simple key-value lookups and XPath-style
 * queries for nested configuration values.</p>
 * 
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Safe value extraction with default fallbacks</li>
 *   <li>XPath queries for nested configuration values</li>
 *   <li>Environment variable override parsing (RH_* pattern)</li>
 *   <li>Type-safe configuration value casting</li>
 *   <li>Undertow server option configuration</li>
 * </ul>
 * 
 * <h2>Environment Variable Overrides</h2>
 * <p>Supports two patterns for environment variable overrides:</p>
 * <ul>
 *   <li>RH_SECTION_KEY format (e.g., RH_CORE_NAME)</li>
 *   <li>RHO format - semicolon-separated key=value pairs</li>
 * </ul>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 1.0
 */
public class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    /**
     * Gets a configuration value by key with a default fallback from a Configuration object.
     * 
     * <p>This is a convenience method that extracts the configuration map from the
     * Configuration object and delegates to {@link #getOrDefault(Map, String, Object, boolean)}.</p>
     *
     * @param <V> the type of the return value
     * @param conf the Configuration object to extract the value from
     * @param key the configuration key to look up
     * @param defaultValue the default value to return if the key is not found or invalid
     * @param silent if true, suppresses warning messages for missing values
     * @return the configuration value cast to type V, or defaultValue if not found
     */
    public static <V extends Object> V getOrDefault(Configuration conf, final String key, final V defaultValue, boolean silent) {
        return getOrDefault(conf.toMap(), key, defaultValue, silent);
    }

    /**
     * Gets a configuration value by key with a default fallback from a Map.
     * 
     * <p>This method safely retrieves values from the configuration map, handling
     * missing keys and type casting errors. If the key is not found or the value
     * cannot be cast to the expected type, the default value is returned.</p>
     * 
     * <h3>Logging Behavior</h3>
     * <ul>
     *   <li>When silent=false and key is missing: logs a warning with the default value</li>
     *   <li>When silent=false and key exists: logs the value at TRACE level</li>
     *   <li>When silent=false and casting fails: logs a warning</li>
     * </ul>
     *
     * @param <V> the type of the return value
     * @param conf the configuration map to extract the value from
     * @param key the configuration key to look up
     * @param defaultValue the default value to return if the key is not found or invalid
     * @param silent if true, suppresses warning messages for missing values
     * @return the configuration value cast to type V, or defaultValue if not found or invalid
     * @throws ClassCastException if the value exists but cannot be cast to type V
     */
    @SuppressWarnings("unchecked")
    public static <V extends Object> V getOrDefault(final Map<String, Object> conf, final String key, final V defaultValue, boolean silent) {
        if (conf == null || conf.get(key) == null) {
            // if default value is null there is no default value actually
            if (defaultValue != null && !silent) {
                LOGGER.warn("Parameter \"{}\" not specified in the configuration file, using its default value \"{}\"", key, defaultValue);
            }
            return defaultValue;
        }

        try {
            if (!silent) {
                LOGGER.trace("configuration parameter \"{}\" set to \"{}\"", key, conf.get(key));
            }
            return (V) conf.get(key);
        } catch (Throwable t) {
            if (!silent) {
                LOGGER.warn("Wrong configuration parameter \"{}\": \"{}\", using its default value \"{}\"", key, conf.get(key), defaultValue);
            }
            return defaultValue;
        }
    }

    /**
     * Finds a configuration value using an XPath expression.
     * 
     * <p>This method uses JXPath to navigate nested configuration structures.
     * Returns null if the path is not found.</p>
     * 
     * @param <V> the type of the return value
     * @param conf the configuration map to search
     * @param xpath the XPath expression (e.g., "/core/name" or "//port")
     * @param silent if true, suppresses warning messages
     * @return the value at the specified path, or null if not found
     * @see #findOrDefault(Map, String, Object, boolean)
     */
    public static <V extends Object> V find(final Map<String, Object> conf, final String xpath, boolean silent) {
        return findOrDefault(conf, xpath, null, silent);
    }

    /**
     * Finds a configuration value using an XPath expression with a default fallback.
     * 
     * <p>This method uses JXPath to navigate nested configuration structures.
     * XPath expressions allow powerful queries into the configuration tree.</p>
     * 
     * <h3>Example XPath Expressions</h3>
     * <ul>
     *   <li>"/core/name" - direct path to core.name</li>
     *   <li>"//port" - find any port value anywhere in the tree</li>
     *   <li>"/proxies[1]/location" - first proxy's location</li>
     * </ul>
     * 
     * @param <V> the type of the return value
     * @param conf the configuration map to search
     * @param xpath the XPath expression to evaluate
     * @param defaultValue the default value to return if path is not found
     * @param silent if true, suppresses warning messages
     * @return the value at the specified path cast to type V, or defaultValue if not found
     */
    @SuppressWarnings("unchecked")
    public static <V extends Object> V findOrDefault(final Map<String, Object> conf, final String xpath, final V defaultValue, boolean silent) {
        var ctx = JXPathContext.newContext(conf);
        ctx.setLenient(true);

        try {
            V value = (V) ctx.getValue(xpath);

            if (value == null) {
                // if default value is null there is no default value actually
                if (defaultValue != null && !silent) {
                    LOGGER.warn("Parameter \"{}\" not specified in the configuration file, using its default value \"{}\"", xpath, defaultValue);
                }

                return defaultValue;
            } else {
                if (!silent) {
                    LOGGER.trace("configuration parameter \"{}\" set to \"{}\"", xpath, value);
                }

                return value;
            }
        } catch (Throwable t) {
            if (!silent) {
                LOGGER.warn("Wrong configuration parameter \"{}\": \"{}\", using its default value \"{}\"", xpath, defaultValue);
            }
            return defaultValue;
        }
    }

    /**
     * Extracts a configuration value as a list of maps.
     * 
     * <p>This method is used for configuration sections that contain arrays of objects,
     * such as proxies, static resources, or any other repeated configuration blocks.</p>
     * 
     * <h3>Example Configuration</h3>
     * <pre>{@code
     * proxies:
     *   - name: "api"
     *     location: "/api"
     *   - name: "web"
     *     location: "/web"
     * }</pre>
     * 
     * @param conf the configuration map to extract from
     * @param key the key of the list configuration
     * @param defaultValue the default value if not found or invalid
     * @param silent if true, suppresses warning messages
     * @return list of configuration maps, or defaultValue if not found or invalid
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> asListOfMaps(final Map<String, Object> conf, final String key, final List<Map<String, Object>> defaultValue, boolean silent) {
        if (conf == null) {
            if (!silent) {
                LOGGER.trace("parameter {} not specified in the configuration file, using its default value {}", key, defaultValue);
            }

            return defaultValue;
        }

        var o = conf.get(key);

        if (o == null) {
            if (!silent) {
                LOGGER.trace("configuration parameter {} not specified in the configuration file, using its default value {}", key, defaultValue);
            }
            return defaultValue;
        } else if (o instanceof List) {
            try {
                return (List<Map<String, Object>>) o;
            } catch (Throwable t) {
                LOGGER.warn("wrong configuration parameter {}", key);
                return defaultValue;
            }
        } else {
            if (!silent) {
                LOGGER.warn("wrong configuration parameter {}, expecting an array of objects", key, defaultValue);
            }
            return defaultValue;
        }
    }

    /**
     * Extracts a configuration value as a map of maps.
     * 
     * <p>This method is used for configuration sections that contain nested objects
     * indexed by keys, useful for named configuration blocks.</p>
     * 
     * <h3>Example Configuration</h3>
     * <pre>{@code
     * services:
     *   auth:
     *     enabled: true
     *     provider: "ldap"
     *   cache:
     *     enabled: false
     *     size: 1000
     * }</pre>
     * 
     * @param conf the configuration map to extract from
     * @param key the key of the map configuration
     * @param defaultValue the default value if not found or invalid
     * @param silent if true, suppresses warning messages
     * @return map of configuration maps, or defaultValue if not found or invalid
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, Object>> asMapOfMaps(final Map<String, Object> conf, final String key, final Map<String, Map<String, Object>> defaultValue, boolean silent) {
        if (conf == null) {
            if (!silent) {
                LOGGER.trace("parameter {} not specified in the configuration file, using its default value {}", key, defaultValue);
            }

            return defaultValue;
        }

        var o = conf.get(key);

        if (o == null) {
            if (!silent) {
                LOGGER.trace("configuration parameter {} not specified in the configuration file, using its default value {}", key, defaultValue);
            }
            return defaultValue;
        } else if (o instanceof Map) {
            try {
                return (Map<String, Map<String, Object>>) o;
            } catch (Throwable t) {
                if (!silent) {
                    LOGGER.warn("wrong configuration parameter {}, expecting a map of maps, using its default value {}", key, defaultValue);
                }
                return defaultValue;
            }
        } else {
            if (!silent) {
                LOGGER.warn("wrong configuration parameter {}, expecting a map of maps, using its default value {}", key, defaultValue);
            }
            return defaultValue;
        }
    }

    /**
     * Extracts a configuration value as a map.
     * 
     * <p>This method is used for configuration sections that contain nested objects,
     * such as core settings, listeners, or any other structured configuration block.</p>
     * 
     * @param conf the configuration map to extract from
     * @param key the key of the nested configuration object
     * @param defaultValue the default value if not found or invalid
     * @param silent if true, suppresses warning messages
     * @return configuration map, or defaultValue if not found or invalid
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(final Map<String, Object> conf, final String key, Map<String, Object> defaultValue, boolean silent) {
        if (conf == null) {
            if (!silent) {
                LOGGER.trace("parameter {} not specified in the configuration file, using its default value {}", key, defaultValue);
            }

            return null;
        }

        var o = conf.get(key);

        if (o == null) {
            if (!silent) {
                LOGGER.trace("configuration parameter {} not specified in the configuration file, using its default value {}", key, defaultValue);
            }
            return defaultValue;
        } else if (o instanceof Map<?, ?> m) {
            try {
                return (Map<String, Object>) o;
            } catch (Throwable t) {
                if (!silent) {
                    LOGGER.warn("wrong configuration parameter {}, expecting a map, using its default value {}", key, defaultValue);
                }

                return null;
            }
        } else {
            if (!silent) {
                LOGGER.warn("wrong configuration parameter {}, expecting a map, using its default value {}", key, defaultValue);
            }
            return null;
        }
    }

    /**
     * Extracts a configuration value as an array of integers.
     * 
     * <p>This method converts a list of integers from the configuration into a primitive
     * int array. Non-integer values in the list are filtered out.</p>
     * 
     * <h3>Example Configuration</h3>
     * <pre>{@code
     * ports: [8080, 8081, 8082]
     * }</pre>
     * 
     * @param conf the configuration map to extract from
     * @param key the key of the integer list
     * @param defaultValue the default value if not found or invalid
     * @param silent if true, suppresses warning messages
     * @return array of integers, or defaultValue if not found or invalid
     */
    public static int[] asArrayOfInts(final Map<String, Object> conf, final String key, final int[] defaultValue, boolean silent) {
        if (conf == null) {
            if (!silent) {
                LOGGER.trace("parameter {} not specified in the configuration file, using its default value {}", key, null);
            }

            return null;
        }

        var o = conf.get(key);

        if (o instanceof List<?> l) {
            try {
                return l.stream().filter(i -> i instanceof Integer).mapToInt(i -> (Integer)i).toArray();
            } catch (Throwable t) {
                if (!silent) {
                    LOGGER.warn("wrong configuration parameter {}, expecting a list of ints", key);
                }

                return null;
            }
        } else {
            if (!silent) {
                LOGGER.warn("wrong configuration parameter {}, expecting a list of ints", key);
            }
            return null;
        }
    }

    /**
     * Extracts a configuration value as a list of strings.
     * 
     * <p>This method is commonly used for configuration values that accept multiple
     * string values, such as allowed origins, plugin packages, or header names.</p>
     * 
     * <h3>Example Configuration</h3>
     * <pre>{@code
     * allowed-origins:
     *   - "http://localhost:3000"
     *   - "https://example.com"
     *   - "https://app.example.com"
     * }</pre>
     * 
     * @param conf the configuration map to extract from
     * @param key the key of the string list
     * @param defaultValue the default value if not found or invalid
     * @param silent if true, suppresses warning messages
     * @return list of strings, or defaultValue if not found, invalid, or empty
     */
    @SuppressWarnings("unchecked")
    public static List<String> asListOfStrings(final Map<String, Object> conf, final String key, final List<String> defaultValue, boolean silent) {
        if (conf == null || conf.get(key) == null) {
            // if default value is null there is no default value actually
            if (defaultValue != null && !silent) {
                LOGGER.trace("parameter {} not specified in the configuration file, using its default value {}", key, defaultValue);
            }
            return defaultValue;
        } else if (conf.get(key) instanceof List) {
            if (!silent) {
                LOGGER.debug("parameter {} set to {}", key, conf.get(key));
            }

            try {
                var ret = ((List<String>) conf.get(key));

                if (ret.isEmpty()) {
                    if (!silent) {
                        LOGGER.warn("wrong value for parameter {}: {}, using its default value {}", key, conf.get(key), defaultValue);
                    }
                    return defaultValue;
                } else {
                    return ret;
                }
            } catch (Throwable t) {
                if (!silent) {
                    LOGGER.warn("wrong value for parameter {}: {}, using its default value {}", key, conf.get(key), defaultValue);
                }
                return defaultValue;
            }
        } else {
            if (!silent) {
                LOGGER.warn("wrong value for parameter {}: {}, using its default value {}", key, conf.get(key), defaultValue);
            }
            return defaultValue;
        }
    }

    /**
     * Extracts a configuration value as a Boolean.
     * 
     * <p>Convenience method that delegates to {@link #getOrDefault(Map, String, Object, boolean)}
     * with type safety for Boolean values.</p>
     * 
     * @param conf the configuration map to extract from
     * @param key the key of the boolean value
     * @param defaultValue the default value if not found or invalid
     * @param silent if true, suppresses warning messages
     * @return Boolean value, or defaultValue if not found or invalid
     */
    public static Boolean asBoolean(final Map<String, Object> conf, final String key, final Boolean defaultValue, boolean silent) {
        return getOrDefault(conf, key, defaultValue, silent);
    }

    /**
     * Extracts a configuration value as a String.
     * 
     * <p>Convenience method that delegates to {@link #getOrDefault(Map, String, Object, boolean)}
     * with type safety for String values.</p>
     * 
     * @param conf the configuration map to extract from
     * @param key the key of the string value
     * @param defaultValue the default value if not found or invalid
     * @param silent if true, suppresses warning messages
     * @return String value, or defaultValue if not found or invalid
     */
    public static String asString(final Map<String, Object> conf, final String key, final String defaultValue, boolean silent) {
        return getOrDefault(conf, key, defaultValue, silent);
    }

    /**
     * Extracts a configuration value as an Integer.
     * 
     * <p>Convenience method that delegates to {@link #getOrDefault(Map, String, Object, boolean)}
     * with type safety for Integer values.</p>
     * 
     * @param conf the configuration map to extract from
     * @param key the key of the integer value
     * @param defaultValue the default value if not found or invalid
     * @param silent if true, suppresses warning messages
     * @return Integer value, or defaultValue if not found or invalid
     */
    public static Integer asInteger(final Map<String, Object> conf, final String key, final Integer defaultValue, boolean silent) {
        return getOrDefault(conf, key, defaultValue, silent);
    }

    /**
     * Extracts a configuration value as a Long.
     * 
     * <p>This method handles numeric values and converts them to Long. It's particularly
     * useful for configuration values that represent large numbers such as timeouts,
     * file sizes, or timestamps.</p>
     * 
     * @param conf the configuration map to extract from
     * @param key the key of the long value
     * @param defaultValue the default value if not found or invalid
     * @param silent if true, suppresses warning messages
     * @return Long value, or defaultValue if not found or invalid
     */
    public static Long asLong(final Map<String, Object> conf, final String key, final Long defaultValue, boolean silent) {
        if (conf == null || conf.get(key) == null) {
            // if default value is null there is no default value actually
            if (defaultValue != null && !silent) {
                LOGGER.debug("parameter {} not specified in the configuration file, using its default value {}", key, defaultValue);
            }
            return defaultValue;
        } else if (conf.get(key) instanceof Number) {
            if (!silent) {
                LOGGER.debug("parameter {} set to {}", key, conf.get(key));
            }
            try {
                return Long.parseLong(conf.get(key).toString());
            } catch (NumberFormatException nfe) {
                if (!silent) {
                    LOGGER.warn("wrong value for parameter {}: {}, using its default value {}", key, conf.get(key), defaultValue);
                }
                return defaultValue;
            }
        } else {
            if (!silent) {
                LOGGER.warn("wrong value for parameter {}: {}, using its default value {}", key, conf.get(key), defaultValue);
            }
            return defaultValue;
        }
    }

    /**
     * Gets a configuration value from environment variables or system properties.
     * 
     * <p>This method looks for environment variables following the pattern RH_PARAMETER_NAME
     * where PARAMETER_NAME is the configuration parameter converted to uppercase with
     * hyphens replaced by underscores.</p>
     * 
     * <h3>Examples</h3>
     * <ul>
     *   <li>confParameter "core-name" → looks for RH_CORE_NAME</li>
     *   <li>confParameter "http-listener-port" → looks for RH_HTTP_LISTENER_PORT</li>
     * </ul>
     * 
     * @param confParameter the configuration parameter name (e.g., "core-name")
     * @return the environment or system property value, or null if not found
     */
    public static String valueFromEnv(final String confParameter) {
        return valueFromEnv(confParameter, false);
    }

    /**
     * Gets a configuration value from environment variables or system properties.
     * 
     * <p>This method looks for environment variables following the pattern RH_PARAMETER_NAME
     * where PARAMETER_NAME is the configuration parameter converted to uppercase with
     * hyphens replaced by underscores. System properties take precedence over environment
     * variables.</p>
     * 
     * @param confParameter the configuration parameter name (e.g., "core-name")
     * @param silent if true, suppresses log messages when a value is found
     * @return the environment or system property value, or null if not found
     */
    public static String valueFromEnv(final String confParameter, boolean silent) {
        var value = _valueFromEnv("RH", confParameter, silent);

        if (value != null) {
            return value;
        }

        return null;
    }

    /**
     * Gets a configuration value from environment with a specific prefix.
     * 
     * <p>Converts the configuration parameter to an environment variable name by
     * uppercasing and replacing hyphens with underscores, then prepending the prefix.</p>
     * 
     * @param prefix the prefix to prepend (e.g., "RH")
     * @param confParameter the configuration parameter name
     * @param silent if true, suppresses log messages
     * @return the environment or system property value, or null if not found
     */
    private static String _valueFromEnv(final String prefix, final String confParameter, boolean silent) {
        var key = prefix != null ? prefix + "_" + confParameter.toUpperCase().replaceAll("-", "_")
                : confParameter.toUpperCase().replaceAll("-", "_");

        return __valueFromEnv(confParameter, key, silent);
    }

    /**
     * Gets a value from system properties or environment variables.
     * 
     * <p>System properties take precedence over environment variables. When a value
     * is found and silent is false, a warning is logged indicating the override.</p>
     * 
     * @param confParameter the original configuration parameter name (for logging)
     * @param key the environment variable or system property key to look up
     * @param silent if true, suppresses log messages
     * @return the value from system property or environment variable, or null if not found
     */
    private static String __valueFromEnv(final String confParameter, final String key, boolean silent) {
        var envValue = System.getProperty(key);

        if (envValue == null) {
            envValue = System.getenv(key);
        }

        if (!silent && envValue != null) {
            LOGGER.warn(">>> Found environment variable '{}': overriding parameter '{}' with value '{}'", key, confParameter, envValue);
        }

        return envValue;
    }

    /**
     * Set of Undertow server options that can be configured.
     * 
     * <p>These options control various aspects of the Undertow server behavior
     * including HTTP/2 settings, buffer sizes, timeouts, and protocol handling.</p>
     */
    @SuppressWarnings("rawtypes")
    private static final Set<Option> UNDERTOW_OPTIONS;

    /**
     * Set of Undertow server options that accept Long values.
     * 
     * <p>These options typically represent sizes or counts that may exceed
     * Integer.MAX_VALUE, such as maximum entity sizes.</p>
     */
    private static final Set<Option<Long>> LONG_UNDERTOW_OPTIONS;

    static {
        UNDERTOW_OPTIONS = Sets.newHashSet();
        UNDERTOW_OPTIONS.add(UndertowOptions.ALLOW_EQUALS_IN_COOKIE_VALUE);
        UNDERTOW_OPTIONS.add(UndertowOptions.ALLOW_UNKNOWN_PROTOCOLS);
        UNDERTOW_OPTIONS.add(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL);
        UNDERTOW_OPTIONS.add(UndertowOptions.ALWAYS_SET_KEEP_ALIVE);
        UNDERTOW_OPTIONS.add(UndertowOptions.BUFFER_PIPELINED_DATA);
        UNDERTOW_OPTIONS.add(UndertowOptions.DECODE_URL);
        UNDERTOW_OPTIONS.add(UndertowOptions.ENABLE_HTTP2);
        UNDERTOW_OPTIONS.add(UndertowOptions.ENABLE_RFC6265_COOKIE_VALIDATION);
        UNDERTOW_OPTIONS.add(UndertowOptions.ENABLE_STATISTICS);
        UNDERTOW_OPTIONS.add(UndertowOptions.HTTP2_HUFFMAN_CACHE_SIZE);
        UNDERTOW_OPTIONS.add(UndertowOptions.HTTP2_SETTINGS_ENABLE_PUSH);
        UNDERTOW_OPTIONS.add(UndertowOptions.HTTP2_SETTINGS_HEADER_TABLE_SIZE);
        UNDERTOW_OPTIONS.add(UndertowOptions.HTTP2_SETTINGS_INITIAL_WINDOW_SIZE);
        UNDERTOW_OPTIONS.add(UndertowOptions.HTTP2_SETTINGS_MAX_CONCURRENT_STREAMS);
        UNDERTOW_OPTIONS.add(UndertowOptions.HTTP2_SETTINGS_MAX_FRAME_SIZE);
        UNDERTOW_OPTIONS.add(UndertowOptions.IDLE_TIMEOUT);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_AJP_PACKET_SIZE);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_BUFFERED_REQUEST_SIZE);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_CACHED_HEADER_SIZE);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_CONCURRENT_REQUESTS_PER_CONNECTION);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_COOKIES);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_HEADERS);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_HEADER_SIZE);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_PARAMETERS);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_QUEUED_READ_BUFFERS);
        UNDERTOW_OPTIONS.add(UndertowOptions.NO_REQUEST_TIMEOUT);
        UNDERTOW_OPTIONS.add(UndertowOptions.RECORD_REQUEST_START_TIME);
        UNDERTOW_OPTIONS.add(UndertowOptions.REQUEST_PARSE_TIMEOUT);
        UNDERTOW_OPTIONS.add(UndertowOptions.REQUIRE_HOST_HTTP11);
        UNDERTOW_OPTIONS.add(UndertowOptions.SHUTDOWN_TIMEOUT);
        UNDERTOW_OPTIONS.add(UndertowOptions.SSL_USER_CIPHER_SUITES_ORDER);
        UNDERTOW_OPTIONS.add(UndertowOptions.URL_CHARSET);

        LONG_UNDERTOW_OPTIONS = Sets.newHashSet();
        LONG_UNDERTOW_OPTIONS.add(UndertowOptions.MAX_ENTITY_SIZE);
        LONG_UNDERTOW_OPTIONS.add(UndertowOptions.MULTIPART_MAX_ENTITY_SIZE);
    }

    /**
     * Configures Undertow server options from the configuration.
     * 
     * <p>This method applies connection options specified in the configuration to the
     * Undertow server builder. It handles both standard options and long-valued options
     * separately. The Date header is explicitly disabled as it's managed by a custom
     * DateHeaderInjector for better virtual thread compatibility.</p>
     * 
     * <h3>Configuration Example</h3>
     * <pre>{@code
     * connection-options:
     *   ENABLE_HTTP2: true
     *   MAX_HEADER_SIZE: 8192
     *   MAX_ENTITY_SIZE: 10485760
     *   IDLE_TIMEOUT: 60000
     * }</pre>
     * 
     * @param builder the Undertow server builder to configure
     * @param configuration the configuration containing connection options
     */
    @SuppressWarnings("unchecked")
    public static void setConnectionOptions(Builder builder, Configuration configuration) {
        Map<String, Object> options = configuration.getConnectionOptions();

        UNDERTOW_OPTIONS.stream().forEach(option -> {
            if (options.containsKey(option.getName())) {
                Object value = options.get(option.getName());

                if (value != null) {
                    builder.setServerOption(option, value);
                    LOGGER.trace("Connection option {}={}", option.getName(), value);
                }
            }
        });

        LONG_UNDERTOW_OPTIONS.stream().forEach(option -> {
            if (options.containsKey(option.getName())) {
                Object value = options.get(option.getName());

                if (value != null) {
                    Long lvalue = 0l + (Integer) value;
                    builder.setServerOption(option, lvalue);
                    LOGGER.trace("Connection option {}={}", option.getName(), lvalue);
                }
            }
        });

        // In Undertow, the `Date` header is added via {@code ThreadLocal<SimpleDateFormat>}.
        // * However, this approach is not optimal for virtual threads
        // we disable it and add the header with DateHeaderInjector
        builder.setServerOption(UndertowOptions.ALWAYS_SET_DATE, false);
    }

    /**
     * Regular expression pattern for splitting RHO format strings.
     * 
     * <p>This pattern matches semicolons while ignoring those inside quoted strings.
     * It handles both single and double quotes with proper escape sequences.</p>
     * 
     * @see <a href="https://stackoverflow.com/a/23667311/4481670">Stack Overflow inspiration</a>
     */
    private static final Pattern SPLIT_REGEX = Pattern.compile(
            "\\\\\"|\"(?:\\\\\"|[^\"])*\"" +
            "|\\\\'|'(?:\\\\'|[^'])*'" +
            "|(;)");

    /**
     * Splits a RHO format string into individual override assignments.
     * 
     * <p>This method uses a regex pattern to correctly handle semicolons that appear
     * inside quoted strings, ensuring that quoted values containing semicolons are
     * not incorrectly split.</p>
     * 
     * @param rho the RHO format string to split
     * @return list of individual assignment strings
     */
    private static List<String> splitOverrides(String rho) {
        var m = SPLIT_REGEX.matcher(rho);

        var regions = new ArrayList<RhOverrideRegion>();

        var lastMatch = 0;
        while (m.find()) {
            // we ignore the strings delimited by " whose size is > 2 (at least two " chars)
            if (m.group().length() == 1) {
                regions.add(new RhOverrideRegion(lastMatch, m.start()));
                lastMatch = m.end();
            }
        }

        if (lastMatch < rho.length()) {
            regions.add(new RhOverrideRegion(lastMatch, rho.length()));
        }

        var assignments = new ArrayList<String>();

        regions.stream().forEach(region -> {
            var assignment = rho.substring(region.start(), region.end()).strip();
            if (!assignment.isBlank()) {
                assignments.add(assignment);
            }
        });

        return assignments;
    }

    public static List<RhOverride> overrides(String rho) {
        return overrides(rho, false, false);
    }

    /**
     * Parses configuration overrides from a RHO format string with environment variable support.
     * 
     * <p>RHO format is a semicolon-separated list of key=value pairs. Values can include
     * environment variable references using ${VAR_NAME} syntax when fromEnv is true.
     * Quoted values are preserved, and the parser handles nested quotes correctly.</p>
     * 
     * <h3>Features</h3>
     * <ul>
     *   <li>Semicolon-separated key=value pairs</li>
     *   <li>Support for quoted values with embedded semicolons</li>
     *   <li>Environment variable expansion with ${VAR_NAME}</li>
     *   <li>XPath-style keys for nested configuration</li>
     * </ul>
     * 
     * @param rho the RHO format string containing overrides
     * @param fromEnv if true, expands ${VAR_NAME} references to environment variables
     * @param silent if true, suppresses log messages
     * @return list of parsed override objects
     */
    public static List<RhOverride> overrides(String rho, boolean fromEnv, boolean silent) {
        var overrides = new ArrayList<RhOverride>();
        var assignments = splitOverrides(rho);

        assignments.stream().forEach(assignment -> {
            var operator = assignment.indexOf("->");

            if (operator == -1) {
                if (!silent) {
                    LOGGER.warn("invalid override (missing '->' operator): " + assignment);
                }

                if (!silent) {
                    LOGGER.warn("invalid override: {}", assignment);
                }
            } else {
                var path = assignment.substring(0, operator).strip();

                if (!path.startsWith("/")) {
                    if (!silent) {
                        LOGGER.warn("invalid override, path must be absolute, i.e. starting with /: " + assignment);
                    }
                    return;
                }

                var ctx = JXPathContext.newContext(Maps.newHashMap());
                ctx.setLenient(true);

                try {
                    ctx.getPointer(path);
                } catch(Exception e) {
                    if (!silent) {
                        LOGGER.warn("invalid override, invalid path: {}, {}", assignment, e.getMessage());
                    }
                    return;
                }

                var _value = assignment.substring(operator+2, assignment.length()).strip();
                var e = "{\"e\":".concat(_value).concat("}");

                try {
                    var value = Document.parse(e).get("e");

                    // turn Document into a Map<String, Object>
                    if (value instanceof Document dv) {
                        value = dv.entrySet().stream()
                            .collect(HashMap::new, (m, v) -> m.put(v.getKey(), v.getValue()), HashMap::putAll);
                            // the following throws an exception due to a known bug
                            // see https://stackoverflow.com/questions/24630963/nullpointerexception-in-collectors-tomap-with-null-entry-values
                            // .collect(Collectors.toMap(_e -> _e.getKey(), _e -> _e.getValue()));
                    }

                    overrides.add(new RhOverride(path, value, assignment));
                } catch (Exception ex) {
                    if (!silent) {
                        LOGGER.warn("invalid override, value is not valid json: {}", assignment);
                    }
                }
            }
        });

        return overrides;
    }

    /**
     * Represents a configuration override parsed from RHO format.
     * 
     * <p>This record encapsulates a single configuration override with its target path,
     * the value to set, and the original raw string representation. Used internally
     * by the configuration system to apply environment variable and file-based overrides.</p>
     * 
     * <h3>Example Overrides</h3>
     * <ul>
     *   <li>path="/core/name", value="my-instance", raw="/core/name=my-instance"</li>
     *   <li>path="/http-listener/port", value=8080, raw="/http-listener/port=8080"</li>
     * </ul>
     * 
     * @param path the XPath-style configuration path to override
     * @param value the parsed value to set at the path
     * @param raw the original raw string representation of the override
     */
    public static record RhOverride(String path, Object value, String raw) {
    }
}

/**
 * Represents a region in a RHO string for parsing.
 * 
 * <p>Used internally by the RHO parser to track regions between semicolons,
 * helping to correctly split the string while respecting quoted values.</p>
 * 
 * @param start the start index of the region (inclusive)
 * @param end the end index of the region (exclusive)
 */
record RhOverrideRegion(int start, int end) {
}
