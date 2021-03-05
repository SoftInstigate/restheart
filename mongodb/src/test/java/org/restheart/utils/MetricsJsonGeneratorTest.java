/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.utils;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.restheart.mongodb.handlers.metrics.MetricsJsonGenerator;
public class MetricsJsonGeneratorTest {

    JsonWriterSettings writerSettings = JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).indent(false).build();

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGenerationWithEmptyRegistry() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        BsonDocument bson = MetricsJsonGenerator.generateMetricsBson(registry, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
        assertEquals(
                "{\"version\": \"3.0.0\", \"gauges\": {}, \"counters\": {}, \"histograms\": {}, \"meters\": {}, \"timers\": {}}",
                bson.toJson()
        );
    }

    /**
     *
     * @throws Exception
     */
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

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGenerationWithGauges() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        registry.gauge("foobar", () -> () -> 5);
        BsonDocument bson = MetricsJsonGenerator.generateMetricsBson(registry, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
        assertEquals("{\"version\": \"3.0.0\", \"gauges\": {\"foobar\": {\"value\": 5}}, \"counters\": {}, \"histograms\": {}, \"meters\": {}, \"timers\": {}}",
                bson.toJson(writerSettings));
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGenerationWithHistogram() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        registry.histogram("foobar").update(5);
        BsonDocument bson = MetricsJsonGenerator.generateMetricsBson(registry, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
        assertEquals("{\"version\": \"3.0.0\", \"gauges\": {}, \"counters\": {}, \"histograms\": {\"foobar\": {\"count\": 1, \"max\": 5.0, \"mean\": 5.0, \"min\": 5.0, \"p50\": 5.0, \"p75\": 5.0, \"p95\": 5.0, \"p98\": 5.0, \"p99\": 5.0, \"p999\": 5.0, \"stddev\": 0.0}}, \"meters\": {}, \"timers\": {}}",
                bson.toJson(writerSettings));
    }

    /**
     *
     * @throws Exception
     */
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

    /**
     *
     * @throws Exception
     */
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
