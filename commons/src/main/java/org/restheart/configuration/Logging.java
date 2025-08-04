/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.configuration;

import static org.restheart.configuration.Utils.asMap;
import static org.restheart.configuration.Utils.getOrDefault;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;

/**
 * Configuration for RESTHeart's logging system.
 * 
 * <p>
 * This record encapsulates all logging-related configuration including log levels,
 * output destinations (console and/or file), request logging modes, and tracing headers
 * for distributed tracing support.
 * </p>
 * 
 * <h2>Configuration Structure</h2>
 * <p>
 * In the configuration file, logging is configured as:
 * </p>
 * 
 * <pre>{@code
 * logging:
 *   log-level: INFO
 *   log-to-console: true
 *   ansi-console: true
 *   log-to-file: false
 *   log-file-path: "./restheart.log"
 *   requests-log-mode: 1
 *   requests-log-exclude-patterns:
 *     - "/ping"
 *     - "/health"
 *     - "/_ping"
 *   packages:
 *     - "org.restheart"
 *     - "com.restheart"
 *   full-stacktrace: false
 *   tracing-headers:
 *     - "X-Request-Id"
 *     - "X-Correlation-Id"
 * }</pre>
 * 
 * <h2>Log Levels</h2>
 * <p>
 * Supported log levels (from least to most verbose):
 * </p>
 * <ul>
 * <li>ERROR - Only error messages</li>
 * <li>WARN - Warnings and errors</li>
 * <li>INFO - Informational messages (default)</li>
 * <li>DEBUG - Debug messages</li>
 * <li>TRACE - Very detailed trace messages</li>
 * </ul>
 * 
 * <h2>Request Logging Modes</h2>
 * <ul>
 * <li>0 - No request logging</li>
 * <li>1 - Log request summary line (default)</li>
 * <li>2 - Log request headers</li>
 * <li>3 - Log request headers and body</li>
 * </ul>
 * 
 * <h2>Request Logging Exclusion</h2>
 * <p>
 * The {@code requests-log-exclude-patterns} configuration allows you to specify
 * request path patterns that should be excluded from logging. This is useful for
 * reducing log noise from health checks, monitoring pings, and other frequent requests.
 * </p>
 * <p>
 * Patterns are matched against the request path and support:
 * </p>
 * <ul>
 * <li>Exact matches: {@code /ping} - excludes exact path "/ping"</li>
 * <li>Wildcard patterns: {@code /api/v*\/health} - excludes paths like "/api/v1/health"</li>
 * <li>Prefix patterns: {@code /monitoring/*} - excludes all paths starting with "/monitoring/"</li>
 * </ul>
 * 
 * <h2>Excluded Request Counting</h2>
 * <p>
 * To maintain visibility into excluded requests, the system logs:
 * </p>
 * <ul>
 * <li>The first excluded request for each pattern</li>
 * <li>Every nth excluded request (configurable via {@code requests-log-exclude-interval})</li>
 * <li>The total count of excluded requests for each pattern</li>
 * </ul>
 * <p>
 * This provides insight into the frequency of excluded requests without overwhelming the logs.
 * </p>
 * 
 * @param logLevel
 *            the minimum log level to output
 * @param logToFile
 *            whether to write logs to a file
 * @param logFilePath
 *            path to the log file (when logToFile is true)
 * @param logToConsole
 *            whether to write logs to console/stdout
 * @param ansiConsole
 *            whether to use ANSI colors in console output
 * @param packages
 *            list of package names to include in logging
 * @param fullStacktrace
 *            whether to print full stack traces for exceptions
 * @param requestsLogMode
 *            the level of detail for request logging (0-3)
 * @param tracingHeaders
 *            list of header names to include in tracing logs
 * @param requestsLogExcludePatterns
 *            list of request path patterns to exclude from logging
 * @param requestsLogExcludeInterval
 *            interval for logging excluded requests (log every nth excluded request)
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 1.0
 */
public record Logging(Level logLevel,
        boolean logToFile,
        String logFilePath,
        boolean logToConsole,
        boolean ansiConsole,
        List<String> packages,
        boolean fullStacktrace,
        int requestsLogMode,
        List<String> tracingHeaders,
        List<String> requestsLogExcludePatterns,
        long requestsLogExcludeInterval) {
    /**
     * Configuration key for the logging section.
     */
    public static final String LOGGING_KEY = "logging";

    /**
     * Configuration key for the log level.
     */
    public static final String LOG_LEVEL_KEY = "log-level";

    /**
     * Configuration key for enabling file logging.
     */
    public static final String ENABLE_LOG_FILE_KEY = "log-to-file";

    /**
     * Configuration key for the log file path.
     */
    public static final String LOG_FILE_PATH_KEY = "log-file-path";

    /**
     * Configuration key for enabling console logging.
     */
    public static final String ENABLE_LOG_CONSOLE_KEY = "log-to-console";

    /**
     * Configuration key for ANSI colored console output.
     */
    public static final String ANSI_CONSOLE_KEY = "ansi-console";

    /**
     * Configuration key for request logging mode (0-3).
     */
    public static final String REQUESTS_LOG_MODE = "requests-log-mode";

    /**
     * Configuration key for tracing headers list.
     */
    public static final String TRACING_HEADERS_KEY = "tracing-headers";

    /**
     * Configuration key for packages to include in logging.
     */
    public static final String PACKAGES_KEY = "packages";

    /**
     * Configuration key for full stack trace printing.
     */
    public static final String PRINT_FULL_STACKTRACE = "full-stacktrace";

    /**
     * Configuration key for request path patterns to exclude from logging.
     */
    public static final String REQUESTS_LOG_EXCLUDE_PATTERNS = "requests-log-exclude-patterns";

    /**
     * Configuration key for the interval of logging excluded requests.
     */
    public static final String REQUESTS_LOG_EXCLUDE_INTERVAL = "requests-log-exclude-interval";

    /**
     * Default packages to include in logging output.
     */
    private static final List<String> DEFAULT_PACKAGES = List.of("org.restheart", "com.restheart");

    /**
     * Default logging configuration used when no configuration is provided.
     * 
     * <p>
     * Default values:
     * </p>
     * <ul>
     * <li>log-level: INFO</li>
     * <li>log-to-file: false</li>
     * <li>log-to-console: true</li>
     * <li>ansi-console: true</li>
     * <li>requests-log-mode: 1 (summary only)</li>
     * <li>full-stacktrace: false</li>
     * <li>requests-log-exclude-patterns: empty list</li>
     * <li>requests-log-exclude-interval: 100</li>
     * </ul>
     */
    private static Logging DEFAULT_LOGGING = new Logging(Level.INFO, false, null, true, true, DEFAULT_PACKAGES, false,
            1, new ArrayList<>(), new ArrayList<>(), 100L);

    /**
     * Creates a Logging configuration from a configuration map.
     * 
     * <p>
     * This constructor extracts logging configuration values from the provided map,
     * using default values for any missing properties.
     * </p>
     * 
     * @param conf
     *            the configuration map containing logging settings
     * @param silent
     *            if true, suppresses warning messages for missing optional properties
     */
    public Logging(final Map<String, Object> conf, final boolean silent) {
        this(
                _level(conf, DEFAULT_LOGGING.logLevel(), silent),
                getOrDefault(conf, ENABLE_LOG_FILE_KEY, DEFAULT_LOGGING.logToFile(), silent),
                getOrDefault(conf, LOG_FILE_PATH_KEY, DEFAULT_LOGGING.logFilePath(), silent),
                getOrDefault(conf, ENABLE_LOG_CONSOLE_KEY, DEFAULT_LOGGING.logToConsole(), silent),
                getOrDefault(conf, ANSI_CONSOLE_KEY, DEFAULT_LOGGING.ansiConsole(), silent),
                // following is optional, so get it always in silent mode
                getOrDefault(conf, PACKAGES_KEY, DEFAULT_LOGGING.packages(), true),
                getOrDefault(conf, PRINT_FULL_STACKTRACE, DEFAULT_LOGGING.fullStacktrace(), true),
                getOrDefault(conf, REQUESTS_LOG_MODE, DEFAULT_LOGGING.requestsLogMode(), silent),
                // following is optional, so get it always in silent mode
                getOrDefault(conf, TRACING_HEADERS_KEY, DEFAULT_LOGGING.tracingHeaders(), true),
                // following is optional, so get it always in silent mode
                getOrDefault(conf, REQUESTS_LOG_EXCLUDE_PATTERNS, DEFAULT_LOGGING.requestsLogExcludePatterns(), true),
                // following is optional, so get it always in silent mode
                getOrDefault(conf, REQUESTS_LOG_EXCLUDE_INTERVAL, DEFAULT_LOGGING.requestsLogExcludeInterval(), true));
    }

    /**
     * Builds a Logging configuration from the main configuration map.
     * 
     * <p>
     * This method looks for the {@code logging} section in the configuration map.
     * If found, it creates a Logging configuration from that section. If not found,
     * returns the default logging configuration.
     * </p>
     * 
     * @param conf
     *            the main configuration map
     * @param silent
     *            if true, suppresses warning messages for missing optional properties
     * @return a Logging instance with configuration values or defaults
     */
    public static Logging build(final Map<String, Object> conf, final boolean silent) {
        final var logging = asMap(conf, LOGGING_KEY, null, silent);

        if (logging != null) {
            return new Logging(logging, silent);
        } else {
            return DEFAULT_LOGGING;
        }
    }

    /**
     * Parses and validates the log level from configuration.
     * 
     * <p>
     * This method attempts to parse the log level string from the configuration.
     * If the value is invalid or missing, it logs a warning and returns the default
     * level.
     * </p>
     * 
     * @param conf
     *            the configuration map containing the log level
     * @param defaultLevel
     *            the default level to use if parsing fails
     * @param silent
     *            if true, suppresses warning messages
     * @return the parsed Level or defaultLevel if parsing fails
     */
    private static Level _level(final Map<String, Object> conf, final Level defaultLevel, final boolean silent) {
        final String _level = getOrDefault(conf, LOG_LEVEL_KEY, null, true);

        if (_level == null) {
            Configuration.LOGGER.warn(
                    "Parameter \"{}\" not specified in the configuration file, using its default value \"{}\"",
                    LOG_LEVEL_KEY, defaultLevel);
            return defaultLevel;
        } else {
            try {
                return Level.valueOf(_level);
            } catch (final Throwable t) {
                Configuration.LOGGER.warn(
                        "Wrong parameter \"{}\"={} specified in the configuration file, using its default value \"{}\"",
                        LOG_LEVEL_KEY, _level, defaultLevel);
                return defaultLevel;
            }
        }
    }
}
