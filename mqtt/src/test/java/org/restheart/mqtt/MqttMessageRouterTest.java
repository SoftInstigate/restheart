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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.restheart.mqtt.MqttMessageRouter.RouterStats;
import org.restheart.mqtt.model.MqttMessage;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;

/**
 * Unit tests for MqttMessageRouter
 * Tests verify that the router correctly handles subscription and unsubscription logic.
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttMessageRouterTest {

    private MqttMessageRouter router;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        router = MqttMessageRouter.getInstance();

        // Clear listeners
        Field listenersField = MqttMessageRouter.class.getDeclaredField("listeners");
        listenersField.setAccessible(true);
        ((Map<?, ?>) listenersField.get(router)).clear();

        // Clear cache
        Field cacheField = MqttMessageRouter.class.getDeclaredField("lastMessageCache");
        cacheField.setAccessible(true);
        ((Map<?, ?>) cacheField.get(router)).clear();

        // Reset rate limiter
        Field messageCountField = MqttMessageRouter.class.getDeclaredField("messageCount");
        messageCountField.setAccessible(true);
        ((AtomicLong) messageCountField.get(router)).set(0);

        Field droppedCountField = MqttMessageRouter.class.getDeclaredField("droppedCount");
        droppedCountField.setAccessible(true);
        ((AtomicLong) droppedCountField.get(router)).set(0);

        // Re-init with defaults
        Field initField = MqttMessageRouter.class.getDeclaredField("initialized");
        initField.setAccessible(true);
        ((AtomicBoolean) initField.get(router)).set(false);

        router.init(5000, true, 1000);
    }

    @Test
    void testSubscribeAndUnsubscribe() {
        try (MockedStatic<MqttClientSingleton> singletonMock = mockStatic(MqttClientSingleton.class)) {
            MqttClientSingleton mockInstance = mock(MqttClientSingleton.class);
            Mqtt5AsyncClient mockClient = mock(Mqtt5AsyncClient.class);
            
            when(mockClient.subscribeWith()).thenThrow(new RuntimeException("SubscribeCalled"));
            when(mockClient.unsubscribeWith()).thenThrow(new RuntimeException("UnsubscribeCalled"));

            when(mockInstance.getClient()).thenReturn(mockClient);
            singletonMock.when(MqttClientSingleton::getInstance).thenReturn(mockInstance);

            Consumer<MqttMessage> listener1 = msg -> {};
            Consumer<MqttMessage> listener2 = msg -> {};

            // 1st listener -> triggers subscribeOnBroker
            RuntimeException ex1 = assertThrows(RuntimeException.class, 
                () -> router.subscribe("test/topic", MqttQos.AT_LEAST_ONCE, listener1));
            assertEquals("SubscribeCalled", ex1.getMessage());

            // 2nd listener on same topic -> does NOT trigger subscribeOnBroker again
            router.subscribe("test/topic", MqttQos.AT_LEAST_ONCE, listener2);

            // Unsubscribe listener1 -> does NOT trigger unsubscribeFromBroker
            router.unsubscribe("test/topic", listener1);

            // Unsubscribe listener2 -> last listener, triggers unsubscribeFromBroker
            RuntimeException ex2 = assertThrows(RuntimeException.class, 
                () -> router.unsubscribe("test/topic", listener2));
            assertEquals("UnsubscribeCalled", ex2.getMessage());
            
            // Verify stats
            RouterStats stats = router.getStats();
            assertEquals(0, stats.getTopicFilters());
            assertEquals(0, stats.getTotalListeners());
        }
    }

    @Test
    void testTopicMatching() throws Exception {
        Method topicMatchesMethod = MqttMessageRouter.class.getDeclaredMethod("topicMatches", String.class, String.class);
        topicMatchesMethod.setAccessible(true);

        // Exact match
        assertTrue((Boolean) topicMatchesMethod.invoke(router, "sensors/temp", "sensors/temp"));
        assertFalse((Boolean) topicMatchesMethod.invoke(router, "sensors/temp", "sensors/hum"));

        // Single level wildcard (+)
        assertTrue((Boolean) topicMatchesMethod.invoke(router, "sensors/temp", "sensors/+"));
        assertTrue((Boolean) topicMatchesMethod.invoke(router, "sensors/hum", "sensors/+"));
        assertFalse((Boolean) topicMatchesMethod.invoke(router, "sensors/temp/1", "sensors/+"));

        // Multi-level wildcard (#)
        assertTrue((Boolean) topicMatchesMethod.invoke(router, "sensors/temp", "sensors/#"));
        assertTrue((Boolean) topicMatchesMethod.invoke(router, "sensors/temp/1", "sensors/#"));
        assertFalse((Boolean) topicMatchesMethod.invoke(router, "system/temp", "sensors/#"));
        
        // Single level wildcards embedded
        assertTrue((Boolean) topicMatchesMethod.invoke(router, "a/b", "+/+"));
    }

    @Test
    void testRateLimitEnforcement() throws Exception {
        // Re-init with limit = 2 msgs per second
        Field initField = MqttMessageRouter.class.getDeclaredField("initialized");
        initField.setAccessible(true);
        ((AtomicBoolean) initField.get(router)).set(false);
        router.init(2, true, 1000);

        Method checkRateLimitMethod = MqttMessageRouter.class.getDeclaredMethod("checkRateLimit");
        checkRateLimitMethod.setAccessible(true);

        // First 2 should pass
        assertTrue((Boolean) checkRateLimitMethod.invoke(router));
        assertTrue((Boolean) checkRateLimitMethod.invoke(router));
        
        // 3rd should fail
        assertFalse((Boolean) checkRateLimitMethod.invoke(router));
        assertFalse((Boolean) checkRateLimitMethod.invoke(router));

        // Sleep 1 second
        Thread.sleep(1050);

        // Should pass again
        assertTrue((Boolean) checkRateLimitMethod.invoke(router));
    }

    @Test
    void testFanoutOrderingAndDispatch() throws Exception {
        // We will simulate `dispatchMessage` and verify listeners are called in order.
        List<String> invocationOrder = new ArrayList<>();

        Consumer<MqttMessage> listenerA = msg -> invocationOrder.add("A");
        Consumer<MqttMessage> listenerB = msg -> invocationOrder.add("B");
        Consumer<MqttMessage> listenerC = msg -> invocationOrder.add("C");

        try (MockedStatic<MqttClientSingleton> singletonMock = mockStatic(MqttClientSingleton.class)) {
            MqttClientSingleton mockInstance = mock(MqttClientSingleton.class);
            Mqtt5AsyncClient mockClient = mock(Mqtt5AsyncClient.class);
            when(mockClient.subscribeWith()).thenThrow(new RuntimeException("Ignored"));
            when(mockInstance.getClient()).thenReturn(mockClient);
            singletonMock.when(MqttClientSingleton::getInstance).thenReturn(mockInstance);

            try {
                router.subscribe("test/order", MqttQos.AT_LEAST_ONCE, listenerA);
            } catch (RuntimeException e) {}
            router.subscribe("test/order", MqttQos.AT_LEAST_ONCE, listenerB);
            router.subscribe("test/order", MqttQos.AT_LEAST_ONCE, listenerC);
        }

        MqttMessage message = new MqttMessage("test/order", "payload", 1, Instant.now());

        Method dispatchMessageMethod = MqttMessageRouter.class.getDeclaredMethod("dispatchMessage", MqttMessage.class);
        dispatchMessageMethod.setAccessible(true);
        dispatchMessageMethod.invoke(router, message);

        assertEquals(3, invocationOrder.size());
        assertEquals("A", invocationOrder.get(0));
        assertEquals("B", invocationOrder.get(1));
        assertEquals("C", invocationOrder.get(2));
    }

    @Test
    void testLastMessageCache() throws Exception {
        MqttMessage message = new MqttMessage("test/cache", "payload", 1, Instant.now());
        
        Method updateCacheMethod = MqttMessageRouter.class.getDeclaredMethod("updateCache", MqttMessage.class);
        updateCacheMethod.setAccessible(true);
        updateCacheMethod.invoke(router, message);

        MqttMessage cached = router.getLastMessage("test/cache");
        assertNotNull(cached);
        assertEquals("payload", cached.getPayload());
        assertEquals("test/cache", cached.getTopic());
    }
}

