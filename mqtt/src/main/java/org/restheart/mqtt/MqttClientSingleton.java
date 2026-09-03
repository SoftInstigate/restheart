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

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.restheart.utils.BootstrapLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.hivemq.client.mqtt.mqtt3.lifecycle.Mqtt3ClientConnectedContext;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import com.hivemq.client.mqtt.mqtt5.lifecycle.Mqtt5ClientConnectedContext;
import com.hivemq.client.util.TypeSwitch;

/**
 * Thread-safe singleton that manages the lifecycle of a MQTT client {@link MqttClient}.
 * <p>
 * This singleton can be configured to use either MQTT v3 or MQTT v5 protocols.
 * It provides features such as automatic reconnection, authentication (username/password),
 * TLS/SSL support, keep-alive, and session management.
 * </p>
 * <p>
 * The singleton instance is initialized via the {@link #init} method and can be retrieved
 * using {@link #getInstance()} or {@link #get()}.
 * </p>
 * <p>
 * Thread-safety: {@link #init} must be called before any other method. After initialization,
 * the configuration is immutable. The {@code connected} flag is updated atomically via
 * the connection callback and read via {@link #isConnected()}.
 * </p>
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttClientSingleton {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttClientSingleton.class);

    /** Indicates whether the singleton has been initialized with configuration parameters. */
    private static volatile boolean initialized = false;

    /** Immutable configuration snapshot, set once during {@link #init}. */
    private static volatile MqttConfig config;

    /** The underlying HiveMQ MQTT client instance. */
    private MqttClient mqttClient;

    /** Flag indicating whether the client is currently connected. */
    private volatile boolean connected = false;

    /**
     * Initializes the MQTT client singleton with the specified configuration.
     * Must be called once before any other method.
     *
     * @param clientConfig the MQTT configuration containing all connection parameters
     */
    public static void init(MqttConfig clientConfig) {
        if (clientConfig == null) {
            throw new IllegalArgumentException("clientConfig must not be null");
        }
        config = clientConfig;
        initialized = true;
    }

    /**
     * Checks if the singleton has been initialized.
     *
     * @return {@code true} if initialized, {@code false} otherwise
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Returns the configuration snapshot. Available only after {@link #init}.
     *
     * @return the immutable configuration, or {@code null} if not initialized
     */
    static MqttConfig getConfig() {
        return config;
    }

    /**
     * Alias for {@link #getInstance()} to retrieve the singleton instance.
     *
     * @return the singleton {@link MqttClientSingleton} instance
     */
    public static MqttClientSingleton get() {
        return getInstance();
    }

    /**
     * Retrieves the singleton {@link MqttClientSingleton} instance.
     *
     * @return the singleton {@link MqttClientSingleton} instance
     */
    public static MqttClientSingleton getInstance() {
        return MqttClientSingletonHolder.INSTANCE;
    }

    /**
     * Private constructor to enforce singleton pattern.
     * Throws an exception if the singleton is not initialized yet.
     *
     * @throws IllegalStateException if called before initialization
     */
    private MqttClientSingleton() {
        if (!initialized) {
            throw new IllegalStateException("MqttClientSingleton is not initialized");
        }
    }

    /**
     * Establishes a connection to the MQTT broker asynchronously, waiting for the connection
     * to be established up to the configured connection timeout limit.
     *
     * @throws IllegalStateException if the singleton is not initialized
     */
    public void connect() {
        if (!initialized || config == null) {
            throw new IllegalStateException("MqttClientSingleton is not initialized");
        }

        if (mqttClient != null && connected) {
            LOGGER.debug("MQTT Client already connected");
            return;
        }

        BootstrapLogger.standalone(LOGGER, "Connecting to MQTT broker at {}...", config.getBrokerUrl());

        try {
            URI uri = new URI(config.getBrokerUrl());
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : (config.isTlsEnabled() ? 8883 : 1883);

            if (config.getProtocolVersion() == 5) {
                mqttClient = buildMqtt5Client(host, port);
            } else {
                mqttClient = buildMqtt3Client(host, port);
            }

            CompletableFuture<Void> connectFuture;
            if (mqttClient instanceof Mqtt5AsyncClient m5) {
                connectFuture = m5.connectWith()
                    .keepAlive(config.getKeepAliveSeconds())
                    .cleanStart(config.isCleanSession())
                    .sessionExpiryInterval(config.getSessionExpirySeconds())
                    .send()
                    .thenAccept(connAck -> {
                        connected = true;
                        BootstrapLogger.standalone(LOGGER, "Connected to MQTT broker {} (MQTT 5.0)",
                            ansi().fg(GREEN).bold().a(config.getBrokerUrl()).reset().toString());
                    });
            } else if (mqttClient instanceof Mqtt3AsyncClient m3) {
                connectFuture = m3.connectWith()
                    .cleanSession(config.isCleanSession())
                    .keepAlive(config.getKeepAliveSeconds())
                    .send()
                    .thenAccept(connAck -> {
                        connected = true;
                        BootstrapLogger.standalone(LOGGER, "Connected to MQTT broker {} (MQTT 3.1.1)",
                            ansi().fg(GREEN).bold().a(config.getBrokerUrl()).reset().toString());
                    });
            } else {
                throw new IllegalStateException("Unknown MQTT client type: " + mqttClient.getClass());
            }

            connectFuture.get(config.getConnectTimeoutSeconds(), TimeUnit.SECONDS);

        } catch (InterruptedException ie) {
            connected = false;
            Thread.currentThread().interrupt();
            LOGGER.error("{} Connection interrupted, the application may be shutting down.",
                ansi().fg(RED).bold().a("Cannot connect to MQTT broker.").reset().toString(), ie);
        } catch (Exception e) {
            connected = false;
            LOGGER.error("{} Check that the broker is running and the configuration property "
                + "'/mqtt-client/broker-url' is set properly",
                ansi().fg(RED).bold().a("Cannot connect to MQTT broker.").reset().toString(), e);
        }
    }

    /**
     * Builds and configures an asynchronous MQTT 5.0 client.
     *
     * @param host the broker host address
     * @param port the broker port
     * @return the constructed {@link Mqtt5AsyncClient}
     */
    private Mqtt5AsyncClient buildMqtt5Client(String host, int port) {
        Mqtt5ClientBuilder builder = MqttClient.builder()
            .useMqttVersion5()
            .identifier(config.getClientId())
            .serverHost(host)
            .serverPort(port);

        if (config.getUsername() != null && config.getPassword() != null) {
            builder.simpleAuth()
                .username(config.getUsername())
                .password(config.getPassword().getBytes(StandardCharsets.UTF_8))
                .applySimpleAuth();
        }

        var willConfig = config.getWillConfig();
        if (willConfig != null && willConfig.getWillTopic() != null && willConfig.getWillPayload() != null) {
            var willPublishBuilder = builder.willPublish()
                .topic(willConfig.getWillTopic())
                .payload(willConfig.getWillPayload().getBytes(StandardCharsets.UTF_8))
                .qos(MqttQos.fromCode(willConfig.getWillQos()))
                .retain(willConfig.getWillRetain())
                .delayInterval(willConfig.getWillDelaySeconds());

            if (willConfig.getWillMessageExpirySeconds() != null) {
                willPublishBuilder.messageExpiryInterval(willConfig.getWillMessageExpirySeconds());
            }

            willPublishBuilder.applyWillPublish();
        }

        var reconnectConfig = config.getReconnectConfig();
        if (reconnectConfig != null && reconnectConfig.isEnabled()) {
            builder.automaticReconnect()
                .initialDelay(reconnectConfig.getInitialDelayMs(), TimeUnit.MILLISECONDS)
                .maxDelay(reconnectConfig.getMaxDelayMs(), TimeUnit.MILLISECONDS)
                .applyAutomaticReconnect()
                .addConnectedListener(context ->
                    TypeSwitch.when(context)
                        .is(Mqtt5ClientConnectedContext.class, q -> {
                            if (!q.getConnAck().isSessionPresent()) {
                                LOGGER.info("New session detected (sessionPresent=false), triggering resubscription");
                                MqttMessageRouter.getInstance().resubscribeAll();
                            } else {
                                LOGGER.info("Existing session detected (sessionPresent=true), skipping resubscription");
                            }
                        })
                );
        }

        if (config.isTlsEnabled()) {
            builder.sslWithDefaultConfig();
        }

        return builder.buildAsync();
    }

    /**
     * Builds and configures an asynchronous MQTT 3.0 client.
     *
     * @param host the broker host address
     * @param port the broker port
     * @return the constructed {@link Mqtt3AsyncClient}
     */
    private Mqtt3AsyncClient buildMqtt3Client(String host, int port) {
        Mqtt3ClientBuilder builder = MqttClient.builder()
            .useMqttVersion3()
            .identifier(config.getClientId())
            .serverHost(host)
            .serverPort(port);

        if (config.getUsername() != null && config.getPassword() != null) {
            builder.simpleAuth()
                .username(config.getUsername())
                .password(config.getPassword().getBytes(StandardCharsets.UTF_8))
                .applySimpleAuth();
        }

        var willConfig = config.getWillConfig();
        if (willConfig != null && willConfig.getWillTopic() != null && willConfig.getWillPayload() != null) {
            builder.willPublish()
                .topic(willConfig.getWillTopic())
                .payload(willConfig.getWillPayload().getBytes(StandardCharsets.UTF_8))
                .qos(MqttQos.fromCode(willConfig.getWillQos()))
                .retain(willConfig.getWillRetain())
                .applyWillPublish();
        }

        var reconnectConfig = config.getReconnectConfig();
        if (reconnectConfig != null && reconnectConfig.isEnabled()) {
            builder.automaticReconnect()
                .initialDelay(reconnectConfig.getInitialDelayMs(), TimeUnit.MILLISECONDS)
                .maxDelay(reconnectConfig.getMaxDelayMs(), TimeUnit.MILLISECONDS)
                .applyAutomaticReconnect()
                .addConnectedListener(context ->
                    TypeSwitch.when(context)
                        .is(Mqtt3ClientConnectedContext.class, q -> {
                            if (!q.getConnAck().isSessionPresent()) {
                                LOGGER.info("New session detected (sessionPresent=false), triggering resubscription");
                                MqttMessageRouter.getInstance().resubscribeAll();
                            } else {
                                LOGGER.info("Existing session detected (sessionPresent=true), skipping resubscription");
                            }
                        })
                );
        }

        if (config.isTlsEnabled()) {
            builder.sslWithDefaultConfig();
        }

        return builder.buildAsync();
    }

    /**
     * Returns the underlying {@link MqttClient} instance.
     * If the client is not yet created or connected, this method will trigger the connection.
     *
     * @return the underlying {@link MqttClient} instance
     * @throws IllegalStateException if the singleton is not initialized
     */
    public MqttClient getClient() {
        if (!initialized || config == null) {
            throw new IllegalStateException("MqttClientSingleton is not initialized");
        }

        if (mqttClient == null) {
            connect();
        }

        return mqttClient;
    }

    /**
     * Returns whether the client is currently connected to the broker.
     *
     * @return {@code true} if connected, {@code false} otherwise
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Initialization-on-demand holder idiom to hold the singleton instance.
     * <p>
     * To ensure singleton integrity across classloaders (e.g., in plugin environments),
     * the instance is registered in the system properties.
     * </p>
     */
    private static class MqttClientSingletonHolder {
        private static final MqttClientSingleton INSTANCE;

        static {
            synchronized (ClassLoader.getSystemClassLoader()) {
                final var sysProps = System.getProperties();
                final var singleton = (MqttClientSingleton) sysProps.get(MqttClientSingleton.class.getName());

                if (singleton != null) {
                    INSTANCE = singleton;
                } else {
                    INSTANCE = new MqttClientSingleton();
                    System.getProperties().put(MqttClientSingleton.class.getName(), INSTANCE);
                }
            }
        }
    }
}
