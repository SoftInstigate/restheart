/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import com.google.common.net.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.restheart.exchange.Request;
import org.restheart.plugins.security.Authenticator;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HttpString;

/**
 * Utility class for metrics collection and management in RESTHeart.
 * 
 * <p>This class provides a centralized set of utilities for working with metrics in RESTHeart,
 * including:</p>
 * <ul>
 *   <li>Failed authentication tracking based on IP addresses or X-Forwarded-For headers</li>
 *   <li>Custom metric label attachment to requests for enhanced monitoring</li>
 *   <li>Configurable strategies for collecting authentication failure metrics</li>
 * </ul>
 * 
 * <p>The class supports two main metric collection strategies for failed authentications:</p>
 * <ul>
 *   <li><strong>REMOTE_IP</strong>: Tracks failures by the direct client IP address</li>
 *   <li><strong>X_FORWARDED_FOR</strong>: Tracks failures using the X-Forwarded-For header,
 *       useful when RESTHeart is behind a proxy or load balancer</li>
 * </ul>
 * 
 * <p>For X-Forwarded-For handling, the class supports reverse indexing to select which
 * IP address to use when multiple proxies are involved. This allows selecting the most
 * relevant IP from a chain of proxy addresses.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Configure to use X-Forwarded-For header for tracking
 * Metrics.collectFailedAuthBy(Metrics.FAILED_AUTH_KEY.X_FORWARDED_FOR);
 * Metrics.xffValueRIndex(1); // Use second-to-last IP in the chain
 * 
 * // Attach custom labels to a request
 * MetricLabel label = new MetricLabel("endpoint", "/api/users");
 * Metrics.attachMetricLabel(request, label);
 * }</pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see MetricLabel
 * @see MetricNameAndLabels
 */
public class Metrics {
    /**
     * HTTP header constant for X-Forwarded-For, used when tracking failed authentications
     * through proxy chains.
     */
    private static final HttpString _X_FORWARDED_FOR = HttpString.tryFromString(HttpHeaders.X_FORWARDED_FOR);

    /**
     * Enumeration of available strategies for collecting failed authentication metrics.
     * 
     * <ul>
     *   <li><strong>REMOTE_IP</strong>: Use the direct client IP address from the connection</li>
     *   <li><strong>X_FORWARDED_FOR</strong>: Use the X-Forwarded-For header value, suitable
     *       for deployments behind proxies or load balancers</li>
     * </ul>
     */
    public enum FAILED_AUTH_KEY { REMOTE_IP, X_FORWARDED_FOR }

    /**
     * The current strategy for collecting failed authentication metrics.
     * Defaults to REMOTE_IP for backward compatibility.
     */
    private static FAILED_AUTH_KEY collectFailedAuthBy = FAILED_AUTH_KEY.REMOTE_IP;
    
    /**
     * The reverse index to use when extracting values from X-Forwarded-For headers.
     * 0 means use the last value (most recent proxy), 1 means second-to-last, etc.
     */
    private static int xffReverseIndex = 0;

     /**
     * Generates a metric name for tracking failed authentication attempts.
     * 
     * <p>The generated name includes the authentication failure tracking strategy
     * (either "failed-auth-remote-ip" or "failed-auth-x-forwarded-for") and the
     * corresponding IP address value. This allows for granular tracking of authentication
     * failures by source.</p>
     * 
     * <p>The method respects the current configuration set via {@link #collectFailedAuthBy(FAILED_AUTH_KEY)}
     * and {@link #xffValueRIndex(int)}.</p>
     * 
     * <p>Examples of generated names:</p>
     * <ul>
     *   <li>"org.restheart.plugins.security.Authenticator.failed-auth-remote-ip.192.168.1.100"</li>
     *   <li>"org.restheart.plugins.security.Authenticator.failed-auth-x-forwarded-for.10.0.0.5"</li>
     *   <li>"org.restheart.plugins.security.Authenticator.failed-auth-x-forwarded-for.not-set"</li>
     * </ul>
     *
     * @param exchange the HTTP server exchange from which to extract IP information
     * @return the name of the histogram that stores the percentage of failed auth requests in the last 10 seconds
     */
    public static String failedAuthHistogramName(HttpServerExchange exchange) {
        return switch(collectFailedAuthBy) {
            case REMOTE_IP -> MetricRegistry.name(Authenticator.class, "failed-auth-remote-ip", ExchangeAttributes.remoteIp().readAttribute(exchange));
            case X_FORWARDED_FOR -> {
                var xff = ExchangeAttributes.requestHeader(_X_FORWARDED_FOR).readAttribute(exchange);
                yield xff == null
                    ? MetricRegistry.name(Authenticator.class, "failed-auth-x-forwarded-for", "not-set")
                    : MetricRegistry.name(Authenticator.class, "failed-auth-x-forwarded-for", xffValue(xff, xffReverseIndex));
            }
        };
    }

    /**
     * Extracts a specific IP address from an X-Forwarded-For header value.
     * 
     * <p>The X-Forwarded-For header typically contains a comma-separated list of IP addresses
     * in the format: "client, proxy1, proxy2, ..., proxyN". This method allows extracting
     * a specific IP from this chain using reverse indexing.</p>
     * 
     * <p>The reverse index (rindex) works as follows:</p>
     * <ul>
     *   <li>0: Returns the last IP (most recent proxy)</li>
     *   <li>1: Returns the second-to-last IP</li>
     *   <li>2: Returns the third-to-last IP</li>
     *   <li>etc.</li>
     * </ul>
     * 
     * <p>Special handling:</p>
     * <ul>
     *   <li>Strips leading/trailing whitespace from the header value</li>
     *   <li>Removes square brackets if present (e.g., "[IP1, IP2]" becomes "IP1, IP2")</li>
     *   <li>Falls back to the last IP if rindex exceeds the number of IPs</li>
     *   <li>Returns null if the input is null</li>
     * </ul>
     * 
     * <p>Example:</p>
     * <pre>{@code
     * String xff = "203.0.113.195, 70.41.3.18, 150.172.238.178";
     * xffValue(xff, 0); // Returns "150.172.238.178"
     * xffValue(xff, 1); // Returns "70.41.3.18"
     * xffValue(xff, 2); // Returns "203.0.113.195"
     * xffValue(xff, 5); // Returns "203.0.113.195" (fallback to first)
     * }</pre>
     *
     * @param xff the X-Forwarded-For header value, may contain multiple comma-separated IPs
     * @param rindex the reverse index (0-based from the end) of the IP to extract
     * @return the extracted IP address (trimmed), or null if xff is null
     */
    public static String xffValue(String xff, int rindex) {
        if (xff == null) {
            return null;
        } else {
            xff = xff.strip();

            if (xff.startsWith("[")) {
                xff = xff.substring(1);
            }

            if (xff.endsWith("]")) {
                xff = xff.substring(0, xff.length()-1);
            }

            var elements = xff.split(",");

            if (elements.length >= rindex) {
                if (rindex >= elements.length) {
                    return elements[0].strip();
                } else {
                    return elements[elements.length - 1 - rindex].strip();
                }
            } else {
                return elements[elements.length - 1].strip();
            }
        }
    }

    /**
     * Sets the strategy for collecting failed authentication metrics.
     * 
     * <p>This method configures how RESTHeart tracks the source of failed authentication
     * attempts. The choice depends on your deployment architecture:</p>
     * <ul>
     *   <li><strong>REMOTE_IP</strong>: Use when RESTHeart is directly exposed to clients
     *       or when you want to track the immediate connection source</li>
     *   <li><strong>X_FORWARDED_FOR</strong>: Use when RESTHeart is behind a proxy, load
     *       balancer, or CDN, and you need to track the original client IP</li>
     * </ul>
     * 
     * <p>This configuration affects the behavior of {@link #failedAuthHistogramName(HttpServerExchange)}
     * and determines which IP address is used for grouping authentication failure metrics.</p>
     * 
     * <p>Example:</p>
     * <pre>{@code
     * // Configure to use X-Forwarded-For when behind a proxy
     * Metrics.collectFailedAuthBy(Metrics.FAILED_AUTH_KEY.X_FORWARDED_FOR);
     * }</pre>
     *
     * @param key the strategy to use for collecting failed auth metrics
     * @see FAILED_AUTH_KEY
     * @see #xffValueRIndex(int)
     */
    public static void collectFailedAuthBy(FAILED_AUTH_KEY key) {
        collectFailedAuthBy = key;
    }

    /**
     * Sets the reverse index for extracting IP addresses from X-Forwarded-For headers.
     * 
     * <p>When X-Forwarded-For contains multiple IP addresses (common in multi-proxy setups),
     * this setting determines which IP to use for metrics collection. The reverse index
     * counts from the end of the IP list.</p>
     * 
     * <p>Common configurations:</p>
     * <ul>
     *   <li><strong>0</strong>: Use the last IP (the most recent proxy) - least trustworthy</li>
     *   <li><strong>1</strong>: Use second-to-last IP (skip one proxy) - common for single proxy setup</li>
     *   <li><strong>2</strong>: Use third-to-last IP (skip two proxies) - for dual proxy setup</li>
     * </ul>
     * 
     * <p>Example with header "203.0.113.195, 70.41.3.18, 150.172.238.178":</p>
     * <ul>
     *   <li>ridx=0 → "150.172.238.178" (last proxy)</li>
     *   <li>ridx=1 → "70.41.3.18" (second proxy)</li>
     *   <li>ridx=2 → "203.0.113.195" (original client)</li>
     * </ul>
     * 
     * <p>Choose the index based on your infrastructure and which proxies you trust.
     * Generally, you want to skip untrusted proxies and use the last trusted proxy's
     * reported client IP.</p>
     *
     * @param ridx the reverse index (0-based from the end) to use when extracting IPs
     * @see #collectFailedAuthBy(FAILED_AUTH_KEY)
     * @see #xffValue(String, int)
     */
    public static void xffValueRIndex(int ridx) {
        xffReverseIndex = ridx;
    }


    /**
     * Attachment key for storing custom metric labels on HTTP exchanges.
     * This allows handlers and interceptors to attach metric labels that will be
     * included when metrics are collected for the request.
     */
    private static final AttachmentKey<List<MetricLabel>> CUSTOM_METRIC_LABELS = AttachmentKey.create(List.class);

    /**
     * Attaches multiple metric labels to a request for enhanced metrics collection.
     * 
     * <p>Labels attached via this method will be included in any metrics collected
     * for this request by the RequestsMetricsCollector. This allows for adding
     * contextual information to metrics, enabling more detailed analysis and filtering.</p>
     * 
     * <p>Common use cases include:</p>
     * <ul>
     *   <li>Adding customer/tenant identifiers for multi-tenant systems</li>
     *   <li>Tagging requests with feature flags or experiment groups</li>
     *   <li>Including API version information</li>
     *   <li>Adding geographic or deployment-specific labels</li>
     * </ul>
     * 
     * <p>Example:</p>
     * <pre>{@code
     * List<MetricLabel> labels = MetricLabel.collect(
     *     new MetricLabel("tenant", "acme-corp"),
     *     new MetricLabel("api_version", "v2"),
     *     new MetricLabel("region", "us-west")
     * );
     * Metrics.attachMetricLabels(request, labels);
     * }</pre>
     * 
     * <p>Note: This method replaces any existing labels. To add to existing labels,
     * retrieve them first with {@link #getMetricLabels(Request)}.</p>
     *
     * @param request the request to attach labels to
     * @param labels the list of labels to attach
     * @see #attachMetricLabel(Request, MetricLabel)
     * @see #getMetricLabels(Request)
     */
    public static void attachMetricLabels(Request<?> request, List<MetricLabel> labels) {
        request.getExchange().putAttachment(CUSTOM_METRIC_LABELS, labels);
    }

    /**
     * Attaches a single metric label to a request for enhanced metrics collection.
     * 
     * <p>This is a convenience method for attaching a single label. The label will be
     * included in any metrics collected for this request by the RequestsMetricsCollector.</p>
     * 
     * <p>Example:</p>
     * <pre>{@code
     * // Add a label for the customer tier
     * MetricLabel tierLabel = new MetricLabel("tier", "premium");
     * Metrics.attachMetricLabel(request, tierLabel);
     * }</pre>
     * 
     * <p>Note: This method replaces any existing labels with a new list containing
     * only the provided label. To preserve existing labels, use {@link #attachMetricLabels(Request, List)}
     * with a combined list.</p>
     *
     * @param request the request to attach the label to
     * @param label the label to attach
     * @see #attachMetricLabels(Request, List)
     * @see #getMetricLabels(Request)
     */
    public static void attachMetricLabel(Request<?> request, MetricLabel label) {
        var labels = new ArrayList<MetricLabel>();
        labels.add(label);
        request.getExchange().putAttachment(CUSTOM_METRIC_LABELS, labels);
    }

    /**
     * Retrieves the metric labels attached to a request.
     *
     * <p>This method returns the list of custom metric labels that have been attached
     * to the request via {@link #attachMetricLabels(Request, List)} or
     * {@link #attachMetricLabel(Request, MetricLabel)}. These labels are used by
     * the metrics collection system to add dimensional data to collected metrics.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * List<MetricLabel> labels = Metrics.getMetricLabels(request);
     * if (labels != null) {
     *     for (MetricLabel label : labels) {
     *         System.out.println(label.name() + "=" + label.value());
     *     }
     * }
     * }</pre>
     *
     * @param request the request from which to retrieve labels
     * @return the list of attached labels, or null if no labels have been attached
     * @see #attachMetricLabels(Request, List)
     * @see #attachMetricLabel(Request, MetricLabel)
     */
    public static List<MetricLabel> getMetricLabels(Request<?> request) {
        return request.getExchange().getAttachment(CUSTOM_METRIC_LABELS);
    }

    // ========== Custom Metrics ==========

    /**
     * Prefix for custom metrics registry names.
     * Each custom metric is registered in its own registry named "METRICS-/{metric_name}".
     */
    private static final String CUSTOM_METRICS_REGISTRY_PREFIX = "METRICS-/";

    /**
     * Gets or creates the registry for a specific metric name.
     *
     * @param metricName the base name of the metric (without labels)
     * @return the metric registry for the given metric name
     */
    private static MetricRegistry getCustomRegistry(String metricName) {
        return SharedMetricRegistries.getOrCreate(CUSTOM_METRICS_REGISTRY_PREFIX + metricName);
    }

    /**
     * Registers a counter metric with the specified name and labels.
     *
     * <p>Counters are monotonically increasing values, ideal for tracking totals.
     * The metric will be exposed at GET /metrics/{name}.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * Metrics.registerCounter(MetricNameAndLabels.of("orders_total")
     *     .label("status", "completed")
     *     .label("region", "us-west"));
     * }</pre>
     *
     * @param nameAndLabels the metric name and labels
     * @return the registered Counter instance
     */
    public static Counter registerCounter(MetricNameAndLabels nameAndLabels) {
        return getCustomRegistry(nameAndLabels.name()).counter(nameAndLabels.toString());
    }

    /**
     * Increments a counter by 1.
     *
     * @param nameAndLabels the metric name and labels
     */
    public static void incrementCounter(MetricNameAndLabels nameAndLabels) {
        registerCounter(nameAndLabels).inc();
    }

    /**
     * Increments a counter by the specified amount.
     *
     * @param nameAndLabels the metric name and labels
     * @param amount the amount to increment by
     */
    public static void incrementCounter(MetricNameAndLabels nameAndLabels, long amount) {
        registerCounter(nameAndLabels).inc(amount);
    }

    /**
     * Decrements a counter by 1.
     *
     * @param nameAndLabels the metric name and labels
     */
    public static void decrementCounter(MetricNameAndLabels nameAndLabels) {
        registerCounter(nameAndLabels).dec();
    }

    /**
     * Decrements a counter by the specified amount.
     *
     * @param nameAndLabels the metric name and labels
     * @param amount the amount to decrement by
     */
    public static void decrementCounter(MetricNameAndLabels nameAndLabels, long amount) {
        registerCounter(nameAndLabels).dec(amount);
    }

    /**
     * Gets the current value of a counter.
     *
     * @param nameAndLabels the metric name and labels
     * @return the current count, or 0 if the counter doesn't exist
     */
    public static long getCounterValue(MetricNameAndLabels nameAndLabels) {
        Counter counter = getCustomRegistry(nameAndLabels.name()).getCounters().get(nameAndLabels.toString());
        return counter != null ? counter.getCount() : 0;
    }

    /**
     * Registers a gauge metric with the specified name, labels, and value supplier.
     *
     * <p>Gauges represent instantaneous values that can increase or decrease.
     * The metric will be exposed at GET /metrics/{name}.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * Metrics.registerGauge(
     *     MetricNameAndLabels.of("active_connections").label("pool", "main"),
     *     () -> connectionPool.getActiveCount()
     * );
     * }</pre>
     *
     * @param <T> the type of the gauge value (must extend Number)
     * @param nameAndLabels the metric name and labels
     * @param valueSupplier a supplier that provides the current value
     * @return the registered Gauge instance
     */
    public static <T extends Number> Gauge<T> registerGauge(MetricNameAndLabels nameAndLabels, Supplier<T> valueSupplier) {
        return getCustomRegistry(nameAndLabels.name()).gauge(nameAndLabels.toString(), () -> (Gauge<T>) valueSupplier::get);
    }

    /**
     * Gets the current value of a gauge.
     *
     * @param nameAndLabels the metric name and labels
     * @return the current gauge value, or null if the gauge doesn't exist
     */
    public static Object getGaugeValue(MetricNameAndLabels nameAndLabels) {
        Gauge<?> gauge = getCustomRegistry(nameAndLabels.name()).getGauges().get(nameAndLabels.toString());
        return gauge != null ? gauge.getValue() : null;
    }

    /**
     * Registers a histogram metric with the specified name and labels.
     *
     * <p>Histograms measure the statistical distribution of values.
     * The metric will be exposed at GET /metrics/{name}.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * Metrics.registerHistogram(
     *     MetricNameAndLabels.of("response_size_bytes").label("endpoint", "/api/users")
     * );
     * }</pre>
     *
     * @param nameAndLabels the metric name and labels
     * @return the registered Histogram instance
     */
    public static Histogram registerHistogram(MetricNameAndLabels nameAndLabels) {
        return getCustomRegistry(nameAndLabels.name()).histogram(nameAndLabels.toString());
    }

    /**
     * Updates a histogram with a new value.
     *
     * @param nameAndLabels the metric name and labels
     * @param value the value to record
     */
    public static void updateHistogram(MetricNameAndLabels nameAndLabels, long value) {
        registerHistogram(nameAndLabels).update(value);
    }

    /**
     * Updates a histogram with a new value.
     *
     * @param nameAndLabels the metric name and labels
     * @param value the value to record
     */
    public static void updateHistogram(MetricNameAndLabels nameAndLabels, int value) {
        registerHistogram(nameAndLabels).update(value);
    }

    /**
     * Registers a histogram metric with a sliding time window reservoir.
     *
     * <p>This is useful for measuring rates over a time window, such as requests per minute.
     * The sliding time window keeps only values recorded within the specified time window,
     * making it ideal for rate calculations.</p>
     *
     * <p>Example for measuring request rate (requests/minute):</p>
     * <pre>{@code
     * // Register histogram with 60-second sliding window
     * Metrics.registerSlidingTimeWindowHistogram(
     *     MetricNameAndLabels.of("request_rate").label("endpoint", "/api/users"),
     *     60,
     *     TimeUnit.SECONDS
     * );
     *
     * // Record each request (value of 1 for each request)
     * Metrics.recordRequest(
     *     MetricNameAndLabels.of("request_rate").label("endpoint", "/api/users")
     * );
     *
     * // The histogram will show statistics over the last 60 seconds
     * // Mean will give you the average requests per second
     * // Count will give you total requests in the window
     * }</pre>
     *
     * @param nameAndLabels the metric name and labels
     * @param windowSize the size of the sliding time window
     * @param windowUnit the time unit of the window size
     * @return the registered Histogram instance with sliding time window reservoir
     */
    public static Histogram registerSlidingTimeWindowHistogram(MetricNameAndLabels nameAndLabels, long windowSize, TimeUnit windowUnit) {
        return getCustomRegistry(nameAndLabels.name()).histogram(
            nameAndLabels.toString(),
            () -> new Histogram(new com.codahale.metrics.SlidingTimeWindowArrayReservoir(windowSize, windowUnit))
        );
    }

    /**
     * Records a request in a sliding time window histogram.
     *
     * <p>This is a convenience method for tracking request rates. It automatically registers
     * a histogram with a 60-second sliding time window if it doesn't exist, and records
     * a value of 1 for the request.</p>
     *
     * <p>To get requests per minute, multiply the histogram's mean rate by 60, or use the count
     * which represents total requests in the last 60 seconds.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * // In your service handler
     * Metrics.recordRequest(
     *     MetricNameAndLabels.of("request_rate")
     *         .label("endpoint", "/api/users")
     *         .label("method", "GET")
     * );
     * }</pre>
     *
     * @param nameAndLabels the metric name and labels
     */
    public static void recordRequest(MetricNameAndLabels nameAndLabels) {
        registerSlidingTimeWindowHistogram(nameAndLabels, 60, TimeUnit.SECONDS).update(1);
    }

    /**
     * Registers a meter metric with the specified name and labels.
     *
     * <p>Meters measure the rate of events over time.
     * The metric will be exposed at GET /metrics/{name}.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * Metrics.registerMeter(
     *     MetricNameAndLabels.of("api_requests")
     *         .label("endpoint", "/api/orders")
     *         .label("method", "POST")
     * );
     * }</pre>
     *
     * @param nameAndLabels the metric name and labels
     * @return the registered Meter instance
     */
    public static Meter registerMeter(MetricNameAndLabels nameAndLabels) {
        return getCustomRegistry(nameAndLabels.name()).meter(nameAndLabels.toString());
    }

    /**
     * Marks the occurrence of an event in a meter.
     *
     * @param nameAndLabels the metric name and labels
     */
    public static void markMeter(MetricNameAndLabels nameAndLabels) {
        registerMeter(nameAndLabels).mark();
    }

    /**
     * Marks multiple occurrences of events in a meter.
     *
     * @param nameAndLabels the metric name and labels
     * @param count the number of events to mark
     */
    public static void markMeter(MetricNameAndLabels nameAndLabels, long count) {
        registerMeter(nameAndLabels).mark(count);
    }

    /**
     * Registers a timer metric with the specified name and labels.
     *
     * <p>Timers measure both the rate of events and the duration of those events.
     * The metric will be exposed at GET /metrics/{name}.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * Metrics.registerTimer(
     *     MetricNameAndLabels.of("database_query_duration")
     *         .label("operation", "select")
     *         .label("table", "users")
     * );
     * }</pre>
     *
     * @param nameAndLabels the metric name and labels
     * @return the registered Timer instance
     */
    public static Timer registerTimer(MetricNameAndLabels nameAndLabels) {
        return getCustomRegistry(nameAndLabels.name()).timer(nameAndLabels.toString());
    }

    /**
     * Updates a timer with a duration.
     *
     * @param nameAndLabels the metric name and labels
     * @param duration the duration to record
     * @param unit the time unit of the duration
     */
    public static void updateTimer(MetricNameAndLabels nameAndLabels, long duration, TimeUnit unit) {
        registerTimer(nameAndLabels).update(duration, unit);
    }

    /**
     * Times the execution of a Runnable operation.
     *
     * <p>Example:</p>
     * <pre>{@code
     * Metrics.time(
     *     MetricNameAndLabels.of("cache_refresh"),
     *     () -> cache.refresh()
     * );
     * }</pre>
     *
     * @param nameAndLabels the metric name and labels
     * @param operation the operation to time
     */
    public static void time(MetricNameAndLabels nameAndLabels, Runnable operation) {
        Timer.Context context = registerTimer(nameAndLabels).time();
        try {
            operation.run();
        } finally {
            context.stop();
        }
    }

    /**
     * Times the execution of a Callable operation and returns its result.
     *
     * <p>Example:</p>
     * <pre>{@code
     * var users = Metrics.time(
     *     MetricNameAndLabels.of("database_query")
     *         .label("operation", "select")
     *         .label("table", "users"),
     *     () -> database.query("SELECT * FROM users")
     * );
     * }</pre>
     *
     * @param <T> the return type of the operation
     * @param nameAndLabels the metric name and labels
     * @param operation the operation to time
     * @return the result of the operation
     * @throws Exception if the operation throws an exception
     */
    public static <T> T time(MetricNameAndLabels nameAndLabels, Callable<T> operation) throws Exception {
        Timer.Context context = registerTimer(nameAndLabels).time();
        try {
            return operation.call();
        } finally {
            context.stop();
        }
    }

    /**
     * Removes a metric from the custom metrics registry.
     *
     * @param nameAndLabels the metric name and labels
     * @return true if the metric was removed, false if it didn't exist
     */
    public static boolean removeMetric(MetricNameAndLabels nameAndLabels) {
        return getCustomRegistry(nameAndLabels.name()).remove(nameAndLabels.toString());
    }

    /**
     * Removes all metrics from a specific named registry.
     *
     * @param metricName the metric name whose registry should be cleared
     */
    public static void removeAllMetrics(String metricName) {
        getCustomRegistry(metricName).removeMatching((name, metric) -> true);
    }

    /**
     * Gets the metric registry for a specific metric name.
     *
     * @param metricName the metric name
     * @return the metric registry for the given metric name
     */
    public static MetricRegistry customRegistry(String metricName) {
        return getCustomRegistry(metricName);
    }
}
