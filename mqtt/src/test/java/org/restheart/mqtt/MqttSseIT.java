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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * Integration test for MqttSseService.
 * Starts Moquette as an embedded broker, publishes MQTT messages,
 * and verifies they are received as SSE events via the router.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttSseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttSseIT.class);

    private static Server server;
    private static int brokerPort;
    private static Properties brokerProps;

    private MqttMessageRouter router;

    @BeforeAll
    static void startBroker() throws Exception {
        brokerProps = new Properties();
        brokerProps.setProperty(BrokerConstants.PORT_PROPERTY_NAME, "1883");
        brokerProps.setProperty(BrokerConstants.HOST_PROPERTY_NAME, "localhost");
        brokerProps.setProperty(BrokerConstants.ALLOW_ANONYMOUS_PROPERTY_NAME, "true");
        brokerProps.setProperty(BrokerConstants.PERSISTENCE_ENABLED_PROPERTY_NAME, "false");
        brokerProps.setProperty(BrokerConstants.ENABLE_TELEMETRY_NAME, "false");

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
                LOGGER.debug("Error stopping broker: {}", e.getMessage());
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        resetSingletons();

        router = MqttMessageRouter.getInstance();
        router.init(5000, true, 1000);

        MqttConfig config = new MqttConfig.Builder()
                .brokerUrl("tcp://localhost:" + brokerPort)
                .protocolVersion(3)
                .clientId("restheart-sse-test-" + System.nanoTime())
                .cleanSession(true)
                .connectTimeoutSeconds(10)
                .reconnectConfig(new MqttConfig.ReconnectConfig(false, 1000L, 30000L))
                .willConfig(new MqttConfig.WillConfig(null, null, 0, false, 0, null))
                .build();

        MqttClientSingleton.init(config);
        MqttClientSingleton.getInstance().connect();
    }

    @AfterEach
    void tearDown() {
        try {
            MqttClient client = MqttClientSingleton.getInstance().getClient();
            if (client instanceof Mqtt3AsyncClient m3) {
                m3.disconnect();
            }
        } catch (Exception e) {
            LOGGER.debug("Error disconnecting: {}", e.getMessage());
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
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to reset MqttClientSingleton: {}", e.getMessage());
        }

        try {
            MqttMessageRouter routerInstance = (MqttMessageRouter) System.getProperties().get(MqttMessageRouter.class.getName());
            if (routerInstance != null) {
                Field routerInitializedField = MqttMessageRouter.class.getDeclaredField("initialized");
                routerInitializedField.setAccessible(true);
                ((AtomicBoolean) routerInitializedField.get(routerInstance)).set(false);

                Field listenersField = MqttMessageRouter.class.getDeclaredField("listeners");
                listenersField.setAccessible(true);
                ((Map<?, ?>) listenersField.get(routerInstance)).clear();

                Field cacheField = MqttMessageRouter.class.getDeclaredField("lastMessageCache");
                cacheField.setAccessible(true);
                ((Map<?, ?>) cacheField.get(routerInstance)).clear();
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to reset MqttMessageRouter: {}", e.getMessage());
        }
    }

    @Test
    void testPublishMessageReceivedByRouter() throws Exception {
        LinkedBlockingQueue<MqttMessage> received = new LinkedBlockingQueue<>();
        router.subscribe("sensors/#", MqttQos.AT_LEAST_ONCE, received::add);

        Thread.sleep(200); // Wait for subscription

        publishMessage("sensors/temp", "{\"temp\":25}");

        MqttMessage msg = received.poll(3, TimeUnit.SECONDS);
        assertNotNull(msg, "Should receive message via router");
        assertEquals("sensors/temp", msg.getTopic());
        assertEquals("{\"temp\":25}", msg.getPayload());
    }

    @Test
    void testMultipleClientsReceiveSameMessage() throws Exception {
        LinkedBlockingQueue<MqttMessage> client1 = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<MqttMessage> client2 = new LinkedBlockingQueue<>();

        router.subscribe("sensors/#", MqttQos.AT_LEAST_ONCE, client1::add);
        router.subscribe("sensors/#", MqttQos.AT_LEAST_ONCE, client2::add);

        Thread.sleep(200);

        publishMessage("sensors/temp", "{\"temp\":25}");

        assertNotNull(client1.poll(3, TimeUnit.SECONDS), "Client 1 should receive message");
        assertNotNull(client2.poll(3, TimeUnit.SECONDS), "Client 2 should receive message");
    }

    @Test
    void testWildcardTopicFilter() throws Exception {
        LinkedBlockingQueue<MqttMessage> tempReceived = new LinkedBlockingQueue<>();
        router.subscribe("sensors/+/temp", MqttQos.AT_LEAST_ONCE, tempReceived::add);

        LinkedBlockingQueue<MqttMessage> humidityReceived = new LinkedBlockingQueue<>();
        router.subscribe("sensors/+/humidity", MqttQos.AT_LEAST_ONCE, humidityReceived::add);

        Thread.sleep(200);

        publishMessage("sensors/room1/temp", "25");
        publishMessage("sensors/room1/humidity", "60");

        assertNotNull(tempReceived.poll(3, TimeUnit.SECONDS), "Should receive temp message");
        assertNotNull(humidityReceived.poll(3, TimeUnit.SECONDS), "Should receive humidity message");
    }

    @Test
    void testClientDisconnectRemovesListener() throws Exception {
        LinkedBlockingQueue<MqttMessage> received = new LinkedBlockingQueue<>();
        java.util.function.Consumer<MqttMessage> listener = received::add;
        router.subscribe("sensors/#", MqttQos.AT_LEAST_ONCE, listener);

        Thread.sleep(200);

        // Verify subscription is active by receiving a message
        publishMessage("sensors/temp", "10");
        assertNotNull(received.poll(2, TimeUnit.SECONDS), "Should receive before unsubscribe");

        // Unsubscribe
        router.unsubscribe("sensors/#", listener);

        publishMessage("sensors/temp", "20");

        // Should not receive since we unsubscribed
        MqttMessage msg = received.poll(500, TimeUnit.MILLISECONDS);
        assertEquals(null, msg, "Should not receive after unsubscribe");
    }

    private void publishMessage(String topic, String payload) {
        MqttClient client = MqttClientSingleton.getInstance().getClient();
        if (client instanceof Mqtt3AsyncClient m3) {
            m3.publishWith()
                .topic(topic)
                .payload(payload.getBytes(StandardCharsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
                .join();
        }
    }
}
