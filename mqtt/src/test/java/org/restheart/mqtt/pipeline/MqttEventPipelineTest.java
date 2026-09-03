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

package org.restheart.mqtt.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.restheart.mqtt.model.MqttMessage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Unit tests for classes in org.restheart.mqtt.pipeline package.
 * Covers each stage in isolation and combination.
 * 
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttEventPipelineTest {

    // Helper to create basic messages
    private MqttMessage createMessage(String topic, String payload, int qos) {
        return new MqttMessage(topic, payload, qos, Instant.now());
    }

    // --- FilterStage Tests ---

    @Test
    public void testFilterStageQos() {
        FilterStage stage = new FilterStage(1); // Min QoS 1

        MqttMessage qos0 = createMessage("sensors/temp", "{}", 0);
        MqttMessage qos1 = createMessage("sensors/temp", "{}", 1);
        MqttMessage qos2 = createMessage("sensors/temp", "{}", 2);

        assertFalse(stage.process(qos0).isPresent());
        assertTrue(stage.process(qos1).isPresent());
        assertTrue(stage.process(qos2).isPresent());
    }

    @Test
    public void testFilterStageTopic() {
        FilterStage stage = new FilterStage("sensors/[^/]+/temp");

        MqttMessage match1 = createMessage("sensors/room1/temp", "{}", 1);
        MqttMessage match2 = createMessage("sensors/room2/temp", "{}", 1);
        MqttMessage mismatch1 = createMessage("sensors/room1/humidity", "{}", 1);
        MqttMessage mismatch2 = createMessage("sensors/temp", "{}", 1);

        assertTrue(stage.process(match1).isPresent());
        assertTrue(stage.process(match2).isPresent());
        assertFalse(stage.process(mismatch1).isPresent());
        assertFalse(stage.process(mismatch2).isPresent());
    }

    @Test
    public void testFilterStageJsonPathNumeric() {
        // Evaluate temperature field > 25.5
        FilterStage stage = new FilterStage("$.temperature", "> 25.5");

        MqttMessage hot = createMessage("sensors/temp", "{\"temperature\": 26.0}", 1);
        MqttMessage cold = createMessage("sensors/temp", "{\"temperature\": 20.0}", 1);
        MqttMessage exact = createMessage("sensors/temp", "{\"temperature\": 25.5}", 1);
        MqttMessage invalidJson = createMessage("sensors/temp", "not json", 1);

        assertTrue(stage.process(hot).isPresent());
        assertFalse(stage.process(cold).isPresent());
        assertFalse(stage.process(exact).isPresent());
        assertFalse(stage.process(invalidJson).isPresent());
        
        // Test other operators
        assertTrue(new FilterStage("$.val", ">= 10").process(createMessage("t", "{\"val\": 10}", 1)).isPresent());
        assertTrue(new FilterStage("$.val", "<= 10").process(createMessage("t", "{\"val\": 10}", 1)).isPresent());
        assertTrue(new FilterStage("$.val", "== 10").process(createMessage("t", "{\"val\": 10}", 1)).isPresent());
        assertTrue(new FilterStage("$.val", "!= 10").process(createMessage("t", "{\"val\": 11}", 1)).isPresent());
    }

    @Test
    public void testFilterStageJsonPathString() {
        FilterStage stage = new FilterStage("$.status", "== 'active'");

        MqttMessage active = createMessage("sensors/status", "{\"status\": \"active\"}", 1);
        MqttMessage inactive = createMessage("sensors/status", "{\"status\": \"inactive\"}", 1);

        assertTrue(stage.process(active).isPresent());
        assertFalse(stage.process(inactive).isPresent());
    }

    // --- MapStage Tests ---

    @Test
    public void testMapStageExtractField() {
        MapStage stage = new MapStage("$.data.value");

        MqttMessage msg = createMessage("test", "{\"data\": {\"value\": 42}}", 1);
        Optional<MqttMessage> result = stage.process(msg);

        assertTrue(result.isPresent());
        assertEquals("42", result.get().getPayload());
    }

    @Test
    public void testMapStageRenameKey() {
        MapStage stage = new MapStage("temp", "temperature");

        MqttMessage msg = createMessage("test", "{\"temp\": 22.5, \"humidity\": 50}", 1);
        Optional<MqttMessage> result = stage.process(msg);

        assertTrue(result.isPresent());
        
        JsonObject obj = JsonParser.parseString(result.get().getPayload()).getAsJsonObject();
        assertTrue(obj.has("temperature"));
        assertFalse(obj.has("temp"));
        assertEquals(22.5, obj.get("temperature").getAsDouble(), 0.001);
        assertEquals(50, obj.get("humidity").getAsInt());
    }

    @Test
    public void testMapStageTemplate() {
        MapStage stage = new MapStage("{\"info\": \"topic: ${topic}, qos: ${qos}\", \"val\": ${payload}}", true);

        MqttMessage msg = createMessage("sensors/1", "100", 2);
        Optional<MqttMessage> result = stage.process(msg);

        assertTrue(result.isPresent());
        
        JsonObject obj = JsonParser.parseString(result.get().getPayload()).getAsJsonObject();
        assertEquals("topic: sensors/1, qos: 2", obj.get("info").getAsString());
        assertEquals(100, obj.get("val").getAsInt());
    }

    // --- ThrottleStage Tests ---

    @Test
    public void testThrottleStageRateLimiting() throws Exception {
        // Max 2 events per second
        ThrottleStage stage = new ThrottleStage(2);

        MqttMessage msg = createMessage("test", "{}", 1);

        // First two events should pass
        assertTrue(stage.process(msg).isPresent());
        assertEquals(1, stage.getAvailableTokens());
        assertTrue(stage.process(msg).isPresent());
        assertEquals(0, stage.getAvailableTokens());

        // Third event should be dropped
        assertFalse(stage.process(msg).isPresent());
        assertEquals(1, stage.getDroppedCount());

        // Move time forward by 0.5 seconds via reflection on lastRefillTime -> refills 1 token (0.5 * 2)
        java.lang.reflect.Field lastRefillTimeField = ThrottleStage.class.getDeclaredField("lastRefillTime");
        lastRefillTimeField.setAccessible(true);
        java.util.concurrent.atomic.AtomicLong lastRefillTime = (java.util.concurrent.atomic.AtomicLong) lastRefillTimeField.get(stage);
        
        lastRefillTime.set(System.nanoTime() - 500_000_000L);
        assertTrue(stage.process(msg).isPresent());
        assertEquals(0, stage.getAvailableTokens());

        // Starved again
        assertFalse(stage.process(msg).isPresent());
        assertEquals(2, stage.getDroppedCount());

        // Move time forward by 2 seconds -> refills up to capacity (max 2 tokens)
        lastRefillTime.set(System.nanoTime() - 2_000_000_000L);
        assertTrue(stage.process(msg).isPresent());
        assertTrue(stage.process(msg).isPresent());
        assertFalse(stage.process(msg).isPresent());
        
        // Reset
        stage.reset();
        assertEquals(2, stage.getAvailableTokens());
        assertEquals(0, stage.getDroppedCount());
    }

    // --- TumblingWindowAggregator Tests ---

    @Test
    public void testTumblingWindowAggregatorFunctions() {
        // Average
        TumblingWindowAggregator avgAgg = new TumblingWindowAggregator(1000, "avg", "$.val");
        avgAgg.process(createMessage("t", "{\"val\": 10}", 1));
        avgAgg.process(createMessage("t", "{\"val\": 20}", 1));
        Optional<MqttMessage> flushedAvg = avgAgg.close();
        assertTrue(flushedAvg.isPresent());
        assertEquals("15.0", flushedAvg.get().getPayload());

        // Sum
        TumblingWindowAggregator sumAgg = new TumblingWindowAggregator(1000, "sum", "$.val");
        sumAgg.process(createMessage("t", "{\"val\": 10}", 1));
        sumAgg.process(createMessage("t", "{\"val\": 20}", 1));
        Optional<MqttMessage> flushedSum = sumAgg.close();
        assertTrue(flushedSum.isPresent());
        assertEquals("30.0", flushedSum.get().getPayload());

        // Count
        TumblingWindowAggregator countAgg = new TumblingWindowAggregator(1000, "count", null);
        countAgg.process(createMessage("t", "{}", 1));
        countAgg.process(createMessage("t", "{}", 1));
        Optional<MqttMessage> flushedCount = countAgg.close();
        assertTrue(flushedCount.isPresent());
        assertEquals("2", flushedCount.get().getPayload());

        // Min & Max
        TumblingWindowAggregator minMaxAgg = new TumblingWindowAggregator(1000, "min", "$.val");
        minMaxAgg.process(createMessage("t", "{\"val\": 3}", 1));
        minMaxAgg.process(createMessage("t", "{\"val\": 10}", 1));
        minMaxAgg.process(createMessage("t", "{\"val\": 2}", 1));
        assertEquals("2.0", minMaxAgg.close().get().getPayload());

        TumblingWindowAggregator maxAgg = new TumblingWindowAggregator(1000, "max", "$.val");
        maxAgg.process(createMessage("t", "{\"val\": 3}", 1));
        maxAgg.process(createMessage("t", "{\"val\": 10}", 1));
        maxAgg.process(createMessage("t", "{\"val\": 2}", 1));
        assertEquals("10.0", maxAgg.close().get().getPayload());

        // Last
        TumblingWindowAggregator lastAgg = new TumblingWindowAggregator(1000, "last", null);
        lastAgg.process(createMessage("t", "first", 1));
        lastAgg.process(createMessage("t", "second", 1));
        assertEquals("second", lastAgg.close().get().getPayload());

        // Array
        TumblingWindowAggregator arrayAgg = new TumblingWindowAggregator(1000, "array", null);
        arrayAgg.process(createMessage("t", "{\"a\": 1}", 1));
        arrayAgg.process(createMessage("t", "non-json", 1));
        Optional<MqttMessage> flushedArray = arrayAgg.close();
        assertTrue(flushedArray.isPresent());
        JsonArray arrayObj = JsonParser.parseString(flushedArray.get().getPayload()).getAsJsonArray();
        assertEquals(2, arrayObj.size());
        assertEquals(1, arrayObj.get(0).getAsJsonObject().get("a").getAsInt());
        assertEquals("non-json", arrayObj.get(1).getAsString());
    }

    @Test
    public void testTumblingWindowAggregatorTimeFlush() throws Exception {
        // Window duration of 1000 ms
        TumblingWindowAggregator aggregator = new TumblingWindowAggregator(1000, "count", null);

        java.lang.reflect.Field windowStartTimeField = TumblingWindowAggregator.class.getDeclaredField("windowStartTime");
        windowStartTimeField.setAccessible(true);

        // Process first message
        Optional<MqttMessage> result1 = aggregator.process(createMessage("t", "{}", 1));
        assertFalse(result1.isPresent());
        assertEquals(1, aggregator.getWindowSize());

        // Move time by 500ms
        windowStartTimeField.setLong(aggregator, System.currentTimeMillis() - 500);
        Optional<MqttMessage> result2 = aggregator.process(createMessage("t", "{}", 1));
        assertFalse(result2.isPresent());
        assertEquals(2, aggregator.getWindowSize());

        // Move time to boundary (1000ms elapsed since window start) and send 3rd message
        windowStartTimeField.setLong(aggregator, System.currentTimeMillis() - 1000);
        Optional<MqttMessage> result3 = aggregator.process(createMessage("t", "{}", 1));
        
        // Window should have flushed! Returning count of 2 (first 2 messages)
        assertTrue(result3.isPresent());
        assertEquals("2", result3.get().getPayload());
        
        // The 3rd message starts the new window
        assertEquals(1, aggregator.getWindowSize());
    }

    @Test
    public void testTumblingWindowAggregatorCloseFlush() {
        TumblingWindowAggregator aggregator = new TumblingWindowAggregator(5000, "count", null);

        // No messages -> empty on close
        assertFalse(aggregator.close().isPresent());

        // With messages -> emits aggregated value on close
        aggregator.process(createMessage("t", "{}", 1));
        aggregator.process(createMessage("t", "{}", 1));
        
        Optional<MqttMessage> flushed = aggregator.close();
        assertTrue(flushed.isPresent());
        assertEquals("2", flushed.get().getPayload());

        // Window is cleared after close
        assertEquals(0, aggregator.getWindowSize());
        assertFalse(aggregator.close().isPresent());
    }

    // --- SlidingWindowAggregator Tests ---

    @Test
    public void testSlidingWindowAggregator() {
        // Size-based sliding window of 3
        SlidingWindowAggregator aggregator = new SlidingWindowAggregator(3, "sum", "$.val");

        // 1st message -> window size < 3, no emission
        Optional<MqttMessage> res1 = aggregator.process(createMessage("t", "{\"val\": 10}", 1));
        assertFalse(res1.isPresent());
        assertEquals(1, aggregator.getCurrentSize());

        // 2nd message -> window size < 3, no emission
        Optional<MqttMessage> res2 = aggregator.process(createMessage("t", "{\"val\": 20}", 1));
        assertFalse(res2.isPresent());
        assertEquals(2, aggregator.getCurrentSize());

        // 3rd message -> window reaches size 3, emits sum (10 + 20 + 30) = 60
        Optional<MqttMessage> res3 = aggregator.process(createMessage("t", "{\"val\": 30}", 1));
        assertTrue(res3.isPresent());
        assertEquals("60.0", res3.get().getPayload());
        assertEquals(3, aggregator.getCurrentSize());

        // 4th message -> window slides, drops 10, adds 40. Sum is (20 + 30 + 40) = 90
        Optional<MqttMessage> res4 = aggregator.process(createMessage("t", "{\"val\": 40}", 1));
        assertTrue(res4.isPresent());
        assertEquals("90.0", res4.get().getPayload());
        assertEquals(3, aggregator.getCurrentSize());

        // Clear
        aggregator.clear();
        assertEquals(0, aggregator.getCurrentSize());
    }

    // --- Combination Tests ---

    @Test
    public void testPipelineCombinationsFilterMap() {
        MqttEventPipeline pipeline = MqttEventPipeline.builder()
            .addStage(new FilterStage("$.temperature", "> 25"))
            .addStage(new MapStage("$.temperature"))
            .build();

        // 1. Hot message (passes filter -> mapped to "30")
        MqttMessage hot = createMessage("t", "{\"temperature\": 30}", 1);
        Optional<MqttMessage> resHot = pipeline.process(hot);
        assertTrue(resHot.isPresent());
        assertEquals("30", resHot.get().getPayload());

        // 2. Cold message (dropped by filter -> returns empty)
        MqttMessage cold = createMessage("t", "{\"temperature\": 20}", 1);
        Optional<MqttMessage> resCold = pipeline.process(cold);
        assertFalse(resCold.isPresent());
    }

    @Test
    public void testPipelineCombinationsThrottleMap() throws Exception {
        ThrottleStage throttle = new ThrottleStage(1);
        MapStage map = new MapStage("{\"v\": ${payload}}", true);

        MqttEventPipeline pipeline = MqttEventPipeline.builder()
            .addStage(throttle)
            .addStage(map)
            .build();

        // 1st message passes throttle and gets mapped
        Optional<MqttMessage> res1 = pipeline.process(createMessage("t", "10", 1));
        assertTrue(res1.isPresent());
        assertEquals("{\"v\": 10}", res1.get().getPayload());

        // 2nd message dropped by throttle -> returns empty
        Optional<MqttMessage> res2 = pipeline.process(createMessage("t", "20", 1));
        assertFalse(res2.isPresent());
    }

    @Test
    public void testPipelineCombinationsTumblingWindowFilterMapTime() throws Exception {
        TumblingWindowAggregator window = new TumblingWindowAggregator(1000, "avg", "$.temperature");
        FilterStage filterCount = new FilterStage(null, null) {
            @Override
            public Optional<MqttMessage> process(MqttMessage message) {
                if (message.getPayload().equals("2.0")) {
                    return Optional.of(message);
                }
                return Optional.empty();
            }
        };

        MapStage map = new MapStage("{\"count\": ${payload}}", true);

        MqttEventPipeline pipeline = MqttEventPipeline.builder()
            .addStage(window)
            .addStage(filterCount)
            .addStage(map)
            .build();

        // 1st message -> accumulated in window -> empty
        assertFalse(pipeline.process(createMessage("t", "{\"temperature\": 2.0}", 1)).isPresent());
        // 2nd message -> accumulated in window -> empty
        assertFalse(pipeline.process(createMessage("t", "{\"temperature\": 2.0}", 1)).isPresent());

        // Advance time to flush window and send 3rd message
        java.lang.reflect.Field windowStartTimeField = TumblingWindowAggregator.class.getDeclaredField("windowStartTime");
        windowStartTimeField.setAccessible(true);
        windowStartTimeField.setLong(window, System.currentTimeMillis() - 1000);

        Optional<MqttMessage> res = pipeline.process(createMessage("t", "{\"temperature\": 5.0}", 1));
        
        // Aggregated average of (2+2)/2 = 2.0 was emitted, which matches filterCount, and mapped to JSON
        assertTrue(res.isPresent());
        assertEquals("{\"count\": 2.0}", res.get().getPayload());
    }

    @Test
    public void testPipelineCombinationsTumblingWindowFilterMapClose() {
        TumblingWindowAggregator window = new TumblingWindowAggregator(5000, "count", null);
        FilterStage filter = new FilterStage(null, null) {
            @Override
            public Optional<MqttMessage> process(MqttMessage message) {
                if (message.getPayload().equals("3")) {
                    return Optional.of(message);
                }
                return Optional.empty();
            }
        };
        MapStage map = new MapStage("{\"total\": ${payload}}", true);

        MqttEventPipeline pipeline = MqttEventPipeline.builder()
            .addStage(window)
            .addStage(filter)
            .addStage(map)
            .build();

        // Send 3 messages (all accumulated, pipeline returns empty)
        assertFalse(pipeline.process(createMessage("t", "{}", 1)).isPresent());
        assertFalse(pipeline.process(createMessage("t", "{}", 1)).isPresent());
        assertFalse(pipeline.process(createMessage("t", "{}", 1)).isPresent());

        // Close the pipeline
        List<MqttMessage> flushed = pipeline.close();
        assertEquals(1, flushed.size());
        assertEquals("{\"total\": 3}", flushed.get(0).getPayload());
    }
}
