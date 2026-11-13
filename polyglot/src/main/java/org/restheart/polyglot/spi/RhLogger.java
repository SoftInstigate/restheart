/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.polyglot.spi;

import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code logger} of the SDK: SLF4J-style {@code {}} placeholders, null-safe, one logger per
 * plugin named after it.
 *
 * <p>Each level has one method taking {@code Value...}, so the guest call never has to pick a
 * Java overload: {@code logger.debug("x {}", foo)} with a null {@code foo} logs {@code x null},
 * where the raw SLF4J logger threw {@code Multiple applicable overloads found}.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RhLogger {
    private final Logger logger;

    /**
     * @param pluginName the plugin name, the logger is {@code org.restheart.polyglot.plugins.<name>}
     */
    public RhLogger(String pluginName) {
        this.logger = LoggerFactory.getLogger("org.restheart.polyglot.plugins." + pluginName);
    }

    /**
     * @param logger the logger to write to
     */
    public RhLogger(Logger logger) {
        this.logger = logger;
    }

    public void trace(String msg, Value... args) {
        if (logger.isTraceEnabled()) {
            logger.trace(msg, format(args));
        }
    }

    public void debug(String msg, Value... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(msg, format(args));
        }
    }

    public void info(String msg, Value... args) {
        if (logger.isInfoEnabled()) {
            logger.info(msg, format(args));
        }
    }

    public void warn(String msg, Value... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(msg, format(args));
        }
    }

    public void error(String msg, Value... args) {
        if (logger.isErrorEnabled()) {
            logger.error(msg, format(args));
        }
    }

    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    /**
     * The arguments as SLF4J gets them: strings as they are, objects and arrays as JSON, a
     * guest error with its message, null as the text {@code null}.
     */
    static Object[] format(Value... args) {
        if (args == null) {
            return new Object[0];
        }

        var out = new Object[args.length];

        for (var i = 0;i < args.length;i++) {
            out[i] = format(args[i]);
        }

        return out;
    }

    static Object format(Value v) {
        if (v == null || v.isNull()) {
            return "null";
        } else if (v.isString()) {
            return v.asString();
        } else if (v.isNumber() || v.isBoolean()) {
            return v.toString();
        } else if (v.isException()) {
            return v.toString();
        } else if (v.isHostObject()) {
            return String.valueOf(v.asHostObject());
        } else if (v.canExecute() && !v.hasMembers()) {
            return "[function]";
        } else if (v.hasArrayElements() || v.hasMembers() || v.isProxyObject()) {
            try {
                return GuestValues.toJson(v).toString();
            } catch (RuntimeException e) {
                return v.toString();
            }
        }
        return v.toString();
    }
}
