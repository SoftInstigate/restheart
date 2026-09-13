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

package org.restheart.mqtt;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Configuration class for MQTT client settings.
 * <p>
 * This class encapsulates all configuration parameters needed to initialize
 * an MQTT client connection. It uses the Builder pattern to provide a fluent
 * API for constructing configuration instances.
 * </p>
 * <p>
 * Instances are immutable: every field is set once from the {@link Builder} and
 * never changed afterwards. {@link Builder#build()} validates the configuration
 * and throws {@link IllegalArgumentException} naming the offending configuration
 * key when a value is invalid.
 * </p>
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttConfig {

    /** Broker URL schemes supported by {@code broker-url}. */
    static final Set<String> SUPPORTED_SCHEMES = Set.of("tcp", "ssl", "mqtts", "ws", "wss");

    private final String brokerUrl;
    private final int protocolVersion;
    private final String clientId;
    private final String username;
    private final String password;
    private final boolean cleanSession;
    private final int keepAliveSeconds;
    private final long sessionExpirySeconds;
    private final int connectTimeoutSeconds;
    private final boolean tlsEnabled;
    private final String tlsTrustStore;
    private final String tlsTrustStorePassword;
    private final ReconnectConfig reconnectConfig;
    private final WillConfig willConfig;

    private MqttConfig(Builder builder) {
        this.brokerUrl = builder.brokerUrl;
        this.protocolVersion = builder.protocolVersion;
        this.clientId = (builder.clientId == null || builder.clientId.isBlank())
            ? "restheart-" + UUID.randomUUID()
            : builder.clientId;
        this.username = builder.username;
        this.password = builder.password;
        this.cleanSession = builder.cleanSession;
        this.keepAliveSeconds = builder.keepAliveSeconds;
        this.sessionExpirySeconds = builder.sessionExpirySeconds;
        this.connectTimeoutSeconds = builder.connectTimeoutSeconds;
        this.tlsEnabled = builder.tlsEnabled;
        this.tlsTrustStore = builder.tlsTrustStore;
        this.tlsTrustStorePassword = builder.tlsTrustStorePassword;
        this.reconnectConfig = builder.reconnectConfig;
        this.willConfig = builder.willConfig;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public String getClientId() {
        return clientId;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public long getSessionExpirySeconds() {
        return sessionExpirySeconds;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    /**
     * @return the path to the JKS/PKCS12 trust store used to validate the broker's
     *         TLS certificate, or {@code null} to use the JVM default trust manager
     */
    public String getTlsTrustStore() {
        return tlsTrustStore;
    }

    /**
     * @return the password protecting {@link #getTlsTrustStore()}, or {@code null}
     */
    public String getTlsTrustStorePassword() {
        return tlsTrustStorePassword;
    }

    public ReconnectConfig getReconnectConfig() {
        return reconnectConfig;
    }

    public WillConfig getWillConfig() {
        return willConfig;
    }

    /**
     * Configuration for automatic reconnection behavior.
     */
    public static class ReconnectConfig {
        private final boolean enabled;
        private final long initialDelayMs;
        private final long maxDelayMs;

        public ReconnectConfig(boolean enabled, long initialDelayMs, long maxDelayMs) {
            this.enabled = enabled;
            this.initialDelayMs = initialDelayMs;
            this.maxDelayMs = maxDelayMs;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public long getMaxDelayMs() {
            return maxDelayMs;
        }
    }

    /**
     * Configuration for MQTT Last will message.
     */
    public static class WillConfig {
        private final String willTopic;
        private final String willPayload;
        private final int willQos;
        private final boolean willRetain;
        private final long willDelaySeconds;
        private final Long willMessageExpirySeconds;

        public WillConfig(String topic, String payload, int Qos, boolean retain,
            long delaySeconds, Long messageExpirySeconds) {

            this.willTopic = topic;
            this.willPayload = payload;
            this.willQos = Qos;
            this.willRetain = retain;
            this.willDelaySeconds = delaySeconds;
            this.willMessageExpirySeconds = messageExpirySeconds;
        }

        public String getWillTopic() {
            return willTopic;
        }

        public String getWillPayload() {
            return willPayload;
        }

        public int getWillQos() {
            return willQos;
        }

        public boolean getWillRetain() {
            return willRetain;
        }

        public long getWillDelaySeconds() {
            return willDelaySeconds;
        }

        public Long getWillMessageExpirySeconds() {
            return willMessageExpirySeconds;
        }
    }

    /**
     * Builder for creating MqttConfig instances.
     */
    public static class Builder {
        private String brokerUrl;
        private int protocolVersion;
        private String clientId;
        private String username;
        private String password;
        private boolean cleanSession;
        private int keepAliveSeconds;
        private long sessionExpirySeconds;
        private int connectTimeoutSeconds;
        private boolean tlsEnabled;
        private String tlsTrustStore;
        private String tlsTrustStorePassword;
        private ReconnectConfig reconnectConfig;
        private WillConfig willConfig;

        public Builder brokerUrl(String brokerUrl) {
            this.brokerUrl = brokerUrl;
            return this;
        }

        public Builder protocolVersion(int protocolVersion) {
            this.protocolVersion = protocolVersion;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder cleanSession(boolean cleanSession) {
            this.cleanSession = cleanSession;
            return this;
        }

        public Builder keepAliveSeconds(int keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
            return this;
        }

        public Builder sessionExpirySeconds(long sessionExpirySeconds) {
            this.sessionExpirySeconds = sessionExpirySeconds;
            return this;
        }

        public Builder connectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
            return this;
        }

        public Builder tlsEnabled(boolean tlsEnabled) {
            this.tlsEnabled = tlsEnabled;
            return this;
        }

        public Builder tlsTrustStore(String tlsTrustStore) {
            this.tlsTrustStore = tlsTrustStore;
            return this;
        }

        public Builder tlsTrustStorePassword(String tlsTrustStorePassword) {
            this.tlsTrustStorePassword = tlsTrustStorePassword;
            return this;
        }

        public Builder reconnectConfig(ReconnectConfig reconnectConfig) {
            this.reconnectConfig = reconnectConfig;
            return this;
        }

        public Builder willConfig(WillConfig willConfig) {
            this.willConfig = willConfig;
            return this;
        }

        /**
         * Validates the accumulated configuration and builds an immutable {@link MqttConfig}.
         *
         * @return the validated, immutable configuration
         * @throws IllegalArgumentException if any configuration value is invalid; the message
         *         names the offending configuration key
         */
        public MqttConfig build() {
            if (protocolVersion != 3 && protocolVersion != 5) {
                throw new IllegalArgumentException(
                    "'protocol-version' must be 3 or 5, got: " + protocolVersion);
            }

            if (brokerUrl == null || brokerUrl.isBlank()) {
                throw new IllegalArgumentException("'broker-url' must not be null or blank");
            }

            final URI uri;
            try {
                uri = new URI(brokerUrl);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("'broker-url' is not a valid URI: " + brokerUrl, e);
            }

            if (uri.getHost() == null) {
                throw new IllegalArgumentException("'broker-url' must specify a host: " + brokerUrl);
            }

            final String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            if (scheme == null || !SUPPORTED_SCHEMES.contains(scheme)) {
                throw new IllegalArgumentException("'broker-url' has an unsupported scheme '" + scheme
                    + "', supported schemes are: " + SUPPORTED_SCHEMES);
            }

            if (keepAliveSeconds < 0) {
                throw new IllegalArgumentException(
                    "'keep-alive-seconds' must be >= 0, got: " + keepAliveSeconds);
            }

            if (connectTimeoutSeconds <= 0) {
                throw new IllegalArgumentException(
                    "'connect-timeout-seconds' must be > 0, got: " + connectTimeoutSeconds);
            }

            if (willConfig != null) {
                final int qos = willConfig.getWillQos();
                if (qos < 0 || qos > 2) {
                    throw new IllegalArgumentException("'will/qos' must be between 0 and 2, got: " + qos);
                }
            }

            return new MqttConfig(this);
        }
    }
}
