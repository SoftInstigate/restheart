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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
 */
public class MqttReconnectIT {

    private static Server server;
    private static int brokerPort;
    private static Properties brokerProps;

    private MqttMessageRouter router;

    @BeforeAll
    static void startBroker() throws Exception {
        brokerProps = new Properties();
        brokerProps.setProperty(BrokerConstants.PORT_PROPERTY_NAME, "1883");  // random port
        brokerProps.setProperty(BrokerConstants.HOST_PROPERTY_NAME, "localhost");
        brokerProps.setProperty(BrokerConstants.ALLOW_ANONYMOUS_PROPERTY_NAME, "true");
        brokerProps.setProperty(BrokerConstants.PERSISTENCE_ENABLED_PROPERTY_NAME, "false");

        server = new Server();
        server.startServer(new MemoryConfig(brokerProps));
        brokerPort = server.getPort();
    }

    @AfterAll
    static void stopBroker() {
        if (server != null) {
            try {
                server.stopServer();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // Reset singletons using reflection
        resetSingletons();

        router = MqttMessageRouter.getInstance();
        router.init(5000, true, 1000);

        // Configure client with custom settings pointing to our broker
        MqttConfig config = new MqttConfig.Builder()
                .brokerUrl("tcp://localhost:" + brokerPort)
                .protocolVersion(3) // MQTT 3.1.1 supported by Moquette
                .clientId("restheart-reconnect-test")
                .cleanSession(true)
                .connectTimeoutSeconds(10)
                .reconnectConfig(new MqttConfig.ReconnectConfig(true, 100L, 1000L))
                .willConfig(new MqttConfig.WillConfig(null, null, 0, false, 0, null))
                .build();

        MqttClientSingleton.init(config);
    }

    @AfterEach
    void tearDown() {
        // Disconnect and clean up
        try {
            MqttClient client = MqttClientSingleton.getInstance().getClient();
            if (client instanceof Mqtt3AsyncClient) {
                ((Mqtt3AsyncClient) client).disconnect();
            }
        } catch (Exception e) {
            // Ignore
        }
        resetSingletons();
    }

    private void resetSingletons() {
        try {
            // Reset MqttClientSingleton
            Field initializedField = MqttClientSingleton.class.getDeclaredField("initialized");
            initializedField.setAccessible(true);
            initializedField.setBoolean(null, false);

            MqttClientSingleton clientInstance = (MqttClientSingleton) System.getProperties().get(MqttClientSingleton.class.getName());
            if (clientInstance != null) {
                Field clientField = MqttClientSingleton.class.getDeclaredField("mqttClient");
                clientField.setAccessible(true);
                clientField.set(clientInstance, null);

                Field connectedField = MqttClientSingleton.class.getDeclaredField("connected");
                connectedField.setAccessible(true);
                connectedField.set(clientInstance, false);
            }

            // Reset MqttMessageRouter
            MqttMessageRouter routerInstance = (MqttMessageRouter) System.getProperties().get(MqttMessageRouter.class.getName());
            if (routerInstance != null) {
                Field routerInitializedField = MqttMessageRouter.class.getDeclaredField("initialized");
                routerInitializedField.setAccessible(true);
                ((AtomicBoolean) routerInitializedField.get(routerInstance)).set(false);

                Field listenersField = MqttMessageRouter.class.getDeclaredField("listeners");
                listenersField.setAccessible(true);
                ((java.util.Map<?, ?>) listenersField.get(routerInstance)).clear();

                Field cacheField = MqttMessageRouter.class.getDeclaredField("lastMessageCache");
                cacheField.setAccessible(true);
                ((java.util.Map<?, ?>) cacheField.get(routerInstance)).clear();
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    @Test
    void testMqttReconnectResubscribe() throws Exception {
        // Verify we can connect and subscribe
        MqttClient client = MqttClientSingleton.getInstance().getClient();
        assertEquals(MqttClientState.CONNECTED, client.getState());

        LinkedBlockingQueue<MqttMessage> receivedMessages = new LinkedBlockingQueue<>();
        router.subscribe("sensors/#", MqttQos.AT_LEAST_ONCE, receivedMessages::add);

        // Wait for subscription to register with the broker
        Thread.sleep(200);

        // Publish message 1
        publishMessage("sensors/temp", "payload1");

        // Assert message 1 is received
        MqttMessage msg1 = receivedMessages.poll(3, TimeUnit.SECONDS);
        assertNotNull(msg1, "Failed to receive message 1");
        assertEquals("sensors/temp", msg1.getTopic());
        assertEquals("payload1", msg1.getPayload());

        // Stop the embedded broker
        server.stopServer();
        
        // Sleep to ensure client recognizes disconnection
        Thread.sleep(500);

        // Start the broker again on the same port
        Properties restartProps = new Properties(brokerProps);
        restartProps.setProperty(BrokerConstants.PORT_PROPERTY_NAME, String.valueOf(brokerPort));
        server = new Server();
        server.startServer(new MemoryConfig(restartProps));

        // Wait for automatic reconnection
        boolean reconnected = false;
        for (int i = 0; i < 50; i++) {
            if (MqttClientSingleton.getInstance().getClient().getState() == MqttClientState.CONNECTED) {
                reconnected = true;
                break;
            }
            Thread.sleep(100);
        }
        assertTrue(reconnected, "Client failed to reconnect after broker restart");

        // Wait for resubscription to be processed
        Thread.sleep(500);

        // Publish message 2
        publishMessage("sensors/temp", "payload2");

        // Assert message 2 is received (meaning subscription was successfully restored)
        MqttMessage msg2 = receivedMessages.poll(5, TimeUnit.SECONDS);
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
