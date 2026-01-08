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
package org.restheart.security.tokens;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.restheart.configuration.ConfigurationException;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.BootstrapLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides consistent JWT configuration across all JWT-related components.
 * <p>
 * This provider ensures that jwtTokenManager and jwtAuthenticationMechanism
 * use the same key, algorithm, issuer, and audience for signing and verifying tokens.
 * If no key is configured, it generates a secure random key for the session.
 * </p>
 * <p>
 * Configuration:
 * <ul>
 *   <li><code>key</code> - The JWT signing key (optional). If null, a secure random key is generated.</li>
 *   <li><code>algorithm</code> - The signing algorithm (default: HS256)</li>
 *   <li><code>issuer</code> - The token issuer (default: restheart.org)</li>
 *   <li><code>audience</code> - The token audience (optional, can be String or Array)</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Important for Clustered Deployments:</strong><br>
 * In a clustered deployment, all nodes must use the same JWT configuration to verify tokens
 * issued by other nodes. Configure the same values in all nodes' configuration files.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
    name = "jwtConfigProvider",
    description = "Provides consistent JWT configuration (key, algorithm, issuer, audience) for token generation and verification",
    enabledByDefault = true
)
public class JwtConfigProvider implements Provider<JwtConfigProvider.JwtConfig> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtConfigProvider.class);
    private static final int KEY_SIZE_BYTES = 64; // 512 bits for HMAC algorithms

    @Inject("config")
    private Map<String, Object> config;

    private JwtConfig jwtConfig;

    @OnInit
    public void init() throws ConfigurationException {
        BootstrapLogger.startPhase(LOGGER, "JWT CONFIGURATION");
        
        String configuredKey = argOrDefault(config, "key", null);
        String key;

        if (configuredKey == null) {
            // Generate secure random key
            key = generateSecureRandomKey();
            BootstrapLogger.info(LOGGER, "No JWT key configured. Generated secure random key for this session.");
            BootstrapLogger.warnSubItem(LOGGER, "IMPORTANT: In clustered deployments, all nodes must use the same JWT key!");
            BootstrapLogger.warnSubItem(LOGGER, "Configure 'key' in jwtConfigProvider to ensure consistent token verification across nodes.");
        } else if ("secret".equals(configuredKey)) {
            // Reject insecure default key
            LOGGER.error("❌ SECURITY RISK: Cannot use default 'secret' key!");
            throw new ConfigurationException(
                "Using default 'secret' key is insecure. " +
                "Set key to null in jwtConfigProvider configuration to auto-generate a secure key, " +
                "or provide your own secure key (minimum 32 characters)."
            );
        } else if (configuredKey.length() < 32) {
            // Reject weak keys
            LOGGER.error("❌ SECURITY RISK: JWT key is too short (minimum 32 characters required)");
            throw new ConfigurationException(
                "JWT key must be at least 32 characters long for security. " +
                "Current key length: " + configuredKey.length()
            );
        } else {
            key = configuredKey;
            BootstrapLogger.info(LOGGER, "Using configured JWT key");
        }

        String algorithm = argOrDefault(config, "algorithm", "HS256");
        String issuer = argOrDefault(config, "issuer", "restheart.org");
        
        // Handle audience - can be null, String, or Array
        var _audience = argOrDefault(config, "audience", null);
        List<String> audience = new ArrayList<>();
        
        if (_audience == null) {
            // null is valid - no audience validation
        } else if (_audience instanceof String audienceStr) {
            audience.add(audienceStr);
        } else if (_audience instanceof List<?> audienceList) {
            audienceList.stream()
                .filter(String.class::isInstance)
                .map(e -> (String) e)
                .forEach(audience::add);
        } else {
            throw new ConfigurationException("Wrong audience, must be a String or an Array of Strings");
        }

        this.jwtConfig = new JwtConfig(key, algorithm, issuer, audience.isEmpty() ? null : audience.toArray(String[]::new));
        
        BootstrapLogger.info(LOGGER, "Algorithm: {}, Issuer: {}, Audience: {}", 
            algorithm, issuer, audience.isEmpty() ? "null" : String.join(", ", audience));
        
        BootstrapLogger.endPhase(LOGGER, "JWT CONFIGURATION COMPLETED");
    }

    @Override
    public JwtConfig get(PluginRecord<?> caller) {
        LOGGER.debug("Providing JWT config to: {}", caller.getName());
        return this.jwtConfig;
    }

    /**
     * Generates a cryptographically secure random key for JWT signing.
     *
     * @return a Base64-encoded random key
     */
    private String generateSecureRandomKey() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] keyBytes = new byte[KEY_SIZE_BYTES];
        secureRandom.nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    /**
     * JWT configuration record containing all shared JWT settings
     */
    public record JwtConfig(String key, String algorithm, String issuer, String[] audience) {
        public boolean hasAudience() {
            return audience != null && audience.length > 0;
        }
    }
}
