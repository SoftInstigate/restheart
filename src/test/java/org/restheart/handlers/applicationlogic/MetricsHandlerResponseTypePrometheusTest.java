package org.restheart.handlers.applicationlogic;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.COLLECTION;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.DATABASE;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.ROOT;

public class MetricsHandlerResponseTypePrometheusTest {

    private static final String REGISTRY_NAME_DATABASE = "fancy-shop";
    private static final String REGISTRY_NAME_COLLECTION = "products";

    private static final String METRICS_TIMER_NAME = "requests.GET.2xx";

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("http_response_timers_.+\\{.+\\} \\d+\\.*\\d* (\\d+)");
    private static final Pattern MEAN_RATE_PATTERN = Pattern.compile("http_response_timers_mean_rate\\{.+\\} (\\d+\\.\\d+) \\$TIMESTAMP\\$");

    MetricsHandler handler;

    MetricRegistry rootRegistry;
    MetricRegistry databaseRegistry;
    MetricRegistry collectionRegistry;

    @Before
    public void setUp() {

        // we need to clear the metrics before each run, because they are kept static and thus would alter with each test, but we want static sample data!
        SharedMetricRegistries.clear();

        // create a new handler
        handler = new MetricsHandler(null, null);

        // provide sample metrics data (MetricsInstrumentationHandler uses timers only, so we create sample data for timers only)
        collectionRegistry = handler.metrics.registry(REGISTRY_NAME_DATABASE, REGISTRY_NAME_COLLECTION);
        updateTimer(collectionRegistry, 10);

        databaseRegistry = handler.metrics.registry(REGISTRY_NAME_DATABASE);
        updateTimer(databaseRegistry, 10);
        updateTimer(databaseRegistry, 5);

        rootRegistry = handler.metrics.registry();
        updateTimer(rootRegistry, 10);
        updateTimer(rootRegistry, 5);
        updateTimer(rootRegistry, 2);
    }

    private void updateTimer(MetricRegistry registry, long timerValue) {
        registry.timer(METRICS_TIMER_NAME).update(timerValue, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testMetricsForRoot() throws IOException {
        String expectedMetrics = "http_response_timers_count{database=\"_all_\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 3 $TIMESTAMP$\n" +
                "http_response_timers_max{database=\"_all_\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_mean{database=\"_all_\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 5.666666666666666 $TIMESTAMP$\n" +
                "http_response_timers_min{database=\"_all_\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 2.0 $TIMESTAMP$\n" +
                "http_response_timers_p50{database=\"_all_\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 5.0 $TIMESTAMP$\n" +
                "http_response_timers_p75{database=\"_all_\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p95{database=\"_all_\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p98{database=\"_all_\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p99{database=\"_all_\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p999{database=\"_all_\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_stddev{database=\"_all_\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 3.299831645537221 $TIMESTAMP$\n" +
                "http_response_timers_m15_rate{database=\"_all_\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_m1_rate{database=\"_all_\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_m5_rate{database=\"_all_\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_mean_rate{database=\"_all_\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} $MEAN_RATE$ $TIMESTAMP$\n" +
                "\n" +
                "http_response_timers_count{database=\"fancy-shop\",collection=\"products\",type=\"requests\",method=\"GET\",code=\"2xx\"} 1 $TIMESTAMP$\n" +
                "http_response_timers_max{database=\"fancy-shop\",collection=\"products\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_mean{database=\"fancy-shop\",collection=\"products\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_min{database=\"fancy-shop\",collection=\"products\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p50{database=\"fancy-shop\",collection=\"products\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p75{database=\"fancy-shop\",collection=\"products\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p95{database=\"fancy-shop\",collection=\"products\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p98{database=\"fancy-shop\",collection=\"products\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p99{database=\"fancy-shop\",collection=\"products\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p999{database=\"fancy-shop\",collection=\"products\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_stddev{database=\"fancy-shop\",collection=\"products\",type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_m15_rate{database=\"fancy-shop\",collection=\"products\",type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_m1_rate{database=\"fancy-shop\",collection=\"products\",type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_m5_rate{database=\"fancy-shop\",collection=\"products\",type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_mean_rate{database=\"fancy-shop\",collection=\"products\",type=\"requests\",method=\"GET\",code=\"2xx\"} $MEAN_RATE$ $TIMESTAMP$\n" +
                "\n" +
                "http_response_timers_count{database=\"fancy-shop\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 2 $TIMESTAMP$\n" +
                "http_response_timers_max{database=\"fancy-shop\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_mean{database=\"fancy-shop\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 7.5 $TIMESTAMP$\n" +
                "http_response_timers_min{database=\"fancy-shop\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 5.0 $TIMESTAMP$\n" +
                "http_response_timers_p50{database=\"fancy-shop\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p75{database=\"fancy-shop\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p95{database=\"fancy-shop\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p98{database=\"fancy-shop\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p99{database=\"fancy-shop\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p999{database=\"fancy-shop\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_stddev{database=\"fancy-shop\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 2.5 $TIMESTAMP$\n" +
                "http_response_timers_m15_rate{database=\"fancy-shop\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_m1_rate{database=\"fancy-shop\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_m5_rate{database=\"fancy-shop\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_mean_rate{database=\"fancy-shop\",collection=\"_all_\",type=\"requests\",method=\"GET\",code=\"2xx\"} $MEAN_RATE$ $TIMESTAMP$";
        assertMetrics(expectedMetrics, MetricsHandler.ResponseType.PROMETHEUS.generateResponse(ROOT, rootRegistry));
    }

    @Test
    public void testMetricsForDatabase() throws IOException {
        String expectedMetrics = "http_response_timers_count{type=\"requests\",method=\"GET\",code=\"2xx\"} 2 $TIMESTAMP$\n" +
                "http_response_timers_max{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_mean{type=\"requests\",method=\"GET\",code=\"2xx\"} 7.5 $TIMESTAMP$\n" +
                "http_response_timers_min{type=\"requests\",method=\"GET\",code=\"2xx\"} 5.0 $TIMESTAMP$\n" +
                "http_response_timers_p50{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p75{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p95{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p98{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p99{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p999{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_stddev{type=\"requests\",method=\"GET\",code=\"2xx\"} 2.5 $TIMESTAMP$\n" +
                "http_response_timers_m15_rate{type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_m1_rate{type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_m5_rate{type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_mean_rate{type=\"requests\",method=\"GET\",code=\"2xx\"} $MEAN_RATE$ $TIMESTAMP$";
        assertMetrics(expectedMetrics, MetricsHandler.ResponseType.PROMETHEUS.generateResponse(DATABASE, databaseRegistry));
    }

    @Test
    public void testMetricsForCollection() throws IOException {
        String expectedMetrics = "http_response_timers_count{type=\"requests\",method=\"GET\",code=\"2xx\"} 1 $TIMESTAMP$\n" +
                "http_response_timers_max{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_mean{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_min{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p50{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p75{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p95{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p98{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p99{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_p999{type=\"requests\",method=\"GET\",code=\"2xx\"} 10.0 $TIMESTAMP$\n" +
                "http_response_timers_stddev{type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_m15_rate{type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_m1_rate{type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_m5_rate{type=\"requests\",method=\"GET\",code=\"2xx\"} 0.0 $TIMESTAMP$\n" +
                "http_response_timers_mean_rate{type=\"requests\",method=\"GET\",code=\"2xx\"} $MEAN_RATE$ $TIMESTAMP$";
        assertMetrics(expectedMetrics, MetricsHandler.ResponseType.PROMETHEUS.generateResponse(COLLECTION, collectionRegistry));
    }

    private void assertMetrics(String expectedMetrics, String metrics) {
        assertEquals(expectedMetrics, replaceDynamicValues(metrics));
    }

    private String replaceDynamicValues(String metrics) {
        metrics = replaceMetricsValues(metrics, TIMESTAMP_PATTERN, "$TIMESTAMP$");
        metrics = replaceMetricsValues(metrics, MEAN_RATE_PATTERN, "$MEAN_RATE$");
        return metrics;
    }

    private String replaceMetricsValues(String metrics, Pattern pattern, String replacement) {
        Matcher matcher = pattern.matcher(metrics);
        while(matcher.find()) {
            String toBeReplaced = matcher.group(1);
            metrics = metrics.replaceAll(toBeReplaced, Matcher.quoteReplacement(replacement));
        }

        return metrics;
    }
}