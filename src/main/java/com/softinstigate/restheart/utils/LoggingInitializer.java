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
package com.softinstigate.restheart.utils;

import ch.qos.logback.classic.Level;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import java.io.File;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class LoggingInitializer
{
    public static void setLogLevel(Level level)
    {
        LoggerContext loggerContext = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger("com.softinstigate");

        logger.setLevel(level);
    }
    
    public static void stopConsoleLogging()
    {
        LoggerContext loggerContext = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);

        Appender<ILoggingEvent> appender = rootLogger.getAppender("STDOUT");

        appender.stop();
        //rootLogger.detachAppender("STDOUT");
    }

    public static void startFileLogging(String logFilePath)
    {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

        LoggerContext loggerContext = logger.getLoggerContext();
        
        RollingFileAppender<ILoggingEvent> rfAppender = new RollingFileAppender<>();
        rfAppender.setContext(loggerContext);
        rfAppender.setFile(logFilePath);

        FixedWindowRollingPolicy fwRollingPolicy = new FixedWindowRollingPolicy();
        fwRollingPolicy.setContext(loggerContext);
        fwRollingPolicy.setFileNamePattern(logFilePath + "-%i.log.zip");
        fwRollingPolicy.setParent(rfAppender);
        fwRollingPolicy.start();

        SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<>();
        triggeringPolicy.setMaxFileSize("5MB");
        triggeringPolicy.start();

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"); 
        encoder.start();

        rfAppender.setEncoder(encoder);
        rfAppender.setRollingPolicy(fwRollingPolicy);
        rfAppender.setTriggeringPolicy(triggeringPolicy);
        rfAppender.start();
        
        logger.addAppender(rfAppender);
    }
    
    private static String getFileNameFromPath(String filePath)
    {
        String[] tokens = filePath.split(File.separator);
        
        return tokens[tokens.length -1];
    }
}