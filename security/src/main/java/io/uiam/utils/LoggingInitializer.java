/*
 * uIAM - the IAM for microservices
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
package io.uiam.utils;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class LoggingInitializer {

    private static final int ASYNC_MAX_FLUSH_TIME = 5000;
    private static final int ASYNC_QUEUE_SIZE = 1024;

    /**
     *
     * @param level
     */
    public static void setLogLevel(Level level) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger("io.uiam");

        String logbackConfigurationFile = System.getProperty("logback.configurationFile");
        if (logbackConfigurationFile != null && !logbackConfigurationFile.isEmpty()) {
            logger.info("Loglevel was set via logback configuration file with level {}", logger.getLevel());
            level = logger.getLevel();
        }

        logger.setLevel(level);
    }

    /**
     *
     */
    public static void stopConsoleLogging() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);

        Appender<ILoggingEvent> appender = rootLogger.getAppender("STDOUT");

        appender.stop();
    }

    /**
     *
     * @param logFilePath
     */
    public static void startFileLogging(String logFilePath) {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

        LoggerContext loggerContext = rootLogger.getLoggerContext();

        RollingFileAppender<ILoggingEvent> rfAppender = new RollingFileAppender<>();
        rfAppender.setContext(loggerContext);
        rfAppender.setFile(logFilePath);

        FixedWindowRollingPolicy fwRollingPolicy = new FixedWindowRollingPolicy();
        fwRollingPolicy.setContext(loggerContext);
        fwRollingPolicy.setFileNamePattern(logFilePath + "-%i.log.zip");
        fwRollingPolicy.setParent(rfAppender);
        fwRollingPolicy.start();

        SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<>();
        triggeringPolicy.setMaxFileSize(FileSize.valueOf("5 mb"));
        triggeringPolicy.start();

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %highlight(%-5level) %logger{36} - %msg%n");
        encoder.start();

        rfAppender.setEncoder(encoder);
        rfAppender.setName("ROLLINGFILE");
        rfAppender.setRollingPolicy(fwRollingPolicy);
        rfAppender.setTriggeringPolicy(triggeringPolicy);
        rfAppender.start();

        AsyncAppender asyncAppender = new AsyncAppender();
        asyncAppender.setContext(loggerContext);
        asyncAppender.setName("ASYNC");
        asyncAppender.setQueueSize(ASYNC_QUEUE_SIZE);
        asyncAppender.setMaxFlushTime(ASYNC_MAX_FLUSH_TIME);
        asyncAppender.addAppender(rfAppender);
        asyncAppender.start();

        rootLogger.addAppender(asyncAppender);
    }

    public static void stopLogging() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.getLoggerContext().stop();
    }

    private LoggingInitializer() {
    }
}
