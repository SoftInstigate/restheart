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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

    private final String mongoHost;
    private final int mongoPort;
    private final String mongoUser;
    private final String mongoPassword;
    
    private final String idmImpl;
    private final Map<String, Object> idmArgs;
    private final String amImpl;
    private final Map<String, Object> amArgs;
        
    private final String logFilePath;
    private final Level logLevel;
    private final boolean logToConsole;
    private final boolean logToFile;

    private final int ioThreads;
    private final int workerThreads;
    private final int bufferSize;
    private final int buffersPerRegion;
    private final boolean directBuffers;

    private final boolean forceGzipEncoding;

    public Configuration(String confFilePath)
    {
        Yaml yaml = new Yaml();
        
        Map<String, Object> conf = null;
        
        try
        {
            conf = (Map<String, Object>) yaml.load(new FileInputStream(new File(confFilePath)));
        }
        catch(FileNotFoundException fnef)
        {
            logger.error("configuration file not found. starting with default parameters.");
        }
        catch(Throwable t)
        {
            logger.error("wrong configuration file format. starting with default parameters.");
        }
        
        httpsListener = getAsBooleanOrDefault(conf, "https-listener", true);
        httpsPort = getAsIntegerOrDefault(conf, "https-port", 8443);
        httpsHost = getAsStringOrDefault(conf, "https-host", "0.0.0.0");

        httpListener = getAsBooleanOrDefault(conf, "http-listener", false);
        httpPort = getAsIntegerOrDefault(conf, "http-port", 8080);
        httpHost = getAsStringOrDefault(conf, "http-host", "0.0.0.0");

        ajpListener = getAsBooleanOrDefault(conf, "ajp-listener", false);
        ajpPort = getAsIntegerOrDefault(conf, "ajp-port", 8009);
        ajpHost = getAsStringOrDefault(conf, "ajp-host", "0.0.0.0");

        useEmbeddedKeystore = getAsBooleanOrDefault(conf, "use-embedded-keystore", true);
        keystoreFile = getAsStringOrDefault(conf, "keystore-file", null);
        keystorePassword = getAsStringOrDefault(conf, "keystore-password", null);
        certPassword = getAsStringOrDefault(conf, "certpassword", null);

        mongoHost = getAsStringOrDefault(conf, "mongo-host", "127.0.0.1");
        mongoPort = getAsIntegerOrDefault(conf, "mongo-port", 27017);
        mongoUser = getAsStringOrDefault(conf, "mongo-user", "");
        mongoPassword = getAsStringOrDefault(conf, "mongo-password", "");
        
        Map<String, Object> idm = getAsMap(conf, "idm");
        Map<String, Object> am = getAsMap(conf, "access-manager");
        Map<String, Object> ach = getAsMap(conf, "authentication-constraint-handler");
        
        idmImpl = getAsStringOrDefault(idm, "implementation-class", null);
        idmArgs = idm;

        amImpl = getAsStringOrDefault(am, "implementation-class", null);
        amArgs = am;
        
        logFilePath = getAsStringOrDefault(conf, "log-file-path", System.getProperty("java.io.tmpdir" + File.separator +  "restheart.log"));
        String _logLevel = getAsStringOrDefault(conf, "log-level", "WARN");
        logToConsole = getAsBooleanOrDefault(conf, "enable-log-console", true);
        logToFile = getAsBooleanOrDefault(conf, "enable-log-file", true);

        Level level;
        
        try
        {
             level = Level.valueOf(_logLevel);
        }
        catch(Exception e)
        {
            logger.info("wrong value for parameter {}: {}. using its default value {}", "log-level", _logLevel, "WARN");
            level = Level.WARN;
        }
        
        logLevel = level;

        ioThreads = getAsIntegerOrDefault(conf, "io-threads", 8);
        workerThreads = getAsIntegerOrDefault(conf, "worker-threads", 500);
        bufferSize = getAsIntegerOrDefault(conf, "buffer-size", 16384);
        buffersPerRegion = getAsIntegerOrDefault(conf, "buffers-per-region", 20);
        directBuffers = getAsBooleanOrDefault(conf, "direct-buffers", true);

        forceGzipEncoding = getAsBooleanOrDefault(conf, "force-gzip-encoding", true);
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
            return (Map<String, Object>) o;
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
                logger.info("parameter {} not specified in the configuration file. using its default value {}", key, defaultValue);
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
                logger.info("parameter {} not specified in the configuration file. using its default value {}", key, defaultValue);
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
                logger.info("parameter {} not specified in the configuration file. using its default value {}", key, defaultValue);
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
     * @return the mongoHost
     */
    public String getMongoHost()
    {
        return mongoHost;
    }

    /**
     * @return the mongoPort
     */
    public int getMongoPort()
    {
        return mongoPort;
    }

    /**
     * @return the mongoUser
     */
    public String getMongoUser()
    {
        return mongoUser;
    }

    /**
     * @return the mongoPassword
     */
    public String getMongoPassword()
    {
        return mongoPassword;
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
}