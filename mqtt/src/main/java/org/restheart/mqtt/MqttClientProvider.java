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

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * <p>
 * RESTHeart has no plugin shutdown callback, so this provider registers a JVM shutdown hook
 * (once) that closes the {@link MqttClientSingleton}, cancelling any pending automatic
 * reconnect before disconnecting.
 * </p>
 *
 * @see Provider
 * @see MqttClient
 * @see MqttClientSingleton
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name = "mqtt-client",
    description = "Provides a connected MQTT client",
    priority = 10
)
public class MqttClientProvider implements Provider<MqttClient>{

    /** Guards against registering the shutdown hook more than once per classloader. */
    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);

    /** Package-private, for test visibility only: the currently registered shutdown hook, if any. */
    static volatile Thread mqttShutdownHookThread;

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
     *   <li>{@code client-id} - Identifier for the MQTT client; if null or blank, defaults to
     *       "restheart-" + random UUID, otherwise used verbatim</li>
     *   <li>{@code username} - Username for broker authentication (default: null)</li>
     *   <li>{@code password} - Password for broker authentication (default: null)</li>
     *   <li>{@code clean-session} - Whether to discard session state on connection (default: false)</li>
     *   <li>{@code keep-alive-seconds} - Keep-alive time interval (default: 60)</li>
     *   <li>{@code connect-timeout-seconds} - Timeout for establishing connection (default: 10)</li>
     *   <li>{@code tls} - Force SSL/TLS encryption regardless of the broker-url scheme (default: false)</li>
     *   <li>{@code tls-trust-store} - Path to a JKS/PKCS12 trust store for validating the broker's
     *       certificate (default: null, uses the JVM default trust manager)</li>
     *   <li>{@code tls-trust-store-password} - Password protecting {@code tls-trust-store} (default: null)</li>
     *   <li>{@code session-expiry-seconds} - Session expiry interval, MQTT 5 only (default: 4294967295,
     *       i.e. the session never expires)</li>
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
        final String clientId = argOrDefault(config, "client-id", null);
        final String username = argOrDefault(config, "username", null);
        final String password = argOrDefault(config, "password", null);
        final boolean cleanSession = argOrDefault(config, "clean-session", false);
        final int keepAliveSeconds = argOrDefault(config, "keep-alive-seconds", 60);
        final int connectTimeoutSeconds = argOrDefault(config, "connect-timeout-seconds", 10);
        final boolean tlsEnabled = argOrDefault(config, "tls", false);
        final String tlsTrustStore = argOrDefault(config, "tls-trust-store", null);
        final String tlsTrustStorePassword = argOrDefault(config, "tls-trust-store-password", null);

        final long sessionExpirySeconds = argOrDefault(config, "session-expiry-seconds", 0xFFFFFFFFL);

        // Reconnect configuration
        @SuppressWarnings("unchecked")
        final Map<String, Object> reconnectConfigMap = (Map<String, Object>) config.get("reconnect");
        final boolean reconnectEnabled = argOrDefault(reconnectConfigMap, "enabled", true);
        final long initialDelayMs = argOrDefault(reconnectConfigMap, "initial-delay-ms", 1000L);
        final long maxDelayMs = argOrDefault(reconnectConfigMap, "max-delay-ms", 30000L);

        // Will message configuration
        @SuppressWarnings("unchecked")
        final Map<String, Object> willConfigMap = (Map<String, Object>) config.get("will");
        final String willTopic = argOrDefault(willConfigMap, "topic", null);
        final String willPayload = argOrDefault(willConfigMap, "payload", null);
        final int willQos = argOrDefault(willConfigMap, "qos", 0);
        final boolean willRetain = argOrDefault(willConfigMap, "retain", false);
        final long willDelaySeconds = argOrDefault(willConfigMap, "delay-seconds", 0L);
        final Long willMessageExpirySeconds = argOrDefault(willConfigMap, "message-expiry-seconds", null);

        // Build configuration objects
        MqttConfig.ReconnectConfig reconnectConfig= new MqttConfig.ReconnectConfig(
            reconnectEnabled, 
            initialDelayMs, 
            maxDelayMs);

        MqttConfig.WillConfig willConfig = new MqttConfig.WillConfig(willTopic, willPayload, willQos, willRetain, willDelaySeconds, willMessageExpirySeconds);

        MqttConfig mqttConfig = new MqttConfig.Builder()
            .brokerUrl(brokerUrl)
            .protocolVersion(protocolVersion)
            .clientId(clientId)
            .username(username)
            .password(password)
            .cleanSession(cleanSession)
            .keepAliveSeconds(keepAliveSeconds)
            .sessionExpirySeconds(sessionExpirySeconds)
            .connectTimeoutSeconds(connectTimeoutSeconds)
            .tlsEnabled(tlsEnabled)
            .tlsTrustStore(tlsTrustStore)
            .tlsTrustStorePassword(tlsTrustStorePassword)
            .reconnectConfig(reconnectConfig)
            .willConfig(willConfig)
            .build();

        // Initialize the singleton with configuration
        MqttClientSingleton.init(mqttConfig);

        // Force first connection to MQTT broker
        MqttClientSingleton.getInstance().connect();

        // RESTHeart has no plugin shutdown callback (Bootstrapper.stopServer does not notify
        // plugins), so a JVM shutdown hook is the only way to release the MQTT client cleanly.
        registerShutdownHookOnce();
    }

    /**
     * Registers, at most once per classloader, a JVM shutdown hook that closes the
     * {@link MqttClientSingleton}.
     */
    private static void registerShutdownHookOnce() {
        if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            final Thread hook = new Thread(() -> {
                if (MqttClientSingleton.isInitialized()) {
                    MqttClientSingleton.getInstance().close();
                }
            }, "mqtt-client-shutdown");

            Runtime.getRuntime().addShutdownHook(hook);
            mqttShutdownHookThread = hook;
        }
    }

    /**
     * Returns the singleton {@link MqttClient} instance.
     *
     * @param caller the plugin record representing the caller requesting the client
     * @return the configured and connected {@link MqttClient} instance
     */
    @Override
    public MqttClient get(final PluginRecord<?> caller) {
        return MqttClientSingleton.getInstance().getClient();
    }

}
