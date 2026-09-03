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

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.TrustManagerFactory;

import org.restheart.utils.BootstrapLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientBuilderBase;
import com.hivemq.client.mqtt.MqttClientSslConfig;
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
 * using {@link #getInstance()}.
 * </p>
 * <p>
 * Thread-safety: {@link #init} must be called before any other method. After initialization,
 * the configuration is immutable. {@link #connect()} and {@link #close()} are {@code synchronized}
 * and idempotent. The {@code connected} flag is updated atomically from the connected/disconnected
 * listeners and read via {@link #isConnected()}.
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
    private volatile MqttClient mqttClient;

    /** Flag indicating whether the client is currently connected. */
    private volatile boolean connected = false;

    /** Set by {@link #close()}; checked by the disconnected listener to cancel automatic reconnect. */
    private volatile boolean shuttingDown = false;

    /** Set once {@link #close()} has completed, to make repeated calls a no-op. */
    private volatile boolean closed = false;

    /**
     * Listeners invoked when a (re)connect establishes a new (non-persisted) session, i.e. when
     * the broker's CONNACK reports {@code sessionPresent=false}. Registered via
     * {@link #addOnNewSessionListener(Runnable)}.
     */
    private final CopyOnWriteArrayList<Runnable> newSessionListeners = new CopyOnWriteArrayList<>();

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
     * Retrieves the singleton {@link MqttClientSingleton} instance.
     * <p>
     * The instance is always returned, even before {@link #init} has been called: it simply
     * has no configuration yet. Methods that require configuration, such as {@link #connect()}
     * and {@link #getClient()}, throw {@link IllegalStateException} if called before
     * {@link #init}.
     * </p>
     *
     * @return the singleton {@link MqttClientSingleton} instance
     */
    public static MqttClientSingleton getInstance() {
        return MqttClientSingletonHolder.INSTANCE;
    }

    /**
     * Private constructor to enforce singleton pattern.
     * <p>
     * Creating the instance never requires configuration to be present yet: the instance
     * may legitimately be obtained via {@link #getInstance()} before {@link #init} has run
     * (e.g. to register a listener via {@link #addOnNewSessionListener(Runnable)}). Methods
     * that actually need configuration, such as {@link #connect()} and {@link #getClient()},
     * check {@link #initialized} themselves and throw {@link IllegalStateException} if called
     * too early.
     * </p>
     */
    private MqttClientSingleton() {
    }

    /**
     * Establishes a connection to the MQTT broker asynchronously, waiting for the connection
     * to be established up to the configured connection timeout limit.
     * <p>
     * Idempotent: calling this method while already connected is a no-op. A failed connection
     * attempt is logged at ERROR level but does not throw: the automatic reconnect (if enabled)
     * will keep retrying in the background and {@link #isConnected()} reports the true state.
     * </p>
     *
     * @throws IllegalStateException if the singleton is not initialized
     */
    public synchronized void connect() {
        if (!initialized || config == null) {
            throw new IllegalStateException("MqttClientSingleton is not initialized");
        }

        if (closed) {
            LOGGER.debug("MqttClientSingleton has been closed, ignoring connect() request");
            return;
        }

        if (mqttClient != null && connected) {
            LOGGER.debug("MQTT Client already connected");
            return;
        }

        BootstrapLogger.standalone(LOGGER, "Connecting to MQTT broker at {}...", config.getBrokerUrl());

        try {
            MqttEndpoint endpoint = resolveEndpoint(config.getBrokerUrl(), config.isTlsEnabled());

            MqttClient client = config.getProtocolVersion() == 5
                ? buildMqtt5Client(endpoint)
                : buildMqtt3Client(endpoint);
            mqttClient = client;

            CompletableFuture<Void> connectFuture;
            if (client instanceof Mqtt5AsyncClient m5) {
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
            } else if (client instanceof Mqtt3AsyncClient m3) {
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
                throw new IllegalStateException("Unknown MQTT client type: " + client.getClass());
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
     * Disconnects from the MQTT broker and releases the underlying client, cancelling any
     * pending automatic reconnect first (see hivemq-mqtt-client issue #756: disconnecting while
     * a reconnect is in-flight can leave resources unreleased).
     * <p>
     * Safe to call when the client was never connected and safe to call more than once.
     * </p>
     */
    public synchronized void close() {
        shuttingDown = true;

        if (closed) {
            LOGGER.debug("MqttClientSingleton already closed");
            return;
        }

        final MqttClient client = mqttClient;
        if (client == null) {
            closed = true;
            return;
        }

        try {
            final CompletableFuture<Void> disconnectFuture;
            if (client instanceof Mqtt5AsyncClient m5) {
                disconnectFuture = m5.disconnect();
            } else if (client instanceof Mqtt3AsyncClient m3) {
                disconnectFuture = m3.disconnect();
            } else {
                disconnectFuture = CompletableFuture.completedFuture(null);
            }

            final int timeoutSeconds = (config != null && config.getConnectTimeoutSeconds() > 0)
                ? config.getConnectTimeoutSeconds()
                : 10;

            disconnectFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            BootstrapLogger.standalone(LOGGER, "Disconnected from MQTT broker");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while disconnecting from MQTT broker", ie);
        } catch (Exception e) {
            LOGGER.warn("Error while disconnecting from MQTT broker", e);
        } finally {
            connected = false;
            closed = true;
        }
    }

    /**
     * Registers a listener to be invoked when a (re)connect establishes a new (non-persisted)
     * session, i.e. when the broker's CONNACK reports {@code sessionPresent=false}.
     * <p>
     * Used by {@code MqttMessageRouter} to re-issue its broker subscriptions after a reconnect,
     * since broker subscriptions do not survive a disconnect. A listener that throws does not
     * prevent other registered listeners from running; see {@link #notifyNewSessionListeners()}.
     * </p>
     *
     * @param listener the callback to invoke when a new session is established
     */
    public void addOnNewSessionListener(Runnable listener) {
        newSessionListeners.add(listener);
    }

    /**
     * Invokes every listener registered via {@link #addOnNewSessionListener(Runnable)}, catching
     * and logging any exception so that a failing listener cannot prevent the others from running.
     */
    private void notifyNewSessionListeners() {
        for (Runnable listener : newSessionListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                LOGGER.error("Error invoking MQTT new-session listener", e);
            }
        }
    }

    /**
     * Resolves the connection endpoint (host, port, TLS, WebSocket) from the configured
     * broker URL scheme.
     * <p>
     * Supported schemes: {@code tcp} (plain TCP, default port 1883), {@code ssl}/{@code mqtts}
     * (TLS, default port 8883), {@code ws} (WebSocket, default port 80) and {@code wss}
     * (WebSocket over TLS, default port 443). {@code tlsOverride} forces TLS regardless of scheme.
     * </p>
     *
     * @param brokerUrl the configured broker URL
     * @param tlsOverride whether the {@code tls} configuration key forces TLS
     * @return the resolved endpoint
     * @throws IllegalArgumentException if the URL is invalid, has no host or has an unsupported scheme
     */
    static MqttEndpoint resolveEndpoint(String brokerUrl, boolean tlsOverride) {
        if (brokerUrl == null || brokerUrl.isBlank()) {
            throw new IllegalArgumentException("'broker-url' must not be null or blank");
        }

        final URI uri;
        try {
            uri = new URI(brokerUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("'broker-url' is not a valid URI: " + brokerUrl, e);
        }

        final String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("'broker-url' must specify a host: " + brokerUrl);
        }

        final String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme == null || !MqttConfig.SUPPORTED_SCHEMES.contains(scheme)) {
            throw new IllegalArgumentException("'broker-url' has an unsupported scheme '" + scheme
                + "', supported schemes are: " + MqttConfig.SUPPORTED_SCHEMES);
        }

        final boolean schemeTls;
        final boolean webSocket;
        final int defaultPort;
        switch (scheme) {
            case "tcp" -> {
                schemeTls = false;
                webSocket = false;
                defaultPort = 1883;
            }
            case "ssl", "mqtts" -> {
                schemeTls = true;
                webSocket = false;
                defaultPort = 8883;
            }
            case "ws" -> {
                schemeTls = false;
                webSocket = true;
                defaultPort = 80;
            }
            case "wss" -> {
                schemeTls = true;
                webSocket = true;
                defaultPort = 443;
            }
            default -> throw new IllegalArgumentException("'broker-url' has an unsupported scheme '" + scheme
                + "', supported schemes are: " + MqttConfig.SUPPORTED_SCHEMES);
        }

        final boolean tls = schemeTls || tlsOverride;

        // when 'tls: true' upgrades a plaintext scheme and no port is given, the default port
        // must follow the effective TLS state: tcp://host with tls:true means 8883, not 1883
        final int defaultPortForTls = !schemeTls && tls
            ? (webSocket ? 443 : 8883)
            : defaultPort;

        final int port = uri.getPort() > 0 ? uri.getPort() : defaultPortForTls;
        return new MqttEndpoint(host, port, tls, webSocket);
    }

    /**
     * The resolved transport parameters for a broker connection.
     *
     * @param host the broker host
     * @param port the broker port
     * @param tls whether the connection must be secured with TLS
     * @param webSocket whether the connection must be tunneled over WebSocket
     */
    record MqttEndpoint(String host, int port, boolean tls, boolean webSocket) {
    }

    /**
     * Builds and configures an asynchronous MQTT 5.0 client.
     *
     * @param endpoint the resolved connection endpoint
     * @return the constructed {@link Mqtt5AsyncClient}
     */
    private Mqtt5AsyncClient buildMqtt5Client(MqttEndpoint endpoint) {
        Mqtt5ClientBuilder builder = MqttClient.builder()
            .useMqttVersion5()
            .identifier(config.getClientId())
            .serverHost(endpoint.host())
            .serverPort(endpoint.port());

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
                .addConnectedListener(context -> {
                    connected = true;
                    TypeSwitch.when(context)
                        .is(Mqtt5ClientConnectedContext.class, q -> {
                            if (!q.getConnAck().isSessionPresent()) {
                                LOGGER.info("New session detected (sessionPresent=false), triggering resubscription");
                                notifyNewSessionListeners();
                            } else {
                                LOGGER.info("Existing session detected (sessionPresent=true), skipping resubscription");
                            }
                        });
                });
        }

        builder.addDisconnectedListener(context -> {
            connected = false;
            if (shuttingDown) {
                context.getReconnector().reconnect(false);
            }
            LOGGER.debug("MQTT client disconnected, source={}", context.getSource(), context.getCause());
        });

        applyTransportConfig(builder, endpoint);

        return builder.buildAsync();
    }

    /**
     * Builds and configures an asynchronous MQTT 3.0 client.
     *
     * @param endpoint the resolved connection endpoint
     * @return the constructed {@link Mqtt3AsyncClient}
     */
    private Mqtt3AsyncClient buildMqtt3Client(MqttEndpoint endpoint) {
        Mqtt3ClientBuilder builder = MqttClient.builder()
            .useMqttVersion3()
            .identifier(config.getClientId())
            .serverHost(endpoint.host())
            .serverPort(endpoint.port());

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
                .addConnectedListener(context -> {
                    connected = true;
                    TypeSwitch.when(context)
                        .is(Mqtt3ClientConnectedContext.class, q -> {
                            if (!q.getConnAck().isSessionPresent()) {
                                LOGGER.info("New session detected (sessionPresent=false), triggering resubscription");
                                notifyNewSessionListeners();
                            } else {
                                LOGGER.info("Existing session detected (sessionPresent=true), skipping resubscription");
                            }
                        });
                });
        }

        builder.addDisconnectedListener(context -> {
            connected = false;
            if (shuttingDown) {
                context.getReconnector().reconnect(false);
            }
            LOGGER.debug("MQTT client disconnected, source={}", context.getSource(), context.getCause());
        });

        applyTransportConfig(builder, endpoint);

        return builder.buildAsync();
    }

    /**
     * Applies WebSocket and TLS transport settings to the client builder, based on the
     * resolved {@link MqttEndpoint}. When TLS is required and {@code tls-trust-store} is
     * configured, a custom trust manager is loaded from it; otherwise the client's default
     * TLS configuration is used.
     *
     * @param builder the MQTT 3 or MQTT 5 client builder
     * @param endpoint the resolved connection endpoint
     */
    private void applyTransportConfig(MqttClientBuilderBase<?> builder, MqttEndpoint endpoint) {
        if (endpoint.webSocket()) {
            builder.webSocketWithDefaultConfig();
        }

        if (endpoint.tls()) {
            final String trustStorePath = config.getTlsTrustStore();
            if (trustStorePath != null && !trustStorePath.isBlank()) {
                final TrustManagerFactory trustManagerFactory =
                    loadTrustManagerFactory(trustStorePath, config.getTlsTrustStorePassword());
                builder.sslConfig(MqttClientSslConfig.builder()
                    .trustManagerFactory(trustManagerFactory)
                    .build());
            } else {
                builder.sslWithDefaultConfig();
            }
        }
    }

    /**
     * Loads a {@link TrustManagerFactory} from the configured {@code tls-trust-store}.
     * The store type (PKCS12 or JKS) is inferred from the file extension.
     *
     * @param path the path to the trust store file
     * @param password the trust store password, may be {@code null}
     * @return the initialized trust manager factory
     * @throws IllegalStateException if the trust store cannot be read or loaded
     */
    private static TrustManagerFactory loadTrustManagerFactory(String path, String password) {
        try {
            final String lowerPath = path.toLowerCase(Locale.ROOT);
            final String type = (lowerPath.endsWith(".p12") || lowerPath.endsWith(".pfx")) ? "PKCS12" : "JKS";
            final KeyStore trustStore = KeyStore.getInstance(type);
            try (InputStream in = new FileInputStream(path)) {
                trustStore.load(in, password != null ? password.toCharArray() : null);
            }
            final TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            return trustManagerFactory;
        } catch (Exception e) {
            throw new IllegalStateException(
                "Cannot load 'tls-trust-store' from '" + path + "': " + e.getMessage(), e);
        }
    }

    /**
     * Returns the underlying {@link MqttClient} instance built by {@link #connect()}.
     * <p>
     * This method never blocks and never triggers a connection attempt: the one blocking
     * connect happens in {@code MqttClientProvider.init()}.
     * </p>
     *
     * @return the underlying {@link MqttClient} instance
     * @throws IllegalStateException if the {@code mqtt-client} plugin has not completed
     *         initialization yet (i.e. {@link #connect()} has not been called or has not
     *         built a client)
     */
    public MqttClient getClient() {
        final MqttClient client = mqttClient;
        if (!initialized || config == null || client == null) {
            throw new IllegalStateException(
                "MQTT client is not available yet: the 'mqtt-client' plugin has not completed initialization");
        }

        return client;
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
