/*-
 * ========================LICENSE_START=================================
 * restheart-commons
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
package org.restheart.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.restheart.logging.RequestPhaseContext.Phase;

/**
 * Logback converter that generates log prefixes based on the current request phase.
 * Uses RequestPhaseContext to determine which prefix to output.
 *
 * Usage in logback.xml:
 * <conversionRule conversionWord="prefix" class="org.restheart.utils.LogPrefixConverter" />
 <pattern>%d{HH:mm:ss.SSS} [%thread] %highlight(%-5level) %trace%-45logger{45} %prefix%msg%n%throwable{short}</pattern>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class LogPrefixConverter extends ClassicConverter {

    // Phase markers
    private static final String PHASE_START = "┌── ";
    private static final String PHASE_END = "└── ";

    // Item markers within a phase
    private static final String ITEM = "│   ├─ ";
    private static final String ITEM_LAST = "│   └─ ";

    // Sub-item markers (for nested logs)
    private static final String SUBITEM = "│   │  └─ ";
    private static final String SUBITEM_LAST = "│      └─ ";

    // Continuation (for logs within a phase)
    private static final String INFO = "│   ";

    // Standalone message (not part of a group)
    private static final String STANDALONE = "⚬ ";

    // No prefix
    private static final String NONE = "";

    @Override
    public String convert(ILoggingEvent event) {
        var phase = RequestPhaseContext.getPhase();
        var isLast = RequestPhaseContext.isLast();

        // If this is a non-DEBUG log within a grouped context, check if the group would be visible
        // If not, use STANDALONE prefix to avoid orphaned tree symbols
        if (shouldUseStandalonePrefix(event, phase)) {
            return STANDALONE;
        }

        return switch (phase) {
            case PHASE_START -> PHASE_START;
            case PHASE_END -> PHASE_END;
            case ITEM -> isLast ? ITEM_LAST : ITEM;
            case ITEM_LAST -> ITEM_LAST;
            case SUBITEM -> isLast ? SUBITEM_LAST : SUBITEM;
            case INFO -> INFO;
            case STANDALONE -> STANDALONE;
            case NONE -> NONE;
        };
    }

    /**
     * Determines if a STANDALONE prefix should be used instead of group prefixes.
     * This happens when:
     * 1. The event is at WARN/ERROR/INFO level (higher than DEBUG)
     * 2. The phase indicates we're inside a group (ITEM, SUBITEM, INFO)
     * 3. DEBUG logging is not enabled for the logger
     * 4. The group start (PHASE_START) uses DEBUG level
     * 
     * In this case, the group context (PHASE_START, etc.) wouldn't be visible,
     * so using group prefixes would create orphaned tree symbols.
     * 
     * However, if PHASE_START uses INFO/WARN/ERROR (visible level), then we should
     * keep the group prefixes even when DEBUG is disabled.
     */
    private boolean shouldUseStandalonePrefix(ILoggingEvent event, Phase phase) {
        // Only applies to phases that are part of a group
        if (phase != Phase.ITEM && phase != Phase.ITEM_LAST && 
            phase != Phase.SUBITEM && phase != Phase.INFO) {
            return false;
        }

        // Only applies to non-DEBUG log levels
        if (event.getLevel().levelInt <= Level.DEBUG_INT) {
            return false;
        }

        // Always use group prefixes when explicitly set - the caller knows what they're doing
        // This is the fix: don't force STANDALONE when using BootstrapLogger
        // The shouldUseStandalonePrefix logic was too aggressive
        return false;
    }
}
