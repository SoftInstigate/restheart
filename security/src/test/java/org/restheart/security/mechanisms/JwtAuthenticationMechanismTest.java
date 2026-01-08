/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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

package org.restheart.security.mechanisms;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.configuration.ConfigurationException;
import org.restheart.security.tokens.JwtConfigProvider;

public class JwtAuthenticationMechanismTest {
    @Test
    void testJwtConfigProviderRejectsWeakKey() {
        var provider = new JwtConfigProvider();
        
        // Inject config with short key (less than 32 characters)
        Map<String, Object> config = new HashMap<>();
        config.put("key", "tooshort");
        config.put("algorithm", "HS256");
        config.put("issuer", "test");
        config.put("audience", null);
        
        // Use reflection to set the config field
        try {
            var configField = JwtConfigProvider.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(provider, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        // Should throw ConfigurationException for weak key
        assertThrows(ConfigurationException.class, () -> provider.init());
    }
    
    @Test
    void testJwtConfigProviderRejectsSecretKey() {
        var provider = new JwtConfigProvider();
        
        // Inject config with "secret" key
        Map<String, Object> config = new HashMap<>();
        config.put("key", "secret");
        config.put("algorithm", "HS256");
        config.put("issuer", "test");
        config.put("audience", null);
        
        // Use reflection to set the config field
        try {
            var configField = JwtConfigProvider.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(provider, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        // Should throw ConfigurationException for "secret" key
        assertThrows(ConfigurationException.class, () -> provider.init());
    }
    
    @Test
    void testJwtConfigProviderAcceptsStrongKey() throws ConfigurationException {
        var provider = new JwtConfigProvider();
        
        // Inject config with strong key (32+ characters)
        Map<String, Object> config = new HashMap<>();
        config.put("key", "C0mpl3x@JWT!Key$With@UpperAndLowercase123456");
        config.put("algorithm", "HS256");
        config.put("issuer", "test");
        config.put("audience", null);
        
        // Use reflection to set the config field
        try {
            var configField = JwtConfigProvider.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(provider, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        // Should not throw exception
        provider.init();
        
        // Should provide valid config - access internal jwtConfig field directly
        try {
            var jwtConfigField = JwtConfigProvider.class.getDeclaredField("jwtConfig");
            jwtConfigField.setAccessible(true);
            var jwtConfig = (JwtConfigProvider.JwtConfig) jwtConfigField.get(provider);
            assertNotNull(jwtConfig);
            assertNotNull(jwtConfig.key());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Test
    void testJwtConfigProviderGeneratesRandomKey() throws ConfigurationException {
        var provider = new JwtConfigProvider();
        
        // Inject config with null key (should auto-generate)
        Map<String, Object> config = new HashMap<>();
        config.put("key", null);
        config.put("algorithm", "HS256");
        config.put("issuer", "test");
        config.put("audience", null);
        
        // Use reflection to set the config field
        try {
            var configField = JwtConfigProvider.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(provider, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        // Should not throw exception and generate key
        provider.init();
        
        // Should provide valid config with generated key - access internal jwtConfig field directly
        try {
            var jwtConfigField = JwtConfigProvider.class.getDeclaredField("jwtConfig");
            jwtConfigField.setAccessible(true);
            var jwtConfig = (JwtConfigProvider.JwtConfig) jwtConfigField.get(provider);
            assertNotNull(jwtConfig);
            assertNotNull(jwtConfig.key());
            // Generated key should be at least 32 characters
            assertTrue(jwtConfig.key().length() >= 32, "Generated key should be at least 32 characters");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
