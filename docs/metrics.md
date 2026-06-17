# Metrics

RESTHeart includes a built-in metrics system that collects HTTP request data and optionally JVM metrics, exposes them in Prometheus format via a REST endpoint, and provides a real-time browser dashboard.

## Architecture

```
┌──────────────┐    Prometheus     ┌──────────────────┐     HTML/JS     ┌─────────────────┐
│  RESTHeart   │─── text format ──▶│  GET /metrics    │◀────────────────│  /metrics-ui    │
│  (interceptor│    on demand      │  (MetricsService)│    Chart.js     │  (embedded SPA) │
│   collects)  │                   └──────────────────┘                 └─────────────────┘
└──────────────┘
```

The metrics module has three components:

| Component | Type | Description |
|---|---|---|
| **RequestsMetricsCollector** | Interceptor (`REQUEST_BEFORE_AUTH`) | Records timing and count for every HTTP request, grouped by method + status code + path template |
| **JvmMetricsCollector** | Initializer | Optionally registers JVM memory and GC metrics (disabled by default) |
| **MetricsService** | Service (`GET /metrics`) | Serves aggregated metrics in Prometheus exposition format |
| **Metrics UI** | Embedded static page | Self-contained dashboard at `/metrics-ui` that visualizes metrics in real time |

## Configuration

Metrics configuration is in `restheart.yml` (or overridden via the `RHO` environment variable).

### Metrics Service

```yaml
metrics:
  enabled: true                          # enable/disable the metrics service
  uri: /metrics                          # endpoint path
  missing-registry-status-code: 404      # HTTP status when no registries exist (404 or 200)
```

### Request Metrics Collector

```yaml
requestsMetricsCollector:
  enabled: true
  include: ["/*"]                        # path patterns to track
  exclude: ["/metrics", "/metrics/*"]    # path patterns to skip (avoids feedback loop)
```

### JVM Metrics Collector

```yaml
jvmMetricsCollector:
  enabled: false   # set to true to expose JVM memory and GC metrics
```

### Metrics UI (Static Resources)

The dashboard is served as an embedded static resource:

```yaml
static-resources:
  - what: static/metrics
    where: /metrics-ui
    welcome-file: restheart-metrics.html
    embedded: true
```

No additional configuration is needed — the UI is built into the `restheart-metrics` JAR.

## Using the Metrics UI

Open `/metrics-ui` in a browser while RESTHeart is running. The dashboard connects to the RESTHeart server and displays real-time metrics.

### Connection

1. Enter the RESTHeart server URL (e.g. `http://localhost:8080`) in the **server** field
2. Provide authentication credentials (default: `admin` / `secret`)
3. Click **connect**

The dashboard auto-discovers available metric registries by calling `GET /metrics`.

### Dashboard Panels

| Panel | Description |
|---|---|
| **KPI Row** | Total requests (since startup), request rate (1-min and 5-min rolling), latency percentiles (p50, p95, p99) |
| **Request Rate Chart** | Time-series line chart of requests/sec per method+status combination |
| **Request Count Chart** | Bar chart of cumulative request counts per method+status |
| **Latency Panels** | Distribution bars showing p50, p75, p95, p98, p99, p999 latency in milliseconds |

### Features

- **Auto-refresh**: Configurable interval (5s, 10s, 15s, 30s, 1m). Enabled by default at 30s.
- **Filter chips**: Click to toggle visibility of specific method+status combinations. Preferences are persisted in `localStorage` per host.
- **Dark theme**: Optimized for monitoring screens and low-light environments.

### Screenshot

Navigate to `http://localhost:8080/metrics-ui` after starting RESTHeart to see the dashboard.

## Prometheus Endpoint

The `/metrics` endpoint returns metrics in Prometheus exposition format. You can scrape it with Prometheus or any compatible tool.

### List Registries

```bash
curl -u admin:secret http://localhost:8080/metrics
```

Returns a JSON array of available registries:

```json
["/requests"]
```

If `jvmMetricsCollector` is enabled, you'll also see `/jvm`.

### Get Registry Metrics

```bash
curl -u admin:secret http://localhost:8080/metrics/requests
```

Returns Prometheus text format:

```
# HELP requests_total Total number of HTTP requests
# TYPE requests_total counter
requests_total{response_status_code="200",request_method="GET",path_template="/{db}/{coll}"} 142
# HELP requests_rate_1m Request rate (1-minute moving average)
# TYPE requests_rate_1m gauge
requests_rate_1m{response_status_code="200",request_method="GET",path_template="/{db}/{coll}"} 2.35
```

### Prometheus Scrape Config

```yaml
scrape_configs:
  - job_name: 'restheart'
    metrics_path: '/metrics/requests'
    basic_auth:
      username: 'admin'
      password: 'secret'
    static_configs:
      - targets: ['localhost:8080']
```

## Custom Metrics

RESTHeart provides a `Metrics` API in the `restheart-commons` module for registering custom application metrics from plugins.

### Available Metric Types

| Type | Description |
|---|---|
| **Counter** | Monotonically increasing value (e.g. total orders processed) |
| **Gauge** | Point-in-time value (e.g. active connections, queue size) |
| **Histogram** | Statistical distribution of values (e.g. response sizes) |
| **Meter** | Event rate with 1-min, 5-min, 15-min moving averages |
| **Timer** | Combines a histogram of durations with a meter of event rates |

### Example

```java
@RegisterPlugin(name = "orderService", description = "Order service with metrics", defaultURI = "/orders")
public class OrderService implements JsonService {

    private final Counter totalOrders = Metrics.counter("orders_total");
    private final Gauge<Integer> activeOrders = Metrics.gauge("active_orders", () -> getActiveCount());

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        totalOrders.inc();
        // ... handle request
    }
}
```

Custom metrics are automatically exposed alongside built-in metrics at `/metrics` in Prometheus format. See the [`custom-metrics-example`](../examples/custom-metrics-example/) for a complete working example.

## Metric Labels

HTTP request metrics include the following labels:

| Label | Description | Example |
|---|---|---|
| `request_method` | HTTP method | `GET`, `POST`, `PUT`, `DELETE` |
| `response_status_code` | HTTP status code | `200`, `404`, `500` |
| `path_template` | Matched path template | `/{db}/{coll}`, `/{db}/{coll}/{id}` |

### Custom Labels

Plugins can attach custom labels to requests using the `Metrics` API:

```java
Metrics.addMetricLabel(request, "tenant", "acme-corp");
```

These labels will appear in the `/metrics` output and in the Metrics UI filter panel.

## Disabling Metrics

To disable the metrics system entirely:

```yaml
metrics:
  enabled: false

requestsMetricsCollector:
  enabled: false

jvmMetricsCollector:
  enabled: false
```

Or via the `RHO` environment variable:

```bash
RHO='/metrics/enabled->false;/requestsMetricsCollector/enabled->false'
```

## See Also

- [Static Resources](https://restheart.org/docs/static-resources) — How RESTHeart serves static files
- [Custom Metrics Example](../examples/custom-metrics-example/) — Full working plugin with custom metrics
- [Prometheus Documentation](https://prometheus.io/docs/introduction/overview/) — Setting up Prometheus scraping
