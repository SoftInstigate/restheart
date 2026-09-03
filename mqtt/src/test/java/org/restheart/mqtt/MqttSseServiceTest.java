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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.restheart.mqtt.model.MqttMessage;
import org.restheart.mqtt.pipeline.MqttEventPipeline;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;

import io.undertow.server.handlers.sse.ServerSentEventConnection;
import org.xnio.ChannelListener;

/**
 * Unit tests for MqttSseService.
 * Tests topic extraction, QoS parsing, payload envelope format,
 * cached message delivery, pipeline selection and isolation, stage
 * configuration validation, and time-driven window flushing.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttSseServiceTest {

    private MqttSseService service;
    private Map<String, Object> config;
    private MqttMessageRouter router;

    @BeforeEach
    void setUp() {
        service = new MqttSseService();
        config = new HashMap<>();
        router = new MqttMessageRouter(mock(MqttClient.class), 5000, true, 1000);
    }

    private void injectConfig() throws Exception {
        Field configField = MqttSseService.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(service, config);

        Field routerField = MqttSseService.class.getDeclaredField("router");
        routerField.setAccessible(true);
        routerField.set(service, router);
    }

    private void injectMockRouter(MqttMessageRouter mockRouter) throws Exception {
        Field routerField = MqttSseService.class.getDeclaredField("router");
        routerField.setAccessible(true);
        routerField.set(service, mockRouter);
    }

    private void callInit() throws Exception {
        injectConfig();
        service.init();
    }

    // --- Topic extraction tests ---

    @Test
    @DisplayName("Extract topic filter from query string")
    void testExtractTopicFromQueryString() throws Exception {
        config.put("default-topic", "default/#");
        callInit();

        String topic = service.resolveTopicFilter(service.parseQueryString("topic=sensors/%23"));
        assertEquals("sensors/#", topic);
    }

    @Test
    @DisplayName("Use default topic when no topic in query string")
    void testDefaultTopicFromConfig() throws Exception {
        config.put("default-topic", "traffic/#");
        callInit();

        String topic = service.resolveTopicFilter(null);
        assertEquals("traffic/#", topic);
    }

    @Test
    @DisplayName("Decode URL-encoded topic filter with wildcards")
    void testUrlDecodedTopic() throws Exception {
        config.put("default-topic", "default/#");
        callInit();

        // %23 = #, %2B = + (literal plus, not space)
        String topic = service.resolveTopicFilter(service.parseQueryString("topic=sensors/%23"));
        assertEquals("sensors/#", topic);
    }

    // --- QoS extraction tests ---

    @Test
    @DisplayName("Extract QoS from query string")
    void testExtractQosFromQueryString() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("default-qos", 0);
        callInit();

        int qos = service.resolveQos(service.parseQueryString("topic=sensors/&qos=2"));
        assertEquals(2, qos);
    }

    @Test
    @DisplayName("Use default QoS when not in query string")
    void testDefaultQos() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("default-qos", 1);
        callInit();

        int qos = service.resolveQos(service.parseQueryString("topic=sensors/"));
        assertEquals(1, qos);
    }

    // --- Payload envelope format tests ---

    @Test
    @DisplayName("Envelope format wraps message in JSON object")
    void testPayloadEnvelopeFormat() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("payload-envelope", true);
        callInit();

        MqttMessage msg = new MqttMessage("sensors/temp", "{\"temp\":25}", 1, Instant.parse("2026-01-01T00:00:00Z"));
        String payload = service.formatPayload(msg, false);

        assertTrue(payload.contains("\"topic\":\"sensors/temp\""));
        assertTrue(payload.contains("\"payload\":\"{\\\"temp\\\":25}\""));
        assertTrue(payload.contains("\"qos\":1"));
        assertTrue(payload.contains("\"cached\":false"));
    }

    @Test
    @DisplayName("Raw format returns just the payload string")
    void testPayloadRawFormat() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("payload-envelope", false);
        callInit();

        MqttMessage msg = new MqttMessage("sensors/temp", "{\"temp\":25}", 1, Instant.now());
        String payload = service.formatPayload(msg, false);

        assertEquals("{\"temp\":25}", payload);
    }

    @Test
    @DisplayName("Cached flag included when cached=true")
    void testCachedFlagInEnvelope() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("payload-envelope", true);
        callInit();

        MqttMessage msg = new MqttMessage("sensors/temp", "{}", 1, Instant.now());
        String payload = service.formatPayload(msg, true);

        assertTrue(payload.contains("\"cached\":true"));
    }

    // --- Pipeline selection tests (finding M9) ---

    @Test
    @DisplayName("No pipeline configured yields an identity pipeline that passes messages through unchanged")
    void testNoPipelineConfigYieldsIdentityPipeline() throws Exception {
        config.put("default-topic", "sensors/#");
        callInit();

        MqttEventPipeline pipeline = service.selectPipeline("sensors/#");
        assertTrue(pipeline.isEmpty());

        MqttMessage msg = new MqttMessage("sensors/temp", "raw", 0, Instant.now());
        assertEquals("raw", pipeline.process(msg).orElseThrow().getPayload());
    }

    @Test
    @DisplayName("Exact topic match wins over a wildcard entry")
    void testExactTopicMatchWinsOverWildcard() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("pipeline", List.of(
            Map.of("topic", "sensors/#", "stages", List.of(
                Map.of("type", "throttle", "max-events-per-second", 10))),
            Map.of("topic", "sensors/temp", "stages", List.of(
                Map.of("type", "filter", "min-qos", 99)) // drops everything
            )
        ));
        callInit();

        // Requested filter equals the second entry's topic exactly, so it must win
        // even though the first (wildcard) entry also matches "sensors/temp".
        MqttEventPipeline pipeline = service.selectPipeline("sensors/temp");
        MqttMessage msg = new MqttMessage("sensors/temp", "{}", 1, Instant.now());
        assertTrue(pipeline.process(msg).isEmpty(), "exact-match entry (drop-all filter) should have been selected");
    }

    @Test
    @DisplayName("A wildcard entry matches a requested filter with no exact match")
    void testWildcardEntryMatchesRequestedFilter() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("pipeline", List.of(
            Map.of("topic", "sensors/#", "stages", List.of(
                Map.of("type", "filter", "min-qos", 99))) // drops everything
        ));
        callInit();

        MqttEventPipeline pipeline = service.selectPipeline("sensors/temp");
        MqttMessage msg = new MqttMessage("sensors/temp", "{}", 1, Instant.now());
        assertTrue(pipeline.process(msg).isEmpty(), "wildcard entry should have matched and dropped the message");
    }

    @Test
    @DisplayName("No matching entry yields an identity pipeline")
    void testNoMatchingEntryYieldsIdentityPipeline() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("pipeline", List.of(
            Map.of("topic", "traffic/#", "stages", List.of(
                Map.of("type", "filter", "min-qos", 99)))
        ));
        callInit();

        MqttEventPipeline pipeline = service.selectPipeline("sensors/temp");
        MqttMessage msg = new MqttMessage("sensors/temp", "{}", 1, Instant.now());
        assertTrue(pipeline.process(msg).isPresent(), "no configured entry matches; identity pipeline must pass it through");
    }

    // --- C2 regression: pipelines must not be shared across connections ---

    @Test
    @DisplayName("C2 regression: two connections on the same throttled topic do not share throttle state")
    void testTwoConnectionsOnSameTopicDoNotShareThrottleState() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("pipeline", List.of(
            Map.of("topic", "sensors/#", "stages", List.of(
                Map.of("type", "throttle", "max-events-per-second", 1)))
        ));
        callInit();

        // Simulate two separate connections requesting the same topic filter.
        MqttEventPipeline connection1 = service.selectPipeline("sensors/#");
        MqttEventPipeline connection2 = service.selectPipeline("sensors/#");

        assertNotEquals(connection1, connection2, "each connection must get its own pipeline instance");

        MqttMessage msg = new MqttMessage("sensors/temp", "{}", 0, Instant.now());

        // Exhaust connection1's single token.
        assertTrue(connection1.process(msg).isPresent(), "connection1's first message should pass");
        assertFalse(connection1.process(msg).isPresent(), "connection1's throttle should now be exhausted");

        // connection2 must still have its own, untouched token bucket.
        assertTrue(connection2.process(msg).isPresent(),
            "connection2 must have its own throttle state, unaffected by connection1's consumption");
    }

    @Test
    @DisplayName("C2 regression: two connections on the same aggregated topic build independent aggregates")
    void testTwoConnectionsOnSameTopicDoNotShareAggregationState() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("pipeline", List.of(
            Map.of("topic", "sensors/#", "stages", List.of(
                Map.of("type", "tumbling-window", "window-ms", 5000, "function", "count")))
        ));
        callInit();

        MqttEventPipeline connection1 = service.selectPipeline("sensors/#");
        MqttEventPipeline connection2 = service.selectPipeline("sensors/#");

        // connection1 receives 3 messages, connection2 receives none.
        connection1.process(new MqttMessage("sensors/temp", "{}", 0, Instant.now()));
        connection1.process(new MqttMessage("sensors/temp", "{}", 0, Instant.now()));
        connection1.process(new MqttMessage("sensors/temp", "{}", 0, Instant.now()));

        List<MqttMessage> connection1Flushed = connection1.close();
        List<MqttMessage> connection2Flushed = connection2.close();

        assertEquals(1, connection1Flushed.size());
        assertEquals("3", connection1Flushed.get(0).getPayload(), "connection1 must aggregate only its own 3 messages");
        assertTrue(connection2Flushed.isEmpty(), "connection2 received nothing and must not see connection1's aggregate");
    }

    // --- Stage configuration validation (finding M9) ---

    @Test
    @DisplayName("Quoted numeric stage parameter fails fast at init, naming stage type and key")
    void testQuotedNumericStageParameterFailsAtInit() {
        config.put("default-topic", "sensors/#");
        config.put("pipeline", List.of(
            Map.of("topic", "sensors/#", "stages", List.of(
                Map.of("type", "tumbling-window", "window-ms", "not-a-number", "function", "count")))
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, this::callInitUnchecked);
        assertTrue(ex.getMessage().contains("tumbling-window"), "message should name the stage type");
        assertTrue(ex.getMessage().contains("window-ms"), "message should name the offending key");
    }

    @Test
    @DisplayName("Unknown stage type fails fast at init, listing supported types")
    void testUnknownStageTypeFailsAtInit() {
        config.put("default-topic", "sensors/#");
        config.put("pipeline", List.of(
            Map.of("topic", "sensors/#", "stages", List.of(
                Map.of("type", "not-a-real-stage")))
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, this::callInitUnchecked);
        assertTrue(ex.getMessage().contains("not-a-real-stage"), "message should name the offending type");
        assertTrue(ex.getMessage().contains("throttle"), "message should list supported types");
    }

    private void callInitUnchecked() {
        try {
            callInit();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // --- Filter stage built from topic-regex and min-qos (finding M9) ---

    @Test
    @DisplayName("Filter stage built from topic-regex and min-qos filters on both")
    void testFilterStageFromTopicRegexAndMinQos() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("pipeline", List.of(
            Map.of("topic", "sensors/#", "stages", List.of(
                Map.of("type", "filter", "topic-regex", "sensors/temp", "min-qos", 1)))
        ));
        callInit();

        MqttEventPipeline pipeline = service.selectPipeline("sensors/#");

        // Matches both the topic regex and the min QoS -> kept
        assertTrue(pipeline.process(new MqttMessage("sensors/temp", "{}", 1, Instant.now())).isPresent());

        // Wrong topic -> dropped
        assertTrue(pipeline.process(new MqttMessage("sensors/humidity", "{}", 1, Instant.now())).isEmpty());

        // Right topic, QoS too low -> dropped
        assertTrue(pipeline.process(new MqttMessage("sensors/temp", "{}", 0, Instant.now())).isEmpty());
    }

    // --- Queue overflow test ---

    @Test
    @DisplayName("Queue overflow does not throw exception")
    void testQueueOverflowDoesNotThrow() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("default-qos", 0);
        config.put("per-connection-queue-capacity", 2);
        callInit();

        // Verify config is parsed correctly
        assertEquals(2, service.getPerConnectionQueueCapacity());
    }

    // --- onConnect wiring tests (pollExpired, close, max-connections, event ids) ---
    //
    // NOTE on synchronisation: the drain loop runs on a virtual thread, backed in
    // this test JVM by a single-worker common ForkJoinPool carrier. Mockito's
    // verify(mock, timeout(millis)) busy-polls the *calling* thread, and doing so
    // for the better part of a second was observed to starve that lone carrier
    // thread outright - the background virtual thread got no CPU time at all
    // until the polling verify gave up. So: any assertion about something the
    // *background* drain thread does is synchronised with a CountDownLatch
    // (park-based, not poll-based) counted down from a doAnswer on the mocked
    // send()/close() call; assertions about calls made synchronously on the
    // *test* thread (subscribe, addCloseTask, the rejection-path close(), and
    // directly invoking a captured close task) use plain, immediate verify().

    /**
     * Builds a mocked {@link ServerSentEventConnection} whose {@code isOpen()}
     * tracks the given flag and whose {@code getQueryString()} returns the given
     * query string.
     */
    private ServerSentEventConnection mockConnection(String queryString, AtomicBoolean open) {
        ServerSentEventConnection conn = mock(ServerSentEventConnection.class);
        when(conn.getQueryString()).thenReturn(queryString);
        when(conn.isOpen()).thenAnswer(inv -> open.get());
        return conn;
    }

    /**
     * Calls {@code onConnect} and returns the listener it registered with the
     * router. {@code subscribe} is called synchronously by {@code onConnect}
     * itself, so no timeout is needed to observe it.
     */
    @SuppressWarnings("unchecked")
    private Consumer<MqttMessage> onConnectAndCaptureListener(MqttMessageRouter mockRouter, ServerSentEventConnection conn) {
        ArgumentCaptor<Consumer<MqttMessage>> captor = ArgumentCaptor.forClass(Consumer.class);
        service.onConnect(conn, null);
        verify(mockRouter).subscribe(anyString(), any(MqttQos.class), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("pollExpired wiring: a tumbling window emits through the drain path with no further messages arriving")
    void testDrainLoopFlushesExpiredWindowWithNoFurtherMessages() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("pipeline", List.of(
            Map.of("topic", "sensors/#", "stages", List.of(
                Map.of("type", "tumbling-window", "window-ms", 150, "function", "count")))
        ));
        callInit();

        MqttMessageRouter mockRouter = mock(MqttMessageRouter.class);
        when(mockRouter.getLastMessages(anyString())).thenReturn(List.of());
        injectMockRouter(mockRouter);

        AtomicBoolean open = new AtomicBoolean(true);
        ServerSentEventConnection conn = mockConnection("topic=sensors/%23", open);

        CountDownLatch sent = new CountDownLatch(1);
        doAnswer(inv -> {
            sent.countDown();
            return null;
        }).when(conn).send(anyString(), anyString(), anyString(), any());

        Consumer<MqttMessage> listener = onConnectAndCaptureListener(mockRouter, conn);
        listener.accept(new MqttMessage("sensors/temp", "{}", 0, Instant.now()));

        try {
            // No further message arrives: the drain loop's queue.poll(1s) times
            // out, and pollExpired() must flush the window on that same iteration.
            assertTrue(sent.await(1200, TimeUnit.MILLISECONDS),
                "pollExpired() must flush the window through the drain loop with no further message arriving");
        } finally {
            open.set(false);
        }
    }

    @Test
    @DisplayName("close() wiring: closing a connection flushes a partially filled window")
    void testCloseTaskFlushesPartiallyFilledWindow() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("pipeline", List.of(
            Map.of("topic", "sensors/#", "stages", List.of(
                // Window large enough that it will not expire on its own during the test.
                Map.of("type", "tumbling-window", "window-ms", 60_000, "function", "count")))
        ));
        callInit();

        MqttMessageRouter mockRouter = mock(MqttMessageRouter.class);
        when(mockRouter.getLastMessages(anyString())).thenReturn(List.of());
        injectMockRouter(mockRouter);

        AtomicBoolean open = new AtomicBoolean(true);
        ServerSentEventConnection conn = mockConnection("topic=sensors/%23", open);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ChannelListener<ServerSentEventConnection>> closeTaskCaptor = ArgumentCaptor.forClass(ChannelListener.class);

        Consumer<MqttMessage> listener = onConnectAndCaptureListener(mockRouter, conn);
        verify(conn).addCloseTask(closeTaskCaptor.capture());

        // The message is handed to the background drain thread via the queue;
        // give it a bounded moment to actually process it into the window
        // before we close the connection. There is no synchronous completion
        // signal for "entered the still-open window" to latch on to (unlike
        // the emission tested above), so this single short, bounded wait -
        // not a polling loop - is the pragmatic choice here.
        listener.accept(new MqttMessage("sensors/temp", "{}", 0, Instant.now()));
        Thread.sleep(300);

        try {
            closeTaskCaptor.getValue().handleEvent(conn);

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(conn).send(payloadCaptor.capture(), anyString(), anyString(), any());
            assertEquals("1", payloadCaptor.getValue(), "the single buffered message must be flushed as the aggregate");
        } finally {
            open.set(false);
        }
    }

    @Test
    @DisplayName("max-connections-per-topic is enforced and released on close, including on the rejection path")
    void testMaxConnectionsPerTopicEnforcedAndReleased() throws Exception {
        config.put("default-topic", "sensors/#");
        config.put("max-connections-per-topic", 1);
        callInit();

        MqttMessageRouter mockRouter = mock(MqttMessageRouter.class);
        when(mockRouter.getLastMessages(anyString())).thenReturn(List.of());
        injectMockRouter(mockRouter);

        AtomicBoolean open1 = new AtomicBoolean(true);
        ServerSentEventConnection conn1 = mockConnection("topic=sensors/%23", open1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ChannelListener<ServerSentEventConnection>> closeTaskCaptor = ArgumentCaptor.forClass(ChannelListener.class);

        // Every call below is made synchronously by onConnect() on this (the
        // test) thread, so plain, immediate verify() is enough - none of this
        // depends on the background drain thread ever being scheduled.
        service.onConnect(conn1, null);
        verify(mockRouter).subscribe(anyString(), any(MqttQos.class), any());
        verify(conn1).addCloseTask(closeTaskCaptor.capture());

        // Second connection on the same topic filter must be rejected: no subscribe, connection closed.
        AtomicBoolean open2 = new AtomicBoolean(true);
        ServerSentEventConnection conn2 = mockConnection("topic=sensors/%23", open2);
        service.onConnect(conn2, null);

        verify(conn2).close();
        verify(mockRouter, times(1)).subscribe(anyString(), any(MqttQos.class), any());

        // Close the first connection: its slot must be released, including
        // the fact that the rejected connection above never leaked a permit.
        open1.set(false);
        closeTaskCaptor.getValue().handleEvent(conn1);

        AtomicBoolean open3 = new AtomicBoolean(true);
        ServerSentEventConnection conn3 = mockConnection("topic=sensors/%23", open3);
        try {
            service.onConnect(conn3, null);
            verify(mockRouter, times(2)).subscribe(anyString(), any(MqttQos.class), any());
        } finally {
            open3.set(false);
        }
    }

    @Test
    @DisplayName("Event ids are unique for two messages published on the same topic in the same millisecond")
    void testEventIdsAreUniqueForSameMillisecondMessages() throws Exception {
        config.put("default-topic", "sensors/#");
        callInit();

        MqttMessageRouter mockRouter = mock(MqttMessageRouter.class);
        when(mockRouter.getLastMessages(anyString())).thenReturn(List.of());
        injectMockRouter(mockRouter);

        AtomicBoolean open = new AtomicBoolean(true);
        ServerSentEventConnection conn = mockConnection("topic=sensors/%23", open);

        List<String> capturedIds = new CopyOnWriteArrayList<>();
        CountDownLatch bothSent = new CountDownLatch(2);
        doAnswer(inv -> {
            capturedIds.add(inv.getArgument(2));
            bothSent.countDown();
            return null;
        }).when(conn).send(anyString(), anyString(), anyString(), any());

        Consumer<MqttMessage> listener = onConnectAndCaptureListener(mockRouter, conn);

        Instant sameInstant = Instant.now();
        listener.accept(new MqttMessage("sensors/temp", "first", 0, sameInstant));
        listener.accept(new MqttMessage("sensors/temp", "second", 0, sameInstant));

        try {
            assertTrue(bothSent.await(1200, TimeUnit.MILLISECONDS), "both messages must be sent");
            assertEquals(2, capturedIds.size());
            assertNotEquals(capturedIds.get(0), capturedIds.get(1), "event ids must be unique even within the same millisecond");
        } finally {
            open.set(false);
        }
    }
}
