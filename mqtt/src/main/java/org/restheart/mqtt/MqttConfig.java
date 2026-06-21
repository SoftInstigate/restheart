/*-
 * ========================LICENSE_START=================================
 * restheart-mongoclient-provider
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

/**
 * Configuration class for MQTT client settings.
 * <p>
 * This class encapsulates all configuration parameters needed to initialize
 * an MQTT client connection. It uses the Builder pattern to provide a fluent
 * API for constructing configuration instances.
 * </p>
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 */
public class MqttConfig {
    
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
    private ReconnectConfig reconnectConfig;
    private WillConfig willConfig;
   

    private MqttConfig(Builder builder) {
        this.brokerUrl = builder.brokerUrl;
        this.protocolVersion = builder.protocolVersion;
        this.clientId = builder.clientId;
        this.username = builder.username;
        this.password = builder.password;
        this.cleanSession = builder.cleanSession;
        this.keepAliveSeconds = builder.keepAliveSeconds;
        this.sessionExpirySeconds = builder.sessionExpirySeconds;
        this.connectTimeoutSeconds = builder.connectTimeoutSeconds;
        this.tlsEnabled = builder.tlsEnabled;
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
        private boolean enabled;
        private long initialDelayMs;
        private long maxDelayMs;

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
        private  String willTopic;
        private  String willPayload;
        private  int willQos;
        private  boolean willRetain;
        private  long willDelaySeconds;
        private  Long willMessageExpirySeconds;

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
        private  String brokerUrl;
        private  int protocolVersion;
        private  String clientId;
        private  String username;
        private  String password;
        private  boolean cleanSession;
        private  int keepAliveSeconds;
        private  long sessionExpirySeconds;
        private  int connectTimeoutSeconds;
        private  boolean tlsEnabled;
        private  ReconnectConfig reconnectConfig;
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

        public Builder reconnectConfig(ReconnectConfig reconnectConfig) {
            this.reconnectConfig = reconnectConfig;
            return this;
        }

        public Builder willConfig(WillConfig willConfig) {
            this.willConfig = willConfig;
            return this;
        }

        public MqttConfig build() {
            return new MqttConfig(this);
        }
    }
}
