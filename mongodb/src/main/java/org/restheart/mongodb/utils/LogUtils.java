/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
package org.restheart.mongodb.utils;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.MAGENTA;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.ansi;
import org.slf4j.Logger;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class LogUtils {

    /**
     *
     */
    public static enum Level {

        /**
         *
         */
        TRACE,

        /**
         *
         */
        DEBUG,

        /**
         *
         */
        INFO,

        /**
         *
         */
        WARN,

        /**
         *
         */
        ERROR
    }

    /**
     *
     * @param logger
     * @param level
     * @param format
     * @param argArray
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
     *
     * @param LOGGER
     * @param rows
     */
    public static void boxedError(
            Logger LOGGER,
            String... rows) {
        boxedMessage(LOGGER, Level.ERROR, RED, GREEN, rows);
    }

    /**
     *
     * @param LOGGER
     * @param rows
     */
    public static void boxedWarn(
            Logger LOGGER,
            String... rows) {
        boxedMessage(LOGGER, Level.WARN, MAGENTA, GREEN, rows);
    }

    /**
     *
     * @param LOGGER
     * @param rows
     */
    public static void boxedInfo(
            Logger LOGGER,
            String... rows) {
        boxedMessage(LOGGER, Level.INFO, GREEN, GREEN, rows);
    }

    /**
     *
     * @param LOGGER
     * @param level
     * @param firstRowColor
     * @param rowsColor
     * @param rows
     */
    public static void boxedMessage(
            Logger LOGGER,
            Level level,
            Color firstRowColor,
            Color rowsColor,
            String... rows) {

        var msg = header(firstRowColor);
        var first = true;
        for (var row : rows) {
            msg.a(sr(firstRowColor))
                    .fg(first ? firstRowColor: rowsColor)
                    .a(pad(row, 66))
                    .a(er(firstRowColor))
                    .reset();

            first = false;
        }

        msg.a(footer(firstRowColor));

        LogUtils.log(LOGGER, level, msg.toString(), (Object[])null);
    }

    private static Ansi sr(Color color) {
        return ansi().fg(color).a("| ").reset();
    }

    private static Ansi er(Color color) {
        return ansi().fg(color).a("|\n").reset();
    }

    private static Ansi header(Color color) {
        return ansi().a("\n").fg(color).a(
                "*-------------------------------------------------------------------*\n"
                + "|                                                                   |\n")
                .reset();
    }

    private static Ansi footer(Color color) {
        return ansi().fg(color).a(
                "|                                                                   |\n"
                + "*-------------------------------------------------------------------*\n")
                .reset();

    }

    private static String pad(String s, int length) {
        var ret = new String(s);
        while (ret.length() < length) {
            ret = ret.concat(" ");
        }
        return ret;
    }
}
