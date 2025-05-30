/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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
package org.restheart.utils;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.ansi;
import org.slf4j.Logger;

/**
 * Utility class for enhanced logging operations with colored console output.
 * Provides methods for logging at different levels and creating boxed messages
 * for better visual presentation in console output.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class LogUtils {
    /**
     * Enumeration of logging levels supported by this utility.
     */
    public static enum Level {
        /** Trace level logging */
        TRACE, 
        /** Debug level logging */
        DEBUG, 
        /** Info level logging */
        INFO, 
        /** Warning level logging */
        WARN, 
        /** Error level logging */
        ERROR
    }

    /**
     * Logs a message at the specified level using the provided logger.
     *
     * @param logger the SLF4J logger to use for logging
     * @param level the logging level to use
     * @param format the message format string
     * @param argArray arguments for the format string
     */
    public static void log(Logger logger, Level level, String format, Object... argArray) {
        if (logger != null && level != null) {
            switch (level) {
                case TRACE:
                    logger.trace(format, argArray);
                    break;
                case DEBUG:
                    logger.debug(format, argArray);
                    break;
                case INFO:
                    logger.info(format, argArray);
                    break;
                case WARN:
                    logger.warn(format, argArray);
                    break;
                case ERROR:
                    logger.error(format, argArray);
                    break;
            }
        }
    }

    /**
     * Logs an error message in a colored box format for better visibility.
     *
     * @param LOGGER the SLF4J logger to use
     * @param rows the message rows to display in the box
     */
    public static void boxedError(
            Logger LOGGER,
            String... rows) {
        boxedMessage(LOGGER, Level.ERROR, RED, GREEN, rows);
    }

    /**
     * Logs a warning message in a colored box format for better visibility.
     *
     * @param LOGGER the SLF4J logger to use
     * @param rows the message rows to display in the box
     */
    public static void boxedWarn(
            Logger LOGGER,
            String... rows) {
        boxedMessage(LOGGER, Level.WARN, RED, GREEN, rows);
    }

    /**
     * Logs an info message in a colored box format for better visibility.
     *
     * @param LOGGER the SLF4J logger to use
     * @param rows the message rows to display in the box
     */
    public static void boxedInfo(
            Logger LOGGER,
            String... rows) {
        boxedMessage(LOGGER, Level.INFO, GREEN, GREEN, rows);
    }

    /**
     * Logs a message in a colored box format with customizable colors.
     *
     * @param LOGGER the SLF4J logger to use
     * @param level the logging level to use
     * @param firstRowColor the color for the first row of the message
     * @param rowsColor the color for subsequent rows of the message
     * @param rows the message rows to display in the box
     */
    public static void boxedMessage(
            Logger LOGGER,
            Level level,
            Color firstRowColor,
            Color rowsColor,
            String... rows) {

        var msg = header();
        var first = true;
        for (var row : rows) {
            msg.a(sr())
                    .fg(first ? firstRowColor: rowsColor)
                    .a(pad(row, 66))
                    .a(er())
                    .reset();

            first = false;
        }

        msg.a(footer());

        LogUtils.log(LOGGER, level, msg.toString(), (Object[])null);
    }

    /**
     * Creates the start row formatting for boxed messages.
     *
     * @return Ansi object with start row formatting
     */
    private static Ansi sr() {
        return ansi().fg(GREEN).a("| ").reset();
    }

    /**
     * Creates the end row formatting for boxed messages.
     *
     * @return Ansi object with end row formatting
     */
    private static Ansi er() {
        return ansi().fg(GREEN).a("|\n").reset();
    }

    /**
     * Creates the header formatting for boxed messages.
     *
     * @return Ansi object with header formatting
     */
    private static Ansi header() {
        return ansi().a("\n").fg(GREEN).a(
                "*-------------------------------------------------------------------*\n"
                + "|                                                                   |\n")
                .reset();
    }

    /**
     * Creates the footer formatting for boxed messages.
     *
     * @return Ansi object with footer formatting
     */
    private static Ansi footer() {
        return ansi().fg(GREEN).a(
                "|                                                                   |\n"
                + "*-------------------------------------------------------------------*\n")
                .reset();

    }

    /**
     * Pads a string to the specified length with spaces.
     *
     * @param s the string to pad
     * @param length the target length
     * @return the padded string
     */
    private static String pad(String s, int length) {
        while (s.length() < length) {
            s = s.concat(" ");
        }
        return s;
    }
}
