/*-
 * ========================LICENSE_START=================================
 * restheart-metrics
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
package org.restheart.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toMap;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonValue;

public class MetricsJsonGenerator {
    private final String singularRateUnitString;
    private final double rateFactor;
    private final TimeUnit durationUnit;
    private final double durationFactor;
    boolean showSamples = false;

    private MetricsJsonGenerator(TimeUnit rateUnit, TimeUnit durationUnit) {
        this.durationUnit = durationUnit;
        this.durationFactor = 1.0 / durationUnit.toNanos(1);
        this.rateFactor = rateUnit.toSeconds(1);
        this.singularRateUnitString = singular(rateUnit);
    }

    private static String singular(TimeUnit timeUnit) {
        var unitString = timeUnit.name().toLowerCase(Locale.US);
        return unitString.substring(0, unitString.length() - 1);
    }

    /**
     *
     * @param registry
     * @param rateUnit
     * @param durationUnit
     * @return
     */
    @SuppressWarnings("unchecked")
    public static BsonDocument generateMetricsBson(MetricRegistry registry, TimeUnit rateUnit, TimeUnit durationUnit) {
        var generator = new MetricsJsonGenerator(rateUnit, durationUnit);

        return new BsonDocument()
                .append("version", new BsonString("3.0.0"))
                .append("gauges", generator.toBson(registry.getGauges(), generator::generateGauge))
                .append("counters", generator.toBson(registry.getCounters(), generator::generateCounter))
                .append("histograms", generator.toBson(registry.getHistograms(), generator::generateHistogram))
                .append("meters", generator.toBson(registry.getMeters(), generator::generateMeter))
                .append("timers", generator.toBson(registry.getTimers(), generator::generateTimer));
    }

    private BsonDocument generateTimer(Timer timer) {
        var document = new BsonDocument();
        final var snapshot = timer.getSnapshot();
        document.append("count", new BsonInt64(timer.getCount()));
        document.append("max", new BsonDouble(snapshot.getMax() * durationFactor));
        document.append("mean", new BsonDouble(snapshot.getMean() * durationFactor));
        document.append("min", new BsonDouble(snapshot.getMin() * durationFactor));

        document.append("p50", new BsonDouble(snapshot.getMedian() * durationFactor));
        document.append("p75", new BsonDouble(snapshot.get75thPercentile() * durationFactor));
        document.append("p95", new BsonDouble(snapshot.get95thPercentile() * durationFactor));
        document.append("p98", new BsonDouble(snapshot.get98thPercentile() * durationFactor));
        document.append("p99", new BsonDouble(snapshot.get99thPercentile() * durationFactor));
        document.append("p999", new BsonDouble(snapshot.get999thPercentile() * durationFactor));

        if (showSamples) {
            document.append("values", new BsonArray(
                    Arrays.stream(snapshot.getValues())
                            .mapToDouble(x -> x * durationFactor)
                            .mapToObj(BsonDouble::new)
                            .collect(Collectors.toList())
            ));
        }

        document.append("stddev", new BsonDouble(snapshot.getStdDev() * durationFactor));
        document.append("m15_rate", new BsonDouble(timer.getFifteenMinuteRate() * rateFactor));
        document.append("m1_rate", new BsonDouble(timer.getOneMinuteRate() * rateFactor));
        document.append("m5_rate", new BsonDouble(timer.getFiveMinuteRate() * rateFactor));
        document.append("mean_rate", new BsonDouble(timer.getMeanRate() * rateFactor));
        document.append("duration_units", new BsonString(durationUnit.name().toLowerCase(Locale.US)));
        document.append("rate_units", new BsonString("calls/" + singularRateUnitString));
        return document;
    }

    private BsonDocument generateCounter(Counter counter) {
        return new BsonDocument().append("count", new BsonInt64(counter.getCount()));
    }

    private BsonDocument generateMeter(Meter meter) {
        var document = new BsonDocument();

        document.append("count", new BsonInt64(meter.getCount()));
        document.append("m15_rate", new BsonDouble(meter.getFifteenMinuteRate() * rateFactor));
        document.append("m1_rate", new BsonDouble(meter.getOneMinuteRate() * rateFactor));
        document.append("m5_rate", new BsonDouble(meter.getFiveMinuteRate() * rateFactor));
        document.append("mean_rate", new BsonDouble(meter.getMeanRate() * rateFactor));
        document.append("units", new BsonString("calls/" + singularRateUnitString));

        return document;
    }

    private BsonDocument generateHistogram(Histogram histogram) {
        var document = new BsonDocument();
        final var snapshot = histogram.getSnapshot();
        document.append("count", new BsonInt64(histogram.getCount()));
        document.append("max", new BsonDouble(snapshot.getMax()));
        document.append("mean", new BsonDouble(snapshot.getMean()));
        document.append("min", new BsonDouble(snapshot.getMin()));
        document.append("p50", new BsonDouble(snapshot.getMedian()));
        document.append("p75", new BsonDouble(snapshot.get75thPercentile()));
        document.append("p95", new BsonDouble(snapshot.get95thPercentile()));
        document.append("p98", new BsonDouble(snapshot.get98thPercentile()));
        document.append("p99", new BsonDouble(snapshot.get99thPercentile()));
        document.append("p999", new BsonDouble(snapshot.get999thPercentile()));

        if (showSamples) {
            document.append("values", new BsonArray(
                    Arrays.stream(snapshot.getValues())
                            .mapToObj(BsonInt64::new)
                            .collect(Collectors.toList())
            ));
        }

        document.append("stddev", new BsonDouble(snapshot.getStdDev()));
        return document;
    }

    private <T> BsonDocument generateGauge(Gauge<T> gauge) {
        try {
            T value = gauge.getValue();
            final BsonValue valueAsBson;
            if (value instanceof Double) {
                valueAsBson = new BsonDouble((Double) value);
            } else if (value instanceof Float) {
                valueAsBson = new BsonDouble((Float) value);
            } else if (value instanceof Long) {
                valueAsBson = new BsonInt64((Long) value);
            } else if (value instanceof Integer) {
                valueAsBson = new BsonInt32((Integer) value);
            } else { //returning a string is formally wrong here, but the best I can get for all the rest
                valueAsBson = new BsonString(value.toString());
            }
            return new BsonDocument().append("value", valueAsBson);
        } catch (RuntimeException re) {
            return new BsonDocument().append("error", new BsonString(re.toString()));
        }
    }

    private <T> BsonDocument toBson(Map<String, T> map, Function<T, BsonDocument> f) {
        var collect = map.entrySet().stream().collect(toMap(Map.Entry::getKey, x -> f.apply(x.getValue())));
        var document = new BsonDocument();
        document.putAll(collect);
        return document;
    }
}
