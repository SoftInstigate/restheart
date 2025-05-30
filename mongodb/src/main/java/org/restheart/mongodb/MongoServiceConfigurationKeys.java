/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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

import org.restheart.exchange.ExchangeKeys.ETAG_CHECK_POLICY;
import org.restheart.exchange.ExchangeKeys.REPRESENTATION_FORMAT;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface MongoServiceConfigurationKeys {
    /**
     * default mongo uri mongodb://127.0.0.1
     */
    public static final String DEFAULT_MONGO_URI = "mongodb://127.0.0.1";


    /**
     * default represetation format
     */
    public static final REPRESENTATION_FORMAT DEFAULT_REPRESENTATION_FORMAT = REPRESENTATION_FORMAT.STANDARD;

    /**
     * default db etag check policy
     */
    public static final ETAG_CHECK_POLICY DEFAULT_DB_ETAG_CHECK_POLICY = ETAG_CHECK_POLICY.REQUIRED_FOR_DELETE;

    /**
     * default coll etag check policy
     */
    public static final ETAG_CHECK_POLICY DEFAULT_COLL_ETAG_CHECK_POLICY = ETAG_CHECK_POLICY.REQUIRED_FOR_DELETE;

    /**
     * default doc etag check policy
     */
    public static final ETAG_CHECK_POLICY DEFAULT_DOC_ETAG_CHECK_POLICY = ETAG_CHECK_POLICY.OPTIONAL;

    /**
     * default doc etag check policy
     */
    public static final int DEFAULT_MAX_DOC_ETAG_CHECK_POLICY = 1000;

    /**
     * default value for max-pagesize
     */
    public static final int DEFAULT_MAX_PAGESIZE = 1000;

    /**
     * default value for max-pagesize
     */
    public static final int DEFAULT_DEFAULT_PAGESIZE = 100;

    /**
     * default value for cursor batch size
     */
    public static final int DEFAULT_CURSOR_BATCH_SIZE = 1000;

    /**
     * the key for the local-cache-enabled property.
     */
    public static final String LOCAL_CACHE_ENABLED_KEY = "local-cache-enabled";

    /**
     * the key for the local-cache-ttl property.
     */
    public static final String LOCAL_CACHE_TTL_KEY = "local-cache-ttl";

    /**
     * the key for the schema-cache-enabled property.
     */
    public static final String SCHEMA_CACHE_ENABLED_KEY = "schema-cache-enabled";

    /**
     * the key for the schema-cache-ttl property.
     */
    public static final String SCHEMA_CACHE_TTL_KEY = "schema-cache-ttl";

    /**
     * the key for the query-time-limit property.
     */
    public static final String QUERY_TIME_LIMIT_KEY = "query-time-limit";

    /**
     * the key for the aggregation-time-limit property
     */
    public static final String AGGREGATION_TIME_LIMIT_KEY = "aggregation-time-limit";

    /**
     * The key for enabling check that aggregation variables contains operators.
     */
    public static final String AGGREGATION_CHECK_OPERATORS = "aggregation-check-operators";

    /**
     * the key for the mongo-uri property.
     */
    public static final String MONGO_URI_KEY = "mongo-uri";

    /**
     * the key for the mongo-mounts property.
     */
    public static final String MONGO_MOUNTS_KEY = "mongo-mounts";

    /**
     * the key for the what property.
     */
    public static final String MONGO_MOUNT_WHAT_KEY = "what";

    /**
     * the key for the where property.
     */
    public static final String MONGO_MOUNT_WHERE_KEY = "where";

    /**
     * the default value for the where mongo-mount property.
     */
    public static final String  DEFAULT_MONGO_MOUNT_WHERE = "/";

    /**
     * the default value for the waht mongo-mount property.
     */
    public static final String  DEFAULT_MONGO_MOUNT_WHAT = "/restheart";


    /**
     * the key for the instance-base-url property.
     */
    public static final String INSTANCE_BASE_URL_KEY = "instance-base-url";

    /**
     * the key for the instance-name property.
     */
    public static final String REPRESENTATION_FORMAT_KEY = "default-representation-format";


    /**
     * the key for the get-collection-cache-enabled property.
     */
    public static final String GET_COLLECTION_CACHE_ENABLED_KEY = "get-collection-cache-enabled";

    /**
     * the key for the get-collection-cache-size property.
     */
    public static final String GET_COLLECTION_CACHE_SIZE_KEY = "get-collection-cache-size";

    /**
     * the key for the get-collection-cache-ttl property.
     */
    public static final String GET_COLLECTION_CACHE_TTL_KEY = "get-collection-cache-ttl";

    /**
     * the key for the get-collection-cache-docs property.
     */
    public static final String GET_COLLECTION_CACHE_DOCS_KEY = "get-collection-cache-docs";

    /**
     * the key for the etag-check-policy property.
     */
    public static final String ETAG_CHECK_POLICY_KEY = "etag-check-policy";

    /**
     * the key for the etag-check-policy.db property.
     */
    public static final String ETAG_CHECK_POLICY_DB_KEY = "db";

    /**
     * the key for the etag-check-policy.coll property.
     */
    public static final String ETAG_CHECK_POLICY_COLL_KEY = "coll";

    /**
     * the key for the etag-check-policy.doc property.
     */
    public static final String ETAG_CHECK_POLICY_DOC_KEY = "doc";

    /**
     * The key for specifying the max pagesize
     */
    public static final String MAX_PAGESIZE_KEY = "max-pagesize";

    /**
     * The key for specifying the default pagesize
     */
    public static final String DEFAULT_PAGESIZE_KEY = "default-pagesize";

    /**
     * The key for specifying the cursor batch size
     */
    public static final String CURSOR_BATCH_SIZE_KEY = "cursor-batch-size";
}
