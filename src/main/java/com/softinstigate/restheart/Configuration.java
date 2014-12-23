/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
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
package com.softinstigate.restheart;

import ch.qos.logback.classic.Level;
import com.softinstigate.restheart.utils.URLUtilis;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Utility class to help dealing with the restheart configuration file.
 *
 * @author Andrea Di Cesare
 */
public class Configuration {

    /**
     * the restheart version
     */
    public static final String RESTHEART_VERSION = Configuration.class.getPackage().getImplementationVersion();

    /**
     * URL pointing to the online documentation specific for this version.
     */
    public static final String RESTHEART_ONLINE_DOC_URL = "http://www.restheart.org/docs/v0.9";

    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    private final boolean httpsListener;
    private final int httpsPort;
    private final String httpsHost;

    private final boolean httpListener;
    private final int httpPort;
    private final String httpHost;

    private final boolean ajpListener;
    private final int ajpPort;
    private final String ajpHost;

    private final boolean useEmbeddedKeystore;
    private final String keystoreFile;
    private final String keystorePassword;
    private final String certPassword;

    private final List<Map<String, Object>> mongoServers;
    private final List<Map<String, Object>> mongoCredentials;
    private final List<Map<String, Object>> mongoMounts;

    private final List<Map<String, Object>> staticResourcesMounts;

    private final List<Map<String, Object>> applicationLogicMounts;

    private final String idmImpl;
    private final Map<String, Object> idmArgs;
    private final String amImpl;
    private final Map<String, Object> amArgs;

    private final String logFilePath;
    private final Level logLevel;
    private final boolean logToConsole;
    private final boolean logToFile;

    private final boolean localCacheEnabled;
    private final long localCacheTtl;

    private final int requestsLimit;

    private final int ioThreads;
    private final int workerThreads;
    private final int bufferSize;
    private final int buffersPerRegion;
    private final boolean directBuffers;

    private final boolean forceGzipEncoding;

    /**
     * default mongodb port 27017.
     */
    public static final int DEFAULT_MONGO_PORT = 27017;

    /**
     * default mongodb host 127.0.0.1.
     */
    public static final String DEFAULT_MONGO_HOST = "127.0.0.1";

    /**
     * default ajp host 0.0.0.0.
     */
    public static final String DEFAULT_AJP_HOST = "0.0.0.0";

    /**
     * default ajp port 8009.
     */
    public static final int DEFAULT_AJP_PORT = 8009;

    /**
     * default http host 0.0.0.0.
     */
    public static final String DEFAULT_HTTP_HOST = "0.0.0.0";

    /**
     * default http port 8080.
     */
    public static final int DEFAULT_HTTP_PORT = 8080;

    /**
     * default https host 0.0.0.0.
     */
    public static final String DEFAULT_HTTPS_HOST = "0.0.0.0";

    /**
     * default https port 4443.
     */
    public static final int DEFAULT_HTTPS_PORT = 4443;

    /**
     * default am implementation class.
     */
    public static final String DEFAULT_AM_IMPLEMENTATION_CLASS = "com.softinstigate.restheart.security.impl.SimpleAccessManager";

    /**
     * default idm implementation class.
     */
    public static final String DEFAULT_IDM_IMPLEMENTATION_CLASS = "com.softinstigate.restheart.security.impl.SimpleFileIdentityManager";

    /**
     * the key for the local-cache-enabled property.
     */
    public static final String LOCAL_CACHE_ENABLED_KEY = "local-cache-enabled";

    /**
     * the key for the local-cache-ttl property.
     */
    public static final String LOCAL_CACHE_TTL_KEY = "local-cache-ttl";

    /**
     * the key for the force-gzip-encoding property.
     */
    public static final String FORCE_GZIP_ENCODING_KEY = "force-gzip-encoding";

    /**
     * the key for the direct-buffers property.
     */
    public static final String DIRECT_BUFFERS_KEY = "direct-buffers";

    /**
     * the key for the buffers-per-region property.
     */
    public static final String BUFFERS_PER_REGION_KEY = "buffers-per-region";

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
     * the key for the implementation-class property.
     */
    public static final String IMPLEMENTATION_CLASS_KEY = "implementation-class";

    /**
     * the key for the access-manager property.
     */
    public static final String ACCESS_MANAGER_KEY = "access-manager";

    /**
     * the key for the idm property.
     */
    public static final String IDM_KEY = "idm";

    /**
     * the key for the mongo-servers property.
     */
    public static final String MONGO_SERVERS_KEY = "mongo-servers";

    /**
     * the key for the mongo-credentials property.
     */
    public static final String MONGO_CREDENTIALS_KEY = "mongo-credentials";

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
     * the key for the port property.
     */
    public static final String MONGO_PORT_KEY = "port";

    /**
     * the key for the host property.
     */
    public static final String MONGO_HOST_KEY = "host";

    /**
     * the key for the application-logic-mounts property.
     */
    public static final String APPLICATION_LOGIC_MOUNTS_KEY = "application-logic-mounts";

    /**
     * the key for the args property.
     */
    public static final String APPLICATION_LOGIC_MOUNT_ARGS_KEY = "args";

    /**
     * the key for the what property.
     */
    public static final String APPLICATION_LOGIC_MOUNT_WHAT_KEY = "what";

    /**
     * the key for the where property.
     */
    public static final String APPLICATION_LOGIC_MOUNT_WHERE_KEY = "where";

    /**
     * the key for the secured property.
     */
    public static final String APPLICATION_LOGIC_MOUNT_SECURED_KEY = "secured";

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
     * the key for the secured property.
     */
    public static final String STATIC_RESOURCES_MOUNT_SECURED_KEY = "secured";

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
    private static final String HTTPS_HOST_KEY = "https-host";

    /**
     * the key for the https-port property.
     */
    private static final String HTTPS_PORT_KEY = "https-port";

    /**
     * the key for the https-listener property.
     */
    public static final String HTTPS_LISTENER = "https-listener";

    /**
     * Creates a new instance of ErrorHandler with defaults values.
     */
    public Configuration() {
        httpsListener = true;
        httpsPort = DEFAULT_HTTPS_PORT;
        httpsHost = DEFAULT_HTTPS_HOST;

        httpListener = true;
        httpPort = DEFAULT_HTTP_PORT;
        httpHost = DEFAULT_HTTP_HOST;

        ajpListener = false;
        ajpPort = DEFAULT_AJP_PORT;
        ajpHost = DEFAULT_AJP_HOST;

        useEmbeddedKeystore = true;
        keystoreFile = null;
        keystorePassword = null;
        certPassword = null;

        mongoServers = new ArrayList<>();
        Map<String, Object> defaultMongoServer = new HashMap<>();
        defaultMongoServer.put(MONGO_HOST_KEY, DEFAULT_MONGO_HOST);
        defaultMongoServer.put(MONGO_PORT_KEY, DEFAULT_MONGO_PORT);
        mongoServers.add(defaultMongoServer);

        mongoCredentials = null;

        mongoMounts = new ArrayList<>();
        Map<String, Object> defaultMongoMounts = new HashMap<>();
        defaultMongoMounts.put(MONGO_MOUNT_WHAT_KEY, "*");
        defaultMongoMounts.put(MONGO_MOUNT_WHERE_KEY, "/");
        mongoMounts.add(defaultMongoMounts);

        applicationLogicMounts = new ArrayList<>();

        staticResourcesMounts = new ArrayList<>();

        HashMap<String, Object> browserStaticResourcesMountArgs = new HashMap<>();

        browserStaticResourcesMountArgs.put(STATIC_RESOURCES_MOUNT_WHAT_KEY, "browser");
        browserStaticResourcesMountArgs.put(STATIC_RESOURCES_MOUNT_WHERE_KEY, "/browser");
        browserStaticResourcesMountArgs.put(STATIC_RESOURCES_MOUNT_WELCOME_FILE_KEY, "browser.html");
        browserStaticResourcesMountArgs.put(STATIC_RESOURCES_MOUNT_SECURED_KEY, false);
        browserStaticResourcesMountArgs.put(STATIC_RESOURCES_MOUNT_EMBEDDED_KEY, true);

        staticResourcesMounts.add(browserStaticResourcesMountArgs);

        idmImpl = null;
        idmArgs = null;

        amImpl = null;
        amArgs = null;

        logFilePath = URLUtilis.removeTrailingSlashes(System.getProperty("java.io.tmpdir"))
                .concat(File.separator + "restheart.log");
        logToConsole = true;
        logToFile = true;
        logLevel = Level.INFO;

        localCacheEnabled = true;
        localCacheTtl = 1000;

        requestsLimit = 100;
        ioThreads = 2;
        workerThreads = 32;
        bufferSize = 16384;
        buffersPerRegion = 20;
        directBuffers = true;

        forceGzipEncoding = false;
    }

    /**
     * Creates a new instance of ErrorHandler from the configuration file For
     * any missing property the default value is used.
     *
     * @param confFilePath the path of the configuration file
     */
    public Configuration(final String confFilePath) {
        Yaml yaml = new Yaml();

        Map<String, Object> conf = null;

        FileInputStream fis = null;

        try {
            fis = new FileInputStream(new File(confFilePath));
            conf = (Map<String, Object>) yaml.load(fis);
        } catch (FileNotFoundException fnef) {
            LOGGER.error("configuration file not found. starting with default parameters.");
            conf = null;
        } catch (Throwable t) {
            LOGGER.error("wrong configuration file format. starting with default parameters.", t);
            conf = null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ioe) {
                    LOGGER.warn("error closing the configuration file", ioe);
                }
            }
        }

        httpsListener = getAsBooleanOrDefault(conf, HTTPS_LISTENER, true);
        httpsPort = getAsIntegerOrDefault(conf, HTTPS_PORT_KEY, DEFAULT_HTTPS_PORT);
        httpsHost = getAsStringOrDefault(conf, HTTPS_HOST_KEY, DEFAULT_HTTPS_HOST);

        httpListener = getAsBooleanOrDefault(conf, HTTP_LISTENER_KEY, false);
        httpPort = getAsIntegerOrDefault(conf, HTTP_PORT_KEY, DEFAULT_HTTP_PORT);
        httpHost = getAsStringOrDefault(conf, HTTP_HOST_KEY, DEFAULT_HTTP_HOST);

        ajpListener = getAsBooleanOrDefault(conf, AJP_LISTENER_KEY, false);
        ajpPort = getAsIntegerOrDefault(conf, AJP_PORT_KEY, DEFAULT_AJP_PORT);
        ajpHost = getAsStringOrDefault(conf, AJP_HOST_KEY, DEFAULT_AJP_HOST);

        useEmbeddedKeystore = getAsBooleanOrDefault(conf, USE_EMBEDDED_KEYSTORE_KEY, true);
        keystoreFile = getAsStringOrDefault(conf, KEYSTORE_FILE_KEY, null);
        keystorePassword = getAsStringOrDefault(conf, KEYSTORE_PASSWORD_KEY, null);
        certPassword = getAsStringOrDefault(conf, CERT_PASSWORD_KEY, null);

        List<Map<String, Object>> mongoServersDefault = new ArrayList<>();
        Map<String, Object> defaultMongoServer = new HashMap<>();
        defaultMongoServer.put(MONGO_HOST_KEY, "127.0.0.1");
        defaultMongoServer.put(MONGO_PORT_KEY, 27017);
        mongoServersDefault.add(defaultMongoServer);

        mongoServers = getAsListOfMaps(conf, MONGO_SERVERS_KEY, mongoServersDefault);
        mongoCredentials = getAsListOfMaps(conf, MONGO_CREDENTIALS_KEY, null);

        List<Map<String, Object>> mongoMountsDefault = new ArrayList<>();
        Map<String, Object> defaultMongoMounts = new HashMap<>();
        defaultMongoMounts.put(MONGO_MOUNT_WHAT_KEY, "*");
        defaultMongoMounts.put(MONGO_MOUNT_WHERE_KEY, "/");
        mongoMountsDefault.add(defaultMongoMounts);

        mongoMounts = getAsListOfMaps(conf, MONGO_MOUNTS_KEY, mongoMountsDefault);

        applicationLogicMounts = getAsListOfMaps(conf, APPLICATION_LOGIC_MOUNTS_KEY, new ArrayList<>());

        staticResourcesMounts = getAsListOfMaps(conf, STATIC_RESOURCES_MOUNTS_KEY, new ArrayList<>());

        Map<String, Object> idm = getAsMap(conf, IDM_KEY);
        Map<String, Object> am = getAsMap(conf, ACCESS_MANAGER_KEY);

        idmImpl = getAsStringOrDefault(idm, IMPLEMENTATION_CLASS_KEY, DEFAULT_IDM_IMPLEMENTATION_CLASS);
        idmArgs = idm;

        amImpl = getAsStringOrDefault(am, IMPLEMENTATION_CLASS_KEY, DEFAULT_AM_IMPLEMENTATION_CLASS);
        amArgs = am;

        logFilePath = getAsStringOrDefault(conf, LOG_FILE_PATH_KEY,
                URLUtilis.removeTrailingSlashes(System.getProperty("java.io.tmpdir"))
                .concat(File.separator + "restheart.log"));
        String _logLevel = getAsStringOrDefault(conf, LOG_LEVEL_KEY, "WARN");
        logToConsole = getAsBooleanOrDefault(conf, ENABLE_LOG_CONSOLE_KEY, true);
        logToFile = getAsBooleanOrDefault(conf, ENABLE_LOG_FILE_KEY, true);

        Level level;

        try {
            level = Level.valueOf(_logLevel);
        } catch (Exception e) {
            LOGGER.info("wrong value for parameter {}: {}. using its default value {}", "log-level", _logLevel, "WARN");
            level = Level.WARN;
        }

        logLevel = level;

        requestsLimit = getAsIntegerOrDefault(conf, REQUESTS_LIMIT_KEY, 100);

        localCacheEnabled = getAsBooleanOrDefault(conf, LOCAL_CACHE_ENABLED_KEY, true);
        localCacheTtl = getAsLongOrDefault(conf, LOCAL_CACHE_TTL_KEY, (long) 1000);

        ioThreads = getAsIntegerOrDefault(conf, IO_THREADS_KEY, 2);
        workerThreads = getAsIntegerOrDefault(conf, WORKER_THREADS_KEY, 32);
        bufferSize = getAsIntegerOrDefault(conf, BUFFER_SIZE_KEY, 16384);
        buffersPerRegion = getAsIntegerOrDefault(conf, BUFFERS_PER_REGION_KEY, 20);
        directBuffers = getAsBooleanOrDefault(conf, DIRECT_BUFFERS_KEY, true);

        forceGzipEncoding = getAsBooleanOrDefault(conf, FORCE_GZIP_ENCODING_KEY, false);
    }

    private static List<Map<String, Object>> getAsListOfMaps(final Map<String, Object> conf, final String key, final List<Map<String, Object>> defaultValue) {
        if (conf == null) {
            LOGGER.warn("parameters group {} not specified in the configuration file. using its default value {}", key, defaultValue);

            return defaultValue;
        }

        Object o = conf.get(key);

        if (o instanceof List) {
            return (List<Map<String, Object>>) o;
        } else {
            LOGGER.warn("parameters group {} not specified in the configuration file, using its default value {}", key, defaultValue);
            return defaultValue;
        }
    }

    private static Map<String, Object> getAsMap(final Map<String, Object> conf, final String key) {
        if (conf == null) {
            LOGGER.warn("parameters group {} not specified in the configuration file.", key);
            return null;
        }

        Object o = conf.get(key);

        if (o instanceof Map) {
            return (Map<String, Object>) o;
        } else {
            LOGGER.warn("parameters group {} not specified in the configuration file.", key);
            return null;
        }
    }

    private static Boolean getAsBooleanOrDefault(final Map<String, Object> conf, final String key, final Boolean defaultValue) {
        if (conf == null) {
            LOGGER.error("tried to get paramenter {} from a null configuration map. using its default value {}", key, defaultValue);
            return defaultValue;
        }

        Object o = conf.get(key);

        if (o == null) {
            if (defaultValue != null) // if default value is null there is no default value actually
            {
                LOGGER.info("parameter {} not specified in the configuration file. using its default value {}", key, defaultValue);
            }
            return defaultValue;
        } else if (o instanceof Boolean) {
            LOGGER.debug("paramenter {} set to {}", key, o);
            return (Boolean) o;
        } else {
            LOGGER.info("wrong value for parameter {}: {}. using its default value {}", key, o, defaultValue);
            return defaultValue;
        }
    }

    private static String getAsStringOrDefault(final Map<String, Object> conf, final String key, final String defaultValue) {

        if (conf == null || conf.get(key) == null) {
            if (defaultValue != null) // if default value is null there is no default value actually
            {
                LOGGER.info("parameter {} not specified in the configuration file. using its default value {}", key, defaultValue);
            }
            return defaultValue;
        } else if (conf.get(key) instanceof String) {
            LOGGER.debug("paramenter {} set to {}", key, conf.get(key));
            return (String) conf.get(key);
        } else {
            LOGGER.info("wrong value for parameter {}: {}. using its default value {}", key, conf.get(key), defaultValue);
            return defaultValue;
        }
    }

    private static Integer getAsIntegerOrDefault(final Map<String, Object> conf, final String key, final Integer defaultValue) {
        if (conf == null || conf.get(key) == null) {
            if (defaultValue != null) // if default value is null there is no default value actually
            {
                LOGGER.info("parameter {} not specified in the configuration file. using its default value {}", key, defaultValue);
            }
            return defaultValue;
        } else if (conf.get(key) instanceof Integer) {
            LOGGER.debug("paramenter {} set to {}", key, conf.get(key));
            return (Integer) conf.get(key);
        } else {
            LOGGER.info("wrong value for parameter {}: {}. using its default value {}", key, conf.get(key), defaultValue);
            return defaultValue;
        }
    }

    private static Long getAsLongOrDefault(final Map<String, Object> conf, final String key, final Long defaultValue) {
        if (conf == null || conf.get(key) == null) {
            if (defaultValue != null) // if default value is null there is no default value actually
            {
                LOGGER.info("parameter {} not specified in the configuration file. using its default value {}", key, defaultValue);
            }
            return defaultValue;
        } else if (conf.get(key) instanceof Number) {
            LOGGER.debug("paramenter {} set to {}", key, conf.get(key));
            try {
                return Long.parseLong(conf.get(key).toString());
            } catch (NumberFormatException nfe) {
                LOGGER.info("wrong value for parameter {}: {}. using its default value {}", key, conf.get(key), defaultValue);
                return defaultValue;
            }
        } else {
            LOGGER.info("wrong value for parameter {}: {}. using its default value {}", key, conf.get(key), defaultValue);
            return defaultValue;
        }
    }

    /**
     * @return the httpsListener
     */
    public final boolean isHttpsListener() {
        return httpsListener;
    }

    /**
     * @return the httpsPort
     */
    public final int getHttpsPort() {
        return httpsPort;
    }

    /**
     * @return the httpsHost
     */
    public final String getHttpsHost() {
        return httpsHost;
    }

    /**
     * @return the httpListener
     */
    public final boolean isHttpListener() {
        return httpListener;
    }

    /**
     * @return the httpPort
     */
    public final int getHttpPort() {
        return httpPort;
    }

    /**
     * @return the httpHost
     */
    public final String getHttpHost() {
        return httpHost;
    }

    /**
     * @return the ajpListener
     */
    public final boolean isAjpListener() {
        return ajpListener;
    }

    /**
     * @return the ajpPort
     */
    public final int getAjpPort() {
        return ajpPort;
    }

    /**
     * @return the ajpHost
     */
    public final String getAjpHost() {
        return ajpHost;
    }

    /**
     * @return the useEmbeddedKeystore
     */
    public final boolean isUseEmbeddedKeystore() {
        return useEmbeddedKeystore;
    }

    /**
     * @return the keystoreFile
     */
    public final String getKeystoreFile() {
        return keystoreFile;
    }

    /**
     * @return the keystorePassword
     */
    public final String getKeystorePassword() {
        return keystorePassword;
    }

    /**
     * @return the certPassword
     */
    public final String getCertPassword() {
        return certPassword;
    }

    /**
     * @return the logFilePath
     */
    public final String getLogFilePath() {
        return logFilePath;
    }

    /**
     * @return the logLevel
     */
    public final Level getLogLevel() {
        return logLevel;
    }

    /**
     * @return the logToConsole
     */
    public final boolean isLogToConsole() {
        return logToConsole;
    }

    /**
     * @return the logToFile
     */
    public final boolean isLogToFile() {
        return logToFile;
    }

    /**
     * @return the ioThreads
     */
    public final int getIoThreads() {
        return ioThreads;
    }

    /**
     * @return the workerThreads
     */
    public final int getWorkerThreads() {
        return workerThreads;
    }

    /**
     * @return the bufferSize
     */
    public final int getBufferSize() {
        return bufferSize;
    }

    /**
     * @return the buffersPerRegion
     */
    public final int getBuffersPerRegion() {
        return buffersPerRegion;
    }

    /**
     * @return the directBuffers
     */
    public final boolean isDirectBuffers() {
        return directBuffers;
    }

    /**
     * @return the forceGzipEncoding
     */
    public final boolean isForceGzipEncoding() {
        return forceGzipEncoding;
    }

    /**
     * @return the idmImpl
     */
    public final String getIdmImpl() {
        return idmImpl;
    }

    /**
     * @return the idmArgs
     */
    public final Map<String, Object> getIdmArgs() {
        return idmArgs;
    }

    /**
     * @return the amImpl
     */
    public final String getAmImpl() {
        return amImpl;
    }

    /**
     * @return the amArgs
     */
    public final Map<String, Object> getAmArgs() {
        return amArgs;
    }

    /**
     * @return the requestsLimit
     */
    public final int getRequestLimit() {
        return getRequestsLimit();
    }

    /**
     * @return the mongoServers
     */
    public final List<Map<String, Object>> getMongoServers() {
        return mongoServers;
    }

    /**
     * @return the mongoCredentials
     */
    public final List<Map<String, Object>> getMongoCredentials() {
        return mongoCredentials;
    }

    /**
     * @return the mongoMountsDefault
     */
    public final List<Map<String, Object>> getMongoMounts() {
        return mongoMounts;
    }

    /**
     * @return the localCacheEnabled
     */
    public final boolean isLocalCacheEnabled() {
        return localCacheEnabled;
    }

    /**
     * @return the localCacheTtl
     */
    public final long getLocalCacheTtl() {
        return localCacheTtl;
    }

    /**
     * @return the requestsLimit
     */
    public final int getRequestsLimit() {
        return requestsLimit;
    }

    /**
     * @return the applicationLogicMounts
     */
    public final List<Map<String, Object>> getApplicationLogicMounts() {
        return applicationLogicMounts;
    }

    /**
     * @return the staticResourcesMounts
     */
    public final List<Map<String, Object>> getStaticResourcesMounts() {
        return staticResourcesMounts;
    }
}
