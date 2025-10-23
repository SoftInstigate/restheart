# Sliding Time Window Histogram - Request Rate Example

This example shows how to use a sliding time window histogram to measure request rate (requests per minute).

## Overview

A sliding time window histogram keeps only values recorded within a specified time window. This is perfect for calculating rates over time, such as requests per minute.

## Implementation

### 1. Register the Histogram (in your Service's @OnInit method)

```java
import org.restheart.metrics.Metrics;
import org.restheart.metrics.MetricNameAndLabels;
import java.util.concurrent.TimeUnit;

@OnInit
public void onInit() {
    // Register a histogram with 60-second sliding window
    Metrics.registerSlidingTimeWindowHistogram(
        MetricNameAndLabels.of("request_rate")
            .label("endpoint", "/api/users")
            .label("method", "GET"),
        60,
        TimeUnit.SECONDS
    );
}
```

### 2. Record Each Request (in your handler method)

```java
@Override
public void handle(JsonRequest req, JsonResponse res) {
    // Record the request (value of 1 for each request)
    Metrics.updateHistogram(
        MetricNameAndLabels.of("request_rate")
            .label("endpoint", "/api/users")
            .label("method", "GET"),
        1
    );

    // Or use the convenience method (automatically uses 60-second window)
    Metrics.recordRequest(
        MetricNameAndLabels.of("request_rate")
            .label("endpoint", "/api/users")
            .label("method", "GET")
    );

    // Your handler logic here...
}
```

### 3. Query the Metrics

Get the metrics via HTTP:

```bash
curl http://localhost:8080/metrics/request_rate
```

The response will show statistics in Prometheus format:

```
# TYPE request_rate histogram
request_rate{endpoint="/api/users",method="GET",quantile="0.5"} 12.0
request_rate{endpoint="/api/users",method="GET",quantile="0.75"} 15.0
request_rate{endpoint="/api/users",method="GET",quantile="0.95"} 18.0
request_rate{endpoint="/api/users",method="GET",quantile="0.99"} 20.0
request_rate_count{endpoint="/api/users",method="GET"} 45.0
request_rate_mean{endpoint="/api/users",method="GET"} 0.75
```

## Understanding the Metrics

- **count**: Total requests in the last 60 seconds (e.g., 45 requests)
- **mean**: Average requests per second (e.g., 0.75 req/s)
- **Requests per minute**: `count` value directly, or `mean * 60` (e.g., 0.75 * 60 = 45 req/min)
- **Percentiles**: Show distribution of request timing within the window

## Complete Example

```java
package org.restheart.examples;

import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.metrics.Metrics;
import org.restheart.metrics.MetricNameAndLabels;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

import java.util.concurrent.TimeUnit;

@RegisterPlugin(
    name = "requestRateExample",
    description = "demonstrates request rate tracking with sliding time window",
    defaultURI = "/request-rate-example"
)
public class RequestRateExampleService implements JsonService {

    @OnInit
    public void onInit() {
        // Register request rate metric with 60-second sliding window
        Metrics.registerSlidingTimeWindowHistogram(
            MetricNameAndLabels.of("api_request_rate")
                .label("service", "request-rate-example"),
            60,
            TimeUnit.SECONDS
        );
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        // Record this request
        Metrics.recordRequest(
            MetricNameAndLabels.of("api_request_rate")
                .label("service", "request-rate-example")
        );

        // Handle the request
        res.setStatusCode(HttpStatus.SC_OK);
        res.setContent("Request recorded in metrics!");
    }
}
```

## Alternative: Using Custom Window Sizes

You can use different window sizes for different use cases:

```java
// 5-minute window for longer-term rate tracking
Metrics.registerSlidingTimeWindowHistogram(
    MetricNameAndLabels.of("request_rate_5min").label("endpoint", "/api/users"),
    5,
    TimeUnit.MINUTES
);

// 10-second window for very short-term rate tracking
Metrics.registerSlidingTimeWindowHistogram(
    MetricNameAndLabels.of("request_rate_10sec").label("endpoint", "/api/users"),
    10,
    TimeUnit.SECONDS
);
```

## Direct Registry Access

If you need more control, you can access the registry directly:

```java
var registry = Metrics.customRegistry("request_rate");
var histogram = registry.histogram(
    MetricNameAndLabels.of("request_rate").label("endpoint", "/api/users").toString(),
    () -> new com.codahale.metrics.Histogram(
        new com.codahale.metrics.SlidingTimeWindowArrayReservoir(60, TimeUnit.SECONDS)
    )
);

// Record requests
histogram.update(1);

// Get statistics
var snapshot = histogram.getSnapshot();
long requestsPerMinute = histogram.getCount(); // Requests in last 60 seconds
double requestsPerSecond = snapshot.getMean();
```

## Benefits of Sliding Time Window

1. **Accurate Rate Calculation**: Only counts requests within the time window
2. **Automatic Decay**: Old requests automatically drop out of the window
3. **Real-time Visibility**: Always shows current rate, not historical average
4. **Memory Efficient**: Fixed memory usage regardless of request volume

## See Also

- [Metrics.java](../../commons/src/main/java/org/restheart/metrics/Metrics.java) - Full API documentation
- [Dropwizard Metrics - Reservoirs](https://metrics.dropwizard.io/4.2.0/manual/core.html#histograms) - Underlying metrics library
