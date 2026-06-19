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

import java.util.Map;
import java.util.UUID;

import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

import com.hivemq.client.mqtt.MqttClient;

/**
 * RESTHeart provider plugin that supplies a configured and connected HiveMQ {@link MqttClient}.
 * <p>
 * This provider extracts MQTT configuration options from RESTHeart configuration,
 * initializes the {@link MqttClientSingleton}, and triggers the initial connection to the MQTT broker.
 * </p>
 *
 * @see Provider
 * @see MqttClient
 * @see MqttClientSingleton
 */
@RegisterPlugin(
    name = "mqtt-client",
    description = "Provides a connected MQTT client",
    priority = 10
)
public class MqttClientProvider implements Provider<MqttClient>{
    
    /**
     * Configuration map containing properties for the MQTT client plugin,
     * injected automatically by RESTHeart.
     */
    @Inject("config")
    private Map<String, Object> config;

    /**
     * Initializes the MQTT client configuration, sets default values for missing configuration keys,
     * configures the {@link MqttClientSingleton}, and establishes the initial connection to the broker.
     * <p>
     * Supported configuration keys in the {@code config} map:
     * <ul>
     *   <li>{@code broker-url} - The broker endpoint URL (default: "tcp://localhost:1883")</li>
     *   <li>{@code protocol-version} - MQTT version, 3 or 5 (default: 3)</li>
     *   <li>{@code client-id} - Identifier for the MQTT client (default: "restheart-" + random UUID)</li>
     *   <li>{@code username} - Username for broker authentication (default: null)</li>
     *   <li>{@code password} - Password for broker authentication (default: null)</li>
     *   <li>{@code clean-session} - Whether to discard session state on connection (default: true)</li>
     *   <li>{@code keep-alive-seconds} - Keep-alive time interval (default: 60)</li>
     *   <li>{@code connect-timeout-seconds} - Timeout for establishing connection (default: 10)</li>
     *   <li>{@code tls} - Enable SSL/TLS encryption (default: false)</li>
     *   <li>{@code session-expiry-seconds} - Session expiry interval (default: 0)</li>
     *   <li>{@code reconnect/enabled} - Enable automatic reconnect (default: true)</li>
     *   <li>{@code reconnect/initial-delay-ms} - Reconnect initial delay in ms (default: 1000)</li>
     *   <li>{@code reconnect/max-delay-ms} - Reconnect max delay in ms (default: 30000)</li>
     * </ul>
     * </p>
     */
    @OnInit
    public void init() {
        final String brokerUrl = argOrDefault(config, "broker-url", "tcp://localhost:1883");
        final int protocolVersion = argOrDefault(config, "protocol-version", 3);
        final String clientId = argOrDefault(config, "client-id", "restheart-" + UUID.randomUUID().toString());
        final String username = argOrDefault(config, "username", null);
        final String password = argOrDefault(config, "password", null);
        final boolean cleanSession = argOrDefault(config, "clean-session", true);
        final int keepAliveSeconds = argOrDefault(config, "keep-alive-seconds", 60);
        final int connectTimeoutSeconds = argOrDefault(config, "connect-timeout-seconds", 10);
        final boolean tlsEnabled = argOrDefault(config, "tls", false);

        final long sessionExpirySeconds = argOrDefault(config, "session-expiry-seconds", 0xFFFFFFFF);

        // Reconnect configuration
        final boolean reconnectEnabled = argOrDefault(config, "reconnect/enabled", true);
        final long initialDelayMs = argOrDefault(config, "reconnect/initial-delay-ms", 1000L);
        final long maxDelayMs = argOrDefault(config, "reconnect/max-delay-ms", 30000L);

        // Initialize the singleton with configuration
        MqttClientSingleton.init(
            brokerUrl,
            protocolVersion,
            clientId,
            username,
            password,
            cleanSession,
            keepAliveSeconds,
            sessionExpirySeconds,
            connectTimeoutSeconds,
            tlsEnabled,
            reconnectEnabled,
            initialDelayMs,
            maxDelayMs
        );

        // Force first connection to MQTT broker
        MqttClientSingleton.getInstance().connect();
    }

    /**
     * Returns the singleton {@link MqttClient} instance.
     *
     * @param caller the plugin record representing the caller requesting the client
     * @return the configured and connected {@link MqttClient} instance
     */
    @Override
    public MqttClient get(final PluginRecord<?> caller) {
        return MqttClientSingleton.get().getClient();
    }
    
}
