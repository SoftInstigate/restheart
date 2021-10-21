/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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

package org.restheart;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationUtils {
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
                    LOGGER.warn("wrong configuration parameter {}, expecting a map of maps", key, defaultValue);
                }
                return defaultValue;
            }
        } else {
            if (!silent) {
                LOGGER.warn("wrong configuration parameter {}, expecting a map of maps", key, defaultValue);
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
    public static Map<String, Object> asMap(final Map<String, Object> conf, final String key, boolean silent) {
        if (conf == null) {
            if (!silent) {
                LOGGER.trace("parameter {} not specified in the configuration file, using its default value {}", key, null);
            }

            return null;
        }

        var o = conf.get(key);

        if (o instanceof Map<?, ?> m) {
            try {
                return (Map<String, Object>) o;
            } catch (Throwable t) {
                if (!silent) {
                    LOGGER.warn("wrong configuration parameter {}, expecting a map", key);
                }

                return null;
            }
        } else {
            if (!silent) {
                LOGGER.warn("wrong configuration parameter {}, expecting a map", key);
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

        value = _valueFromEnv("RESTHEART", confParameter, silent);

        if (value != null) {
            return value;
        }

        // legacy variable pattern
        value = _valueFromEnv("RESTHEART_SECURITY", confParameter, silent);

        if (value != null) {
            return value;
        }

        // no prefix
        value = _valueFromEnv(null, confParameter, silent);

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
}
