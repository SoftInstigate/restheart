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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.restheart.mqtt.MqttMessageRouter.RouterStats;
import org.restheart.mqtt.model.MqttMessage;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.unsubscribe.Mqtt5UnsubscribeBuilder;
import com.hivemq.client.mqtt.mqtt5.message.unsubscribe.unsuback.Mqtt5UnsubAck;

/**
 * Unit tests for {@link MqttMessageRouter}.
 * <p>
 * {@code MqttMessageRouter} touches no statics: every test constructs it directly with a
 * Mockito-mocked {@link MqttClient} and nothing else, so there is no shared state between tests.
 * </p>
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttMessageRouterTest {

    private static Method privateMethod(String name, Class<?>... parameterTypes) throws Exception {
        Method method = MqttMessageRouter.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Mqtt5Publish mockPublish(String topic, String payload, MqttQos qos) {
        Mqtt5Publish publish = mock(Mqtt5Publish.class);
        when(publish.getTopic()).thenReturn(MqttTopic.of(topic));
        when(publish.getPayloadAsBytes()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));
        when(publish.getQos()).thenReturn(qos);
        return publish;
    }

    /**
     * Captures the single global publish consumer registered by the router's constructor against
     * a {@link Mqtt5AsyncClient} mocked with {@link org.mockito.Mockito#RETURNS_DEEP_STUBS}, so
     * that tests can simulate the broker delivering a publish by invoking it directly.
     */
    @SuppressWarnings("unchecked")
    private static Consumer<Mqtt5Publish> capturedGlobalConsumer(Mqtt5AsyncClient mockClient) {
        ArgumentCaptor<Consumer<Mqtt5Publish>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockClient, times(1)).publishes(eq(MqttGlobalPublishFilter.ALL), captor.capture());
        return captor.getValue();
    }

    /**
     * A hand-wired (not {@code RETURNS_DEEP_STUBS}) mock of the HiveMQ {@link Mqtt5AsyncClient}
     * subscribe/unsubscribe builder chains.
     * <p>
     * The HiveMQ builder API relies heavily on sibling generic interfaces sharing bridge methods
     * (e.g. {@code Mqtt5UnsubscribeBuilder.Send.Start} and {@code .Send.Complete} both erase
     * {@code topicFilter(String)} differently), which confuses Mockito's deep-stub cache: it can
     * either fail to reuse the same child mock across calls, or throw a {@link ClassCastException}
     * building one. Explicitly mocking each stage sidesteps that entirely.
     */
    private static final class Mqtt5Fixture {
        final Mqtt5AsyncClient client = mock(Mqtt5AsyncClient.class);
        final Mqtt5AsyncClient.Mqtt5SubscribeAndCallbackBuilder.Start.Complete subscribeStage =
            mock(Mqtt5AsyncClient.Mqtt5SubscribeAndCallbackBuilder.Start.Complete.class);

        @SuppressWarnings("unchecked")
        Mqtt5Fixture() {
            when(client.subscribeWith()).thenReturn(subscribeStage);
            when(subscribeStage.topicFilter(anyString())).thenReturn(subscribeStage);
            when(subscribeStage.qos(any())).thenReturn(subscribeStage);
            when(subscribeStage.send()).thenReturn(new CompletableFuture<>());

            Mqtt5UnsubscribeBuilder.Send.Start<CompletableFuture<Mqtt5UnsubAck>> unsubscribeStart =
                mock(Mqtt5UnsubscribeBuilder.Send.Start.class);
            Mqtt5UnsubscribeBuilder.Send.Complete<CompletableFuture<Mqtt5UnsubAck>> unsubscribeComplete =
                mock(Mqtt5UnsubscribeBuilder.Send.Complete.class);
            when(client.unsubscribeWith()).thenReturn(unsubscribeStart);
            when(unsubscribeStart.topicFilter(anyString())).thenReturn(unsubscribeComplete);
            when(unsubscribeComplete.send()).thenReturn(new CompletableFuture<>());
        }
    }

    @Test
    void testSubscribeAndUnsubscribe() {
        Mqtt5AsyncClient mockClient = mock(Mqtt5AsyncClient.class);
        when(mockClient.subscribeWith()).thenThrow(new RuntimeException("SubscribeCalled"));
        when(mockClient.unsubscribeWith()).thenThrow(new RuntimeException("UnsubscribeCalled"));

        MqttMessageRouter router = new MqttMessageRouter(mockClient, 5000, true, 1000);

        Consumer<MqttMessage> listener1 = msg -> {};
        Consumer<MqttMessage> listener2 = msg -> {};

        // 1st listener -> triggers subscribeOnBroker
        RuntimeException ex1 = assertThrows(RuntimeException.class,
            () -> router.subscribe("test/topic", MqttQos.AT_LEAST_ONCE, listener1));
        assertEquals("SubscribeCalled", ex1.getMessage());

        // 2nd listener on same topic, same QoS -> does NOT trigger subscribeOnBroker again
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

    @Test
    void testUnsupportedClientTypeLogsErrorAndDoesNotThrow() {
        // Neither Mqtt5AsyncClient nor Mqtt3AsyncClient: the missing "else" branches (finding A1)
        // must log an error rather than fail silently or throw.
        MqttClient plainClient = mock(MqttClient.class);

        MqttMessageRouter router = assertDoesNotThrow(() -> new MqttMessageRouter(plainClient, 5000, true, 1000));

        Consumer<MqttMessage> listener = msg -> {};
        assertDoesNotThrow(() -> router.subscribe("a/b", MqttQos.AT_LEAST_ONCE, listener));
        assertDoesNotThrow(() -> router.unsubscribe("a/b", listener));
    }

    @Test
    void testFanoutOrderingAndDispatch() throws Exception {
        // We will simulate `dispatchMessage` and verify listeners are called in order.
        List<String> invocationOrder = new ArrayList<>();

        Consumer<MqttMessage> listenerA = msg -> invocationOrder.add("A");
        Consumer<MqttMessage> listenerB = msg -> invocationOrder.add("B");
        Consumer<MqttMessage> listenerC = msg -> invocationOrder.add("C");

        Mqtt5AsyncClient mockClient = mock(Mqtt5AsyncClient.class, RETURNS_DEEP_STUBS);

        MqttMessageRouter router = new MqttMessageRouter(mockClient, 5000, true, 1000);

        router.subscribe("test/order", MqttQos.AT_LEAST_ONCE, listenerA);
        router.subscribe("test/order", MqttQos.AT_LEAST_ONCE, listenerB);
        router.subscribe("test/order", MqttQos.AT_LEAST_ONCE, listenerC);

        MqttMessage message = new MqttMessage("test/order", "payload", 1, Instant.now());

        Method dispatchMessageMethod = privateMethod("dispatchMessage", MqttMessage.class);
        dispatchMessageMethod.invoke(router, message);

        assertEquals(3, invocationOrder.size());
        assertEquals("A", invocationOrder.get(0));
        assertEquals("B", invocationOrder.get(1));
        assertEquals("C", invocationOrder.get(2));
    }

    // --- A1: no duplicate fanout on overlapping topic filters (the regression test for the whole task) ---

    @Test
    void testOverlappingFiltersDeliverReceivedMessageExactlyOnceToEachListener() throws Exception {
        Mqtt5AsyncClient mockClient = mock(Mqtt5AsyncClient.class, RETURNS_DEEP_STUBS);
        MqttMessageRouter router = new MqttMessageRouter(mockClient, 5000, true, 1000);

        // A single global publish consumer is registered once, at construction time
        Consumer<Mqtt5Publish> globalConsumer = capturedGlobalConsumer(mockClient);

        AtomicInteger overlappingListenerCalls = new AtomicInteger();
        AtomicInteger exactListenerCalls = new AtomicInteger();
        CountDownLatch delivered = new CountDownLatch(2);

        // Two overlapping filters, each with its own listener
        router.subscribe("sensors/#", MqttQos.AT_LEAST_ONCE, msg -> {
            overlappingListenerCalls.incrementAndGet();
            delivered.countDown();
        });
        router.subscribe("sensors/temp", MqttQos.AT_LEAST_ONCE, msg -> {
            exactListenerCalls.incrementAndGet();
            delivered.countDown();
        });

        // Registering two (even overlapping) filters must not register additional global consumers
        verify(mockClient, times(1)).publishes(eq(MqttGlobalPublishFilter.ALL), org.mockito.ArgumentMatchers.any());

        // Simulate the broker delivering exactly one publish matching both filters
        globalConsumer.accept(mockPublish("sensors/temp", "21.5", MqttQos.AT_LEAST_ONCE));

        assertTrue(delivered.await(2, TimeUnit.SECONDS), "both listeners should have been invoked");
        // give any (incorrect) duplicate async dispatch a chance to show up before asserting
        Thread.sleep(200);

        assertEquals(1, overlappingListenerCalls.get(), "the sensors/# listener must be invoked exactly once");
        assertEquals(1, exactListenerCalls.get(), "the sensors/temp listener must be invoked exactly once");
    }

    // --- A9: token-bucket rate limiter ---

    @Test
    void testRateLimitAdmitsExactlyConfiguredCountPerWindow() throws Exception {
        MqttMessageRouter router = new MqttMessageRouter(mock(MqttClient.class), 3, true, 1000);
        Method checkRateLimit = privateMethod("checkRateLimit");

        assertTrue((Boolean) checkRateLimit.invoke(router));
        assertTrue((Boolean) checkRateLimit.invoke(router));
        assertTrue((Boolean) checkRateLimit.invoke(router));
        assertFalse((Boolean) checkRateLimit.invoke(router));
        assertFalse((Boolean) checkRateLimit.invoke(router));
    }

    @Test
    void testRateLimitRefillsOverTime() throws Exception {
        MqttMessageRouter router = new MqttMessageRouter(mock(MqttClient.class), 2, true, 1000);
        Method checkRateLimit = privateMethod("checkRateLimit");

        assertTrue((Boolean) checkRateLimit.invoke(router));
        assertTrue((Boolean) checkRateLimit.invoke(router));
        assertFalse((Boolean) checkRateLimit.invoke(router));

        Thread.sleep(1050);

        assertTrue((Boolean) checkRateLimit.invoke(router), "the bucket must have refilled after ~1s");
    }

    @Test
    void testRateLimitDisabledWhenMaxMessagesPerSecondIsNonPositive() throws Exception {
        MqttMessageRouter router = new MqttMessageRouter(mock(MqttClient.class), 0, true, 1000);
        Method checkRateLimit = privateMethod("checkRateLimit");

        for (int i = 0; i < 10_000; i++) {
            assertTrue((Boolean) checkRateLimit.invoke(router), "rate limiting must be disabled when maxMessagePerSecond <= 0");
        }
    }

    @Test
    void testMessagesReceivedAndDroppedAreDistinctCumulativeCounters() {
        Mqtt5AsyncClient mockClient = mock(Mqtt5AsyncClient.class, RETURNS_DEEP_STUBS);
        MqttMessageRouter router = new MqttMessageRouter(mockClient, 2, true, 1000);

        Consumer<Mqtt5Publish> globalConsumer = capturedGlobalConsumer(mockClient);
        Mqtt5Publish publish = mockPublish("sensors/temp", "x", MqttQos.AT_LEAST_ONCE);

        for (int i = 0; i < 5; i++) {
            globalConsumer.accept(publish);
        }

        RouterStats stats = router.getStats();
        assertEquals(2, stats.getMessagesReceived(), "only messages admitted by the rate limiter are counted as received");
        assertEquals(3, stats.getMessagesDropped());
    }

    // --- A8: highest requested QoS wins, and resubscribeAll uses the tracked QoS ---

    @Test
    void testHighestRequestedQosWinsAndIsUsedByResubscribeAll() {
        Mqtt5Fixture fixture = new Mqtt5Fixture();
        MqttMessageRouter router = new MqttMessageRouter(fixture.client, 5000, true, 1000);

        ArgumentCaptor<MqttQos> qosCaptor = ArgumentCaptor.forClass(MqttQos.class);

        router.subscribe("sensors/temp", MqttQos.AT_MOST_ONCE, msg -> {});
        router.subscribe("sensors/temp", MqttQos.EXACTLY_ONCE, msg -> {}); // upgrade: must re-subscribe
        router.subscribe("sensors/temp", MqttQos.AT_LEAST_ONCE, msg -> {}); // lower than tracked: no new broker call

        verify(fixture.subscribeStage, times(2)).qos(qosCaptor.capture());
        List<MqttQos> requestedSoFar = qosCaptor.getAllValues();
        assertEquals(MqttQos.AT_MOST_ONCE, requestedSoFar.get(0));
        assertEquals(MqttQos.EXACTLY_ONCE, requestedSoFar.get(1));

        router.resubscribeAll();

        // a fresh captor: capture() re-records every matching invocation on every verify() call,
        // so re-using qosCaptor here would duplicate the first two entries instead of adding one
        ArgumentCaptor<MqttQos> resubscribeQosCaptor = ArgumentCaptor.forClass(MqttQos.class);
        verify(fixture.subscribeStage, times(3)).qos(resubscribeQosCaptor.capture());
        assertEquals(MqttQos.EXACTLY_ONCE, resubscribeQosCaptor.getAllValues().get(2),
            "resubscribeAll must use the tracked (highest) QoS, not a constant");
    }

    // --- Item 6: startup (configured) subscriptions survive listener churn ---

    @Test
    void testConfiguredSubscriptionSurvivesLastListenerUnsubscribing() {
        Mqtt5AsyncClient mockClient = mock(Mqtt5AsyncClient.class, RETURNS_DEEP_STUBS);
        MqttMessageRouter router = new MqttMessageRouter(mockClient, 5000, true, 1000);

        router.subscribeFromConfig("sensors/#", MqttQos.AT_LEAST_ONCE);

        Consumer<MqttMessage> listener = msg -> {};
        router.subscribe("sensors/#", MqttQos.AT_LEAST_ONCE, listener);
        router.unsubscribe("sensors/#", listener);

        assertEquals(1, router.getStats().getTopicFilters(),
            "a configured filter must remain subscribed after its last listener unsubscribes");
        verify(mockClient, never()).unsubscribeWith();
    }

    @Test
    void testNonConfiguredSubscriptionIsUnsubscribedWhenLastListenerLeaves() {
        Mqtt5Fixture fixture = new Mqtt5Fixture();
        MqttMessageRouter router = new MqttMessageRouter(fixture.client, 5000, true, 1000);

        Consumer<MqttMessage> listener = msg -> {};
        router.subscribe("devices/status", MqttQos.AT_LEAST_ONCE, listener);
        router.unsubscribe("devices/status", listener);

        assertEquals(0, router.getStats().getTopicFilters(),
            "a non-configured filter must be unsubscribed once its last listener leaves");
        verify(fixture.client, times(1)).unsubscribeWith();
    }

    // --- A4: last-message cache ---

    @Test
    void testGetLastMessageKeepsExactTopicSemantics() throws Exception {
        MqttMessageRouter router = new MqttMessageRouter(mock(MqttClient.class), 5000, true, 1000);

        Method updateCache = privateMethod("updateCache", MqttMessage.class);
        MqttMessage message = new MqttMessage("test/cache", "payload", 1, Instant.now());
        updateCache.invoke(router, message);

        MqttMessage cached = router.getLastMessage("test/cache");
        assertNotNull(cached);
        assertEquals("payload", cached.getPayload());
        assertEquals("test/cache", cached.getTopic());

        // a topic filter is not a topic: exact lookup must not match through wildcards
        assertNull(router.getLastMessage("test/#"));
    }

    @Test
    void testGetLastMessagesMatchesWildcardFilterOrderedByReceivedAt() throws Exception {
        MqttMessageRouter router = new MqttMessageRouter(mock(MqttClient.class), 5000, true, 1000);
        Method updateCache = privateMethod("updateCache", MqttMessage.class);

        MqttMessage later = new MqttMessage("sensors/temp", "20", 1, Instant.parse("2026-01-01T00:00:05Z"));
        MqttMessage earlier = new MqttMessage("sensors/humidity", "55", 1, Instant.parse("2026-01-01T00:00:00Z"));
        MqttMessage unrelated = new MqttMessage("traffic/flow", "3", 1, Instant.parse("2026-01-01T00:00:02Z"));

        // insert out of chronological order
        updateCache.invoke(router, later);
        updateCache.invoke(router, unrelated);
        updateCache.invoke(router, earlier);

        List<MqttMessage> matches = router.getLastMessages("sensors/#");

        assertEquals(2, matches.size());
        assertEquals("sensors/humidity", matches.get(0).getTopic(), "results must be ordered by receivedAt");
        assertEquals("sensors/temp", matches.get(1).getTopic());

        assertTrue(router.getLastMessages("nothing/here").isEmpty());
    }

    @Test
    void testCacheEvictsLeastRecentlyUsedEntryAtCapacity() throws Exception {
        MqttMessageRouter router = new MqttMessageRouter(mock(MqttClient.class), 5000, true, 2);
        Method updateCache = privateMethod("updateCache", MqttMessage.class);

        updateCache.invoke(router, new MqttMessage("t1", "a", 0, Instant.now()));
        updateCache.invoke(router, new MqttMessage("t2", "b", 0, Instant.now()));

        // accessing t1 makes it the most recently used
        assertNotNull(router.getLastMessage("t1"));

        // exceeding capacity must evict the least recently used entry (t2), not an arbitrary one
        updateCache.invoke(router, new MqttMessage("t3", "c", 0, Instant.now()));

        assertNull(router.getLastMessage("t2"), "the least recently used entry must be evicted");
        assertNotNull(router.getLastMessage("t1"));
        assertNotNull(router.getLastMessage("t3"));
        assertEquals(2, router.getStats().getCachedMessages());
    }

    @Test
    void testConcurrentCacheUpdatesDoNotExceedCapacityOrThrow() throws Exception {
        int capacity = 50;
        MqttMessageRouter router = new MqttMessageRouter(mock(MqttClient.class), 5000, true, capacity);
        Method updateCache = privateMethod("updateCache", MqttMessage.class);

        int producers = 8;
        int messagesPerProducer = 500;
        ExecutorService executor = Executors.newFixedThreadPool(producers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int p = 0; p < producers; p++) {
            int producerId = p;
            futures.add(executor.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < messagesPerProducer; i++) {
                        updateCache.invoke(router,
                            new MqttMessage("topic/" + producerId + "/" + i, "payload", 0, Instant.now()));
                    }
                } catch (Throwable t) {
                    failure.set(t);
                }
            }));
        }

        start.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertNull(failure.get(), "concurrent cache updates must never throw (no check-then-act race)");
        assertTrue(router.getStats().getCachedMessages() <= capacity,
            "the cache must never exceed its configured capacity under concurrent updates");
    }
}
