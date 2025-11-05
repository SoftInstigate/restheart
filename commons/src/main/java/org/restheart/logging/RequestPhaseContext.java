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

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * ScopedValue-based context for tracking the current request processing phase
 * to determine appropriate log prefixes. This is virtual thread-friendly as it uses
 * ScopedValue to hold the exchange reference and exchange attachments for data storage.
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestPhaseContext {
    
    private static final ScopedValue<HttpServerExchange> CURRENT_EXCHANGE = ScopedValue.newInstance();
    private static final AttachmentKey<PhaseInfo> PHASE_INFO_KEY = AttachmentKey.create(PhaseInfo.class);
    
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
     * Run a task with the exchange set in the ScopedValue context.
     * This should be called at the entry point of request handling.
     * 
     * @param exchange the HttpServerExchange
     * @param task the task to run with the exchange in scope
     * @throws Exception if the task throws an exception
     */
    public static void runWithExchange(HttpServerExchange exchange, ThrowingRunnable task) throws Exception {
        ScopedValue.where(CURRENT_EXCHANGE, exchange).call(() -> {
            task.run();
            return null;
        });
    }
    
    /**
     * Call a task with the exchange set in the ScopedValue context.
     * This is a convenience method for async operations that need to propagate the exchange.
     * 
     * @param task the task to run with the exchange in scope
     */
    public static void callWithCurrentExchange(Runnable task) {
        if (CURRENT_EXCHANGE.isBound()) {
            var exchange = CURRENT_EXCHANGE.get();
            ScopedValue.where(CURRENT_EXCHANGE, exchange).run(task);
        } else {
            task.run();
        }
    }
    
    /**
     * Set the current phase
     * 
     * @param phase the phase to set
     */
    public static void setPhase(Phase phase) {
        if (CURRENT_EXCHANGE.isBound()) {
            var exchange = CURRENT_EXCHANGE.get();
            var current = exchange.getAttachment(PHASE_INFO_KEY);
            if (current == null) {
                current = new PhaseInfo();
            }
            exchange.putAttachment(PHASE_INFO_KEY, new PhaseInfo(phase, current.isLast));
        }
        // If not bound, silently ignore (e.g., during bootstrap logging)
    }
    
    /**
     * Set whether this is the last item (affects prefix generation)
     * 
     * @param isLast true if this is the last item
     */
    public static void setLast(boolean isLast) {
        if (CURRENT_EXCHANGE.isBound()) {
            var exchange = CURRENT_EXCHANGE.get();
            var current = exchange.getAttachment(PHASE_INFO_KEY);
            if (current == null) {
                current = new PhaseInfo();
            }
            exchange.putAttachment(PHASE_INFO_KEY, new PhaseInfo(current.phase, isLast));
        }
    }
    
    /**
     * Get the current phase
     * 
     * @return the current phase
     */
    public static Phase getPhase() {
        if (CURRENT_EXCHANGE.isBound()) {
            var exchange = CURRENT_EXCHANGE.get();
            var info = exchange.getAttachment(PHASE_INFO_KEY);
            if (info != null) {
                return info.phase;
            }
        }
        return Phase.NONE;
    }
    
    /**
     * Check if this is the last item
     * 
     * @return true if this is the last item
     */
    public static boolean isLast() {
        if (CURRENT_EXCHANGE.isBound()) {
            var exchange = CURRENT_EXCHANGE.get();
            var info = exchange.getAttachment(PHASE_INFO_KEY);
            if (info != null) {
                return info.isLast;
            }
        }
        return false;
    }
    
    /**
     * Clear the phase context
     */
    public static void clear() {
        if (CURRENT_EXCHANGE.isBound()) {
            var exchange = CURRENT_EXCHANGE.get();
            exchange.removeAttachment(PHASE_INFO_KEY);
        }
    }
    
    /**
     * Reset to default values
     */
    public static void reset() {
        if (CURRENT_EXCHANGE.isBound()) {
            var exchange = CURRENT_EXCHANGE.get();
            exchange.putAttachment(PHASE_INFO_KEY, new PhaseInfo());
        }
    }
    
    /**
     * Functional interface for tasks that can throw exceptions
     */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
    
    /**
     * Internal class to hold phase information (immutable)
     */
    private static class PhaseInfo {
        final Phase phase;
        final boolean isLast;
        
        PhaseInfo() {
            this(Phase.NONE, false);
        }
        
        PhaseInfo(Phase phase, boolean isLast) {
            this.phase = phase;
            this.isLast = isLast;
        }
    }
}
