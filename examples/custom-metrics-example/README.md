# Custom Metrics Example

This example demonstrates how to use custom application metrics in RESTHeart. The `Metrics` API allows developers to register and collect custom metrics that are automatically exposed via the `/metrics` endpoint in Prometheus format.

## Overview

The example service simulates an order processing system and demonstrates all available metric types:

- **Counter**: Monotonically increasing values (e.g., total orders processed)
- **Gauge**: Values that can increase or decrease (e.g., active orders, cache hit rate)
- **Histogram**: Statistical distribution of values (e.g., order values, order sizes)
- **Meter**: Rate of events over time (e.g., order processing rate)
- **Timer**: Duration and rate of events (e.g., order processing time)

## Building the Example

```bash
cd examples/custom-metrics-example
mvn clean package
```

This will create `custom-metrics-example.jar` in the `target` directory.

## Running the Example

1. Copy the JAR to RESTHeart's plugins directory:
```bash
cp target/custom-metrics-example.jar <restheart-dir>/plugins/
```

2. Start RESTHeart (the plugin will be automatically loaded)

3. Enable the metrics service in your RESTHeart configuration if not already enabled

## Usage

### Submit an Order

POST an order to generate metrics:

```bash
curl -X POST http://localhost:8080/custom-metrics-example \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-12345",
    "items": 3,
    "value": 250.75
  }'
```

Response:
```json
{
  "status": "success",
  "orderId": "ORD-12345",
  "message": "Order processed successfully",
  "items": 3,
  "value": 250.75,
  "metrics": {
    "list": "/metrics",
    "orders_total": "/metrics/orders_total",
    "active_orders": "/metrics/active_orders",
    "order_value_dollars": "/metrics/order_value_dollars"
  }
}
```

### View Custom Metrics

Each custom metric is exposed at its own endpoint. First, get the list of all metrics:

```bash
curl http://localhost:8080/metrics
```

Then query specific metrics:

```bash
curl http://localhost:8080/metrics/orders_total
curl http://localhost:8080/metrics/active_orders
curl http://localhost:8080/metrics/order_value_dollars
```

Each metric endpoint returns data in Prometheus format:

```
# TYPE orders_total gauge
orders_total{service="order-processing"} 42.0

# TYPE order_items_total gauge
order_items_total{service="order-processing"} 127.0

# TYPE active_orders gauge
active_orders 2.0

# TYPE cache_hit_rate gauge
cache_hit_rate{cache_type="orders"} 78.3

# TYPE order_value_dollars summary
order_value_dollars{service="order-processing",quantile="0.5"} 15000.0
order_value_dollars{service="order-processing",quantile="0.75"} 25000.0
order_value_dollars{service="order-processing",quantile="0.95"} 45000.0
order_value_dollars{service="order-processing",quantile="0.99"} 60000.0
order_value_dollars_count{service="order-processing"} 42.0

# TYPE order_processing_duration summary
order_processing_duration_duration{service="order-processing",quantile="0.5"} 0.025
order_processing_duration_duration{service="order-processing",quantile="0.95"} 0.055
order_processing_duration_rate{service="order-processing",period="1 minute"} 12.5
order_processing_duration_count{service="order-processing"} 42.0
```

## See Also

- [Metrics API Documentation](../../commons/src/main/java/org/restheart/metrics/Metrics.java)
- [RESTHeart Metrics Documentation](https://restheart.org/docs/metrics)
- [Prometheus Documentation](https://prometheus.io/docs)