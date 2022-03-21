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
package org.restheart.utils;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.ansi;
import org.slf4j.Logger;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class LogUtils {
    public static enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

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

    public static void boxedError(
            Logger LOGGER,
            String... rows) {
        boxedMessage(LOGGER, Level.ERROR, RED, GREEN, rows);
    }

    public static void boxedWarn(
            Logger LOGGER,
            String... rows) {
        boxedMessage(LOGGER, Level.WARN, RED, GREEN, rows);
    }

    public static void boxedInfo(
            Logger LOGGER,
            String... rows) {
        boxedMessage(LOGGER, Level.INFO, GREEN, GREEN, rows);
    }

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

    private static Ansi sr() {
        return ansi().fg(GREEN).a("| ").reset();
    }

    private static Ansi er() {
        return ansi().fg(GREEN).a("|\n").reset();
    }

    private static Ansi header() {
        return ansi().a("\n").fg(GREEN).a(
                "*-------------------------------------------------------------------*\n"
                + "|                                                                   |\n")
                .reset();
    }

    private static Ansi footer() {
        return ansi().fg(GREEN).a(
                "|                                                                   |\n"
                + "*-------------------------------------------------------------------*\n")
                .reset();

    }

    private static String pad(String s, int length) {
        while (s.length() < length) {
            s = s.concat(" ");
        }
        return s;
    }
}
