# restheart-mqtt

Bridges an external MQTT broker into RESTHeart: incoming topic messages become Server-Sent Events, REST responses, MongoDB documents, or input to your own plugins.

The module connects to any MQTT 3.1.1 or 5.0 broker (Mosquitto, HiveMQ, EMQX, AWS IoT Core) using [hivemq-mqtt-client](https://github.com/hivemq/hivemq-mqtt-client) 1.4.0, and exposes what it receives through ordinary RESTHeart plugins.

## What you get

| Plugin | Kind | Default URI |
|---|---|---|
| `mqtt-client` | `Provider<MqttClient>` | — |
| `mqtt-router` | `Provider<MqttMessageRouter>` | — |
| `mqtt-sse` | `SseService` | `/mqtt-sse` |
| `mqtt-rest` | `JsonService` | `/mqtt` |
| `mqtt-topic-authorizer` | `WildcardInterceptor` | — |
| `mqtt-mongo-writer` | `Initializer` (`AFTER_STARTUP`) | — |

`mqtt-sse` and `mqtt-rest` are both registered with `secure = true`: they require authentication.

## Quick start

```yaml
mqtt-client:
  broker-url: "tcp://localhost:1883"
  protocol-version: 5

mqtt-router:
  subscriptions:
    - topic: "sensors/#"
      qos: 1

mqtt-sse:
  default-topic: "sensors/#"
```

Then:

```
curl -N -u admin:secret 'http://localhost:8080/mqtt-sse?topic=sensors/temp'
curl -u admin:secret 'http://localhost:8080/mqtt?topic=sensors/temp'
```

## Configuration

### `mqtt-client`

| key | default | notes |
|---|---|---|
| `broker-url` | `tcp://localhost:1883` | `tcp`, `ssl`, `mqtt`, `mqtts`, `ws`, `wss` |
| `protocol-version` | `3` | `3` or `5` only; anything else fails at startup |
| `client-id` | generated | `restheart-<uuid>` when absent or blank |
| `username`, `password` | none | |
| `clean-session` | `false` | persistent session, so the broker replays what you missed |
| `keep-alive-seconds` | `60` | |
| `connect-timeout-seconds` | `10` | must be > 0 |
| `session-expiry-seconds` | `4294967295` | MQTT 5 only |
| `tls` | `false` | forces TLS even on a plaintext scheme |
| `tls-trust-store`, `tls-trust-store-password` | none | |
| `reconnect.enabled` | `true` | |
| `reconnect.initial-delay-ms` | `1000` | exponential back-off from here |
| `reconnect.max-delay-ms` | `30000` | |
| `will.topic`, `will.payload` | none | |
| `will.qos` | `0` | must be 0-2 |
| `will.retain` | `false` | |
| `will.delay-seconds` | `0` | |
| `will.message-expiry-seconds` | none | MQTT 5 only |

The port follows the scheme when you do not give one: 1883 for `tcp`, 8883 for `ssl`/`mqtts`, 80 for `ws`, 443 for `wss`. Setting `tls: true` on a plaintext scheme also moves the default port (`tcp://broker` becomes port 8883, not 1883); an explicit port always wins.

`protocol-version` is a construction-time choice — `Mqtt3Client` and `Mqtt5Client` are separate class hierarchies — so changing it requires a restart.

### `mqtt-router`

| key | default | notes |
|---|---|---|
| `max-inflight-messages-per-second` | `5000` | global token bucket; excess messages are dropped, not queued |
| `last-message-cache` | `true` | backs `mqtt-rest` and the SSE replay-on-connect |
| `last-message-cache-size` | `1000` | LRU |
| `subscriptions` | `[]` | list of `{topic, qos}` subscribed at startup |

Subscriptions declared here survive a broker session reset: when the client reconnects with a new session, the router re-subscribes them.

### `mqtt-sse`

| key | default | notes |
|---|---|---|
| `default-topic` | `sensors/#` | used when the request omits `?topic=` |
| `default-qos` | `1` | |
| `per-connection-queue-capacity` | `256` | full queue drops the newest message for that client only |
| `payload-envelope` | `false` | `true` wraps the payload as `{topic, payload, receivedAt, qos, cached}` |
| `last-message-cache` | `true` | sends the cached last message on connect |
| `max-connections-per-topic` | `0` | `0` = unlimited |
| `pipeline` | none | see below |

Query parameters: `?topic=<filter>&qos=<0-2>`.

Each event carries an id of the form `<topic>-<epochMillis>-<n>`, where `n` is a per-connection sequence. **These ids are unique within one stream but are not globally meaningful and cannot be used to resume** — `Last-Event-ID` is currently ignored. Resumable replay is tracked in [#606](https://github.com/SoftInstigate/restheart/issues/606).

### Processing pipeline

Events can pass through an ordered chain of stages before reaching the client. Pipelines are declared per topic filter and **instantiated per connection**, so stages holding state (`throttle`, the window aggregators) never share it between clients.

```yaml
mqtt-sse:
  pipeline:
    - topic: "sensors/#"
      stages:
        - type: filter
          jsonpath: "$.temperature"
          condition: "> 30"
        - type: throttle
          max-events-per-second: 10
        - type: tumbling-window
          window-ms: 5000
          function: avg
          field: "temperature"
```

| stage | parameters |
|---|---|
| `filter` | `jsonpath` + `condition`, or `topic-regex`, or `min-qos` |
| `map` | `extract-field` |
| `throttle` | `max-events-per-second` (default `10`) |
| `tumbling-window` | `window-ms` (default `1000`), `function` (default `count`), `field` |
| `sliding-window` | `window-size` (default `10`), `function`, `field` |

Aggregation functions: `count`, `sum`, `avg`, `min`, `max`. A window whose messages yield no numeric values emits nothing rather than a zero or a sentinel. Tumbling windows are flushed by the connection's drain loop even when no further message arrives, so the last window of a quiet stream is still emitted.

Pipeline selection for a connection is: exact topic-filter match, then MQTT wildcard match, then no pipeline.

### `mqtt-rest`

`GET /mqtt?topic=<topic>` returns the last message cached for that topic:

```json
{"topic": "sensors/temp", "payload": "{\"temp\":25}", "receivedAt": "...", "qos": 1}
```

400 when `topic` is missing or nothing is cached for it. Requires `last-message-cache: true` on `mqtt-router`.

### `mqtt-topic-authorizer`

Restricts which topic filters a role may subscribe to, on both `/mqtt-sse` and `/mqtt`.

```yaml
mqtt-topic-authorizer:
  acl:
    iot-reader:
      - "sensors/#"
      - "devices/+/status"
    admin:
      - "#"
```

An unauthenticated request is denied. A request whose topic filter is not covered by any of the account's roles is denied with 403.

**The ACL check is filter containment, not topic matching, and the difference matters.** A pattern grants a requested filter only when everything the requested filter could match is also matched by the pattern. So `sensors/+` does **not** grant `sensors/#`: `#` reaches deeper levels that `+` cannot. Granting `sensors/+` and receiving `sensors/a/b` would be a privilege escalation, so it is refused. `#` in a pattern grants everything below it, as expected.

### `mqtt-mongo-writer`

Persists messages to MongoDB through a bounded in-memory buffer that absorbs traffic peaks.

```yaml
mqtt-mongo-writer:
  buffer:
    strategy: "ring-buffer"
    capacity: 10000
  drain:
    batch-size: 200
    flush-interval-ms: 500
    max-retries: 3
    retry-delay-ms: 1000
  id-strategy: "topic-timestamp-hash"
  dead-letter-file: "./mqtt-dead-letter.log"
  mongo-sink:
    - topic: "sensors/#"
      database: "iot"
      collection: "sensor-events"
```

Requires the `mongoclient` module: it injects `mclient` rather than opening its own connection.

**Buffer strategies** (`buffer.strategy`, default `ring-buffer`):

| value | on overflow |
|---|---|
| `ring-buffer` | drop the oldest message; the new one is always accepted |
| `drop-incoming` | reject the new message |
| `blocking-queue` | block the producer until space frees up — the only strategy that applies real backpressure rather than losing data |

An unrecognised value fails at startup rather than silently falling back.

**Id strategies** (`id-strategy`, default `auto`):

| value | `_id` | write |
|---|---|---|
| `auto` | ObjectId | `insertMany` |
| `payload-field` | the `id-field` of the payload | upserting `bulkWrite` |
| `topic-timestamp-hash` | hash of topic + timestamp + payload | upserting `bulkWrite` |

The two deduplicating strategies write with upserts, so redelivery — or several RESTHeart nodes each receiving the same broker message — converges on one document instead of raising duplicate-key errors. Duplicate-key (11000) is counted as a success. `id-field` (default `messageId`) must be set and non-blank when `id-strategy` is `payload-field`; both keys are validated at startup, because a typo would otherwise disable deduplication silently.

Batches that still fail after `max-retries` are appended to `dead-letter-file`, one JSON document per line. Failing to write that file is logged, never propagated.

## Using the client from your own plugin

Inject `mqtt-router` to subscribe to topics:

```java
@RegisterPlugin(name = "my-service", description = "...", defaultURI = "/my-service")
public class MyService implements JsonService {
    @Inject("mqtt-router")
    private MqttMessageRouter router;

    @OnInit
    public void onInit() {
        router.subscribe("sensors/#", MqttQos.AT_LEAST_ONCE, msg ->
            LOGGER.info("{} -> {}", msg.getTopic(), msg.getPayload()));
    }
}
```

Inject `mqtt-client` for the raw HiveMQ client when you need to publish, or need protocol features the router does not expose.

A worked example is in [`examples/mqtt-logger`](../examples/mqtt-logger).

Note that `subscribe` currently takes HiveMQ's `MqttQos` in the listener signature, so plugins that use the router take a compile-time dependency on the HiveMQ types.

## Operational notes

**Shutdown.** RESTHeart has no plugin shutdown callback, so the client and the writer's drain loop are stopped from JVM shutdown hooks. A `kill -9` will lose whatever is still buffered.

**Message loss is by design in three places**, each counted so it is visible rather than silent: the router's global rate limit, the SSE per-connection queue, and the writer's buffer under `ring-buffer` or `drop-incoming`. Only `blocking-queue` refuses to lose data.

**Clustering.** On MQTT 3.1.1 there are no shared subscriptions, so every RESTHeart node receives every message. For MongoDB persistence, use `payload-field` or `topic-timestamp-hash` so the nodes converge instead of duplicating. On MQTT 5.0, shared subscriptions are the cleaner answer — tracked in [#602](https://github.com/SoftInstigate/restheart/issues/602).

**Topic authorization on `/mqtt-sse`** requires the fix in [#718](https://github.com/SoftInstigate/restheart/pull/718): before it, SSE handshake requests passed through no interceptor at all, so `mqtt-topic-authorizer` resolved but was never invoked on that path. On earlier RESTHeart releases, `/mqtt-sse` is authenticated but not authorized per topic.

## Building

```
./mvnw -pl mqtt test          # unit tests
./mvnw -pl mqtt verify        # plus integration tests against an embedded Moquette broker
```

`MqttMongoWriterIT` needs a MongoDB on `localhost:27017` and skips itself when there is none.

## Roadmap

Post-v1 work is tracked under [#601](https://github.com/SoftInstigate/restheart/issues/601): MQTT 5 shared subscriptions and user properties (#602), an HTTP → MQTT publish endpoint (#603), a WebSocket bridge (#604), polyglot pipeline stages (#605), replay from MongoDB via `Last-Event-ID` (#606), a dead-letter REST API (#607), metrics (#608), schema validation and pluggable deserializers (#609), and a distributed single-writer mode (#610).

## License

AGPL-3.0, as the rest of RESTHeart. See [LICENSE.txt](../LICENSE.txt).
