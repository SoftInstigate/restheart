/*-
 * ========================LICENSE_START=================================
 * restheart-core
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
package org.restheart.utils;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import java.util.List;
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
    public static void setLogLevel(List<String> packages, Level level) {
        var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        var logbackConfigurationFile = System.getProperty("logback.configurationFile");

        packages.stream().forEachOrdered(pack -> {
            var logger = loggerContext.getLogger(pack);
            if (logbackConfigurationFile != null && !logbackConfigurationFile.isEmpty()) {
                logger.info("Loglevel was set via logback configuration file with level {}", logger.getLevel());
            } else {
                logger.setLevel(level);
            }
        });
    }

    /**
     * used to change the log pattern for console appender
     * @param fullStacktrace
     */
    public static void applyFullstacktraceOption(boolean fullStacktrace) {
        // short stack trace is default, nothing to do
        if (!fullStacktrace) {
            return;
        }

        // logback configured with file, nothing to do
        var logbackConfigurationFile = System.getProperty("logback.configurationFile");
        if (logbackConfigurationFile != null && !logbackConfigurationFile.isEmpty()) {
            return;
        }

        var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        var rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        var _ca = rootLogger.getAppender("STDOUT");

        if (_ca instanceof ConsoleAppender<?> ca) {
            var _e = ca.getEncoder();
            if (_e != null) {
                _e.stop();
            }

            _ca.stop();
            rootLogger.detachAppender(_ca);

            var newAppender = new ConsoleAppender<ILoggingEvent>();
            newAppender.setContext(loggerContext);

            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(loggerContext);

            encoder.setPattern( "%d{HH:mm:ss.SSS} [%thread] %highlight(%-5level) %logger{36} - %msg%n%throwable{full}");
            encoder.start();

            newAppender.setEncoder(encoder);
            newAppender.setName("STDOUT");
            newAppender.start();

            rootLogger.addAppender(newAppender);
        }
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
    public static void startFileLogging(String logFilePath, boolean fullStacktrace) {
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

        if (fullStacktrace) {
            encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %highlight(%-5level) %logger{36} - %msg%n%throwable{full}");
        }  else {
            encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %highlight(%-5level) %logger{36} - %msg%n%throwable{short}");
        }
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
