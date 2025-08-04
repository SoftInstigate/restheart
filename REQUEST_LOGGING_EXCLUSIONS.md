# RESTHeart Request Logging Exclusion Patterns

This feature allows you to exclude specific request paths from being logged, helping to reduce log noise from health checks, monitoring endpoints, and other frequent automated requests.

## Configuration

Add the `requests-log-exclude-patterns` array to your logging configuration. Optionally configure the `requests-log-exclude-interval` to control how often excluded requests are logged:

```yaml
logging:
  log-level: INFO
  log-to-console: true
  requests-log-mode: 1
  
  # Request path patterns to exclude from logging
  requests-log-exclude-patterns:
    - "/ping"              # Exact match for load balancer health checks
    - "/health"            # Exact match for health endpoint  
    - "/_ping"             # Exact match for internal ping
    - "/monitoring/*"      # Wildcard: excludes all paths starting with /monitoring/
    - "/api/*/status"      # Wildcard: excludes /api/v1/status, /api/v2/status, etc.
  
  # Optional: Interval for logging excluded requests (default: 100)
  # Logs the 1st, 100th, 200th, etc. excluded request for each pattern
  # Set to 0 or negative to log only the first occurrence and disable further logging
  requests-log-exclude-interval: 100
```

## Pattern Types

### Exact Matches

```yaml
- "/ping"      # Matches exactly "/ping"
- "/health"    # Matches exactly "/health"
```

### Wildcard Patterns  

```yaml
- "/monitoring/*"     # Matches "/monitoring/health", "/monitoring/status", etc.
- "/api/*/status"     # Matches "/api/v1/status", "/api/v2/status", etc.
- "/app/v*/health"    # Matches "/app/v1.0/health", "/app/v2.5/health", etc.
```

## Use Cases

### Load Balancer Health Checks

```yaml
requests-log-exclude-patterns:
  - "/ping"
  - "/health"
  - "/_health"
```

### Monitoring and Metrics Endpoints

```yaml  
requests-log-exclude-patterns:
  - "/metrics"
  - "/monitoring/*"
  - "/actuator/*"
```

### API Versioning

```yaml
requests-log-exclude-patterns:
  - "/api/*/health"     # Excludes health checks across all API versions
  - "/api/*/ping"       # Excludes pings across all API versions
```

## Before and After

### Before (Noisy Logs)

```log
INFO  o.restheart.handlers.RequestLogger - GET http://10.0.1.47:8080/ping from /10.0.2.65:34640 => status=200 elapsed=2ms contentLength=140
INFO  o.restheart.handlers.RequestLogger - GET http://10.0.1.47:8080/ping from /10.0.2.65:34641 => status=200 elapsed=1ms contentLength=140
INFO  o.restheart.handlers.RequestLogger - GET http://10.0.1.47:8080/ping from /10.0.2.65:34642 => status=200 elapsed=2ms contentLength=140
INFO  o.restheart.handlers.RequestLogger - POST http://10.0.1.47:8080/api/users from /10.0.2.65:34643 => status=201 elapsed=45ms contentLength=256
INFO  o.restheart.handlers.RequestLogger - GET http://10.0.1.47:8080/ping from /10.0.2.65:34644 => status=200 elapsed=1ms contentLength=140
```

### After (Clean Logs with Excluded Request Counting)

```log
INFO  o.restheart.handlers.RequestLogger - EXCLUDED REQUEST #1 for pattern '/ping' (will log every 100th occurrence):
INFO  o.restheart.handlers.RequestLogger - GET http://10.0.1.47:8080/ping from /10.0.2.65:34640 => status=200 elapsed=2ms contentLength=140
INFO  o.restheart.handlers.RequestLogger - POST http://10.0.1.47:8080/api/users from /10.0.2.65:34643 => status=201 elapsed=45ms contentLength=256

... (99 /ping requests are silently excluded) ...

INFO  o.restheart.handlers.RequestLogger - EXCLUDED REQUEST #100 for pattern '/ping' (total excluded: 100):
INFO  o.restheart.handlers.RequestLogger - GET http://10.0.1.47:8080/ping from /10.0.2.65:44640 => status=200 elapsed=1ms contentLength=140

... (another 99 /ping requests are silently excluded) ...

INFO  o.restheart.handlers.RequestLogger - EXCLUDED REQUEST #200 for pattern '/ping' (total excluded: 200):
INFO  o.restheart.handlers.RequestLogger - GET http://10.0.1.47:8080/ping from /10.0.2.65:54640 => status=200 elapsed=2ms contentLength=140
```

## Excluded Request Counting

To maintain visibility into the frequency of excluded requests while reducing log noise, the system implements a counting mechanism:

- **First occurrence**: Always logged with full request details to confirm the pattern is working
- **Periodic logging**: Every nth occurrence is logged with complete request information (configurable via `requests-log-exclude-interval`)
- **Full request details**: When logged, excluded requests show the same information as regular requests (method, URL, status, timing, etc.)
- **Total count**: Shows the cumulative number of excluded requests for each pattern

This provides insight into:

- Whether load balancer health checks are working correctly
- The exact timing and response details of periodic health checks
- The frequency of monitoring requests
- Potential issues if excluded request counts are unexpectedly high or low
- Performance characteristics of excluded endpoints (response times, status codes)

## Configuration Edge Cases

### Zero or Negative Interval Values

If you set `requests-log-exclude-interval` to 0 or a negative value:

```yaml
logging:
  requests-log-exclude-interval: 0  # Log only the first excluded request
```

**Behavior**: Only the first excluded request for each pattern will be logged with the message "logging disabled after first occurrence". Subsequent excluded requests will be counted but not logged.

**Use case**: When you want to confirm that exclusion patterns are working but don't want any periodic logging of excluded requests.

### Empty Patterns List

If `requests-log-exclude-patterns` is empty or not specified:

```yaml
logging:
  requests-log-exclude-patterns: []  # No exclusions
```

**Behavior**: All requests are logged normally, exactly like the original behavior.

## Backward Compatibility

This feature is fully backward compatible. If `requests-log-exclude-patterns` is not specified in your configuration, all requests will be logged as before.

## Performance Impact

The pattern matching is performed only when request logging is enabled (`requests-log-mode > 0`). The matching uses efficient string operations and compiled regex patterns for wildcard matching, so the performance impact is minimal.
