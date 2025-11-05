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

/**
 * ThreadLocal context for tracking the current request processing phase
 * to determine appropriate log prefixes.
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestPhaseContext {
    
    private static final ThreadLocal<PhaseInfo> PHASE_CONTEXT = ThreadLocal.withInitial(PhaseInfo::new);
    
    /**
     * Phase types for request processing
     */
    public enum Phase {
        /** Phase start (uses ┌──) */
        PHASE_START,
        /** Phase end (uses └──) */
        PHASE_END,
        /** Item in phase (uses │   ├─) */
        ITEM,
        /** Last item in phase (uses │   └─) */
        ITEM_LAST,
        /** Sub-item (uses │   │  └─ or │      └─) */
        SUBITEM,
        /** Info/continuation (uses │   ) */
        INFO,
        /** No prefix (default) */
        NONE
    }
    
    /**
     * Set the current phase
     * 
     * @param phase the phase to set
     */
    public static void setPhase(Phase phase) {
        PHASE_CONTEXT.get().phase = phase;
    }
    
    /**
     * Set whether this is the last item (affects prefix generation)
     * 
     * @param isLast true if this is the last item
     */
    public static void setLast(boolean isLast) {
        PHASE_CONTEXT.get().isLast = isLast;
    }
    
    /**
     * Get the current phase
     * 
     * @return the current phase
     */
    public static Phase getPhase() {
        return PHASE_CONTEXT.get().phase;
    }
    
    /**
     * Check if this is the last item
     * 
     * @return true if this is the last item
     */
    public static boolean isLast() {
        return PHASE_CONTEXT.get().isLast;
    }
    
    /**
     * Clear the phase context (call at end of request)
     */
    public static void clear() {
        PHASE_CONTEXT.remove();
    }
    
    /**
     * Reset to default values
     */
    public static void reset() {
        PHASE_CONTEXT.get().phase = Phase.NONE;
        PHASE_CONTEXT.get().isLast = false;
    }
    
    /**
     * Internal class to hold phase information
     */
    private static class PhaseInfo {
        Phase phase = Phase.NONE;
        boolean isLast = false;
    }
}
