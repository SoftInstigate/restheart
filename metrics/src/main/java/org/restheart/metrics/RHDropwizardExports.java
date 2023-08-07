package org.restheart.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import io.prometheus.client.dropwizard.samplebuilder.SampleBuilder;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.dropwizard.samplebuilder.DefaultSampleBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collect Dropwizard metrics from a MetricRegistry.
 *
 * Modified from https://github.com/prometheus/client_java/blob/main/simpleclient_dropwizard/src/main/java/io/prometheus/client/dropwizard/DropwizardExports.java
 * to track additional timer metrics and to handle metric name as MetricNameAndLabels
 */
public class RHDropwizardExports extends io.prometheus.client.Collector implements io.prometheus.client.Collector.Describable {
    private static final Logger LOGGER = Logger.getLogger(RHDropwizardExports.class.getName());
    private MetricRegistry registry;
    private MetricFilter metricFilter;
    private SampleBuilder sampleBuilder;

    /**
     * Creates a new DropwizardExports with a {@link DefaultSampleBuilder} and {@link MetricFilter#ALL}.
     *
     * @param registry a metric registry to export in prometheus.
     */
    public RHDropwizardExports(String registryName) {
        this(SharedMetricRegistries.getOrCreate(registryName));
    }

    /**
     * Creates a new DropwizardExports with a {@link DefaultSampleBuilder} and {@link MetricFilter#ALL}.
     *
     * @param registry a metric registry to export in prometheus.
     */
    public RHDropwizardExports(MetricRegistry registry) {
        this.registry = registry;
        this.metricFilter = MetricFilter.ALL;
        this.sampleBuilder = new RHSampler();
    }

    private static String getHelpMessage(String metricName, Metric metric) {
        return String.format("Generated from Dropwizard metric import (metric=%s, type=%s)", metricName, metric.getClass().getName());
    }

    /**
     * Export counter as Prometheus <a href="https://prometheus.io/docs/concepts/metric_types/#gauge">Gauge</a>.
     */
    MetricFamilySamples fromCounter(String dropwizardName, Counter counter) {
        MetricFamilySamples.Sample sample = sampleBuilder.createSample(dropwizardName, "", new ArrayList<String>(), new ArrayList<String>(), Long.valueOf(counter.getCount()).doubleValue());
        return new MetricFamilySamples(sample.name, Type.GAUGE, getHelpMessage(dropwizardName, counter), Arrays.asList(sample));
    }

    /**
     * Export gauge as a prometheus gauge.
     */
    MetricFamilySamples fromGauge(String dropwizardName, Gauge<?> gauge) {
        Object obj = gauge.getValue();
        double value;
        if (obj instanceof Number) {
            value = ((Number) obj).doubleValue();
        } else if (obj instanceof Boolean) {
            value = ((Boolean) obj) ? 1 : 0;
        } else {
            LOGGER.log(Level.FINE, String.format("Invalid type for Gauge %s: %s", sanitizeMetricName(dropwizardName), obj == null ? "null" : obj.getClass().getName()));
            return null;
        }
        MetricFamilySamples.Sample sample = sampleBuilder.createSample(dropwizardName, "", new ArrayList<String>(), new ArrayList<String>(), value);
        return new MetricFamilySamples(sample.name, Type.GAUGE, getHelpMessage(dropwizardName, gauge), Arrays.asList(sample));
    }

    /**
     * Export a histogram snapshot as a prometheus SUMMARY.
     *
     * @param dropwizardName metric name.
     * @param snapshot       the histogram snapshot.
     * @param count          the total sample count for this snapshot.
     * @param factor         a factor to apply to histogram values.
     */
    MetricFamilySamples fromSnapshotAndCount(String dropwizardName, Snapshot snapshot, long count, double factor, String helpMessage) {
        List<MetricFamilySamples.Sample> samples = Arrays.asList(
                sampleBuilder.createSample(dropwizardName, "", Arrays.asList("quantile"), Arrays.asList("0.5"), snapshot.getMedian() * factor),
                sampleBuilder.createSample(dropwizardName, "", Arrays.asList("quantile"), Arrays.asList("0.75"), snapshot.get75thPercentile() * factor),
                sampleBuilder.createSample(dropwizardName, "", Arrays.asList("quantile"), Arrays.asList("0.95"), snapshot.get95thPercentile() * factor),
                sampleBuilder.createSample(dropwizardName, "", Arrays.asList("quantile"), Arrays.asList("0.98"), snapshot.get98thPercentile() * factor),
                sampleBuilder.createSample(dropwizardName, "", Arrays.asList("quantile"), Arrays.asList("0.99"), snapshot.get99thPercentile() * factor),
                sampleBuilder.createSample(dropwizardName, "", Arrays.asList("quantile"), Arrays.asList("0.999"), snapshot.get999thPercentile() * factor),
                sampleBuilder.createSample(dropwizardName, "_count", new ArrayList<String>(), new ArrayList<String>(), count)
        );
        return new MetricFamilySamples(samples.get(0).name, Type.SUMMARY, helpMessage, samples);
    }

    MetricFamilySamples fromTimer(String dropwizardName, Timer timer, String helpMessage) {
        var snapshot = timer.getSnapshot();

        List<MetricFamilySamples.Sample> samples = Arrays.asList(
                this.sampleBuilder.createSample(dropwizardName, "_duration", Arrays.asList("quantile"), Arrays.asList("0.5"), snapshot.getMedian() * TIMER_FACTOR),
                this.sampleBuilder.createSample(dropwizardName, "_duration", Arrays.asList("quantile"), Arrays.asList("0.75"), snapshot.get75thPercentile() * TIMER_FACTOR),
                this.sampleBuilder.createSample(dropwizardName, "_duration", Arrays.asList("quantile"), Arrays.asList("0.95"), snapshot.get95thPercentile() * TIMER_FACTOR),
                this.sampleBuilder.createSample(dropwizardName, "_duration", Arrays.asList("quantile"), Arrays.asList("0.98"), snapshot.get98thPercentile() * TIMER_FACTOR),
                this.sampleBuilder.createSample(dropwizardName, "_duration", Arrays.asList("quantile"), Arrays.asList("0.99"), snapshot.get99thPercentile() * TIMER_FACTOR),
                this.sampleBuilder.createSample(dropwizardName, "_duration", Arrays.asList("quantile"), Arrays.asList("0.999"), snapshot.get999thPercentile() * TIMER_FACTOR),

                this.sampleBuilder.createSample(dropwizardName, "_rate", Arrays.asList("period"), Arrays.asList("15 minutes"), timer.getFifteenMinuteRate()),
                this.sampleBuilder.createSample(dropwizardName, "_rate", Arrays.asList("period"), Arrays.asList("5 minutes"), timer.getFiveMinuteRate()),
                this.sampleBuilder.createSample(dropwizardName, "_rate", Arrays.asList("period"), Arrays.asList("1 minute"), timer.getOneMinuteRate()),
                this.sampleBuilder.createSample(dropwizardName, "_rate", Arrays.asList("period"), Arrays.asList("overall"), timer.getMeanRate()),


                this.sampleBuilder.createSample(dropwizardName, "_count", new ArrayList<String>(), new ArrayList<String>(), timer.getCount())
        );
        return new MetricFamilySamples(samples.get(0).name, Type.SUMMARY, helpMessage, samples);
    }

    /**
     * Convert histogram snapshot.
     */
    MetricFamilySamples fromHistogram(String dropwizardName, Histogram histogram) {
        return fromSnapshotAndCount(dropwizardName, histogram.getSnapshot(), histogram.getCount(), 1.0, getHelpMessage(dropwizardName, histogram));
    }

    private static double TIMER_FACTOR = 1.0D / TimeUnit.SECONDS.toNanos(1L);

    /**
     * Export Dropwizard Timer as a histogram. Use TIME_UNIT as time unit.
     */
    MetricFamilySamples fromTimer(String dropwizardName, Timer timer) {
        return fromTimer(dropwizardName, timer, getHelpMessage(dropwizardName, timer));
    }

    /**
     * Export a Meter as as prometheus COUNTER.
     */
    MetricFamilySamples fromMeter(String dropwizardName, Meter meter) {
        final MetricFamilySamples.Sample sample = sampleBuilder.createSample(dropwizardName, "_total",
                new ArrayList<String>(),
                new ArrayList<String>(),
                meter.getCount());
        return new MetricFamilySamples(sample.name, Type.COUNTER, getHelpMessage(dropwizardName, meter), Arrays.asList(sample));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List<MetricFamilySamples> collect() {
        Map<String, MetricFamilySamples> mfSamplesMap = new HashMap<String, MetricFamilySamples>();

        for (SortedMap.Entry<String, Gauge> entry : registry.getGauges(metricFilter).entrySet()) {
            addToMap(mfSamplesMap, fromGauge(entry.getKey(), entry.getValue()));
        }
        for (SortedMap.Entry<String, Counter> entry : registry.getCounters(metricFilter).entrySet()) {
            addToMap(mfSamplesMap, fromCounter(entry.getKey(), entry.getValue()));
        }
        for (SortedMap.Entry<String, Histogram> entry : registry.getHistograms(metricFilter).entrySet()) {
            addToMap(mfSamplesMap, fromHistogram(entry.getKey(), entry.getValue()));
        }
        for (SortedMap.Entry<String, Timer> entry : registry.getTimers(metricFilter).entrySet()) {
            addToMap(mfSamplesMap, fromTimer(entry.getKey(), entry.getValue()));
        }
        for (SortedMap.Entry<String, Meter> entry : registry.getMeters(metricFilter).entrySet()) {
            addToMap(mfSamplesMap, fromMeter(entry.getKey(), entry.getValue()));
        }
        return new ArrayList<MetricFamilySamples>(mfSamplesMap.values());
    }

    private void addToMap(Map<String, MetricFamilySamples> mfSamplesMap, MetricFamilySamples newMfSamples)
    {
        if (newMfSamples != null) {
            MetricFamilySamples currentMfSamples = mfSamplesMap.get(newMfSamples.name);
            if (currentMfSamples == null) {
                mfSamplesMap.put(newMfSamples.name, newMfSamples);
            } else {
                List<MetricFamilySamples.Sample> samples = new ArrayList<MetricFamilySamples.Sample>(currentMfSamples.samples);
                samples.addAll(newMfSamples.samples);
                mfSamplesMap.put(newMfSamples.name, new MetricFamilySamples(newMfSamples.name, currentMfSamples.type, currentMfSamples.help, samples));
            }
        }
    }

    @Override
    public List<MetricFamilySamples> describe() {
        return new ArrayList<MetricFamilySamples>();
    }

    private static class RHSampler implements SampleBuilder {
        private static DefaultSampleBuilder DSB = new DefaultSampleBuilder();

        @Override
        public Sample createSample(String dropwizardName, String nameSuffix, List<String> additionalLabelNames, List<String> additionalLabelValues, double value) {
            if (dropwizardName.startsWith("jvm")) {
                return DSB.createSample(dropwizardName, nameSuffix, additionalLabelNames, additionalLabelValues, value);
            } else {
                var nals = MetricNameAndLabels.from(dropwizardName);

                var _additionalLabelNames = new ArrayList<String>();
                var _additionalLabelValues = new ArrayList<String>();

                nals.labels().forEach(l -> {
                    _additionalLabelNames.add(l.name());
                    _additionalLabelValues.add(l.value());
                });

                _additionalLabelNames.addAll(additionalLabelNames);
                _additionalLabelValues.addAll(additionalLabelValues);

                return DSB.createSample(nals.name(), nameSuffix, _additionalLabelNames, _additionalLabelValues, value);
            }
        }
    }
}