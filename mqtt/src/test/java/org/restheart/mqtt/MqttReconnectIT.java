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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restheart.mqtt.model.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.moquette.BrokerConstants;
import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;

/**
 * Integration test for MQTT Client automatic reconnection.
 * Starts Moquette as an embedded broker, connects, stops and restarts the broker,
 * and asserts that the connection is restored and subscriptions are active.
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttReconnectIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttReconnectIT.class);

    private static Server server;
    private static int brokerPort;
    private static Properties brokerProps;

    private MqttMessageRouter router;

    @BeforeAll
    static void startBroker() throws Exception {
        brokerPort = TestPorts.freePort();
        brokerProps = new Properties();
        brokerProps.setProperty(BrokerConstants.PORT_PROPERTY_NAME, String.valueOf(brokerPort));
        brokerProps.setProperty(BrokerConstants.HOST_PROPERTY_NAME, "localhost");
        brokerProps.setProperty(BrokerConstants.ALLOW_ANONYMOUS_PROPERTY_NAME, "true");
        brokerProps.setProperty(BrokerConstants.PERSISTENCE_ENABLED_PROPERTY_NAME, "false");
        brokerProps.setProperty(BrokerConstants.ENABLE_TELEMETRY_NAME, "false");

        server = new Server();
        server.startServer(new MemoryConfig(brokerProps));
        // startServer() returns before the listener necessarily accepts connections
        TestPorts.waitUntilOpen(brokerPort, 10000);
        LOGGER.info("Moquette started on port {}", brokerPort);
    }

    @AfterAll
    static void stopBroker() {
        if (server != null) {
            try {
                server.stopServer();
            } catch (Exception e) {
                LOGGER.debug("Error stopping broker: {}", e.getMessage());
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        resetSingletons();

        MqttConfig config = new MqttConfig.Builder()
                .brokerUrl("tcp://localhost:" + brokerPort)
                .protocolVersion(3)
                .clientId("restheart-reconnect-test")
                .cleanSession(true)
                .connectTimeoutSeconds(10)
                .reconnectConfig(new MqttConfig.ReconnectConfig(true, 200L, 2000L))
                .willConfig(new MqttConfig.WillConfig(null, null, 0, false, 0, null))
                .build();

        MqttClientSingleton.init(config);
        // getClient() no longer connects lazily: the connection must be established explicitly,
        // as MqttClientProvider does at startup, before the router can be built with the client
        MqttClientSingleton.getInstance().connect();

        router = new MqttMessageRouter(MqttClientSingleton.getInstance().getClient(), 5000, true, 1000);
        // MqttMessageRouter no longer wires itself into the singleton: in production
        // MqttRouterProvider does this; here it is done manually since the provider is not used
        MqttClientSingleton.getInstance().addOnNewSessionListener(router::resubscribeAll);
    }

    @AfterEach
    void tearDown() {
        try {
            MqttClient client = MqttClientSingleton.getInstance().getClient();
            if (client instanceof Mqtt3AsyncClient) {
                ((Mqtt3AsyncClient) client).disconnect();
            }
        } catch (Exception e) {
            LOGGER.debug("Error disconnecting client: {}", e.getMessage());
        }
        resetSingletons();
    }

    private void resetSingletons() {
        try {
            Field initializedField = MqttClientSingleton.class.getDeclaredField("initialized");
            initializedField.setAccessible(true);
            initializedField.setBoolean(null, false);

            Field configField = MqttClientSingleton.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(null, null);

            MqttClientSingleton clientInstance = (MqttClientSingleton) System.getProperties().get(MqttClientSingleton.class.getName());
            if (clientInstance != null) {
                Field clientField = MqttClientSingleton.class.getDeclaredField("mqttClient");
                clientField.setAccessible(true);
                clientField.set(clientInstance, null);

                Field connectedField = MqttClientSingleton.class.getDeclaredField("connected");
                connectedField.setAccessible(true);
                connectedField.setBoolean(clientInstance, false);

                Field newSessionListenersField = MqttClientSingleton.class.getDeclaredField("newSessionListeners");
                newSessionListenersField.setAccessible(true);
                ((List<?>) newSessionListenersField.get(clientInstance)).clear();
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to reset MqttClientSingleton: {}", e.getMessage());
        }
    }

    /**
     * Waits until the client reaches the given state, or fails the test.
     * <p>
     * The client connects asynchronously and, on failure, enters
     * {@code DISCONNECTED_RECONNECT} rather than reporting an error, so an
     * instantaneous state assertion is inherently racy.
     */
    private void awaitClientState(MqttClientState expected, int timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        MqttClientState last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                last = MqttClientSingleton.getInstance().getClient().getState();
                if (last == expected) {
                    return;
                }
            } catch (Exception e) {
                // client may throw while reconnecting
            }
            Thread.sleep(50);
        }
        assertEquals(expected, last, "client did not reach " + expected + " within " + timeoutMs + "ms");
    }

    @Test
    void testMqttReconnectResubscribe() throws Exception {
        awaitClientState(MqttClientState.CONNECTED, 10000);

        LinkedBlockingQueue<MqttMessage> receivedMessages = new LinkedBlockingQueue<>();
        router.subscribe("sensors/#", MqttQos.AT_LEAST_ONCE, receivedMessages::add);

        Thread.sleep(200);

        publishMessage("sensors/temp", "payload1");

        MqttMessage msg1 = receivedMessages.poll(3, TimeUnit.SECONDS);
        assertNotNull(msg1, "Failed to receive message 1");
        assertEquals("sensors/temp", msg1.getTopic());
        assertEquals("payload1", msg1.getPayload());

        // Stop broker
        server.stopServer();
        LOGGER.info("Broker stopped, waiting for client to detect disconnect...");

        // Wait for client to detect disconnect
        Thread.sleep(2000);

        // Restart broker on same port
        Properties restartProps = new Properties(brokerProps);
        restartProps.setProperty(BrokerConstants.PORT_PROPERTY_NAME, String.valueOf(brokerPort));
        server = new Server();
        server.startServer(new MemoryConfig(restartProps));

        // Wait for port to be actually open
        TestPorts.waitUntilOpen(brokerPort, 10000);
        LOGGER.info("Broker restarted on port {}, waiting for client reconnect...", brokerPort);

        // Wait for automatic reconnection
        awaitClientState(MqttClientState.CONNECTED, 30000);

        // Wait for resubscription
        Thread.sleep(2000);

        publishMessage("sensors/temp", "payload2");

        MqttMessage msg2 = receivedMessages.poll(10, TimeUnit.SECONDS);
        assertNotNull(msg2, "Failed to receive message after reconnect");
        assertEquals("sensors/temp", msg2.getTopic());
        assertEquals("payload2", msg2.getPayload());
    }

    private void publishMessage(String topic, String payload) {
        MqttClient client = MqttClientSingleton.getInstance().getClient();
        if (client instanceof Mqtt3AsyncClient) {
            ((Mqtt3AsyncClient) client).publishWith()
                    .topic(topic)
                    .payload(payload.getBytes(StandardCharsets.UTF_8))
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .send()
                    .join();
        }
    }
}
