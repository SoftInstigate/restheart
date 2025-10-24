/*-
 * ========================LICENSE_START=================================
 * custom-metrics-example
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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

package org.restheart.examples;

import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.metrics.Metrics;
import org.restheart.metrics.MetricNameAndLabels;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import static org.restheart.utils.GsonUtils.object;
import org.restheart.utils.HttpStatus;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example service demonstrating the usage of custom metrics in RESTHeart.
 *
 * This service exposes an endpoint that simulates order processing and demonstrates
 * all types of custom metrics:
 * - Counter: Total orders and order items
 * - Gauge: Active orders being processed
 * - Histogram: Order values and item counts
 * - Meter: Order processing rate
 * - Timer: Order processing duration
 *
 * Each custom metric is automatically exposed via /metrics endpoint:
 * - GET /metrics returns list of all metrics (including custom ones)
 * - GET /metrics/orders_total returns the orders_total metric
 * - GET /metrics/active_orders returns the active_orders metric
 * - etc.
 *
 * Example usage:
 * POST /custom-metrics-example
 * {
 *   "orderId": "ORD-12345",
 *   "items": 3,
 *   "value": 250.75
 * }
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
    name = "customMetricsExample",
    description = "demonstrates custom metrics usage",
    defaultURI = "/custom-metrics-example"
)
public class CustomMetricsExampleService implements JsonService {
    private static final Random random = new Random();
    private static final AtomicInteger activeOrders = new AtomicInteger(0);

    @OnInit
    public void onInit() {
        // Register a gauge to track active orders
        // The supplier function is called each time metrics are collected
        Metrics.registerGauge(
            MetricNameAndLabels.of("active_orders"),
            () -> activeOrders.get()
        );

        // Register a gauge to track cache hit rate (simulated)
        Metrics.registerGauge(
            MetricNameAndLabels.of("cache_hit_rate").label("cache_type", "orders"),
            () -> 75.5 + random.nextDouble() * 10 // Simulated value between 75.5 and 85.5
        );
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        switch(req.getMethod()) {
            case OPTIONS -> handleOptions(req);

            case POST -> handleOrder(req, res);

            case GET -> handleGetStats(req, res);

            default -> res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    /**
     * Handles GET request to retrieve metric statistics.
     * Demonstrates how to use the new getter methods to access metric objects
     * and retrieve their statistics without side effects.
     */
    private void handleGetStats(JsonRequest req, JsonResponse res) {
        var stats = object();

        // Counter statistics
        var orderMetric = MetricNameAndLabels.of("orders_total").label("service", "order-processing");
        var counter = Metrics.getCounter(orderMetric);
        if (counter != null) {
            stats.put("orders_total", counter.getCount());
        }

        // Histogram statistics - demonstrates accessing snapshot methods like getMax()
        var orderValueMetric = MetricNameAndLabels.of("order_value_dollars").label("service", "order-processing");
        var valueHistogram = Metrics.getHistogram(orderValueMetric);
        if (valueHistogram != null) {
            var snapshot = valueHistogram.getSnapshot();
            stats.put("order_value_stats", object()
                .put("count", valueHistogram.getCount())
                .put("min", snapshot.getMin() / 100.0)
                .put("max", snapshot.getMax() / 100.0)
                .put("mean", snapshot.getMean() / 100.0)
                .put("median", snapshot.getMedian() / 100.0)
                .put("p95", snapshot.get95thPercentile() / 100.0)
                .put("p99", snapshot.get99thPercentile() / 100.0).get());
        }

        // Another histogram for order size
        var orderSizeMetric = MetricNameAndLabels.of("order_size_items").label("service", "order-processing");
        var sizeHistogram = Metrics.getHistogram(orderSizeMetric);
        if (sizeHistogram != null) {
            var snapshot = sizeHistogram.getSnapshot();
            stats.put("order_size_stats", object()
                .put("count", sizeHistogram.getCount())
                .put("min", snapshot.getMin())
                .put("max", snapshot.getMax())
                .put("mean", snapshot.getMean())
                .put("median", snapshot.getMedian()).get());
        }

        // Meter statistics
        var rateMetric = MetricNameAndLabels.of("order_processing_rate").label("service", "order-processing");
        var meter = Metrics.getMeter(rateMetric);
        if (meter != null) {
            stats.put("order_processing_rate", object()
                .put("count", meter.getCount())
                .put("mean_rate", meter.getMeanRate())
                .put("one_minute_rate", meter.getOneMinuteRate())
                .put("five_minute_rate", meter.getFiveMinuteRate())
                .put("fifteen_minute_rate", meter.getFifteenMinuteRate()).get());
        }

        // Timer statistics
        var durationMetric = MetricNameAndLabels.of("order_processing_duration").label("service", "order-processing");
        var timer = Metrics.getTimer(durationMetric);
        if (timer != null) {
            var snapshot = timer.getSnapshot();
            stats.put("order_processing_duration", object()
                .put("count", timer.getCount())
                .put("mean_rate", timer.getMeanRate())
                .put("mean_duration_ms", snapshot.getMean() / 1_000_000.0)
                .put("max_duration_ms", snapshot.getMax() / 1_000_000.0)
                .put("p95_duration_ms", snapshot.get95thPercentile() / 1_000_000.0).get());
        }

        // Gauge statistics
        var activeOrdersGauge = Metrics.getGauge(MetricNameAndLabels.of("active_orders"));
        if (activeOrdersGauge != null) {
            var value = activeOrdersGauge.getValue();
            if (value instanceof Number num) {
                stats.put("active_orders", num);
            }
        }

        res.setContent(object()
            .put("message", "Metric statistics retrieved using getter methods")
            .put("stats", stats.get()));
    }

    /**
     * Handles order processing and demonstrates various metrics
     */
    private void handleOrder(JsonRequest req, JsonResponse res) {
        try {
            // Parse request
            var content = req.getContent();
            if (content == null || !content.isJsonObject()) {
                res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                res.setContent(object().put("error", "Request body must be a JSON object"));
                return;
            }

            var order = content.getAsJsonObject();
            var orderId = order.has("orderId") && order.get("orderId").isJsonPrimitive()
                ? order.get("orderId").getAsString()
                : "UNKNOWN";
            var items = order.has("items") && order.get("items").isJsonPrimitive()
                ? order.get("items").getAsInt()
                : 1;
            var value = order.has("value") && order.get("value").isJsonPrimitive()
                ? order.get("value").getAsDouble()
                : 0.0;

            // Create metric name and labels for this order
            var orderMetric = MetricNameAndLabels.of("orders_total").label("service", "order-processing");

            // Time the order processing operation
            var result = Metrics.time(
                MetricNameAndLabels.of("order_processing_duration").label("service", "order-processing"),
                () -> {
                    // Increment active orders gauge
                    activeOrders.incrementAndGet();

                    try {
                        // Simulate processing delay
                        Thread.sleep(random.nextInt(50) + 10);

                        // Counter: Increment total orders processed
                        Metrics.incrementCounter(orderMetric);

                        // Counter: Increment total items by the number of items in the order
                        Metrics.incrementCounter(
                            MetricNameAndLabels.of("order_items_total").label("service", "order-processing"),
                            items
                        );

                        // Histogram: Record order value distribution
                        Metrics.updateHistogram(
                            MetricNameAndLabels.of("order_value_dollars").label("service", "order-processing"),
                            (long) (value * 100) // Store as cents
                        );

                        // Histogram: Record order size (number of items)
                        Metrics.updateHistogram(
                            MetricNameAndLabels.of("order_size_items").label("service", "order-processing"),
                            items
                        );

                        // Meter: Mark order processing event
                        Metrics.markMeter(
                            MetricNameAndLabels.of("order_processing_rate").label("service", "order-processing")
                        );

                        // Simulate processing result
                        return object()
                            .put("status", "success")
                            .put("orderId", orderId)
                            .put("message", "Order processed successfully")
                            .put("items", items)
                            .put("value", value)
                            .put("metrics", object()
                                .put("list", "/metrics")
                                .put("orders_total", "/metrics/orders_total")
                                .put("active_orders", "/metrics/active_orders")
                                .put("order_value_dollars", "/metrics/order_value_dollars").get()).get();

                    } finally {
                        // Decrement active orders gauge
                        activeOrders.decrementAndGet();
                    }
                }
            );

            res.setContent(result);

        } catch (Exception e) {
            // Counter: Track errors
            Metrics.incrementCounter(
                MetricNameAndLabels.of("order_processing_errors")
                    .label("error_type", e.getClass().getSimpleName())
            );

            res.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            res.setContent(object()
                .put("error", "Order processing failed")
                .put("message", e.getMessage()));
        }
    }
}
