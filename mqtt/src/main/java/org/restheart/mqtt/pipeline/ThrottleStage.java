/*-
 * ========================LICENSE_START=================================
 * restheart-mqtt
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

package org.restheart.mqtt.pipeline;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.restheart.mqtt.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pipeline stage that rate-limits messages using a token bucket algorithm.
 * 
 * This stage is per-connection, meaning each SSE client has its own throttle.
 * Messages exceeding the rate limit are dropped (never blocks).
 * 
 * Token bucket algorithm:
 * - Tokens are added at a constant rate (maxEventsPerSecond)
 * - Each message consumes one token
 * - If no tokens available, message is dropped
 * - Bucket capacity = maxEventsPerSecond (allows bursts)
 * 
 * Example:
 * <pre>
 * // Allow max 10 messages per second per connection
 * new ThrottleStage(10)
 * </pre>
 * 
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 */
public class ThrottleStage implements MqttEventStage {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ThrottleStage.class);
    
    private final int maxEventsPerSecond;
    private final AtomicLong tokens;
    private final AtomicLong lastRefillTime;
    private final AtomicLong droppedCount;
    
    /**
     * Create a throttle stage
     * 
     * @param maxEventsPerSecond Maximum events per second (also bucket capacity)
     */
    public ThrottleStage(int maxEventsPerSecond) {
        if (maxEventsPerSecond <= 0) {
            throw new IllegalArgumentException("maxEventsPerSecond must be positive");
        }
        
        this.maxEventsPerSecond = maxEventsPerSecond;
        this.tokens = new AtomicLong(maxEventsPerSecond); // Start with full bucket
        this.lastRefillTime = new AtomicLong(System.nanoTime());
        this.droppedCount = new AtomicLong(0);
    }
    
    @Override
    public Optional<MqttMessage> process(MqttMessage message) {
        refillTokens();
        
        // Try to consume a token
        long currentTokens = tokens.get();
        
        if (currentTokens > 0) {
            // Token available - consume it
            if (tokens.compareAndSet(currentTokens, currentTokens - 1)) {
                return Optional.of(message);
            } else {
                // CAS failed, retry
                return process(message);
            }
        } else {
            // No tokens available - drop message
            long dropped = droppedCount.incrementAndGet();
            
            if (dropped % 100 == 0) {
                LOGGER.warn("Throttle stage dropped {} messages (rate limit: {} msg/s)", 
                    dropped, maxEventsPerSecond);
            }
            
            return Optional.empty();
        }
    }
    
    /**
     * Refill tokens based on elapsed time
     */
    private void refillTokens() {
        long now = System.nanoTime();
        long lastRefill = lastRefillTime.get();
        long elapsedNanos = now - lastRefill;
        
        // Calculate tokens to add based on elapsed time
        // tokens = (elapsed seconds) * (tokens per second)
        long tokensToAdd = (elapsedNanos * maxEventsPerSecond) / 1_000_000_000L;
        
        if (tokensToAdd > 0) {
            // Try to update last refill time
            if (lastRefillTime.compareAndSet(lastRefill, now)) {
                // Add tokens up to capacity
                long currentTokens = tokens.get();
                long newTokens = Math.min(currentTokens + tokensToAdd, maxEventsPerSecond);
                tokens.set(newTokens);
            }
        }
    }
    
    /**
     * @return Number of messages dropped by this throttle
     */
    public long getDroppedCount() {
        return droppedCount.get();
    }
    
    /**
     * @return Current number of available tokens
     */
    public long getAvailableTokens() {
        return tokens.get();
    }
    
    /**
     * Reset the throttle (refill tokens and clear dropped count)
     */
    public void reset() {
        tokens.set(maxEventsPerSecond);
        lastRefillTime.set(System.nanoTime());
        droppedCount.set(0);
    }
}