# Garbage Collector Selection Guide for RESTHeart

## TL;DR

**Use G1GC (default)** for most RESTHeart deployments  
**Use ZGC** only when heap > 8GB and ultra-low latency is critical

## Quick Decision Tree

```text
┌─────────────────────────────┐
│  What's your heap size?     │
└──────────┬──────────────────┘
           │
    ┌──────┴──────┐
    │             │
   < 8GB        > 8GB
    │             │
    │      ┌──────┴──────┐
    │      │             │
    │   Latency     Throughput
    │   Critical    Priority
    │      │             │
    ▼      ▼             ▼
  G1GC    ZGC          G1GC
```

## Detailed Comparison

### G1GC (Default Choice for RESTHeart)

**Best For:**

- ✅ Heap size: 512MB - 8GB (RESTHeart's typical range)
- ✅ REST API workloads (short-lived requests)
- ✅ Mixed throughput/latency requirements
- ✅ Production stability (mature, well-tested)

**Characteristics:**

- **Pause times**: 10-50ms (target: 50ms)
- **Throughput**: Excellent
- **Memory overhead**: Low
- **Generational**: Yes (better for short-lived objects)

**RESTHeart Performance (Expected):**

```
Heap Size: 2GB
Average Response: ~10ms
P99 Response: ~30ms
GC Pause: 10-30ms
Throughput: ~6500 req/s
```

### ZGC (Ultra-Low Latency Option)

**Best For:**

- ✅ Heap size: 8GB+ (optimized for large heaps)
- ✅ Ultra-low latency requirements (<1ms GC pauses)
- ✅ Strict latency SLAs (P99 < 5ms)
- ✅ Can accept 10-20% throughput reduction

**Characteristics:**

- **Pause times**: <1ms (concurrent)
- **Throughput**: 10-20% lower than G1GC
- **Memory overhead**: 10-15% higher
- **Generational**: No (same treatment for all objects)

**RESTHeart Performance (Expected):**

```text
Heap Size: 8GB
Average Response: ~10ms
P99 Response: ~15ms
GC Pause: <1ms
Throughput: ~5200 req/s (↓20% vs G1GC)
```

## Real-World Scenarios

### Scenario 1: Typical Deployment

```yaml
Deployment: Docker container on AWS ECS
Heap: 2GB (4GB container)
Traffic: 3000 req/s, mostly CRUD operations
Objects: Short-lived (request-scoped)

Recommendation: G1GC ✅
Reason: 
- Heap size in G1GC's sweet spot
- Short-lived objects → generational collection wins
- 20-30ms GC pauses perfectly acceptable
- Higher throughput means lower cost
```

### Scenario 2: High-Traffic API

```yaml
Deployment: Kubernetes cluster
Heap: 4GB (8GB pod)
Traffic: 10,000 req/s, complex aggregations
SLA: P99 < 50ms

Recommendation: G1GC ✅
Reason:
- 10-30ms GC pauses fit within 50ms SLA
- Higher throughput = more headroom
- Stable and predictable behavior
```

### Scenario 3: Ultra-Low Latency

```yaml
Deployment: Bare metal servers
Heap: 16GB
Traffic: 5000 req/s, real-time data feeds
SLA: P99 < 5ms, GC pauses < 1ms

Recommendation: ZGC ✅
Reason:
- Strict latency SLA requires <1ms GC
- Large heap (16GB) where ZGC excels
- Can afford throughput reduction
- Consistent sub-millisecond pauses
```

### Scenario 4: Microservice

```yaml
Deployment: Docker container
Heap: 512MB (1GB container)
Traffic: 500 req/s, lightweight operations

Recommendation: G1GC ✅
Reason:
- Small heap where ZGC has higher overhead
- G1GC's generational collection more efficient
- ZGC overkill for this scale
```

## How to Switch

### Using G1GC (Default)

No configuration needed - it's the default in all RESTHeart Docker images:

```bash
docker run -p 8080:8080 softinstigate/restheart
```

### Switching to ZGC

For large heaps (8GB+) with ultra-low latency needs:

```bash
# Docker
docker run \
  -e JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:-UseG1GC" \
  -p 8080:8080 \
  softinstigate/restheart

# Docker Compose
services:
  restheart:
    image: softinstigate/restheart
    environment:
      JAVA_TOOL_OPTIONS: "-XX:+UseZGC -XX:-UseG1GC"

# Kubernetes
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
      - name: restheart
        env:
        - name: JAVA_TOOL_OPTIONS
          value: "-XX:+UseZGC -XX:-UseG1GC"
```

### Custom G1GC Tuning

For more aggressive pause time targets:

```bash
# Target 20ms pauses (vs default 50ms)
docker run \
  -e JAVA_TOOL_OPTIONS="-XX:MaxGCPauseMillis=20" \
  -p 8080:8080 \
  softinstigate/restheart
```

## Monitoring

### Check Active GC

```bash
# In running container
docker exec <container> java -XX:+PrintFlagsFinal -version | grep -E 'Use(G1|Z)GC'
```

### Monitor GC Performance

```bash
# Add GC logging
docker run \
  -e JAVA_TOOL_OPTIONS="-Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags" \
  -p 8080:8080 \
  softinstigate/restheart

# View GC stats
docker exec <container> cat /tmp/gc.log | grep "Pause"
```

### Key Metrics to Watch

**G1GC:**

- Young GC pause time (should be < 30ms)
- Mixed GC pause time (should be < 50ms)
- GC frequency (less is better)

**ZGC:**

- Pause time (should be < 1ms)
- Load (CPU usage for concurrent GC)
- Memory overhead

## Performance Testing

### Recommended Load Test

```bash
# Test with G1GC (default)
ab -n 100000 -c 100 http://localhost:8080/your-endpoint

# Test with ZGC
docker run -e JAVA_TOOL_OPTIONS="-XX:+UseZGC" ...
ab -n 100000 -c 100 http://localhost:8080/your-endpoint

# Compare results:
# - Request per second (throughput)
# - Mean/median/p99 response times
# - GC pause times from logs
```

## Summary Table

| Factor | G1GC | ZGC | Winner |
|--------|------|-----|--------|
| Heap < 4GB | ⭐⭐⭐⭐⭐ | ⭐⭐ | **G1GC** |
| Heap 4-8GB | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | **G1GC** |
| Heap > 8GB | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | **ZGC** |
| Short-lived objects | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | **G1GC** |
| Throughput | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | **G1GC** |
| Latency (< 50ms OK) | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Tie |
| Latency (< 5ms required) | ⭐⭐ | ⭐⭐⭐⭐⭐ | **ZGC** |
| Memory efficiency | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | **G1GC** |
| Maturity | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | **G1GC** |
| REST APIs (typical) | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | **G1GC** |

## Conclusion

**For 90% of RESTHeart deployments, G1GC is the right choice.**

Only use ZGC if:

- Heap > 8GB
- Business requires <1ms GC pauses
- Latency SLAs are extremely strict (P99 < 5ms)
- Can accept 10-20% throughput reduction

## References

- [G1GC Documentation](https://docs.oracle.com/en/java/javase/24/gctuning/garbage-first-g1-garbage-collector1.html)
- [ZGC Documentation](https://wiki.openjdk.org/display/zgc)
- [Java 24 Virtual Threads](https://docs.oracle.com/en/java/javase/24/core/virtual-threads.html)
- [RESTHeart Performance Tuning](https://restheart.org/docs/performances/)
