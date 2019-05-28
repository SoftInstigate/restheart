/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart;

import org.restheart.handlers.RequestContext;
import org.restheart.representation.Resource;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public interface ConfigurationKeys {
    /**
     * default mongo uri mongodb://127.0.0.1
     */
    public static final String DEFAULT_MONGO_URI = "mongodb://127.0.0.1";
    public static final String DEFAULT_ROUTE = "127.0.0.1";

    /**
     * default http listener.
     */
    public static final boolean DEFAULT_AJP_LISTENER = true;
    
    /**
     * default ajp host 127.0.0.1.
     */
    public static final String DEFAULT_AJP_HOST = DEFAULT_ROUTE;

    /**
     * default ajp port 8009.
     */
    public static final int DEFAULT_AJP_PORT = 8009;

    /**
     * default http listener.
     */
    public static final boolean DEFAULT_HTTP_LISTENER = false;
    
    /**
     * default http host 127.0.0.1.
     */
    public static final String DEFAULT_HTTP_HOST = DEFAULT_ROUTE;

    /**
     * default http port 8080.
     */
    public static final int DEFAULT_HTTP_PORT = 8080;

    /**
     * default https listener.
     */
    public static final boolean DEFAULT_HTTPS_LISTENER = false;
    
    /**
     * default https host 127.0.0.1.
     */
    public static final String DEFAULT_HTTPS_HOST = DEFAULT_ROUTE;

    /**
     * default https port 4443.
     */
    public static final int DEFAULT_HTTPS_PORT = 4443;

    /**
     * default restheart instance name default
     */
    public static final String DEFAULT_INSTANCE_NAME = "default";

    /**
     * default represetation format
     */
    public static final Resource.REPRESENTATION_FORMAT DEFAULT_REPRESENTATION_FORMAT
            = Resource.REPRESENTATION_FORMAT.STANDARD;

    /**
     * the key for the pluging-args property.
     */
    public static final String PLUGINS_ARGS_KEY = "plugins-args";
    
    /**
     * the key for the plugin enabled property.
     */
    public static final String PLUGIN_ENABLED_KEY = "enabled";
    
    /**
     * default db etag check policy
     */
    public static final RequestContext.ETAG_CHECK_POLICY DEFAULT_DB_ETAG_CHECK_POLICY
            = RequestContext.ETAG_CHECK_POLICY.REQUIRED_FOR_DELETE;

    /**
     * default coll etag check policy
     */
    public static final RequestContext.ETAG_CHECK_POLICY DEFAULT_COLL_ETAG_CHECK_POLICY
            = RequestContext.ETAG_CHECK_POLICY.REQUIRED_FOR_DELETE;

    /**
     * default doc etag check policy
     */
    public static final RequestContext.ETAG_CHECK_POLICY DEFAULT_DOC_ETAG_CHECK_POLICY
            = RequestContext.ETAG_CHECK_POLICY.OPTIONAL;

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
     * the key for the force-gzip-encoding property.
     */
    public static final String FORCE_GZIP_ENCODING_KEY = "force-gzip-encoding";

    /**
     * the key for the direct-buffers property.
     */
    public static final String DIRECT_BUFFERS_KEY = "direct-buffers";

    /**
     * the key for the buffer-size property.
     */
    public static final String BUFFER_SIZE_KEY = "buffer-size";

    /**
     * the key for the worker-threads property.
     */
    public static final String WORKER_THREADS_KEY = "worker-threads";

    /**
     * the key for the io-threads property.
     */
    public static final String IO_THREADS_KEY = "io-threads";

    /**
     * the key for the requests-limit property.
     */
    public static final String REQUESTS_LIMIT_KEY = "requests-limit";

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
     * the key for the enable-log-file property.
     */
    public static final String ENABLE_LOG_FILE_KEY = "enable-log-file";

    /**
     * the key for the enable-log-console property.
     */
    public static final String ENABLE_LOG_CONSOLE_KEY = "enable-log-console";

    /**
     * the key for the log-level property.
     */
    public static final String LOG_LEVEL_KEY = "log-level";

    /**
     * the key for the log-file-path property.
     */
    public static final String LOG_FILE_PATH_KEY = "log-file-path";

    /**
     * the key for the requests-log-tracing-headers property.
     */
    public static final String REQUESTS_LOG_TRACE_HEADERS_KEY = "requests-log-trace-headers";

    /**
     * the key for the implementation-class property.
     */
    public static final String IMPLEMENTATION_CLASS_KEY = "implementation-class";

    /**
     * the key for the idm property.
     */
    public static final String IDM_KEY = "idm";

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
     * the key for the auth-db property.
     */
    public static final String MONGO_AUTH_DB_KEY = "auth-db";

    /**
     * the key for the password property.
     */
    public static final String MONGO_PASSWORD_KEY = "password";

    /**
     * the key for the user property.
     */
    public static final String MONGO_USER_KEY = "user";

    /**
     * the key for the static-resources-mounts property.
     */
    public static final String STATIC_RESOURCES_MOUNTS_KEY = "static-resources-mounts";

    /**
     * the key for the what property.
     */
    public static final String STATIC_RESOURCES_MOUNT_WHAT_KEY = "what";

    /**
     * the key for the where property.
     */
    public static final String STATIC_RESOURCES_MOUNT_WHERE_KEY = "where";

    /**
     * the key for the welcome-file property.
     */
    public static final String STATIC_RESOURCES_MOUNT_WELCOME_FILE_KEY = "welcome-file";

    /**
     * the key for the embedded property.
     */
    public static final String STATIC_RESOURCES_MOUNT_EMBEDDED_KEY = "embedded";

    /**
     * the key for the certpassword property.
     */
    public static final String CERT_PASSWORD_KEY = "certpassword";

    /**
     * the key for the keystore-password property.
     */
    public static final String KEYSTORE_PASSWORD_KEY = "keystore-password";

    /**
     * the key for the keystore-file property.
     */
    public static final String KEYSTORE_FILE_KEY = "keystore-file";

    /**
     * the key for the use-embedded-keystore property.
     */
    public static final String USE_EMBEDDED_KEYSTORE_KEY = "use-embedded-keystore";

    /**
     * the key for the ajp-host property.
     */
    public static final String AJP_HOST_KEY = "ajp-host";

    /**
     * the key for the ajp-port property.
     */
    public static final String AJP_PORT_KEY = "ajp-port";

    /**
     * the key for the ajp-listener property.
     */
    public static final String AJP_LISTENER_KEY = "ajp-listener";

    /**
     * the key for the http-host property.
     */
    public static final String HTTP_HOST_KEY = "http-host";

    /**
     * the key for the http-port property.
     */
    public static final String HTTP_PORT_KEY = "http-port";

    /**
     * the key for http-listener the property.
     */
    public static final String HTTP_LISTENER_KEY = "http-listener";

    /**
     * the key for the https-host property.
     */
    public static final String HTTPS_HOST_KEY = "https-host";

    /**
     * the key for the https-port property.
     */
    public static final String HTTPS_PORT_KEY = "https-port";

    /**
     * the key for the https-listener property.
     */
    public static final String HTTPS_LISTENER = "https-listener";

    /**
     * the key for the instance-name property.
     */
    public static final String INSTANCE_NAME_KEY = "instance-name";

    /**
     * the key for the instance-base-url property.
     */
    public static final String INSTANCE_BASE_URL_KEY = "instance-base-url";

    /**
     * the key for the instance-name property.
     */
    public static final String REPRESENTATION_FORMAT_KEY = "default-representation-format";

    /**
     * the key for the eager-cursor-allocation-pool-size property.
     */
    public static final String EAGER_POOL_SIZE = "eager-cursor-allocation-pool-size";

    /**
     * the key for the eager-cursor-allocation-linear-slice-width property.
     */
    public static final String EAGER_LINEAR_SLICE_WIDHT = "eager-cursor-allocation-linear-slice-width";

    /**
     * the key for the eager-cursor-allocation-linear-slice-delta property.
     */
    public static final String EAGER_LINEAR_SLICE_DELTA = "eager-cursor-allocation-linear-slice-delta";

    /**
     * the key for the eager-cursor-allocation-linear-slice-heights property.
     */
    public static final String EAGER_LINEAR_HEIGHTS = "eager-cursor-allocation-linear-slice-heights";

    /**
     * the key for the eager-cursor-allocation-random-slice-min-width property.
     */
    public static final String EAGER_RND_SLICE_MIN_WIDHT = "eager-cursor-allocation-random-slice-min-width";

    /**
     * the key for the eager-cursor-allocation-random-slice-max-cursors
     * property.
     */
    public static final String EAGER_RND_MAX_CURSORS = "eager-cursor-allocation-random-max-cursors";

    /**
     * the key for the auth-token-enabled property.
     */
    public static final String AUTH_TOKEN_ENABLED = "auth-token-enabled";

    /**
     * the key for the auth-token-ttl property.
     */
    public static final String AUTH_TOKEN_TTL = "auth-token-ttl";

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
     * Force http requests logging even if DEBUG is not set
     */
    public static final String LOG_REQUESTS_LEVEL_KEY = "requests-log-level";

    /**
     * Set metrics gathering level (can be ALL, COLLECTION, DATABASE, ROOT,
     * OFF), gradually gathering less specific metrics. Every level contain the
     * upper level as well.
     */
    public static final String METRICS_GATHERING_LEVEL_KEY = "metrics-gathering-level";

    /**
     * The key for enabling the Ansi console (for logging with colors)
     */
    public static final String ANSI_CONSOLE_KEY = "ansi-console";

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

    /**
     * The key to allow unescaped chars in URL
     */
    public static final String ALLOW_UNESCAPED_CHARS_IN_URL = "allow-unescaped-characters-in-url";
}
