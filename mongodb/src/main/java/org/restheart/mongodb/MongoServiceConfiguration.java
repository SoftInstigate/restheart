/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb;

import com.google.common.collect.Maps;
import com.mongodb.ConnectionString;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;
import org.restheart.ConfigurationException;
import org.restheart.exchange.ExchangeKeys.ETAG_CHECK_POLICY;
import org.restheart.exchange.ExchangeKeys.REPRESENTATION_FORMAT;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Utility class to help dealing with the restheart configuration file.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoServiceConfiguration {
    private static MongoServiceConfiguration INSTANCE = null;

    /**
     * undertow connetction options
     *
     * Seehttp://undertow.io/undertow-docs/undertow-docs-2.0.0/index.html#common-listener-options
     */
    public static final String CONNECTION_OPTIONS_KEY = "connection-options";

    /**
     *
     */
    public final static Logger LOGGER = LoggerFactory.getLogger(MongoServiceConfiguration.class);

    private boolean silent = false;
    private final String instanceBaseURL;
    private final REPRESENTATION_FORMAT defaultRepresentationFormat;
    private final ConnectionString mongoUri;
    private final List<Map<String, Object>> mongoMounts;
    private final Map<String, Map<String, Object>> pluginsArgs;
    private final boolean localCacheEnabled;
    private final long localCacheTtl;
    private final boolean schemaCacheEnabled;
    private final long schemaCacheTtl;
    private final int requestsLimit;
    private final int eagerPoolSize;
    private final int eagerLinearSliceWidht;
    private final int eagerLinearSliceDelta;
    private final int[] eagerLinearSliceHeights;
    private final int eagerRndSliceMinWidht;
    private final int eagerRndMaxCursors;
    private final ETAG_CHECK_POLICY dbEtagCheckPolicy;
    private final ETAG_CHECK_POLICY collEtagCheckPolicy;
    private final ETAG_CHECK_POLICY docEtagCheckPolicy;
    private final Map<String, Object> connectionOptions;
    private final long queryTimeLimit;
    private final long aggregationTimeLimit;
    private final boolean aggregationCheckOperators;
    private final int cursorBatchSize;
    private final int defaultPagesize;
    private final int maxPagesize;
    private final METRICS_GATHERING_LEVEL metricsGatheringLevel;

    public static MongoServiceConfiguration get() {
        return INSTANCE;
    }

    public static MongoServiceConfiguration init(Map<String, Object> confs) {
        return init(confs, true);
    }

    public static MongoServiceConfiguration init(Map<String, Object> confs, boolean silent) {
        INSTANCE = new MongoServiceConfiguration(confs, silent);

        return INSTANCE;
    }

    public static MongoServiceConfiguration initFromFile(Path confFile, boolean silent) {
        INSTANCE = new MongoServiceConfiguration(confFile, silent);

        return INSTANCE;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getConfigurationFromFile(final Path confFilePath) throws ConfigurationException {
        Yaml yaml = new Yaml(new SafeConstructor());

        Map<String, Object> conf = null;

        try (var fis = new FileInputStream(confFilePath.toFile())) {
            conf = (Map<String, Object>) yaml.load(fis);
        } catch (FileNotFoundException fne) {
            throw new ConfigurationException("Configuration file not found", fne);
        } catch (Throwable t) {
            throw new ConfigurationException("Error parsing the configuration file", t);
        }

        return conf;
    }

    static boolean isParametric(final Path confFilePath) throws IOException {
        try (var sc = new Scanner(confFilePath, "UTF-8")) {
            return sc.findAll(Pattern.compile("\\{\\{.*\\}\\}")).limit(1).count() > 0;
        }
    }

    /**
     *
     * @param integers
     * @return
     */
    public static int[] convertListToIntArray(List<Object> integers) {
        int[] ret = new int[integers.size()];
        Iterator<Object> iterator = integers.iterator();
        for (int i = 0; i < ret.length; i++) {
            Object o = iterator.next();

            if (o instanceof Integer) {
                ret[i] = (Integer) o;
            } else {
                return new int[0];
            }
        }

        return ret;
    }

    /**
     * the configuration map
     */
    private final Map<String, Object> configurationFileMap;

    /**
     * Creates a new instance of Configuration with defaults values.
     */
    public MongoServiceConfiguration() {
        this(new HashMap<>(), false);
    }

    /**
     * Creates a new instance of Configuration from the configuration file For any
     * missing property the default value is used.
     *
     * @param confFilePath the path of the configuration file
     * @param silent
     * @throws org.restheart.ConfigurationException
     */
    private MongoServiceConfiguration(final Path confFilePath, boolean silent) throws ConfigurationException {
        this(getConfigurationFromFile(confFilePath), silent);
    }

    /**
     * Creates a new instance of Configuration from the configuration file For any
     * missing property the default value is used.
     *
     * @param conf   the key-value configuration map
     * @param silent
     * @throws org.restheart.ConfigurationException
     */
    private MongoServiceConfiguration(Map<String, Object> conf, boolean silent) throws ConfigurationException {
        this.configurationFileMap = conf;
        this.silent = silent;

        instanceBaseURL = getAsStringOrDefault(conf, INSTANCE_BASE_URL_KEY, null);

        String _representationFormat = getAsStringOrDefault(conf, REPRESENTATION_FORMAT_KEY,
                DEFAULT_REPRESENTATION_FORMAT.name());

        REPRESENTATION_FORMAT rf = REPRESENTATION_FORMAT.STANDARD;

        try {
            rf = REPRESENTATION_FORMAT.valueOf(_representationFormat);
        } catch (IllegalArgumentException iar) {
            LOGGER.warn("wrong value for {}. allowed values are {}; " + "setting it to {}", REPRESENTATION_FORMAT_KEY,
                    REPRESENTATION_FORMAT.values(), REPRESENTATION_FORMAT.STANDARD);
        } finally {
            defaultRepresentationFormat = rf;
        }

        ConnectionString _mongoUri;

        try {
            // check the mongo uri
            _mongoUri = new ConnectionString(getAsStringOrDefault(conf, MONGO_URI_KEY, DEFAULT_MONGO_URI));
        } catch (IllegalArgumentException iae) {
            LOGGER.error("Wrong parameter {} in the configuration file: {}, using its default value {}", MONGO_URI_KEY, iae.getMessage(), DEFAULT_MONGO_URI);
            //throw new ConfigurationException("Wrong  group {} not specified in the configuration file, using its default value {}" + MONGO_URI_KEY, iae);
            _mongoUri = new ConnectionString(DEFAULT_MONGO_URI);
        }

        this.mongoUri = _mongoUri;

        List<Map<String, Object>> mongoMountsDefault = new ArrayList<>();
        Map<String, Object> defaultMongoMounts = new HashMap<>();
        defaultMongoMounts.put(MONGO_MOUNT_WHAT_KEY, DEFAULT_MONGO_MOUNT_WHAT);
        defaultMongoMounts.put(MONGO_MOUNT_WHERE_KEY, DEFAULT_MONGO_MOUNT_WHERE);
        mongoMountsDefault.add(defaultMongoMounts);

        mongoMounts = getAsListOfMaps(conf, MONGO_MOUNTS_KEY, mongoMountsDefault);

        pluginsArgs = getAsMapOfMaps(conf, PLUGINS_ARGS_KEY, new LinkedHashMap<>());

        requestsLimit = getAsIntegerOrDefault(conf, REQUESTS_LIMIT_KEY, 100);

        queryTimeLimit = getAsLongOrDefault(conf, QUERY_TIME_LIMIT_KEY, (long) 0);
        aggregationTimeLimit = getAsLongOrDefault(conf, AGGREGATION_TIME_LIMIT_KEY, (long) 0);
        aggregationCheckOperators = getAsBooleanOrDefault(conf, AGGREGATION_CHECK_OPERATORS, true);

        localCacheEnabled = getAsBooleanOrDefault(conf, LOCAL_CACHE_ENABLED_KEY, true);
        localCacheTtl = getAsLongOrDefault(conf, LOCAL_CACHE_TTL_KEY, (long) 1000);

        schemaCacheEnabled = getAsBooleanOrDefault(conf, SCHEMA_CACHE_ENABLED_KEY, true);
        schemaCacheTtl = getAsLongOrDefault(conf, SCHEMA_CACHE_TTL_KEY, (long) 1000);

        eagerPoolSize = getAsIntegerOrDefault(conf, EAGER_POOL_SIZE, 100);
        eagerLinearSliceWidht = getAsIntegerOrDefault(conf, EAGER_LINEAR_SLICE_WIDHT, 1000);
        eagerLinearSliceDelta = getAsIntegerOrDefault(conf, EAGER_LINEAR_SLICE_DELTA, 100);
        eagerLinearSliceHeights = getAsArrayOfInts(conf, EAGER_LINEAR_HEIGHTS, new int[] { 4, 2, 1 });
        eagerRndSliceMinWidht = getAsIntegerOrDefault(conf, EAGER_RND_SLICE_MIN_WIDHT, 1000);
        eagerRndMaxCursors = getAsIntegerOrDefault(conf, EAGER_RND_MAX_CURSORS, 50);

        Map<String, Object> etagCheckPolicies = getAsMap(conf, ETAG_CHECK_POLICY_KEY, null);

        if (etagCheckPolicies != null) {
            String _dbEtagCheckPolicy = getAsStringOrDefault(etagCheckPolicies, ETAG_CHECK_POLICY_DB_KEY,
                    DEFAULT_DB_ETAG_CHECK_POLICY.name());

            String _collEtagCheckPolicy = getAsStringOrDefault(etagCheckPolicies, ETAG_CHECK_POLICY_COLL_KEY,
                    DEFAULT_COLL_ETAG_CHECK_POLICY.name());

            String _docEtagCheckPolicy = getAsStringOrDefault(etagCheckPolicies, ETAG_CHECK_POLICY_DOC_KEY,
                    DEFAULT_DOC_ETAG_CHECK_POLICY.name());

            ETAG_CHECK_POLICY validDbValue;
            ETAG_CHECK_POLICY validCollValue;
            ETAG_CHECK_POLICY validDocValue;

            try {
                validDbValue = ETAG_CHECK_POLICY.valueOf(_dbEtagCheckPolicy);
            } catch (IllegalArgumentException iae) {
                LOGGER.warn("wrong value for parameter {} setting it to default value {}", ETAG_CHECK_POLICY_DB_KEY,
                        DEFAULT_DB_ETAG_CHECK_POLICY);
                validDbValue = DEFAULT_DB_ETAG_CHECK_POLICY;
            }

            dbEtagCheckPolicy = validDbValue;

            try {
                validCollValue = ETAG_CHECK_POLICY.valueOf(_collEtagCheckPolicy);
            } catch (IllegalArgumentException iae) {
                LOGGER.warn("wrong value for parameter {} setting it to default value {}", ETAG_CHECK_POLICY_COLL_KEY,
                        DEFAULT_COLL_ETAG_CHECK_POLICY);
                validCollValue = DEFAULT_COLL_ETAG_CHECK_POLICY;
            }

            collEtagCheckPolicy = validCollValue;

            try {
                validDocValue = ETAG_CHECK_POLICY.valueOf(_docEtagCheckPolicy);
            } catch (IllegalArgumentException iae) {
                LOGGER.warn("wrong value for parameter {} setting it to default value {}", ETAG_CHECK_POLICY_COLL_KEY,
                        DEFAULT_COLL_ETAG_CHECK_POLICY);
                validDocValue = DEFAULT_DOC_ETAG_CHECK_POLICY;
            }

            docEtagCheckPolicy = validDocValue;
        } else {
            dbEtagCheckPolicy = DEFAULT_DB_ETAG_CHECK_POLICY;
            collEtagCheckPolicy = DEFAULT_COLL_ETAG_CHECK_POLICY;
            docEtagCheckPolicy = DEFAULT_DOC_ETAG_CHECK_POLICY;
        }

        connectionOptions = getAsMap(conf, CONNECTION_OPTIONS_KEY, Maps.newHashMap());

        cursorBatchSize = getAsIntegerOrDefault(conf, CURSOR_BATCH_SIZE_KEY, DEFAULT_CURSOR_BATCH_SIZE);

        defaultPagesize = getAsIntegerOrDefault(conf, DEFAULT_PAGESIZE_KEY, DEFAULT_DEFAULT_PAGESIZE);

        maxPagesize = getAsIntegerOrDefault(conf, MAX_PAGESIZE_KEY, DEFAULT_MAX_PAGESIZE);

        {
            METRICS_GATHERING_LEVEL mglevel;
            try {
                String value = getAsStringOrDefault(conf, METRICS_GATHERING_LEVEL_KEY, "ROOT");
                mglevel = METRICS_GATHERING_LEVEL.valueOf(value.toUpperCase(Locale.getDefault()));
            } catch (IllegalArgumentException iae) {
                mglevel = METRICS_GATHERING_LEVEL.ROOT;
            }
            metricsGatheringLevel = mglevel;
        }
    }

    @Override
    public String toString() {
        return "Configuration{" + "silent=" + silent + ", instanceBaseURL=" + instanceBaseURL
                + ", defaultRepresentationFromat=" + defaultRepresentationFormat + ", mongoUri=" + mongoUri
                + ", mongoMounts=" + mongoMounts + ", pluginsArgs=" + getPluginsArgs() + ", localCacheEnabled="
                + localCacheEnabled + ", localCacheTtl=" + localCacheTtl + ", schemaCacheEnabled=" + schemaCacheEnabled
                + ", schemaCacheTtl=" + schemaCacheTtl + ", requestsLimit=" + requestsLimit + ", metricsGatheringLevel="
                + metricsGatheringLevel + ", eagerPoolSize=" + eagerPoolSize + ", eagerLinearSliceWidht="
                + eagerLinearSliceWidht + ", eagerLinearSliceDelta=" + eagerLinearSliceDelta
                + ", eagerLinearSliceHeights=" + Arrays.toString(eagerLinearSliceHeights) + ", eagerRndSliceMinWidht="
                + eagerRndSliceMinWidht + ", eagerRndMaxCursors=" + eagerRndMaxCursors + ", dbEtagCheckPolicy="
                + dbEtagCheckPolicy + ", collEtagCheckPolicy=" + collEtagCheckPolicy + ", docEtagCheckPolicy="
                + docEtagCheckPolicy + ", connectionOptions=" + connectionOptions + ", queryTimeLimit=" + queryTimeLimit
                + ", aggregationTimeLimit=" + aggregationTimeLimit + ", aggregationCheckOperators="
                + aggregationCheckOperators + ", cursorBatchSize=" + cursorBatchSize + ", defaultPagesize="
                + defaultPagesize + ", maxPagesize=" + maxPagesize + ", configurationFileMap=" + configurationFileMap
                + '}';
    }

    /**
     *
     * @param conf
     * @param key
     * @param defaultValue
     * @return
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getAsListOfMaps(final Map<String, Object> conf, final String key,
            final List<Map<String, Object>> defaultValue) {
        if (conf == null) {
            if (!silent) {
                LOGGER.debug(
                        "parameters group {} not specified in the configuration file." + " Using its default value {}",
                        key, defaultValue);
            }

            return defaultValue;
        }

        Object o = conf.get(key);

        if (o instanceof List) {
            return (List<Map<String, Object>>) o;
        } else {
            if (!silent) {
                LOGGER.debug(
                        "parameters group {} not specified in the configuration file." + " Using its default value {}",
                        key, defaultValue);
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
    private Map<String, Object> getAsMap(final Map<String, Object> conf, final String key,
            final Map<String, Object> defaultVal) {
        if (conf == null) {
            if (!silent) {
                LOGGER.debug("parameters group {} not specified in the configuration file.", key);
            }
            return defaultVal;
        }

        Object o = conf.get(key);

        if (o instanceof Map) {
            return (Map<String, Object>) o;
        } else {
            if (!silent) {
                LOGGER.debug("parameters group {} not specified in the configuration file.", key);
            }
            return defaultVal;
        }
    }

    /**
     *
     * @param conf
     * @param key
     * @return
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> getAsMapOfMaps(final Map<String, Object> conf, final String key,
            final Map<String, Map<String, Object>> defaultVal) {
        if (conf == null) {
            if (!silent) {
                LOGGER.debug("parameters {} not specified in the configuration file.", key);
            }
            return defaultVal;
        }

        Object o = conf.get(key);

        if (o instanceof Map) {
            try {
                return (Map<String, Map<String, Object>>) o;
            } catch (Throwable t) {
                LOGGER.warn("Invalid configuration parameter {}", key);
                return defaultVal;
            }
        } else {
            if (!silent) {
                LOGGER.debug("parameters {} not specified in the configuration file.", key);
            }
            return defaultVal;
        }
    }

    /**
     *
     * @param key
     * @return the environment or java property variable, if found
     */
    private static String overriddenValueFromEnv(final String confParameter) {
        var value = _overriddenValueFromEnv("RH", confParameter);

        if (value != null) {
            return value;
        }

        value = _overriddenValueFromEnv("RESTHEART", confParameter);

        if (value != null) {
            return value;
        }

        // legacy variable pattern
        value = _overriddenValueFromEnv("RESTHEART_SECURITY", confParameter);

        if (value != null) {
            return value;
        }

        // no prefix
        value = _overriddenValueFromEnv(null, confParameter);

        if (value != null) {
            return value;
        }

        return null;
    }

    private static String _overriddenValueFromEnv(final String prefix, final String confParameter) {
        var key = prefix != null ? prefix + "_" + confParameter.toUpperCase().replaceAll("-", "_")
                : confParameter.toUpperCase().replaceAll("-", "_");

        return __overriddenValueFromEnv(confParameter, key);
    }

    private static String __overriddenValueFromEnv(final String confParameter, final String key) {

        String envValue = System.getProperty(key);

        if (envValue == null) {
            envValue = System.getenv(key);
        }

        if (null != envValue) {
            LOGGER.warn(">>> Found environment variable '{}': overriding parameter '{}' with value '{}'", key,
                    confParameter, envValue);
        }
        return envValue;
    }

    /**
     *
     * @param conf
     * @param key
     * @param defaultValue
     * @return
     */
    private Boolean getAsBooleanOrDefault(final Map<String, Object> conf, final String key,
            final Boolean defaultValue) {
        String envValue = overriddenValueFromEnv(key);
        if (envValue != null) {
            return Boolean.valueOf(envValue);
        }

        if (conf == null) {
            if (!silent) {
                LOGGER.debug(
                        "tried to get paramenter {} from a null configuration map." + " Using its default value {}",
                        key, defaultValue);
            }
            return defaultValue;
        }

        Object o = conf.get(key);

        if (o == null) {
            // if default value is null there is no default value actually
            if (defaultValue && !silent) {
                LOGGER.debug("parameter {} not specified in the configuration file." + " Using its default value {}",
                        key, defaultValue);
            }
            return defaultValue;
        } else if (o instanceof Boolean) {
            if (!silent) {
                LOGGER.debug("paramenter {} set to {}", key, o);
            }
            return (Boolean) o;
        } else {
            if (!silent) {
                LOGGER.warn("wrong value for parameter {}: {}." + " Using its default value {}", key, o, defaultValue);
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
    private String getAsStringOrDefault(final Map<String, Object> conf, final String key, final String defaultValue) {
        String envValue = overriddenValueFromEnv(key);
        if (envValue != null) {
            return envValue;
        }

        if (conf == null || conf.get(key) == null) {
            // if default value is null there is no default value actually
            if (defaultValue != null && !silent) {
                LOGGER.debug("parameter {} not specified in the configuration file." + " Using its default value {}",
                        key, defaultValue);
            }
            return defaultValue;
        } else if (conf.get(key) instanceof String) {
            if (!silent) {
                LOGGER.debug("paramenter {} set to {}", key, conf.get(key));
            }
            return (String) conf.get(key);
        } else {
            if (!silent) {
                throw new IllegalArgumentException(
                        String.format("Wrong value for parameter %s: %s.}", key, conf.get(key)));
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
    private Integer getAsIntegerOrDefault(final Map<String, Object> conf, final String key,
            final Integer defaultValue) {
        String envValue = overriddenValueFromEnv(key);
        if (envValue != null) {
            return Integer.valueOf(envValue);
        }

        if (conf == null || conf.get(key) == null) {
            // if default value is null there is no default value actually
            if (defaultValue != null && !silent) {
                LOGGER.debug("parameter {} not specified in the configuration file." + " Using its default value {}",
                        key, defaultValue);
            }
            return defaultValue;
        } else if (conf.get(key) instanceof Integer) {
            if (!silent) {
                LOGGER.debug("paramenter {} set to {}", key, conf.get(key));
            }
            return (Integer) conf.get(key);
        } else {
            if (!silent) {
                LOGGER.warn("wrong value for parameter {}: {}." + " Using its default value {}", key, conf.get(key),
                        defaultValue);
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
    private Long getAsLongOrDefault(final Map<String, Object> conf, final String key, final Long defaultValue) {
        String envValue = overriddenValueFromEnv(key);
        if (envValue != null) {
            return Long.valueOf(envValue);
        }

        if (conf == null || conf.get(key) == null) {
            // if default value is null there is no default value actually
            if (defaultValue != null && !silent) {
                LOGGER.debug("parameter {} not specified in the configuration file." + " Using its default value {}",
                        key, defaultValue);
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
                    LOGGER.warn("wrong value for parameter {}: {}. Using its default value {}", key, conf.get(key),
                            defaultValue);
                }
                return defaultValue;
            }
        } else {
            if (!silent) {
                LOGGER.warn("wrong value for parameter {}: {}." + " Using its default value {}", key, conf.get(key),
                        defaultValue);
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
    private int[] getAsArrayOfInts(final Map<String, Object> conf, final String key, final int[] defaultValue) {
        if (conf == null || conf.get(key) == null) {
            // if default value is null there is no default value actually
            if (defaultValue != null && !silent) {
                LOGGER.debug("parameter {} not specified in the configuration file." + " Using its default value {}",
                        key, defaultValue);
            }
            return defaultValue;
        } else if (conf.get(key) instanceof List) {
            if (!silent) {
                LOGGER.debug("paramenter {} set to {}", key, conf.get(key));
            }

            int ret[] = convertListToIntArray((List<Object>) conf.get(key));

            if (ret.length == 0) {
                if (!silent) {
                    LOGGER.warn("wrong value for parameter {}: {}." + " Using its default value {}", key, conf.get(key),
                            defaultValue);
                }
                return defaultValue;
            } else {
                return ret;
            }
        } else {
            if (!silent) {
                LOGGER.warn("wrong value for parameter {}: {}." + " Using its default value {}", key, conf.get(key),
                        defaultValue);
            }
            return defaultValue;
        }
    }

    // @SuppressWarnings("unchecked")
    // private List<String> getAsListOfStrings(final Map<String, Object> conf, final String key,
    //         final List<String> defaultValue) {
    //     if (conf == null || conf.get(key) == null) {
    //         // if default value is null there is no default value actually
    //         if (defaultValue != null && !silent) {
    //             LOGGER.debug("parameter {} not specified in the configuration file." + " Using its default value {}",
    //                     key, defaultValue);
    //         }
    //         return defaultValue;
    //     } else if (conf.get(key) instanceof List) {
    //         if (!silent) {
    //             LOGGER.debug("paramenter {} set to {}", key, conf.get(key));
    //         }

    //         List<String> ret = ((List<String>) conf.get(key));

    //         if (ret.isEmpty()) {
    //             if (!silent) {
    //                 LOGGER.warn("wrong value for parameter {}: {}." + " Using its default value {}", key, conf.get(key),
    //                         defaultValue);
    //             }
    //             return defaultValue;
    //         } else {
    //             return ret;
    //         }
    //     } else {
    //         if (!silent) {
    //             LOGGER.warn("wrong value for parameter {}: {}." + " Using its default value {}", key, conf.get(key),
    //                     defaultValue);
    //         }
    //         return defaultValue;
    //     }
    // }

    /**
     * @return the mongoMounts
     */
    public List<Map<String, Object>> getMongoMounts() {
        return Collections.unmodifiableList(mongoMounts);
    }

    /**
     * @return the localCacheEnabled
     */
    public boolean isLocalCacheEnabled() {
        return localCacheEnabled;
    }

    /**
     * @return the localCacheTtl
     */
    public long getLocalCacheTtl() {
        return localCacheTtl;
    }

    /**
     * @return the requestsLimit
     */
    public int getRequestsLimit() {
        return requestsLimit;
    }

    /**
     * @return the queryTimeLimit
     */
    public long getQueryTimeLimit() {
        return queryTimeLimit;
    }

    /**
     * @return the aggregationTimeLimit
     */
    public long getAggregationTimeLimit() {
        return aggregationTimeLimit;
    }

    /**
     * @return the aggregationCheckOperators
     */
    public boolean getAggregationCheckOperators() {
        return aggregationCheckOperators;
    }

    /**
     * @return the eagerLinearSliceWidht
     */
    public int getEagerLinearSliceWidht() {
        return eagerLinearSliceWidht;
    }

    /**
     * @return the eagerLinearSliceDelta
     */
    public int getEagerLinearSliceDelta() {
        return eagerLinearSliceDelta;
    }

    /**
     * @return the eagerLinearSliceHeights
     */
    public int[] getEagerLinearSliceHeights() {
        return eagerLinearSliceHeights;
    }

    /**
     * @return the eagerRndSliceMinWidht
     */
    public int getEagerRndSliceMinWidht() {
        return eagerRndSliceMinWidht;
    }

    /**
     * @return the eagerRndMaxCursors
     */
    public int getEagerRndMaxCursors() {
        return eagerRndMaxCursors;
    }

    /**
     * @return the eagerPoolSize
     */
    public int getEagerPoolSize() {
        return eagerPoolSize;
    }

    /**
     * @return the mongoUri
     */
    public ConnectionString getMongoUri() {
        return mongoUri;
    }

    /**
     * @return the pluginsArgs
     */
    public Map<String, Map<String, Object>> getPluginsArgs() {
        return pluginsArgs;
    }

    /**
     * @return the schemaCacheEnabled
     */
    public boolean isSchemaCacheEnabled() {
        return schemaCacheEnabled;
    }

    /**
     * @return the schemaCacheTtl
     */
    public long getSchemaCacheTtl() {
        return schemaCacheTtl;
    }

    /**
     * @return the dbEtagCheckPolicy
     */
    public ETAG_CHECK_POLICY getDbEtagCheckPolicy() {
        return dbEtagCheckPolicy;
    }

    /**
     * @return the collEtagCheckPolicy
     */
    public ETAG_CHECK_POLICY getCollEtagCheckPolicy() {
        return collEtagCheckPolicy;
    }

    /**
     * @return the docEtagCheckPolicy
     */
    public ETAG_CHECK_POLICY getDocEtagCheckPolicy() {
        return docEtagCheckPolicy;
    }

    /**
     * @return the connectionOptions
     */
    public Map<String, Object> getConnectionOptions() {
        return Collections.unmodifiableMap(connectionOptions);
    }

    /**
     * @return the instanceBaseURL
     */
    public String getInstanceBaseURL() {
        return instanceBaseURL;
    }

    /**
     * @return the defaultRepresentationFromat
     */
    public REPRESENTATION_FORMAT getDefaultRepresentationFormat() {
        return defaultRepresentationFormat;
    }

    /**
     * @return the configurationFileMap
     */
    public Map<String, Object> getConfigurationFileMap() {
        return Collections.unmodifiableMap(configurationFileMap);
    }

    /**
     * @return the cursorBatchSize
     */
    public int getCursorBatchSize() {
        return cursorBatchSize;
    }

    /**
     * @return the maxPagesize
     */
    public int getMaxPagesize() {
        return maxPagesize;
    }

    /**
     * @return the defaultPagesize
     */
    public int getDefaultPagesize() {
        return defaultPagesize;
    }

    /**
     *
     * @return
     */
    public METRICS_GATHERING_LEVEL getMetricsGatheringLevel() {
        return metricsGatheringLevel;
    }

    /**
     * decides whether metrics are gathered at the given log level or not
     *
     * @param level Metrics Gathering Level
     * @return true if gathering Above Or Equal To Level
     */
    public boolean gatheringAboveOrEqualToLevel(METRICS_GATHERING_LEVEL level) {
        return getMetricsGatheringLevel().compareTo(level) >= 0;
    }

    /**
     *
     */
    public enum METRICS_GATHERING_LEVEL {
        /**
         * do not gather any metrics
         */
        OFF,
        /**
         * gather basic metrics (for all databases, but not specific per database)
         */
        ROOT,
        /**
         * gather basic metrics, and also specific per database (but not
         * collection-specific)
         */
        DATABASE,
        /**
         * gather basic, database, and collection-specific metrics
         */
        COLLECTION
    }
}
