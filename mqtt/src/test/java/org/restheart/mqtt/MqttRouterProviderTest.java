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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;

/**
 * Unit tests for {@link MqttRouterProvider} configuration parsing and defaults.
 * <p>
 * Unlike {@link MqttMessageRouter} itself, this provider is the composition root that wires the
 * router into {@link MqttClientSingleton#addOnNewSessionListener(Runnable)}, so its tests
 * legitimately need {@code MqttClientSingleton}'s static state set up via reflection, mirroring
 * {@link MqttClientSingletonTest}.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttRouterProviderTest {

    private MqttRouterProvider provider;
    private Map<String, Object> config;
    private Mqtt5AsyncClient mockClient;

    private static MqttConfig minimalConfig() {
        return new MqttConfig.Builder()
            .brokerUrl("tcp://localhost:1883")
            .protocolVersion(3)
            .clientId("test-client")
            .cleanSession(true)
            .keepAliveSeconds(60)
            .sessionExpirySeconds(0L)
            .connectTimeoutSeconds(5)
            .tlsEnabled(false)
            .reconnectConfig(new MqttConfig.ReconnectConfig(false, 1000L, 30000L))
            .willConfig(new MqttConfig.WillConfig(null, null, 0, false, 0L, null))
            .build();
    }

    private static void setStatic(String fieldName, Object value) throws Exception {
        Field field = MqttClientSingleton.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    @SuppressWarnings("unchecked")
    private static List<Runnable> newSessionListeners() throws Exception {
        Field field = MqttClientSingleton.class.getDeclaredField("newSessionListeners");
        field.setAccessible(true);
        return (List<Runnable>) field.get(MqttClientSingleton.getInstance());
    }

    private static void injectField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int intField(MqttMessageRouter router, String name) throws Exception {
        Field field = MqttMessageRouter.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(router);
    }

    private static boolean booleanField(MqttMessageRouter router, String name) throws Exception {
        Field field = MqttMessageRouter.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(router);
    }

    private static void invokeNotifyNewSessionListeners() throws Exception {
        Method method = MqttClientSingleton.class.getDeclaredMethod("notifyNewSessionListeners");
        method.setAccessible(true);
        method.invoke(MqttClientSingleton.getInstance());
    }

    @BeforeEach
    void setUp() throws Exception {
        setStatic("initialized", true);
        setStatic("config", minimalConfig());
        newSessionListeners().clear();

        provider = new MqttRouterProvider();
        config = new HashMap<>();
        mockClient = mock(Mqtt5AsyncClient.class);

        injectField(provider, "mqttClient", mockClient);
        injectField(provider, "config", config);
    }

    @AfterEach
    void tearDown() throws Exception {
        newSessionListeners().clear();
        setStatic("initialized", false);
        setStatic("config", null);
    }

    @Test
    @DisplayName("Default configuration values are applied when config map has no router keys")
    void testDefaultConfiguration() throws Exception {
        provider.init();
        MqttMessageRouter router = provider.get(null);

        assertNotNull(router);
        assertEquals(5000, intField(router, "maxMessagePerSecond"));
        assertTrue(booleanField(router, "cacheEnabled"));
        assertEquals(1000, intField(router, "maxCacheSize"));
    }

    @Test
    @DisplayName("Custom configuration values are parsed correctly")
    void testCustomConfiguration() throws Exception {
        config.put("max-inflight-messages-per-second", 42);
        config.put("last-message-cache", false);
        config.put("last-message-cache-size", 7);

        provider.init();
        MqttMessageRouter router = provider.get(null);

        assertEquals(42, intField(router, "maxMessagePerSecond"));
        assertFalse(booleanField(router, "cacheEnabled"));
        assertEquals(7, intField(router, "maxCacheSize"));
    }

    @Test
    @DisplayName("get() returns the same router instance built at init()")
    void testGetReturnsSameInstanceAcrossCalls() throws Exception {
        provider.init();

        MqttMessageRouter first = provider.get(null);
        MqttMessageRouter second = provider.get(null);

        assertSame(first, second);
    }

    @Test
    @DisplayName("init() registers a new-session listener that triggers the router's resubscribeAll()")
    void testInitRegistersNewSessionListenerTriggeringResubscribeAll() throws Exception {
        when(mockClient.subscribeWith()).thenThrow(new RuntimeException("SubscribeCalled"));

        provider.init();
        MqttMessageRouter router = provider.get(null);

        // register one listener so the router has a topic filter to resubscribe
        try {
            router.subscribe("test/topic", MqttQos.AT_LEAST_ONCE, msg -> {});
        } catch (RuntimeException expected) {
            // simulated broker-subscribe failure; the router still recorded the listener
        }

        // clear the invocation recorded by the initial subscribe() above
        reset(mockClient);
        when(mockClient.subscribeWith()).thenThrow(new RuntimeException("SubscribeCalled"));

        // simulate the underlying client reporting a new session after a reconnect: this must
        // invoke the router's resubscribeAll(), wired in by the provider's init()
        invokeNotifyNewSessionListeners();

        verify(mockClient, times(1)).subscribeWith();
    }

    @Test
    @DisplayName("a new-session listener throwing does not prevent other listeners from running")
    void testNewSessionListenerThrowingDoesNotPreventOthersFromRunning() throws Exception {
        AtomicBoolean secondListenerRan = new AtomicBoolean(false);

        MqttClientSingleton.getInstance().addOnNewSessionListener(() -> {
            throw new RuntimeException("boom");
        });
        MqttClientSingleton.getInstance().addOnNewSessionListener(() -> secondListenerRan.set(true));

        invokeNotifyNewSessionListeners();

        assertTrue(secondListenerRan.get(), "a listener throwing must not prevent later listeners from running");
    }
}
