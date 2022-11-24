/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2022 SoftInstigate
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
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    /**
     *
     * @param <V>          return value
     * @param conf
     * @param key
     * @param defaultValue
     * @param silent
     * @return
     */
    public static <V extends Object> V getOrDefault(Configuration conf, final String key, final V defaultValue, boolean silent) {
        return getOrDefault(conf.toMap(), key, defaultValue, silent);
    }

    /**
     *
     * @param <V>          return value
     * @param conf
     * @param key
     * @param defaultValue
     * @param silent
     * @return
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
                LOGGER.trace("configuration paramenter \"{}\" set to \"{}\"", key, conf.get(key));
            }
            return (V) conf.get(key);
        } catch (Throwable t) {
            if (!silent) {
                LOGGER.warn("Wrong configuration parameter \"{}\": \"{}\", using its default value \"{}\"", key, conf.get(key), defaultValue);
            }
            return defaultValue;
        }
    }

    public static <V extends Object> V find(final Map<String, Object> conf, final String xpath, boolean silent) {
        return findOrDefault(conf, xpath, null, silent);
    }

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
                    LOGGER.trace("configuration paramenter \"{}\" set to \"{}\"", xpath, value);
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
     *
     * @param conf
     * @param key
     * @param defaultValue
     * @return
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
     *
     * @param conf
     * @param key
     * @return
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
     *
     * @param conf
     * @param key
     * @return
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
                LOGGER.debug("paramenter {} set to {}", key, conf.get(key));
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

    public static Boolean asBoolean(final Map<String, Object> conf, final String key, final Boolean defaultValue, boolean silent) {
        return getOrDefault(conf, key, defaultValue, silent);
    }

    public static String asString(final Map<String, Object> conf, final String key, final String defaultValue, boolean silent) {
        return getOrDefault(conf, key, defaultValue, silent);
    }

    public static Integer asInteger(final Map<String, Object> conf, final String key, final Integer defaultValue, boolean silent) {
        return getOrDefault(conf, key, defaultValue, silent);
    }

    public static Long asLong(final Map<String, Object> conf, final String key, final Long defaultValue, boolean silent) {
        if (conf == null || conf.get(key) == null) {
            // if default value is null there is no default value actually
            if (defaultValue != null && !silent) {
                LOGGER.debug("parameter {} not specified in the configuration file, using its default value {}", key, defaultValue);
            }
            return defaultValue;
        } else if (conf.get(key) instanceof Number) {
            if (!silent) {
                LOGGER.debug("paramenter {} set to {}", key, conf.get(key));
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
     *
     * @param key
     * @return the environment or java property variable, if found
     */
    public static String valueFromEnv(final String confParameter) {
        return valueFromEnv(confParameter, false);
    }

    /**
     *
     * @param key
     * @return the environment or java property variable, if found
     */
    public static String valueFromEnv(final String confParameter, boolean silent) {
        var value = _valueFromEnv("RH", confParameter, silent);

        if (value != null) {
            return value;
        }

        return null;
    }

    private static String _valueFromEnv(final String prefix, final String confParameter, boolean silent) {
        var key = prefix != null ? prefix + "_" + confParameter.toUpperCase().replaceAll("-", "_")
                : confParameter.toUpperCase().replaceAll("-", "_");

        return __valueFromEnv(confParameter, key, silent);
    }

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

    @SuppressWarnings("rawtypes")
    private static final Set<Option> UNDERTOW_OPTIONS;

    private static final Set<Option<Long>> LONG_UNDERTOW_OPTIONS;

    static {
        UNDERTOW_OPTIONS = Sets.newHashSet();
        UNDERTOW_OPTIONS.add(UndertowOptions.ALLOW_ENCODED_SLASH);
        UNDERTOW_OPTIONS.add(UndertowOptions.ALLOW_EQUALS_IN_COOKIE_VALUE);
        UNDERTOW_OPTIONS.add(UndertowOptions.ALLOW_UNKNOWN_PROTOCOLS);
        UNDERTOW_OPTIONS.add(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL);
        UNDERTOW_OPTIONS.add(UndertowOptions.ALWAYS_SET_DATE);
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
    }

    // matches ; in a way that we can ignore matches that are inside quotes
    // inspired by https://stackoverflow.com/a/23667311/4481670
    private static Pattern SPLIT_REGEX = Pattern.compile(
            "\\\\\"|\"(?:\\\\\"|[^\"])*\"" +
            "|\\\\'|'(?:\\\\'|[^'])*'" +
            "|(;)");

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

    public static List<RhOverride> overrides(String rho, boolean lenient, boolean silent) {
        var overrides = new ArrayList<RhOverride>();
        var assignments = splitOverrides(rho);

        assignments.stream().forEach(assignment -> {
            var operator = assignment.indexOf("->");

            if (operator == -1) {
                if (!lenient) {
                    throw new IllegalArgumentException("invalid override: " + assignment);
                }

                if (!silent) {
                    LOGGER.warn("invalid override: {}", assignment);
                }
            } else {
                var path = assignment.substring(0, operator).strip();

                if (!path.startsWith("/") && !lenient) {
                    throw new IllegalArgumentException("invalid override, path must be absolute, i.e. starting with /: " + assignment);
                }

                var ctx = JXPathContext.newContext(Maps.newHashMap());
                ctx.setLenient(true);

                try {
                    ctx.getPointer(path);
                } catch(Exception e) {
                    if (!lenient) {
                        throw new IllegalArgumentException("invalid override, invalid path: " + assignment + ", " + e.getMessage());
                    }

                    if (!silent) {
                        LOGGER.warn("invalid override, invalid path: {}, {}", assignment, e.getMessage());
                    }
                }

                var _value = assignment.substring(operator+2, assignment.length()).strip();
                var e = "{\"e\":".concat(_value).concat("}");

                try {
                    var value = Document.parse(e).get("e");
                    overrides.add(new RhOverride(path, value, assignment));
                } catch (Exception ex) {
                    if (!lenient) {
                        throw new IllegalArgumentException("invalid override, value is not valid json: " + assignment);
                    }

                    if (!silent) {
                        LOGGER.warn("invalid override, value is not valid json: {}", assignment);
                    }
                }
            }
        });

        return overrides;
    }

    public static record RhOverride(String path, Object value, String raw) {
    }
}

record RhOverrideRegion(int start, int end) {
}
