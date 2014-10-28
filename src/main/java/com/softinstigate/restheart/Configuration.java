/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart;

import ch.qos.logback.classic.Level;
import com.softinstigate.restheart.utils.URLUtilis;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author uji
 */
public class Configuration
{
    public static String DOC_Path = "http://www.restheart.org/docs/v0.9";
    
    private static final Logger logger = LoggerFactory.getLogger(Configuration.class);

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

    public static final String LOCAL_CACHE_ENABLED = "local-cache-enabled";
    public static final String LOCAL_CACHE_TTL = "local-cache-ttl";
    
    public static final String FORCE_GZIP_ENCODING = "force-gzip-encoding";
    public static final String DIRECT_BUFFERS = "direct-buffers";
    public static final String BUFFERS_PER_REGION = "buffers-per-region";
    public static final String BUFFER_SIZE = "buffer-size";
    public static final String WORKER_THREADS = "worker-threads";
    public static final String IO_THREADS = "io-threads";
    public static final String REQUESTS_LIMIT = "requests-limit";
    public static final String ENABLE_LOG_FILE = "enable-log-file";
    public static final String ENABLE_LOG_CONSOLE = "enable-log-console";
    public static final String LOG_LEVEL = "log-level";
    public static final String LOG_FILE_PATH = "log-file-path";
    public static final String IMPLEMENTATION_CLASS = "implementation-class";
    public static final String ACCESS_MANAGER = "access-manager";
    public static final String IDM = "idm";
    
    public static final String MONGO_SERVERS = "mongo-servers";
    public static final String MONGO_CREDENTIALS = "mongo-credentials";
    public static final String MONGO_MOUNTS = "mongo-mounts";
    public static final String MONGO_MOUNT_WHAT = "what";
    public static final String MONGO_MOUNT_WHERE = "where";
    public static final String MONGO_AUTH_DB = "auth-db";
    public static final String MONGO_PASSWORD = "password";
    public static final String MONGO_USER = "user";
    public static final String MONGO_PORT = "port";
    public static final String MONGO_HOST = "host";
    
    public static final String APPLICATION_LOGIC_MOUNTS = "application-logic-mounts";
    public static final String APPLICATION_LOGIC_MOUNT_WHAT = "what";
    public static final String APPLICATION_LOGIC_MOUNT_ARGS = "args";
    public static final String APPLICATION_LOGIC_MOUNT_WHERE = "where";
    public static final String APPLICATION_LOGIC_MOUNT_SECURED = "secured";

    public static final String CERT_PASSWORD = "certpassword";
    public static final String KEYSTORE_PASSWORD = "keystore-password";
    public static final String KEYSTORE_FILE = "keystore-file";
    public static final String USE_EMBEDDED_KEYSTORE = "use-embedded-keystore";
    public static final String AJP_HOST = "ajp-host";
    public static final String AJP_PORT = "ajp-port";
    public static final String AJP_LISTENER = "ajp-listener";
    public static final String HTTP_HOST = "http-host";
    public static final String HTTP_PORT = "http-port";
    public static final String HTTP_LISTENER = "http-listener";
    public static final String HTTPS_HOST = "https-host";
    public static final String HTTPS_PORT = "https-port";
    public static final String HTTPS_LISTENER = "https-listener";

    public Configuration()
    {
        httpsListener = true;
        httpsPort = 4443;
        httpsHost = "0.0.0.0";

        httpListener = true;
        httpPort = 8080;
        httpHost = "0.0.0.0";

        ajpListener = false;
        ajpPort = 8009;
        ajpHost = "0.0.0.0";

        useEmbeddedKeystore = true;
        keystoreFile = null;
        keystorePassword = null;
        certPassword = null;

        mongoServers = new ArrayList<>();
        Map<String, Object> defaultMongoServer = new HashMap<>();
        defaultMongoServer.put(MONGO_HOST, "127.0.0.1");
        defaultMongoServer.put(MONGO_PORT, 27017);
        mongoServers.add(defaultMongoServer);

        mongoCredentials = null;

        mongoMounts = new ArrayList<>();
        Map<String, Object> defaultMongoMounts = new HashMap<>();
        defaultMongoMounts.put(MONGO_MOUNT_WHAT, "*");
        defaultMongoMounts.put(MONGO_MOUNT_WHERE, "/");
        mongoMounts.add(defaultMongoMounts);
        
        applicationLogicMounts = new ArrayList<>();

        idmImpl = null;
        idmArgs = null;

        amImpl = null;
        amArgs = null;

        logFilePath = URLUtilis.removeTrailingSlashes(System.getProperty("java.io.tmpdir")) + File.separator + "restheart.log";
        logToConsole = true;
        logToFile = true;
        logLevel = Level.INFO;

        localCacheEnabled = false;
        localCacheTtl = 1000;
        
        requestsLimit = 100;
        ioThreads = 2;
        workerThreads = 32;
        bufferSize = 16384;
        buffersPerRegion = 20;
        directBuffers = true;

        forceGzipEncoding = false;
    }

    public Configuration(String confFilePath)
    {
        Yaml yaml = new Yaml();

        Map<String, Object> conf = null;

        try
        {
            conf = (Map<String, Object>) yaml.load(new FileInputStream(new File(confFilePath)));
        }
        catch (FileNotFoundException fnef)
        {
            logger.error("configuration file not found. starting with default parameters.");
            conf = null;
        }
        catch (Throwable t)
        {
            logger.error("wrong configuration file format. starting with default parameters.", t);
            conf = null;
        }

        if (conf == null)
        {
            httpsListener = true;
            httpsPort = 8443;
            httpsHost = "0.0.0.0";

            httpListener = true;
            httpPort = 8080;
            httpHost = "0.0.0.0";

            ajpListener = false;
            ajpPort = 8009;
            ajpHost = "0.0.0.0";

            useEmbeddedKeystore = true;
            keystoreFile = null;
            keystorePassword = null;
            certPassword = null;

            mongoServers = new ArrayList<>();
            Map<String, Object> defaultMongoServer = new HashMap<>();
            defaultMongoServer.put(MONGO_HOST, "127.0.0.1");
            defaultMongoServer.put(MONGO_PORT, 27017);
            mongoServers.add(defaultMongoServer);
            
            mongoMounts = new ArrayList<>();
            Map<String, Object> defaultMongoMounts = new HashMap<>();
            defaultMongoMounts.put(MONGO_MOUNT_WHAT, "*");
            defaultMongoMounts.put(MONGO_MOUNT_WHERE, "/");
            mongoMounts.add(defaultMongoMounts);
            
            applicationLogicMounts = new ArrayList<>();

            mongoCredentials = null;

            idmImpl = null;
            idmArgs = null;

            amImpl = null;
            amArgs = null;

            logFilePath = URLUtilis.removeTrailingSlashes(System.getProperty("java.io.tmpdir")) + File.separator + "restheart.log";
            logToConsole = true;
            logToFile = true;
            logLevel = Level.INFO;

            localCacheEnabled = false;
            localCacheTtl = 1000;
            
            requestsLimit = 100;
            
            ioThreads = 2;
            workerThreads = 32;
            bufferSize = 16384;
            buffersPerRegion = 20;
            directBuffers = true;

            forceGzipEncoding = false;
        }
        else
        {
            httpsListener = getAsBooleanOrDefault(conf, HTTPS_LISTENER, true);
            httpsPort = getAsIntegerOrDefault(conf, HTTPS_PORT, 8443);
            httpsHost = getAsStringOrDefault(conf, HTTPS_HOST, "0.0.0.0");

            httpListener = getAsBooleanOrDefault(conf, HTTP_LISTENER, false);
            httpPort = getAsIntegerOrDefault(conf, HTTP_PORT, 8080);
            httpHost = getAsStringOrDefault(conf, HTTP_HOST, "0.0.0.0");

            ajpListener = getAsBooleanOrDefault(conf, AJP_LISTENER, false);
            ajpPort = getAsIntegerOrDefault(conf, AJP_PORT, 8009);
            ajpHost = getAsStringOrDefault(conf, AJP_HOST, "0.0.0.0");

            useEmbeddedKeystore = getAsBooleanOrDefault(conf, USE_EMBEDDED_KEYSTORE, true);
            keystoreFile = getAsStringOrDefault(conf, KEYSTORE_FILE, null);
            keystorePassword = getAsStringOrDefault(conf, KEYSTORE_PASSWORD, null);
            certPassword = getAsStringOrDefault(conf, CERT_PASSWORD, null);

            List<Map<String, Object>> mongoServersDefault = new ArrayList<>();
            Map<String, Object> defaultMongoServer = new HashMap<>();
            defaultMongoServer.put(MONGO_HOST, "127.0.0.1");
            defaultMongoServer.put(MONGO_PORT, 27017);
            mongoServersDefault.add(defaultMongoServer);

            mongoServers = getAsListOfMaps(conf, MONGO_SERVERS, mongoServersDefault);
            mongoCredentials = getAsListOfMaps(conf, MONGO_CREDENTIALS, null);
            
            List<Map<String, Object>> mongoMountsDefault = new ArrayList<>();
            Map<String, Object> defaultMongoMounts = new HashMap<>();
            defaultMongoMounts.put(MONGO_MOUNT_WHAT, "*");
            defaultMongoMounts.put(MONGO_MOUNT_WHERE, "/");
            mongoMountsDefault.add(defaultMongoMounts);
            
            mongoMounts = getAsListOfMaps(conf, MONGO_MOUNTS, mongoMountsDefault);
            
            applicationLogicMounts = getAsListOfMaps(conf, APPLICATION_LOGIC_MOUNTS, new ArrayList<>());

            Map<String, Object> idm = getAsMap(conf, IDM);
            Map<String, Object> am = getAsMap(conf, ACCESS_MANAGER);

            idmImpl = getAsStringOrDefault(idm, IMPLEMENTATION_CLASS, "com.softinstigate.restheart.security.impl.SimpleFileIdentityManager");
            idmArgs = idm;

            amImpl = getAsStringOrDefault(am, IMPLEMENTATION_CLASS, "com.softinstigate.restheart.security.impl.SimpleAccessManager");
            amArgs = am;

            logFilePath = getAsStringOrDefault(conf, LOG_FILE_PATH, URLUtilis.removeTrailingSlashes(System.getProperty("java.io.tmpdir")) + File.separator + "restheart.log");
            String _logLevel = getAsStringOrDefault(conf, LOG_LEVEL, "WARN");
            logToConsole = getAsBooleanOrDefault(conf, ENABLE_LOG_CONSOLE, true);
            logToFile = getAsBooleanOrDefault(conf, ENABLE_LOG_FILE, true);

            Level level;

            try
            {
                level = Level.valueOf(_logLevel);
            }
            catch (Exception e)
            {
                logger.info("wrong value for parameter {}: {}. using its default value {}", "log-level", _logLevel, "WARN");
                level = Level.WARN;
            }

            logLevel = level;

            requestsLimit = getAsIntegerOrDefault(conf, REQUESTS_LIMIT, 100);
            
            localCacheEnabled = getAsBooleanOrDefault(conf, LOCAL_CACHE_ENABLED, false);
            localCacheTtl = getAsLongOrDefault(conf, LOCAL_CACHE_TTL, (long)1000);

            ioThreads = getAsIntegerOrDefault(conf, IO_THREADS, 2);
            workerThreads = getAsIntegerOrDefault(conf, WORKER_THREADS, 32);
            bufferSize = getAsIntegerOrDefault(conf, BUFFER_SIZE, 16384);
            buffersPerRegion = getAsIntegerOrDefault(conf, BUFFERS_PER_REGION, 20);
            directBuffers = getAsBooleanOrDefault(conf, DIRECT_BUFFERS, true);

            forceGzipEncoding = getAsBooleanOrDefault(conf, FORCE_GZIP_ENCODING, false);
        }
    }

    private static List<Map<String, Object>> getAsListOfMaps(Map<String, Object> conf, String key, List<Map<String, Object>> defaultValue)
    {
        if (conf == null)
        {
            logger.warn("parameters group {} not specified in the configuration file. using its default value {}", key, defaultValue);

            return defaultValue;
        }

        Object o = conf.get(key);

        if (o instanceof List)
        {
            return (List<Map<String, Object>>) o;
        }
        else
        {
            logger.warn("parameters group {} not specified in the configuration file, using its default value {}", key, defaultValue);
            return defaultValue;
        }
    }

    private static Map<String, Object> getAsMap(Map<String, Object> conf, String key)
    {
        if (conf == null)
        {
            logger.warn("parameters group {} not specified in the configuration file.", key);
            return null;
        }

        Object o = conf.get(key);

        if (o instanceof Map)
        {
            return (Map<String, Object>) o;
        }
        else
        {
            logger.warn("parameters group {} not specified in the configuration file.", key);
            return null;
        }
    }

    private static Boolean getAsBooleanOrDefault(Map<String, Object> conf, String key, Boolean defaultValue)
    {
        if (conf == null)
        {
            logger.error("tried to get paramenter {} from a null configuration map. using its default value {}", key, defaultValue);
            return defaultValue;
        }

        Object o = conf.get(key);

        if (o == null)
        {
            if (defaultValue != null) // if default value is null there is no default value actually
            {
                logger.info("parameter {} not specified in the configuration file. using its default value {}", key, defaultValue);
            }
            return defaultValue;
        }
        else if (o instanceof Boolean)
        {
            logger.debug("paramenter {} set to {}", key, o);
            return (Boolean) o;
        }
        else
        {
            logger.info("wrong value for parameter {}: {}. using its default value {}", key, o, defaultValue);
            return defaultValue;
        }
    }

    private static String getAsStringOrDefault(Map<String, Object> conf, String key, String defaultValue)
    {
        if (conf == null)
        {
            logger.error("tried to get paramenter {} from a null configuration map. using its default value {}", key, defaultValue);
            return null;
        }

        Object o = conf.get(key);

        if (o == null)
        {
            if (defaultValue != null) // if default value is null there is no default value actually
            {
                logger.info("parameter {} not specified in the configuration file. using its default value {}", key, defaultValue);
            }
            return defaultValue;
        }
        else if (o instanceof String)
        {
            logger.debug("paramenter {} set to {}", key, o);
            return (String) o;
        }
        else
        {
            logger.info("wrong value for parameter {}: {}. using its default value {}", key, o, defaultValue);
            return defaultValue;
        }
    }

    private static Integer getAsIntegerOrDefault(Map<String, Object> conf, String key, Integer defaultValue)
    {
        if (conf == null)
        {
            logger.error("tried to get paramenter {} from a null configuration map. using its default value {}", key, defaultValue);
            return null;
        }

        Object o = conf.get(key);

        if (o == null)
        {
            if (defaultValue != null) // if default value is null there is no default value actually
            {
                logger.info("parameter {} not specified in the configuration file. using its default value {}", key, defaultValue);
            }
            return defaultValue;
        }
        else if (o instanceof Integer)
        {
            logger.debug("paramenter {} set to {}", key, o);
            return (Integer) o;
        }
        else
        {
            logger.info("wrong value for parameter {}: {}. using its default value {}", key, o, defaultValue);
            return defaultValue;
        }
    }
    
    private static Long getAsLongOrDefault(Map<String, Object> conf, String key, Long defaultValue)
    {
        if (conf == null)
        {
            logger.error("tried to get paramenter {} from a null configuration map. using its default value {}", key, defaultValue);
            return null;
        }

        Object o = conf.get(key);

        if (o == null)
        {
            if (defaultValue != null) // if default value is null there is no default value actually
            {
                logger.info("parameter {} not specified in the configuration file. using its default value {}", key, defaultValue);
            }
            return defaultValue;
        }
        else if (o instanceof Number)
        {
            logger.debug("paramenter {} set to {}", key, o);
            try
            {
                return Long.parseLong(o.toString());
            }
            catch(NumberFormatException nfe)
            {
                logger.info("wrong value for parameter {}: {}. using its default value {}", key, o, defaultValue);
                return defaultValue;
            }
        }
        else
        {
            logger.info("wrong value for parameter {}: {}. using its default value {}", key, o, defaultValue);
            return defaultValue;
        }
    }

    /**
     * @return the httpsListener
     */
    public boolean isHttpsListener()
    {
        return httpsListener;
    }

    /**
     * @return the httpsPort
     */
    public int getHttpsPort()
    {
        return httpsPort;
    }

    /**
     * @return the httpsHost
     */
    public String getHttpsHost()
    {
        return httpsHost;
    }

    /**
     * @return the httpListener
     */
    public boolean isHttpListener()
    {
        return httpListener;
    }

    /**
     * @return the httpPort
     */
    public int getHttpPort()
    {
        return httpPort;
    }

    /**
     * @return the httpHost
     */
    public String getHttpHost()
    {
        return httpHost;
    }

    /**
     * @return the ajpListener
     */
    public boolean isAjpListener()
    {
        return ajpListener;
    }

    /**
     * @return the ajpPort
     */
    public int getAjpPort()
    {
        return ajpPort;
    }

    /**
     * @return the ajpHost
     */
    public String getAjpHost()
    {
        return ajpHost;
    }

    /**
     * @return the useEmbeddedKeystore
     */
    public boolean isUseEmbeddedKeystore()
    {
        return useEmbeddedKeystore;
    }

    /**
     * @return the keystoreFile
     */
    public String getKeystoreFile()
    {
        return keystoreFile;
    }

    /**
     * @return the keystorePassword
     */
    public String getKeystorePassword()
    {
        return keystorePassword;
    }

    /**
     * @return the certPassword
     */
    public String getCertPassword()
    {
        return certPassword;
    }

    /**
     * @return the logFilePath
     */
    public String getLogFilePath()
    {
        return logFilePath;
    }

    /**
     * @return the logLevel
     */
    public Level getLogLevel()
    {
        return logLevel;
    }

    /**
     * @return the logToConsole
     */
    public boolean isLogToConsole()
    {
        return logToConsole;
    }

    /**
     * @return the logToFile
     */
    public boolean isLogToFile()
    {
        return logToFile;
    }

    /**
     * @return the ioThreads
     */
    public int getIoThreads()
    {
        return ioThreads;
    }

    /**
     * @return the workerThreads
     */
    public int getWorkerThreads()
    {
        return workerThreads;
    }

    /**
     * @return the bufferSize
     */
    public int getBufferSize()
    {
        return bufferSize;
    }

    /**
     * @return the buffersPerRegion
     */
    public int getBuffersPerRegion()
    {
        return buffersPerRegion;
    }

    /**
     * @return the directBuffers
     */
    public boolean isDirectBuffers()
    {
        return directBuffers;
    }

    /**
     * @return the forceGzipEncoding
     */
    public boolean isForceGzipEncoding()
    {
        return forceGzipEncoding;
    }

    /**
     * @return the idmImpl
     */
    public String getIdmImpl()
    {
        return idmImpl;
    }

    /**
     * @return the idmArgs
     */
    public Map<String, Object> getIdmArgs()
    {
        return idmArgs;
    }

    /**
     * @return the amImpl
     */
    public String getAmImpl()
    {
        return amImpl;
    }

    /**
     * @return the amArgs
     */
    public Map<String, Object> getAmArgs()
    {
        return amArgs;
    }

    /**
     * @return the requestsLimit
     */
    public int getRequestLimit()
    {
        return getRequestsLimit();
    }

    /**
     * @return the mongoServers
     */
    public List<Map<String, Object>> getMongoServers()
    {
        return mongoServers;
    }

    /**
     * @return the mongoCredentials
     */
    public List<Map<String, Object>> getMongoCredentials()
    {
        return mongoCredentials;
    }

    /**
     * @return the mongoMountsDefault
     */
    public List<Map<String, Object>> getMongoMounts()
    {
        return mongoMounts;
    }

    /**
     * @return the localCacheEnabled
     */
    public boolean isLocalCacheEnabled()
    {
        return localCacheEnabled;
    }

    /**
     * @return the localCacheTtl
     */
    public long getLocalCacheTtl()
    {
        return localCacheTtl;
    }

    /**
     * @return the requestsLimit
     */
    public int getRequestsLimit()
    {
        return requestsLimit;
    }

    /**
     * @return the applicationLogicMounts
     */
    public List<Map<String, Object>> getApplicationLogicMounts()
    {
        return applicationLogicMounts;
    }
}
