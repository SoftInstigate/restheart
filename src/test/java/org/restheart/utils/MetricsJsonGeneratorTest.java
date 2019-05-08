package org.restheart.utils;

import org.restheart.handlers.metrics.MetricsJsonGenerator;
import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class MetricsJsonGeneratorTest {

    JsonWriterSettings writerSettings = JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).indent(false).build();

    @Test
    public void testGenerationWithEmptyRegistry() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        BsonDocument bson = MetricsJsonGenerator.generateMetricsBson(registry, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
        assertEquals(
                "{\"version\": \"3.0.0\", \"gauges\": {}, \"counters\": {}, \"histograms\": {}, \"meters\": {}, \"timers\": {}}",
                bson.toJson()
        );
    }

    @Test
    public void testGenerationWithOneTimer() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        registry.timer("foobar").update(5, TimeUnit.MILLISECONDS);
        BsonDocument bson = MetricsJsonGenerator.generateMetricsBson(registry, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
        assertEquals("{\"version\": \"3.0.0\", \"gauges\": {}, \"counters\": {}, \"histograms\": {}, \"meters\": {}, \"timers\": {\"foobar\": {\"count\": 1, \"max\": 5.0, \"mean\": 5.0, \"min\": 5.0, \"p50\": 5.0, \"p75\": 5.0, \"p95\": 5.0, \"p98\": 5.0, \"p99\": 5.0, \"p999\": 5.0, \"stddev\": 0.0, \"m15_rate\": 0.0, \"m1_rate\": 0.0, \"m5_rate\": 0.0, \"mean_rate\": 1, \"duration_units\": \"milliseconds\", \"rate_units\": \"calls/second\"}}}",
                bson.toJson(writerSettings)
                        //mean_rate is different for each call, so set it fixed for the test output
                        .replaceAll("\"mean_rate\": [0-9.]+", "\"mean_rate\": 1"));
    }

    @Test
    public void testGenerationWithGauges() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        registry.gauge("foobar", () -> () -> 5);
        BsonDocument bson = MetricsJsonGenerator.generateMetricsBson(registry, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
        assertEquals("{\"version\": \"3.0.0\", \"gauges\": {\"foobar\": {\"value\": 5}}, \"counters\": {}, \"histograms\": {}, \"meters\": {}, \"timers\": {}}",
                bson.toJson(writerSettings));
    }

    @Test
    public void testGenerationWithHistogram() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        registry.histogram("foobar").update(5);
        BsonDocument bson = MetricsJsonGenerator.generateMetricsBson(registry, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
        assertEquals("{\"version\": \"3.0.0\", \"gauges\": {}, \"counters\": {}, \"histograms\": {\"foobar\": {\"count\": 1, \"max\": 5.0, \"mean\": 5.0, \"min\": 5.0, \"p50\": 5.0, \"p75\": 5.0, \"p95\": 5.0, \"p98\": 5.0, \"p99\": 5.0, \"p999\": 5.0, \"stddev\": 0.0}}, \"meters\": {}, \"timers\": {}}",
                bson.toJson(writerSettings));
    }

    @Test
    public void testGenerationWithCounter() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        Counter counter = registry.counter("foobar");
        counter.inc();
        counter.inc();
        counter.inc();
        BsonDocument bson = MetricsJsonGenerator.generateMetricsBson(registry, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
        assertEquals("{\"version\": \"3.0.0\", \"gauges\": {}, \"counters\": {\"foobar\": {\"count\": 3}}, \"histograms\": {}, \"meters\": {}, \"timers\": {}}",
                bson.toJson(writerSettings));
    }

    @Test
    public void testGenerationWithMeter() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        registry.meter("foobar").mark();
        BsonDocument bson = MetricsJsonGenerator.generateMetricsBson(registry, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
        assertEquals("{\"version\": \"3.0.0\", \"gauges\": {}, \"counters\": {}, \"histograms\": {}, \"meters\": {\"foobar\": {\"count\": 1, \"m15_rate\": 0.0, \"m1_rate\": 0.0, \"m5_rate\": 0.0, \"mean_rate\": 1, \"units\": \"calls/second\"}}, \"timers\": {}}",
                bson.toJson(writerSettings)
                        //mean_rate is different for each call, so set it fixed for the test output
                        .replaceAll("\"mean_rate\": [0-9.]+", "\"mean_rate\": 1")
        );
    }
}
