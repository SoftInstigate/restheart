/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2022 SoftInstigate
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.restheart.configuration.Utils.getOrDefault;
import static org.restheart.configuration.Utils.asMap;
import ch.qos.logback.classic.Level;

public record Logging(Level logLevel,
    boolean logToFile,
    String logFilePath,
    boolean logToConsole,
    boolean ansiConsole,
    List<String> packages,
    boolean fullStacktrace,
    int requestsLogMode,
    List<String> tracingHeaders) {
    public static final String LOGGING_KEY = "logging";
    public static final String LOG_LEVEL_KEY = "log-level";
    public static final String ENABLE_LOG_FILE_KEY = "log-to-file";
    public static final String LOG_FILE_PATH_KEY = "log-file-path";
    public static final String ENABLE_LOG_CONSOLE_KEY = "log-to-console";
    public static final String ANSI_CONSOLE_KEY = "ansi-console";
    public static final String REQUESTS_LOG_MODE = "requests-log-mode";
    public static final String TRACING_HEADERS_KEY = "tracing-headers";
    public static final String PACKAGES_KEY = "packages";
    public static final String PRINT_FULL_STACKTRACE = "full-stacktrace";

    private static final List<String> DEFAULT_PACKAGES = List.of("org.restheart", "com.restheart");

    private static Logging DEFAULT_LOGGING = new Logging(Level.INFO, false, null, true,true, DEFAULT_PACKAGES, false, 1, new ArrayList<>());

    public Logging(Map<String, Object> conf, boolean silent) {
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
            getOrDefault(conf, TRACING_HEADERS_KEY, DEFAULT_LOGGING.tracingHeaders(), true));
    }

    public static Logging build(Map<String, Object> conf, boolean silent) {
        var logging = asMap(conf, LOGGING_KEY, null, silent);

        if (logging != null) {
            return new Logging(logging, silent);
        } else {
            return DEFAULT_LOGGING;
        }
    }

    private static Level _level(Map<String, Object> conf, Level defaultLevel, boolean silent) {
        String _level = getOrDefault(conf, LOG_LEVEL_KEY, null, true);

        if (_level == null) {
            Configuration.LOGGER.warn("Parameter \"{}\" not specified in the configuration file, using its default value \"{}\"", LOG_LEVEL_KEY, defaultLevel);
            return defaultLevel;
        } else {
            try {
                return Level.valueOf(_level);
            } catch (Throwable t) {
                Configuration.LOGGER.warn("Wrong parameter \"{}\"={} specified in the configuration file, using its default value \"{}\"", LOG_LEVEL_KEY, _level, defaultLevel);
                return defaultLevel;
            }
        }
    }
}
