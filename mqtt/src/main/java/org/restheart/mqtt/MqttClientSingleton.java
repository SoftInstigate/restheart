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
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;

/**
 * Thread-safe singleton that manages the lifecycle of a MQTT client  {@link MqttClient}.
 * <p>
 * This singleton can be configured to use either MQTT v3 or MQTT v5 protocols.
 * It provides features such as automatic reconnection, authentication (username/password),
 * TLS/SSL support, keep-alive, and session management.
 * </p>
 * <p>
 * The singleton instance is initialized via the {@link #init} method and can be retrieved
 * using {@link #getInstance()} or {@link #get()}.
 * </p>
 * 
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 */
public class MqttClientSingleton {

    /** Indicates whether the singleton has been initialized with configuration parameters. */
    private static boolean initialized = false;
    /** The MQTT broker URL (e.g., "tcp://localhost:1883"). */
    private static String brokerUrl;
    /** The MQTT protocol version (3 or 5). */
    private static int protocolVersion;
    /** The unique client identifier for connection to the broker. */
    private static String clientId;
    /** The username for broker authentication (optional). */
    private static String username;
    /** The password for broker authentication (optional). */
    private static String password;
    /** Whether to start clean sessions or resume previous ones. */
    private static boolean cleanSession;
    /** The keep-alive interval in seconds. */
    private static int keepAliveSeconds;
    /** The session expiry interval in seconds (MQTT v5 only). */
    private static long sessionExpirySeconds;
    /** The connection timeout in seconds. */
    private static int connectTimeoutSeconds;
    /** Whether SSL/TLS is enabled for connections. */
    private static boolean tlsEnabled;
    /** Whether automatic reconnection is enabled. */
    private static boolean reconnectEnabled;
    /** The initial delay in milliseconds for reconnection attempts. */
    private static long initialDelayMs;
    /** The maximum delay in milliseconds for reconnection attempts. */
    private static long maxDelayMs;
    /** The will message topic (optional). */
    private static String willTopic;
    /** The will message payload (optional). */
    private static String willPayload;
    /** The will message QoS level (0, 1, or 2). */
    private static int willQos;
    /** Whether the will message should be retained. */
    private static boolean willRetain;
    /** The will delay interval in seconds (MQTT v5 only). */
    private static long willDelaySeconds;
    /** The will message expiry interval in seconds (MQTT v5 only, optional). */
    private static Long willMessageExpirySeconds;

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttClientSingleton.class);

    /** The underlying HiveMQ MQTT client instance. */
    private MqttClient mqttClient; // Either Mqtt3AsyncClient or Mqtt5AsyncClient
    /** Flag indicating whether the client is currently connected. */
    private boolean connected = false;

    /**
     * Initializes the MQTT client singleton with the specified configuration parameters.
     *
     * @param config the MQTT configuration containing all connection parameters
     */
    public static void init(MqttConfig clientConfig) {

        MqttClientSingleton.brokerUrl = clientConfig.getBrokerUrl();
        MqttClientSingleton.protocolVersion = clientConfig.getProtocolVersion();
        MqttClientSingleton.clientId = clientConfig.getClientId();
        MqttClientSingleton.username = clientConfig.getUsername();
        MqttClientSingleton.password = clientConfig.getPassword();
        MqttClientSingleton.cleanSession = clientConfig.isCleanSession();
        MqttClientSingleton.keepAliveSeconds = clientConfig.getKeepAliveSeconds();
        MqttClientSingleton.connectTimeoutSeconds = clientConfig.getConnectTimeoutSeconds();
        MqttClientSingleton.tlsEnabled = clientConfig.isTlsEnabled();
        MqttClientSingleton.sessionExpirySeconds = clientConfig.getSessionExpirySeconds();
        
        MqttConfig.ReconnectConfig reconnectConfig = clientConfig.getReconnectConfig();
        MqttClientSingleton.reconnectEnabled = reconnectConfig.isEnabled();
        MqttClientSingleton.initialDelayMs = reconnectConfig.getInitialDelayMs();
        MqttClientSingleton.maxDelayMs = reconnectConfig.getMaxDelayMs();

        MqttConfig.WillConfig willConfig = clientConfig.getWillConfig();
        MqttClientSingleton.willTopic = willConfig.getWillTopic();
        MqttClientSingleton.willPayload = willConfig.getWillPayload();
        MqttClientSingleton.willQos = willConfig.getWillQos();
        MqttClientSingleton.willRetain = willConfig.getWillRetain();
        MqttClientSingleton.willDelaySeconds = willConfig.getWillDelaySeconds();
        MqttClientSingleton.willMessageExpirySeconds = willConfig.getWillMessageExpirySeconds();

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
        if (!initialized) {
            throw new IllegalStateException("MqttClientSingleton is not initialized");
        }

        if (mqttClient != null && connected) {
            LOGGER.debug("MQTT Client already connected");
            return;
        }

        BootstrapLogger.standalone(LOGGER, "Connecting to MQTT broker at {}...", brokerUrl);

        try {
            //Parse broker URL
            URI uri = new URI(brokerUrl);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : (tlsEnabled ? 8883 : 1883);

            // Build client based on protocol version
            if (protocolVersion == 5) {
                mqttClient = buildMqtt5Client(host, port);
            } else {
                mqttClient = buildMqtt3Client(host, port);
            }

            // Attempt connection
            CompletableFuture<Void> connectFuture;
            if (mqttClient instanceof Mqtt5AsyncClient) {
                connectFuture = ((Mqtt5AsyncClient) mqttClient).connectWith()
                    .keepAlive(keepAliveSeconds)
                    .cleanStart(cleanSession)
                    .sessionExpiryInterval(sessionExpirySeconds)
                    .send()
                    .thenAccept(connAck -> {
                    connected = true;
                    BootstrapLogger.standalone(LOGGER, "Connected to MQTT broker {} (MQTT 5.0)", 
                        ansi().fg(GREEN).bold().a(brokerUrl).reset().toString());
                });
            } else {
                connectFuture = ((Mqtt3AsyncClient) mqttClient).connectWith()
                    .cleanSession(cleanSession)
                    .keepAlive(keepAliveSeconds)
                    .send()
                    .thenAccept(connAck -> {
                    connected = true;
                    BootstrapLogger.standalone(LOGGER, "Connected to MQTT broker {} (MQTT 5.0)", ansi().fg(GREEN).bold().a(brokerUrl).reset().toString());
                });
            }

            connectFuture.get(connectTimeoutSeconds, TimeUnit.SECONDS);
            
        } catch (InterruptedException ie) {
            LOGGER.error(ansi().fg(RED).bold().a("Cannot connect to MQTT broker.").reset().toString() 
                    + "Connection interrupted, The application may be shutting down.", ie);
                    connected = false;
                    Thread.currentThread().interrupt();
        }
        catch (Exception e) {
            LOGGER.error(ansi().fg(RED).bold().a("Cannot connect to MQTT broker.").reset().toString() 
                    + "Check that the broker is running and" 
                    + "the configuration property '/mqtt-client/broker-url'" 
                    + "is set properly", e);
            connected = false;
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
            .identifier(clientId)
            .serverHost(host)
            .serverPort(port);
        
        //Add authentication if provided
        if (username != null && password != null) {
            builder.simpleAuth()
                .username(username)
                .password(password.getBytes(StandardCharsets.UTF_8))
                .applySimpleAuth();
        }

        // Configure will message if provided
        if (willTopic != null && willPayload != null) {
            var willPublishBuilder = builder.willPublish()
                .topic(willTopic)
                .payload(willPayload.getBytes(StandardCharsets.UTF_8))
                .qos(MqttQos.fromCode(willQos))
                .retain(willRetain)
                .delayInterval(willDelaySeconds);

            // Add message expiry if provided
            if (willMessageExpirySeconds != null) {
                willPublishBuilder.messageExpiryInterval(willMessageExpirySeconds);
            }

            willPublishBuilder.applyWillPublish();
        }

        // Configure automatic reconnect
        if (reconnectEnabled) {
            builder.automaticReconnect()
                .initialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .maxDelay(maxDelayMs, TimeUnit.MILLISECONDS)
                .applyAutomaticReconnect();
        }

        if (tlsEnabled) {
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
            .identifier(clientId)
            .serverHost(host)
            .serverPort(port);
        
        // Add authentication if provided
        if (username != null && password != null) {
            builder.simpleAuth()
                .username(username)
                .password(password.getBytes())
                .applySimpleAuth();
        }

        // Configure will message if provided
        if (willTopic != null && willPayload != null) {
            builder.willPublish()
                .topic(willTopic)
                .payload(willPayload.getBytes(StandardCharsets.UTF_8))
                .qos(MqttQos.fromCode(willQos))
                .retain(willRetain)
                .applyWillPublish();
        }

        // Configure automatic reconnect
        if (reconnectEnabled) {
            builder.automaticReconnect()
                .initialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .maxDelay(maxDelayMs, TimeUnit.MILLISECONDS)
                .applyAutomaticReconnect();
        }

        // Configure TLS if enabled
        if (tlsEnabled) {
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
        if (!initialized) {
            throw new IllegalStateException("MqttClientSingleton is not initialized");
        }

        if (mqttClient == null) {
            connect();
        }

        return mqttClient;
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

        // Make sure the Singleton is a Singleton even in a multi
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
