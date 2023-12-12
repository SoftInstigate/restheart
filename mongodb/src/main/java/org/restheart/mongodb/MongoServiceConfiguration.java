/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.restheart.configuration.ConfigurationException;
import static org.restheart.configuration.Utils.asBoolean;
import static org.restheart.configuration.Utils.asInteger;
import static org.restheart.configuration.Utils.asListOfMaps;
import static org.restheart.configuration.Utils.asLong;
import static org.restheart.configuration.Utils.asMap;
import static org.restheart.configuration.Utils.asString;
import org.restheart.exchange.ExchangeKeys.ETAG_CHECK_POLICY;
import org.restheart.exchange.ExchangeKeys.REPRESENTATION_FORMAT;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.AGGREGATION_CHECK_OPERATORS;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.AGGREGATION_TIME_LIMIT_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.CURSOR_BATCH_SIZE_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.DEFAULT_COLL_ETAG_CHECK_POLICY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.DEFAULT_CURSOR_BATCH_SIZE;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.DEFAULT_DB_ETAG_CHECK_POLICY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.DEFAULT_DEFAULT_PAGESIZE;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.DEFAULT_DOC_ETAG_CHECK_POLICY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.DEFAULT_MAX_PAGESIZE;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.DEFAULT_MONGO_MOUNT_WHAT;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.DEFAULT_MONGO_MOUNT_WHERE;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.DEFAULT_MONGO_URI;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.DEFAULT_PAGESIZE_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.DEFAULT_REPRESENTATION_FORMAT;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.ETAG_CHECK_POLICY_COLL_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.ETAG_CHECK_POLICY_DB_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.ETAG_CHECK_POLICY_DOC_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.ETAG_CHECK_POLICY_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.GET_COLLECTION_CACHE_DOCS_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.GET_COLLECTION_CACHE_ENABLED_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.GET_COLLECTION_CACHE_SIZE_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.GET_COLLECTION_CACHE_TTL_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.INSTANCE_BASE_URL_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.LOCAL_CACHE_ENABLED_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.LOCAL_CACHE_TTL_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.MAX_PAGESIZE_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.METRICS_GATHERING_LEVEL_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.MONGO_MOUNTS_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.MONGO_MOUNT_WHAT_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.MONGO_MOUNT_WHERE_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.MONGO_URI_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.QUERY_TIME_LIMIT_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.REPRESENTATION_FORMAT_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.REQUESTS_LIMIT_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.SCHEMA_CACHE_ENABLED_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.SCHEMA_CACHE_TTL_KEY;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.ConnectionString;

/**
 * Utility class to help dealing with the restheart configuration file.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@SuppressWarnings("deprecation")
public class MongoServiceConfiguration {
    private static MongoServiceConfiguration INSTANCE = null;

    /**
     * undertow connection options
     *
     * Seehttp://undertow.io/undertow-docs/undertow-docs-2.0.0/index.html#common-listener-options
     */
    public static final String CONNECTION_OPTIONS_KEY = "connection-options";

    /**
     *
     */
    public final static Logger LOGGER = LoggerFactory.getLogger(MongoServiceConfiguration.class);

    private final String uri;
    private final String instanceBaseURL;
    private final REPRESENTATION_FORMAT defaultRepresentationFormat;
    private final ConnectionString mongoUri;
    private final List<Map<String, Object>> mongoMounts;
    private final boolean localCacheEnabled;
    private final long localCacheTtl;
    private final boolean schemaCacheEnabled;
    private final long schemaCacheTtl;
    private final int requestsLimit;
    private final boolean getCollectionCacheEnabled;
    private final int getCollectionCacheSize;
    private final int getCollectionCacheTTL;
    private final int getCollectionCacheDocs;
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

    /**
     * @deprecated will be removed in RH v8.0
     */
    @Deprecated
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

    /**
     * the configuration
     */
    private final Map<String, Object> mongoSrvConfiguration;

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
     * @param conf   the key-value configuration map
     * @param silent
     * @throws org.restheart.configuration.ConfigurationException
     */
    @SuppressWarnings("deprecated")
    private MongoServiceConfiguration(Map<String, Object> conf, boolean silent) throws ConfigurationException {
        this.mongoSrvConfiguration = conf;

        uri = asString(conf, "uri", null, silent);

        instanceBaseURL = asString(conf, INSTANCE_BASE_URL_KEY, null, silent);

        var _representationFormat = asString(conf, REPRESENTATION_FORMAT_KEY, DEFAULT_REPRESENTATION_FORMAT.name(), silent);

        var rf = REPRESENTATION_FORMAT.STANDARD;

        try {
            rf = REPRESENTATION_FORMAT.valueOf(_representationFormat);
        } catch (IllegalArgumentException iar) {
            LOGGER.warn("wrong value for {}. allowed values are {}; " + "setting it to {}", REPRESENTATION_FORMAT_KEY, REPRESENTATION_FORMAT.values(), REPRESENTATION_FORMAT.STANDARD);
        } finally {
            defaultRepresentationFormat = rf;
        }

        ConnectionString _mongoUri;

        try {
            // check the mongo uri
            _mongoUri = new ConnectionString(asString(conf, MONGO_URI_KEY, DEFAULT_MONGO_URI, silent));
        } catch (IllegalArgumentException iae) {
            LOGGER.error("Wrong parameter {} in the configuration file: {}, using its default value {}", MONGO_URI_KEY, iae.getMessage(), DEFAULT_MONGO_URI);
            //throw new ConfigurationException("Wrong  group {} not specified in the configuration file, using its default value {}" + MONGO_URI_KEY, iae);
            _mongoUri = new ConnectionString(DEFAULT_MONGO_URI);
        }

        mongoUri = _mongoUri;

        List<Map<String, Object>> mongoMountsDefault = new ArrayList<>();
        Map<String, Object> defaultMongoMounts = new HashMap<>();
        defaultMongoMounts.put(MONGO_MOUNT_WHAT_KEY, DEFAULT_MONGO_MOUNT_WHAT);
        defaultMongoMounts.put(MONGO_MOUNT_WHERE_KEY, DEFAULT_MONGO_MOUNT_WHERE);
        mongoMountsDefault.add(defaultMongoMounts);

        mongoMounts = asListOfMaps(conf, MONGO_MOUNTS_KEY, mongoMountsDefault, silent);

        requestsLimit = asInteger(conf, REQUESTS_LIMIT_KEY, 100, silent);

        queryTimeLimit = asLong(conf, QUERY_TIME_LIMIT_KEY, (long) 0, silent);
        aggregationTimeLimit = asLong(conf, AGGREGATION_TIME_LIMIT_KEY, (long) 0, silent);
        aggregationCheckOperators = asBoolean(conf, AGGREGATION_CHECK_OPERATORS, true, silent);

        localCacheEnabled = asBoolean(conf, LOCAL_CACHE_ENABLED_KEY, true, silent);
        localCacheTtl = asLong(conf, LOCAL_CACHE_TTL_KEY, (long) 1000, silent);

        schemaCacheEnabled = asBoolean(conf, SCHEMA_CACHE_ENABLED_KEY, true, silent);
        schemaCacheTtl = asLong(conf, SCHEMA_CACHE_TTL_KEY, (long) 1000, silent);

        getCollectionCacheEnabled = asBoolean(conf, GET_COLLECTION_CACHE_ENABLED_KEY, true, silent);
        getCollectionCacheSize = asInteger(conf, GET_COLLECTION_CACHE_SIZE_KEY, 100, silent);
        getCollectionCacheTTL = asInteger(conf, GET_COLLECTION_CACHE_TTL_KEY, 10_000, silent);
        getCollectionCacheDocs = asInteger(conf, GET_COLLECTION_CACHE_DOCS_KEY, 1_000, silent);

        Map<String, Object> etagCheckPolicies = asMap(conf, ETAG_CHECK_POLICY_KEY, null, silent);

        if (etagCheckPolicies != null) {
            var _dbEtagCheckPolicy = asString(etagCheckPolicies, ETAG_CHECK_POLICY_DB_KEY, DEFAULT_DB_ETAG_CHECK_POLICY.name(), silent);

            var _collEtagCheckPolicy = asString(etagCheckPolicies, ETAG_CHECK_POLICY_COLL_KEY, DEFAULT_COLL_ETAG_CHECK_POLICY.name(), silent);

            var _docEtagCheckPolicy = asString(etagCheckPolicies, ETAG_CHECK_POLICY_DOC_KEY, DEFAULT_DOC_ETAG_CHECK_POLICY.name(), silent);

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

        connectionOptions = asMap(conf, CONNECTION_OPTIONS_KEY, null, silent);

        cursorBatchSize = asInteger(conf, CURSOR_BATCH_SIZE_KEY, DEFAULT_CURSOR_BATCH_SIZE, silent);

        defaultPagesize = asInteger(conf, DEFAULT_PAGESIZE_KEY, DEFAULT_DEFAULT_PAGESIZE, silent);

        maxPagesize = asInteger(conf, MAX_PAGESIZE_KEY, DEFAULT_MAX_PAGESIZE, silent);

        METRICS_GATHERING_LEVEL mglevel;
        try {
            var value = asString(conf, METRICS_GATHERING_LEVEL_KEY, "OFF", silent);
            mglevel = METRICS_GATHERING_LEVEL.valueOf(value.toUpperCase(Locale.getDefault()));
        } catch (IllegalArgumentException iae) {
            mglevel = METRICS_GATHERING_LEVEL.OFF;
        }
        metricsGatheringLevel = mglevel;

        if (metricsGatheringLevel != METRICS_GATHERING_LEVEL.OFF) {
            LOGGER.warn("Deprecated mongo metrics enabled. It will be removed in RESTHeart v8.0. Use requestsMetricsCollector instead, see https://restheart.org/docs/monitoring");
        }
    }

    @Override
    public String toString() {
        return "Configuration{instanceBaseURL=" + instanceBaseURL
                + ", defaultRepresentationFromat=" + defaultRepresentationFormat + ", mongoUri=" + mongoUri
                + ", mongoMounts=" + mongoMounts + ", localCacheEnabled="
                + localCacheEnabled + ", localCacheTtl=" + localCacheTtl + ", schemaCacheEnabled=" + schemaCacheEnabled
                + ", schemaCacheTtl=" + schemaCacheTtl + ", requestsLimit=" + requestsLimit
                + ", cacheEnabled=" + getCollectionCacheEnabled + ", cacheSize=" + getCollectionCacheSize + ", cacheTTL" + getCollectionCacheTTL
                + ", dbEtagCheckPolicy=" + dbEtagCheckPolicy + ", collEtagCheckPolicy=" + collEtagCheckPolicy + ", docEtagCheckPolicy="
                + docEtagCheckPolicy + ", connectionOptions=" + connectionOptions + ", queryTimeLimit=" + queryTimeLimit
                + ", aggregationTimeLimit=" + aggregationTimeLimit + ", aggregationCheckOperators="
                + aggregationCheckOperators + ", cursorBatchSize=" + cursorBatchSize + ", defaultPagesize="
                + defaultPagesize + ", maxPagesize=" + maxPagesize + ", configurationFileMap=" + mongoSrvConfiguration
                + ", metricsGatheringLevel=" + metricsGatheringLevel
                + '}';
    }

    public String getUri() {
        return this.uri;
    }

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
     * @return the getCollectionCacheEnabled
     */
    public boolean isGetCollectionCacheEnabled() {
        return getCollectionCacheEnabled;
    }

    /**
     * @return the getCollectionCacheSize
     */
    public int getGetCollectionCacheSize() {
        return getCollectionCacheSize;
    }

    /**
     * @return the getCollectionCacheTTL
     */
    public int getGetCollectionCacheTTL() {
        return getCollectionCacheTTL;
    }

    /**
     * @return the getCollectionCacheDocs
     */
    public int getGetCollectionCacheDocs() {
        return getCollectionCacheDocs;
    }

    /**
     * @return the mongoUri
     */
    public ConnectionString getMongoUri() {
        return mongoUri;
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
        return Collections.unmodifiableMap(mongoSrvConfiguration);
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
     * @deprecated will be removed in RH v8.0
     */
    @Deprecated
    public METRICS_GATHERING_LEVEL getMetricsGatheringLevel() {
        return metricsGatheringLevel;
    }

    /**
     * @deprecated will be removed in RH v8.0
     *
     * decides whether metrics are gathered at the given log level or not
     *
     * @param level Metrics Gathering Level
     * @return true if gathering Above Or Equal To Level
     */
    @Deprecated
    public boolean gatheringAboveOrEqualToLevel(METRICS_GATHERING_LEVEL level) {
        return getMetricsGatheringLevel().compareTo(level) >= 0;
    }

    /**
     * @deprecated will be removed in RH v8.0
     */
    @Deprecated
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
