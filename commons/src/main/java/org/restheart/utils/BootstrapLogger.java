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
package org.restheart.utils;

import org.slf4j.Logger;

/**
 * Utility class for consistent bootstrap/startup logging with tree-style prefixes.
 * This class provides methods to log bootstrap messages with visual hierarchy
 * using box-drawing characters.
 * 
 * Usage example:
 * <pre>
 * BootstrapLogger.startPhase(LOGGER, "PLUGIN INITIALIZATION");
 * BootstrapLogger.info(LOGGER, "Loading plugins from: {}", pluginPath);
 * BootstrapLogger.item(LOGGER, "myPlugin (MyPlugin) - Priority: 100");
 * BootstrapLogger.subItem(LOGGER, "✓ Loaded successfully");
 * BootstrapLogger.endPhase(LOGGER, "PLUGIN INITIALIZATION COMPLETED");
 * </pre>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BootstrapLogger {
    
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
    
    private BootstrapLogger() {
        // Utility class, no instantiation
    }
    
    /**
     * Start a bootstrap phase
     * 
     * @param logger the logger to use
     * @param message the message
     * @param args message arguments
     */
    public static void startPhase(Logger logger, String message, Object... args) {
        logger.info(PHASE_START + message, args);
    }
    
    /**
     * End a bootstrap phase
     * 
     * @param logger the logger to use
     * @param message the message
     * @param args message arguments
     */
    public static void endPhase(Logger logger, String message, Object... args) {
        logger.info(PHASE_END + message, args);
    }
    
    /**
     * Log an item within a phase (not the last item)
     * 
     * @param logger the logger to use
     * @param message the message
     * @param args message arguments
     */
    public static void item(Logger logger, String message, Object... args) {
        logger.info(ITEM + message, args);
    }
    
    /**
     * Log the last item within a phase
     * 
     * @param logger the logger to use
     * @param message the message
     * @param args message arguments
     */
    public static void lastItem(Logger logger, String message, Object... args) {
        logger.info(ITEM_LAST + message, args);
    }
    
    /**
     * Log a sub-item (e.g., success/failure under an item)
     * 
     * @param logger the logger to use
     * @param message the message
     * @param args message arguments
     */
    public static void subItem(Logger logger, String message, Object... args) {
        logger.info(SUBITEM + message, args);
    }
    
    /**
     * Log the last sub-item
     * 
     * @param logger the logger to use
     * @param message the message
     * @param args message arguments
     */
    public static void lastSubItem(Logger logger, String message, Object... args) {
        logger.info(SUBITEM_LAST + message, args);
    }
    
    /**
     * Log informational message within a phase (no structural markers)
     * 
     * @param logger the logger to use
     * @param message the message
     * @param args message arguments
     */
    public static void info(Logger logger, String message, Object... args) {
        logger.info(INFO + message, args);
    }
    
    /**
     * Log a debug message with item prefix
     * 
     * @param logger the logger to use
     * @param message the message
     * @param args message arguments
     */
    public static void debugItem(Logger logger, String message, Object... args) {
        logger.debug(ITEM + message, args);
    }
    
    /**
     * Log a debug message with sub-item prefix
     * 
     * @param logger the logger to use
     * @param message the message
     * @param args message arguments
     */
    public static void debugSubItem(Logger logger, String message, Object... args) {
        logger.debug(SUBITEM + message, args);
    }
    
    /**
     * Log a debug message with info prefix
     * 
     * @param logger the logger to use
     * @param message the message
     * @param args message arguments
     */
    public static void debugInfo(Logger logger, String message, Object... args) {
        logger.debug(INFO + message, args);
    }
    
    /**
     * Log an error with item prefix
     * 
     * @param logger the logger to use
     * @param message the message
     * @param args message arguments
     */
    public static void errorItem(Logger logger, String message, Object... args) {
        logger.error(ITEM + message, args);
    }
    
    /**
     * Log an error with sub-item prefix
     * 
     * @param logger the logger to use
     * @param message the message
     * @param args message arguments
     */
    public static void errorSubItem(Logger logger, String message, Object... args) {
        logger.error(SUBITEM + message, args);
    }
}
