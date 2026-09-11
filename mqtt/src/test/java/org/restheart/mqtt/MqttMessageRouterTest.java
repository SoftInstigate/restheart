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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.restheart.mqtt.MqttMessageRouter.RouterStats;
import org.restheart.mqtt.model.MqttMessage;
import org.restheart.mqtt.model.Qos;

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

    private static Method privateMethod(String name, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        Method method = MqttMessageRouter.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Mqtt5Publish mockPublish(String topic, String payload, MqttQos qos) {
        return mockPublish(topic, payload, qos, false);
    }

    private static Mqtt5Publish mockPublish(String topic, String payload, MqttQos qos, boolean retain) {
        Mqtt5Publish publish = mock(Mqtt5Publish.class);
        when(publish.getTopic()).thenReturn(MqttTopic.of(topic));
        when(publish.getPayloadAsBytes()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));
        when(publish.getQos()).thenReturn(qos);
        when(publish.isRetain()).thenReturn(retain);
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
        // The three-argument overload: the router registers with manualAcknowledgement = true, so
        // that a durable listener can hold a message until it has actually taken it.
        verify(mockClient, times(1)).publishes(eq(MqttGlobalPublishFilter.ALL), captor.capture(), eq(true));
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
        final Mqtt5UnsubscribeBuilder.Send.Start<CompletableFuture<Mqtt5UnsubAck>> unsubscribeStart;

        @SuppressWarnings("unchecked")
        Mqtt5Fixture() {
            when(client.subscribeWith()).thenReturn(subscribeStage);
            when(subscribeStage.topicFilter(anyString())).thenReturn(subscribeStage);
            when(subscribeStage.qos(any())).thenReturn(subscribeStage);
            when(subscribeStage.send()).thenReturn(new CompletableFuture<>());

            unsubscribeStart = mock(Mqtt5UnsubscribeBuilder.Send.Start.class);
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
            () -> router.subscribe("test/topic", Qos.AT_LEAST_ONCE, listener1));
        assertEquals("SubscribeCalled", ex1.getMessage());

        // 2nd listener on same topic, same QoS -> does NOT trigger subscribeOnBroker again
        router.subscribe("test/topic", Qos.AT_LEAST_ONCE, listener2);

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
        assertDoesNotThrow(() -> router.subscribe("a/b", Qos.AT_LEAST_ONCE, listener));
        assertDoesNotThrow(() -> router.unsubscribe("a/b", listener));
    }

    @Test
    void testFanoutOrderingAndDispatch() throws ReflectiveOperationException {
        // We will simulate `dispatchMessage` and verify listeners are called in order.
        List<String> invocationOrder = new ArrayList<>();

        Consumer<MqttMessage> listenerA = msg -> invocationOrder.add("A");
        Consumer<MqttMessage> listenerB = msg -> invocationOrder.add("B");
        Consumer<MqttMessage> listenerC = msg -> invocationOrder.add("C");

        Mqtt5AsyncClient mockClient = mock(Mqtt5AsyncClient.class, RETURNS_DEEP_STUBS);

        MqttMessageRouter router = new MqttMessageRouter(mockClient, 5000, true, 1000);

        router.subscribe("test/order", Qos.AT_LEAST_ONCE, listenerA);
        router.subscribe("test/order", Qos.AT_LEAST_ONCE, listenerB);
        router.subscribe("test/order", Qos.AT_LEAST_ONCE, listenerC);

        MqttMessage message = new MqttMessage("test/order", "payload", 1, Instant.now());

        Method dispatchMessageMethod = privateMethod("dispatchMessage", MqttMessage.class, Runnable.class);
        dispatchMessageMethod.invoke(router, message, (Runnable) () -> { });

        assertEquals(3, invocationOrder.size());
        assertEquals("A", invocationOrder.get(0));
        assertEquals("B", invocationOrder.get(1));
        assertEquals("C", invocationOrder.get(2));
    }

    // --- A1: no duplicate fanout on overlapping topic filters (the regression test for the whole task) ---

    @Test
    void testOverlappingFiltersDeliverReceivedMessageExactlyOnceToEachListener()
        throws ReflectiveOperationException, InterruptedException {
        Mqtt5AsyncClient mockClient = mock(Mqtt5AsyncClient.class, RETURNS_DEEP_STUBS);
        MqttMessageRouter router = new MqttMessageRouter(mockClient, 5000, true, 1000);

        // A single global publish consumer is registered once, at construction time
        Consumer<Mqtt5Publish> globalConsumer = capturedGlobalConsumer(mockClient);

        AtomicInteger overlappingListenerCalls = new AtomicInteger();
        AtomicInteger exactListenerCalls = new AtomicInteger();
        CountDownLatch delivered = new CountDownLatch(2);

        // Two overlapping filters, each with its own listener
        router.subscribe("sensors/#", Qos.AT_LEAST_ONCE, msg -> {
            overlappingListenerCalls.incrementAndGet();
            delivered.countDown();
        });
        router.subscribe("sensors/temp", Qos.AT_LEAST_ONCE, msg -> {
            exactListenerCalls.incrementAndGet();
            delivered.countDown();
        });

        // Registering two (even overlapping) filters must not register additional global consumers
        verify(mockClient, times(1)).publishes(eq(MqttGlobalPublishFilter.ALL), org.mockito.ArgumentMatchers.any(), eq(true));

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
    void testRateLimitAdmitsExactlyConfiguredCountPerWindow() throws ReflectiveOperationException {
        MqttMessageRouter router = new MqttMessageRouter(mock(MqttClient.class), 3, true, 1000);
        Method checkRateLimit = privateMethod("checkRateLimit");

        assertTrue((Boolean) checkRateLimit.invoke(router));
        assertTrue((Boolean) checkRateLimit.invoke(router));
        assertTrue((Boolean) checkRateLimit.invoke(router));
        assertFalse((Boolean) checkRateLimit.invoke(router));
        assertFalse((Boolean) checkRateLimit.invoke(router));
    }

    @Test
    void testRateLimitRefillsOverTime() throws ReflectiveOperationException, InterruptedException {
        MqttMessageRouter router = new MqttMessageRouter(mock(MqttClient.class), 2, true, 1000);
        Method checkRateLimit = privateMethod("checkRateLimit");

        assertTrue((Boolean) checkRateLimit.invoke(router));
        assertTrue((Boolean) checkRateLimit.invoke(router));
        assertFalse((Boolean) checkRateLimit.invoke(router));

        Thread.sleep(1050);

        assertTrue((Boolean) checkRateLimit.invoke(router), "the bucket must have refilled after ~1s");
    }

    @Test
    void testRateLimitDisabledWhenMaxMessagesPerSecondIsNonPositive() throws ReflectiveOperationException {
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

        // Dispatch runs on a virtual thread per message, and the rate-limit decision now lives
        // there, so the drop counter is settled asynchronously.
        awaitCondition(() -> router.getStats().getMessagesDropped() == 3, 5_000);

        RouterStats stats = router.getStats();

        // "Received" means received from the broker - all five - not "survived the rate limiter".
        // The counters deliberately overlap now: a rate-limited message is still delivered to
        // every durable listener and still persisted, so calling it unreceived would be false,
        // and dropped/received is the loss rate an operator actually wants.
        assertEquals(5, stats.getMessagesReceived(),
            "every message the broker delivered is received, whatever the rate limiter then does with it");
        assertEquals(3, stats.getMessagesDropped(), "three were refused live delivery");
    }

    // --- A8: highest requested QoS wins, and resubscribeAll uses the tracked QoS ---

    @Test
    void testHighestRequestedQosWinsAndIsUsedByResubscribeAll() {
        Mqtt5Fixture fixture = new Mqtt5Fixture();
        MqttMessageRouter router = new MqttMessageRouter(fixture.client, 5000, true, 1000);

        ArgumentCaptor<MqttQos> qosCaptor = ArgumentCaptor.forClass(MqttQos.class);

        router.subscribe("sensors/temp", Qos.AT_MOST_ONCE, msg -> {});
        router.subscribe("sensors/temp", Qos.EXACTLY_ONCE, msg -> {}); // upgrade: must re-subscribe
        router.subscribe("sensors/temp", Qos.AT_LEAST_ONCE, msg -> {}); // lower than tracked: no new broker call

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

        router.subscribeFromConfig("sensors/#", Qos.AT_LEAST_ONCE);

        Consumer<MqttMessage> listener = msg -> {};
        router.subscribe("sensors/#", Qos.AT_LEAST_ONCE, listener);
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
        router.subscribe("devices/status", Qos.AT_LEAST_ONCE, listener);
        router.unsubscribe("devices/status", listener);

        assertEquals(0, router.getStats().getTopicFilters(),
            "a non-configured filter must be unsubscribed once its last listener leaves");
        verify(fixture.client, times(1)).unsubscribeWith();
    }

    // --- A4: last-message cache ---

    @Test
    void testGetLastMessageKeepsExactTopicSemantics() throws ReflectiveOperationException {
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
    void testGetLastMessagesMatchesWildcardFilterOrderedByReceivedAt() throws ReflectiveOperationException {
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
    void testCacheEvictsLeastRecentlyUsedEntryAtCapacity() throws ReflectiveOperationException {
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
    void testConcurrentCacheUpdatesDoNotExceedCapacityOrThrow()
        throws InterruptedException, ExecutionException, TimeoutException, NoSuchMethodException {
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
    @Test
    void testConcurrentSubscribeIsNotLostByAnUnsubscribeOfTheLastListener()
        throws InterruptedException, ExecutionException, TimeoutException {
        // Regression test. subscribe() and unsubscribe() used to mutate the listener list and the
        // map that holds it as separate steps: unsubscribe removed the last listener, saw the
        // list was empty, and then called listeners.remove(filter, list). A subscribe() landing
        // in that window took the SAME list object back out of the map and added to it - and the
        // two-argument remove still matched, because it compares the mapped value with the one
        // passed and they are the same object whatever it now contains. The new listener was
        // evicted along with the broker subscription, and its owner never learned: it simply
        // received nothing, forever. Two SSE clients opening and closing on one topic filter is
        // enough to reach it.
        //
        // The assertion is on the router's own observable state rather than on internals: after
        // each round exactly one listener - the newly subscribed one - must still be registered.
        var router = new MqttMessageRouter(mock(MqttClient.class), 5000, true, 100);
        var topic = "sensors/race";

        var executor = Executors.newFixedThreadPool(2);
        var failure = new AtomicReference<Throwable>();
        try {
            for (int round = 0; round < 500 && failure.get() == null; round++) {
                Consumer<MqttMessage> leaving = msg -> { };
                Consumer<MqttMessage> arriving = msg -> { };
                router.subscribe(topic, Qos.AT_LEAST_ONCE, leaving);

                var start = new CountDownLatch(1);
                var unsubscribing = executor.submit(() -> {
                    await(start, failure);
                    router.unsubscribe(topic, leaving);
                });
                var subscribing = executor.submit(() -> {
                    await(start, failure);
                    router.subscribe(topic, Qos.AT_LEAST_ONCE, arriving);
                });

                start.countDown();
                unsubscribing.get(10, TimeUnit.SECONDS);
                subscribing.get(10, TimeUnit.SECONDS);

                assertEquals(1, router.getStats().getTotalListeners(),
                    "a subscribe concurrent with the unsubscribe of the last listener must survive "
                        + "it; round " + round);

                router.unsubscribe(topic, arriving);
                assertEquals(0, router.getStats().getTotalListeners(),
                    "the filter must be left clean between rounds; round " + round);
            }
        } finally {
            executor.shutdown();
        }

        assertNull(failure.get(), "no round may throw");
    }

    private static void await(CountDownLatch latch, AtomicReference<Throwable> failure) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failure.set(e);
        }
    }

    // --- manual acknowledgement: who gets to hold up the broker ---

    @Test
    void testWithNoDurableListenerTheMessageIsAcknowledgedImmediately() throws Exception {
        // The common case - an SSE-only deployment - must behave exactly as it always has. Someone
        // watching a dashboard has no business delaying an acknowledgement.
        MqttMessageRouter router = new MqttMessageRouter(mock(MqttClient.class), 5000, true, 100);
        router.subscribe("sensors/#", Qos.AT_LEAST_ONCE, msg -> { });

        var acked = new AtomicBoolean(false);
        Method dispatch = privateMethod("dispatchMessage", MqttMessage.class, Runnable.class);
        dispatch.invoke(router, new MqttMessage("sensors/temp", "1", 1, Instant.now()),
            (Runnable) () -> acked.set(true));

        assertTrue(acked.get(), "with nothing durable registered the message must be acknowledged at once");
    }

    @Test
    void testADurableListenerHoldsTheAcknowledgementUntilItReportsIn() throws Exception {
        MqttMessageRouter router = new MqttMessageRouter(mock(MqttClient.class), 5000, true, 100);

        var held = new AtomicReference<Runnable>();
        router.subscribeDurable("sensors/#", Qos.AT_LEAST_ONCE, (msg, taken) -> held.set(taken));

        var acked = new AtomicBoolean(false);
        Method dispatch = privateMethod("dispatchMessage", MqttMessage.class, Runnable.class);
        dispatch.invoke(router, new MqttMessage("sensors/temp", "1", 1, Instant.now()),
            (Runnable) () -> acked.set(true));

        // This is the whole point: the listener has been handed the message but has not yet taken
        // responsibility for it - it is in an in-memory buffer, not on disk - so the broker must
        // still consider it owed.
        assertFalse(acked.get(), "a durable listener that has not reported in must hold the acknowledgement");

        held.get().run();
        assertTrue(acked.get(), "and release it once it has");
    }

    @Test
    void testTheAcknowledgementWaitsForTheLastDurableListener() throws Exception {
        MqttMessageRouter router = new MqttMessageRouter(mock(MqttClient.class), 5000, true, 100);

        var first = new AtomicReference<Runnable>();
        var second = new AtomicReference<Runnable>();
        router.subscribeDurable("sensors/#", Qos.AT_LEAST_ONCE, (msg, taken) -> first.set(taken));
        router.subscribeDurable("sensors/temp", Qos.AT_LEAST_ONCE, (msg, taken) -> second.set(taken));

        var acked = new AtomicBoolean(false);
        Method dispatch = privateMethod("dispatchMessage", MqttMessage.class, Runnable.class);
        dispatch.invoke(router, new MqttMessage("sensors/temp", "1", 1, Instant.now()),
            (Runnable) () -> acked.set(true));

        first.get().run();
        assertFalse(acked.get(), "one of two is not enough - the other has not taken it yet");

        // Calling back twice must not release on the other's behalf: the guard is per listener.
        first.get().run();
        assertFalse(acked.get(), "a listener reporting twice must not count as two");

        second.get().run();
        assertTrue(acked.get(), "the last one releases it");
    }

    @Test
    void testADurableListenerThatThrowsLeavesTheMessageUnacknowledged() throws Exception {
        MqttMessageRouter router = new MqttMessageRouter(mock(MqttClient.class), 5000, true, 100);
        router.subscribeDurable("sensors/#", Qos.AT_LEAST_ONCE, (msg, taken) -> {
            throw new IllegalStateException("cannot take it");
        });

        var acked = new AtomicBoolean(false);
        Method dispatch = privateMethod("dispatchMessage", MqttMessage.class, Runnable.class);
        dispatch.invoke(router, new MqttMessage("sensors/temp", "1", 1, Instant.now()),
            (Runnable) () -> acked.set(true));

        // Acknowledging here would tell the broker the message is safe when it demonstrably is
        // not, and it would never be redelivered. Leaving it unacknowledged costs an in-flight
        // slot; losing it costs the message.
        assertFalse(acked.get(),
            "a listener that failed to take the message must not have it acknowledged on its behalf");
    }

    @Test
    void testALiveListenerCannotHoldUpADurableOne() throws Exception {
        MqttMessageRouter router = new MqttMessageRouter(mock(MqttClient.class), 5000, true, 100);
        router.subscribe("sensors/#", Qos.AT_LEAST_ONCE, msg -> {
            throw new IllegalStateException("a broken SSE listener");
        });
        var held = new AtomicReference<Runnable>();
        router.subscribeDurable("sensors/#", Qos.AT_LEAST_ONCE, (msg, taken) -> held.set(taken));

        var acked = new AtomicBoolean(false);
        Method dispatch = privateMethod("dispatchMessage", MqttMessage.class, Runnable.class);
        dispatch.invoke(router, new MqttMessage("sensors/temp", "1", 1, Instant.now()),
            (Runnable) () -> acked.set(true));

        held.get().run();
        assertTrue(acked.get(),
            "a failing live listener must not affect the durable path - the two have opposite "
                + "obligations and must not be able to interfere");
    }

    /**
     * Spins until {@code condition} holds or {@code timeoutMillis} elapses, failing the test if it
     * never does. Needed because dispatch runs on a virtual thread per message, so anything it
     * counts is settled after the call that triggered it has already returned.
     *
     * @param condition     what to wait for
     * @param timeoutMillis how long to wait before failing
     */
    private static void awaitCondition(java.util.function.BooleanSupplier condition, long timeoutMillis) {
        var deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.onSpinWait();
        }
        org.junit.jupiter.api.Assertions.fail("condition not met within " + timeoutMillis + " ms");
    }

    @Test
    void testTheRateLimitRefusesLiveDeliveryButNeverThePersistencePath() {
        // A limit of 1/s, then five messages. Live listeners see one; the durable listener must
        // see all five and be given the chance to take every one of them.
        //
        // This is the separation the limit lacked: it ran before the fan-out, so a number chosen
        // to protect a dashboard silently governed what reached storage too. And once the
        // acknowledgement became manual, a message cut there was never acknowledged either, so a
        // flood filled the broker's in-flight window and stalled delivery outright.
        Mqtt5AsyncClient mockClient = mock(Mqtt5AsyncClient.class, RETURNS_DEEP_STUBS);
        MqttMessageRouter router = new MqttMessageRouter(mockClient, 1, true, 1000);

        var live = new AtomicInteger();
        var durable = new AtomicInteger();
        router.subscribe("sensors/#", Qos.AT_LEAST_ONCE, msg -> live.incrementAndGet());
        router.subscribeDurable("sensors/#", Qos.AT_LEAST_ONCE, (msg, taken) -> {
            durable.incrementAndGet();
            taken.run();
        });

        Consumer<Mqtt5Publish> globalConsumer = capturedGlobalConsumer(mockClient);
        for (int i = 0; i < 5; i++) {
            globalConsumer.accept(mockPublish("sensors/temp", "x", MqttQos.AT_LEAST_ONCE));
        }

        awaitCondition(() -> durable.get() == 5, 5_000);

        assertEquals(5, durable.get(), "persistence must see every message, whatever the rate limit says");
        assertEquals(1, live.get(), "live delivery is what the rate limit governs");
        assertEquals(4, router.getStats().getMessagesDropped(), "and the four it refused are counted");
    }


    // --- Broker subscriptions are a minimal covering set, not one per routed filter.
    //
    // MQTT 3.1.1 lets a broker deliver one copy of a message per matching subscription, and
    // Mosquitto does. Subscribing to every routed filter therefore meant that an SSE client asking
    // for "sensors/temp" while mqtt-router.subscriptions held "sensors/#" - the configuration the
    // README tells people to use - made every message on that topic arrive twice: two SSE events,
    // two callbacks into a custom plugin, two MongoDB documents under id-strategy auto, and a
    // doubled mqtt_router_messages_received. Measured against a real Mosquitto before the fix. ---

    @Test
    @DisplayName("a filter covered by a broader subscription is not subscribed on the broker")
    void testCoveredFilterIsNotSubscribedOnTheBroker() {
        Mqtt5Fixture fixture = new Mqtt5Fixture();
        MqttMessageRouter router = new MqttMessageRouter(fixture.client, 5000, true, 1000);

        router.subscribeFromConfig("sensors/#", Qos.AT_LEAST_ONCE);
        router.subscribe("sensors/temp", Qos.AT_LEAST_ONCE, msg -> {});

        ArgumentCaptor<String> filterCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.subscribeStage, times(1)).topicFilter(filterCaptor.capture());
        assertEquals(List.of("sensors/#"), filterCaptor.getAllValues(),
            "sensors/temp is already delivered by sensors/#; subscribing to both makes the broker "
                + "send every matching message twice");
    }

    @Test
    @DisplayName("a covered filter still receives its messages, routed locally")
    void testCoveredFilterStillReceivesMessages() {
        Mqtt5AsyncClient mockClient = mock(Mqtt5AsyncClient.class, RETURNS_DEEP_STUBS);
        MqttMessageRouter router = new MqttMessageRouter(mockClient, 5000, true, 1000);

        var delivered = new java.util.concurrent.CopyOnWriteArrayList<String>();
        router.subscribeFromConfig("sensors/#", Qos.AT_LEAST_ONCE);
        router.subscribe("sensors/temp", Qos.AT_LEAST_ONCE, msg -> delivered.add(msg.getPayload()));

        capturedGlobalConsumer(mockClient).accept(mockPublish("sensors/temp", "hello", MqttQos.AT_LEAST_ONCE));

        // Not subscribing a covered filter on the broker is only safe because dispatch matches
        // each message against every routed filter locally.
        awaitCondition(() -> delivered.size() == 1, 5_000);
        assertEquals(List.of("hello"), List.copyOf(delivered),
            "exactly once - the whole point of covering is that it changes nothing but the copies");
    }

    @Test
    @DisplayName("a broader filter arriving later takes over, and the narrower one is unsubscribed")
    void testBroaderFilterSupersedesAnAlreadySubscribedNarrowerOne() {
        Mqtt5Fixture fixture = new Mqtt5Fixture();
        MqttMessageRouter router = new MqttMessageRouter(fixture.client, 5000, true, 1000);

        router.subscribe("sensors/temp", Qos.AT_LEAST_ONCE, msg -> {});
        router.subscribeFromConfig("sensors/#", Qos.AT_LEAST_ONCE);

        ArgumentCaptor<String> subscribed = ArgumentCaptor.forClass(String.class);
        verify(fixture.subscribeStage, times(2)).topicFilter(subscribed.capture());
        assertEquals(List.of("sensors/temp", "sensors/#"), subscribed.getAllValues());

        ArgumentCaptor<String> unsubscribed = ArgumentCaptor.forClass(String.class);
        verify(fixture.unsubscribeStart, times(1)).topicFilter(unsubscribed.capture());
        assertEquals("sensors/temp", unsubscribed.getValue(),
            "once sensors/# is subscribed, keeping sensors/temp too duplicates every message");
    }

    @Test
    @DisplayName("covering never downgrades QoS: the covering filter is subscribed at the highest QoS it covers")
    void testCoveringFilterTakesTheHighestQosItCovers() {
        Mqtt5Fixture fixture = new Mqtt5Fixture();
        MqttMessageRouter router = new MqttMessageRouter(fixture.client, 5000, true, 1000);

        router.subscribeFromConfig("sensors/#", Qos.AT_MOST_ONCE);
        // A durable consumer needs at least QoS 1: QoS 0 has no acknowledgement to withhold.
        router.subscribeDurable("sensors/temp", Qos.AT_LEAST_ONCE, (msg, taken) -> taken.run());

        ArgumentCaptor<MqttQos> qosCaptor = ArgumentCaptor.forClass(MqttQos.class);
        verify(fixture.subscribeStage, times(2)).qos(qosCaptor.capture());
        assertEquals(MqttQos.AT_MOST_ONCE, qosCaptor.getAllValues().get(0));
        assertEquals(MqttQos.AT_LEAST_ONCE, qosCaptor.getAllValues().get(1),
            "sensors/# now stands in for a QoS 1 durable subscription and must be raised to match, "
                + "or the durability guarantee is silently lost");
    }

    @Test
    @DisplayName("a live listener going away does not take a durable listener's subscription with it")
    void testLastLiveListenerDoesNotUnsubscribeAFilterADurableListenerNeeds() {
        Mqtt5Fixture fixture = new Mqtt5Fixture();
        MqttMessageRouter router = new MqttMessageRouter(fixture.client, 5000, true, 1000);

        Consumer<MqttMessage> live = msg -> {};
        router.subscribeDurable("sensors/temp", Qos.AT_LEAST_ONCE, (msg, taken) -> taken.run());
        router.subscribe("sensors/temp", Qos.AT_LEAST_ONCE, live);

        router.unsubscribe("sensors/temp", live);

        verify(fixture.unsubscribeStart, never()).topicFilter(anyString());
    }


    // --- The broker's retain flag has to survive the trip to the consumer.
    //
    // A retained message is the topic's last known state replayed to a new subscription, not an
    // event that has just happened - and receivedAt cannot say so, because it is assigned locally
    // on reception, so a value published days ago arrives stamped with today's time. The flag is the
    // only warning available, the protocol provides it, and the router used to throw it away. ---

    @Test
    @DisplayName("a retained publish produces a message flagged retained")
    void testRetainFlagReachesTheListener() {
        Mqtt5AsyncClient mockClient = mock(Mqtt5AsyncClient.class, RETURNS_DEEP_STUBS);
        MqttMessageRouter router = new MqttMessageRouter(mockClient, 5000, true, 1000);

        var received = new java.util.concurrent.CopyOnWriteArrayList<MqttMessage>();
        router.subscribe("sensors/#", Qos.AT_LEAST_ONCE, received::add);

        Consumer<Mqtt5Publish> globalConsumer = capturedGlobalConsumer(mockClient);
        globalConsumer.accept(mockPublish("sensors/temp", "{}", MqttQos.AT_LEAST_ONCE, true));
        globalConsumer.accept(mockPublish("sensors/temp", "{}", MqttQos.AT_LEAST_ONCE, false));

        awaitCondition(() -> received.size() == 2, 5_000);
        assertTrue(received.get(0).isRetain(), "a retained publish must arrive flagged as retained");
        assertFalse(received.get(1).isRetain(), "an ordinary publish must not be flagged as retained");
    }

    @Test
    @DisplayName("the cached copy keeps the retain flag, so a replay does not contradict the live delivery")
    void testCachedCopyKeepsTheRetainFlag() {
        Mqtt5AsyncClient mockClient = mock(Mqtt5AsyncClient.class, RETURNS_DEEP_STUBS);
        MqttMessageRouter router = new MqttMessageRouter(mockClient, 5000, true, 1000);

        capturedGlobalConsumer(mockClient).accept(mockPublish("sensors/temp", "{}", MqttQos.AT_LEAST_ONCE, true));

        awaitCondition(() -> !router.getLastMessages("sensors/#").isEmpty(), 5_000);
        assertTrue(router.getLastMessages("sensors/#").get(0).isRetain(),
            "the client that triggers the subscribe and the client that gets the replay must agree "
                + "that the message was retained");
    }
}
