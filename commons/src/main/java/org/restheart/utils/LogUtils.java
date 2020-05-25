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
package org.restheart.utils;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.ansi;
import org.slf4j.Logger;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
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
        var ret = new String(s);
        while (ret.length() < length) {
            ret = ret.concat(" ");
        }
        return ret;
    }
}
