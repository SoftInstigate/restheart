/*-
 * ========================LICENSE_START=================================
 * restheart-core
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
package org.restheart.handlers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RequestLogger pattern matching functionality
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestLoggerTest {
    
    private RequestLogger requestLogger;
    private Method matchesPatternMethod;
    
    @BeforeEach
    void setUp() throws Exception {
        requestLogger = new RequestLogger();
        // Access the private method for testing
        matchesPatternMethod = RequestLogger.class.getDeclaredMethod("matchesPattern", String.class, String.class);
        matchesPatternMethod.setAccessible(true);
    }
    
    @Test
    void testExactMatches() throws Exception {
        assertTrue(matchesPattern("/ping", "/ping"));
        assertTrue(matchesPattern("/health", "/health"));
        assertTrue(matchesPattern("/_ping", "/_ping"));
        
        assertFalse(matchesPattern("/ping", "/health"));
        assertFalse(matchesPattern("/ping-test", "/ping"));
        assertFalse(matchesPattern("/test/ping", "/ping"));
    }
    
    @Test
    void testWildcardMatches() throws Exception {
        // Simple wildcard at end
        assertTrue(matchesPattern("/monitoring/health", "/monitoring/*"));
        assertTrue(matchesPattern("/monitoring/status", "/monitoring/*"));
        assertTrue(matchesPattern("/monitoring/", "/monitoring/*"));
        
        // Wildcard in middle
        assertTrue(matchesPattern("/api/v1/status", "/api/*/status"));
        assertTrue(matchesPattern("/api/v2/status", "/api/*/status"));
        assertTrue(matchesPattern("/api/beta/status", "/api/*/status"));
        
        // Multiple wildcards
        assertTrue(matchesPattern("/api/v1/test/status", "/api/*/test/*"));
        
        assertFalse(matchesPattern("/api/status", "/api/*/status")); // missing middle part
        assertFalse(matchesPattern("/api/v1/health", "/api/*/status")); // wrong ending
    }
    
    @Test
    void testComplexPatterns() throws Exception {
        // Mixed patterns
        assertTrue(matchesPattern("/app/v1.0/health", "/app/v*/health"));
        assertTrue(matchesPattern("/app/v2.5/health", "/app/v*/health"));
        
        assertFalse(matchesPattern("/app/beta/health", "/app/v*/health"));
        assertFalse(matchesPattern("/app/v1.0/status", "/app/v*/health"));
    }
    
    private boolean matchesPattern(String requestPath, String pattern) throws Exception {
        return (Boolean) matchesPatternMethod.invoke(requestLogger, requestPath, pattern);
    }
}
