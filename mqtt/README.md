# restheart-mqtt

Bridges an external MQTT broker into RESTHeart: incoming topic messages become Server-Sent Events, REST responses, MongoDB documents, or input to your own plugins.

The module connects to any MQTT 3.1.1 or 5.0 broker (Mosquitto, HiveMQ, EMQX) using [hivemq-mqtt-client](https://github.com/hivemq/hivemq-mqtt-client) 1.4.0, and exposes what it receives through ordinary RESTHeart plugins. Brokers that require mutual TLS with a client certificate — AWS IoT Core among them — are not supported: the module exposes only trust-store settings (`tls`, `tls-trust-store`, `tls-trust-store-password`), never a key store or client certificate.

## Not bundled

`restheart-mqtt` does not ship with RESTHeart: it is not in the distribution zip and not in the Docker image. It is installed separately — see "Installing" below. This is deliberate. MQTT is far from RESTHeart's habitual use cases, and `hivemq-mqtt-client` brings 13 transitive jars including RxJava and seven Netty modules, a second network stack and reactive runtime that exists nowhere else in a product built on Undertow/XNIO. Bundling it would add all of that to every RESTHeart installation, including the ones that never touch MQTT.

The module still lives in the RESTHeart monorepo, and its integration tests run against the core built alongside it — see "Building".

## What you get

| Plugin | Kind | Default URI | Enabled by default |
|---|---|---|---|
| `mqtt-client` | `Provider<MqttClient>` | — | No (Tier 1, the module switch) |
| `mqtt-router` | `Provider<MqttMessageRouter>` | — | No (gated explicitly, see below) |
| `mqtt-sse` | `SseService` | `/mqtt-sse` | No (Tier 2) |
| `mqtt-rest` | `JsonService` | `/mqtt` | No (Tier 2) |
| `mqtt-topic-authorizer` | `WildcardInterceptor` | — | Yes (deliberately, fails closed) |
| `mqtt-mongo-writer` | `Initializer` (`AFTER_STARTUP`) | — | No (Tier 2) |
| `mqtt-status` | `Initializer` (`AFTER_STARTUP`) | — | Yes (diagnostic sentinel) |

How the pieces fit together:

```mermaid
flowchart LR
    broker[("MQTT broker")]
    mongo[("MongoDB")]
    callers(["HTTP clients"])

    subgraph rh["RESTHeart"]
        direction TB
        client["<b>mqtt-client</b><br><i>the broker connection</i>"]
        router["<b>mqtt-router</b><br><i>fan-out + last-value cache</i>"]
        sse["<b>mqtt-sse</b><br><code>/mqtt-sse</code>"]
        rest["<b>mqtt-rest</b><br><code>/mqtt</code>"]
        writer["<b>mqtt-mongo-writer</b>"]
        own["<i>your own plugin</i>"]
    end

    broker ==> client
    client ==> router
    router --> sse
    router --> rest
    router --> writer
    router --> own
    writer --> mongo
    sse -.-> callers
    rest -.-> callers
```

`mqtt-sse` and `mqtt-rest` are both registered with `secure = true`: they require authentication. See "Enablement" below for what the "Enabled by default" column means in practice.

## Enablement

The module is dormant on installation, in two tiers.

**Tier 1 (the module switch).** `mqtt-client` is registered with `enabledByDefault = false`. Nothing else in the module can do anything until it is armed:

```yaml
mqtt-client:
  enabled: true
  broker-url: "tcp://broker:1883"
```

`mqtt-router` is registered with `enabledByDefault = false` too, and is enabled together with `mqtt-client`. It does not rely on `ProvidersChecker` following the injection graph: a disabled provider is never instantiated, so with `mqtt-client` off, `mqtt-client` is absent from the provider registry entirely and an enabled `mqtt-router` would log a "no provider found" ERROR on every startup. Enabling `mqtt-client` without `mqtt-router` leaves the module with no message routing, so enable both.

**Tier 2 (opt-in surfaces).** Arming Tier 1 alone exposes no HTTP endpoint. `mqtt-sse`, `mqtt-rest` and `mqtt-mongo-writer` are each independently registered with `enabledByDefault = false`, and are switched on one at a time as needed:

```yaml
mqtt-sse:
  enabled: true

mqtt-rest:
  enabled: true

mqtt-mongo-writer:
  enabled: true
```

**`mqtt-topic-authorizer` stays enabled by default**, regardless of the two tiers above, deliberately, not as an oversight. While every Tier 2 endpoint is off, the authorizer is simply never invoked and costs nothing. The moment one is turned on, the authorizer is already active and fails closed with no ACL configured, so a Tier 2 endpoint can never be reachable without topic authorization already in force, not even for a moment, and not even because an operator forgot a flag.

### Four realistic configurations

**Provider only.** Inject `mqtt-router` from your own plugin, as [`examples/mqtt-logger`](../examples/mqtt-logger) does, with no HTTP endpoint exposed at all:

```mermaid
flowchart LR
    broker[("MQTT broker")] ==> client["<b>mqtt-client</b>"] ==> router["<b>mqtt-router</b>"] --> own["<i>your plugin</i><br>injects mqtt-router"]
```

```yaml
mqtt-client:
  enabled: true
  broker-url: "tcp://broker:1883"

mqtt-router:
  enabled: true
```

**Live SSE:**

```mermaid
sequenceDiagram
    autonumber
    participant C as HTTP client
    participant A as mqtt-topic-authorizer
    participant S as mqtt-sse
    participant R as mqtt-router
    participant B as MQTT broker

    C->>A: GET /mqtt-sse?topic=sensors/%23
    Note over A: REQUEST_AFTER_AUTH<br>checks the topic filter against the ACL
    alt filter not granted
        A-->>C: 403, never reaches the router
    else filter granted
        A->>S: request continues
        S->>R: subscribe(filter, qos, listener)
        R->>B: SUBSCRIBE (once per filter)
        S-->>C: 200, text/event-stream held open
        B->>R: message on the topic
        R->>S: listener invoked
        S-->>C: event: mqtt-message
    end
```

```yaml
mqtt-client:
  enabled: true
  broker-url: "tcp://broker:1883"

mqtt-router:
  enabled: true

mqtt-sse:
  enabled: true
  default-topic: "sensors/#"

mqtt-topic-authorizer:
  acl:
    iot-reader:
      - "sensors/#"
```

**REST polling.** `mqtt-rest` only ever answers from the router's last-message cache, so something has to prime it:

```mermaid
flowchart LR
    broker[("MQTT broker")] ==> client["<b>mqtt-client</b>"] ==> router["<b>mqtt-router</b>"]
    router --> cache[("last-message cache<br><i>in memory</i>")]
    caller(["HTTP client"]) -->|"GET /mqtt?topic=..."| rest["<b>mqtt-rest</b>"]
    rest --> cache
    rest -.->|"200 with the last message<br>404 if nothing cached yet"| caller
```

```yaml
mqtt-client:
  enabled: true
  broker-url: "tcp://broker:1883"

mqtt-router:
  enabled: true
  last-message-cache: true
  subscriptions:
    - topic: "sensors/#"
      qos: 1

mqtt-rest:
  enabled: true

mqtt-topic-authorizer:
  acl:
    iot-reader:
      - "sensors/#"
```

**MongoDB persistence:**

```mermaid
flowchart LR
    broker[("MQTT broker")] ==> client["<b>mqtt-client</b>"] ==> router["<b>mqtt-router</b>"]
    router --> writer["<b>mqtt-mongo-writer</b>"]
    writer --> buffer["buffer<br><i>bounded, drop policy</i>"]
    buffer -->|"drain loop, batched"| mongo[("MongoDB<br><i>db.collection per sink</i>")]
```

```yaml
mqtt-client:
  enabled: true
  broker-url: "tcp://broker:1883"

mqtt-router:
  enabled: true

mqtt-mongo-writer:
  enabled: true
  mongo-sink:
    - topic: "sensors/#"
      database: "iot"
      collection: "sensor-events"
```

This last one also needs the `mongoclient` module configured and connected: `mqtt-mongo-writer` injects `mclient` rather than opening its own connection.

## Installing

The module is distributed as an archive containing the plugin, its runtime dependencies, a sample configuration and both licences. Download the latest build from `master` and unpack it into your instance's plugins directory:

```
curl -LO https://github.com/SoftInstigate/restheart/releases/download/mqtt-snapshot/restheart-mqtt-10.0.0-SNAPSHOT.zip
unzip restheart-mqtt-10.0.0-SNAPSHOT.zip -d /opt/restheart/plugins/
```

That archive is published by [`.github/workflows/mqtt.yml`](../.github/workflows/mqtt.yml) on every push to `master`, and **only after this module's integration tests have passed** against the core built alongside it. The release notes record which commit each build came from.

To build it yourself instead — necessarily, if you are working on the module:

```
./mvnw -pl mqtt package
unzip mqtt/target/restheart-mqtt-<version>.zip -d /opt/restheart/plugins/
```

Either way you get:

```
plugins/restheart-mqtt-<version>/
├── restheart-mqtt.jar
├── lib/                              hivemq-mqtt-client and its 13 transitives
├── restheart-mqtt-default-config.yml
├── LICENSE.txt
└── COMM-LICENSE.txt
```

The version directory is deliberate, not an accident of packaging. `PluginsScanner` scans the plugins directory two levels deep and treats any `lib` path segment as classpath-only, never scanning it for plugins, so both the jar and its dependencies are picked up from there. Keeping `lib/` inside the module's own directory rather than merging it into the shared `plugins/lib` is what stops this module's Netty and RxJava from mixing with other plugins' dependencies — see [#724](https://github.com/SoftInstigate/restheart/issues/724).

Then copy the settings you need from `restheart-mqtt-default-config.yml` into your instance's configuration, and enable at least `mqtt-client` and `mqtt-router`. See "Enablement" above for what each plugin's switch does.

**The RESTHeart you install into must be recent enough** to run the SSE handshake through `WildcardInterceptor`s (`SseWildcardInterceptorsExecutor`). Without that, `mqtt-topic-authorizer` resolves but is never invoked on the `/mqtt-sse` path, leaving the endpoint authenticated but not authorized per topic — a topic outside the ACL is silently accepted instead of rejected with `403`. See "Operational notes".

## The traps

Most of these are silent: nothing refuses to start, and nothing complains unless you go looking. One of them is not, and is called out below.

- **A config block present without `enabled: true` leaves the plugin off.** The block looks complete and correct; it just isn't read, because `PluginRecord.isEnabled` falls back to the plugin's compiled default (`false` for every Tier 1/2 plugin) whenever the `enabled` key is absent.
- **Plugin config blocks are top-level keys named after the plugin.** There is no `plugins-args:` wrapper: `PluginsFactory` looks up each plugin's arguments by name directly at the root of the configuration map. That wrapper form is not handled at all, so every setting nested under it is ignored and the plugin runs entirely on defaults. This is the one trap here that announces itself: core logs a WARN at startup naming every plugin whose block a `plugins-args` wrapper swallowed ([#723](https://github.com/SoftInstigate/restheart/issues/723)).
- **`mqtt-rest` answers `404` forever** unless something populates the router's last-message cache (`mqtt-router.subscriptions`, a live SSE client, or the writer's `mongo-sink`) **and** `mqtt-router.last-message-cache` is `true`.
- **`mqtt-mongo-writer` with an empty `mongo-sink` runs and writes nothing.** With no sinks, the writer never subscribes to anything on the router at all, so nothing is ever offered to the buffer and it stays empty — not a buffer that fills and drains into nowhere.
- **`mqtt-topic-authorizer` with no `acl` denies everything with `403`.** There is no permissive default.
- **A request with no `?topic=` is not unauthenticated territory.** `mqtt-sse` subscribes such a request to its `default-topic` (`sensors/#` by default), so the ACL must grant that filter or the request is refused with `403`. Granting only specific topics while leaving `default-topic` at its default is the common mistake.
- **MQTT 5 settings under `protocol-version: 3` are dropped.** `session-expiry-seconds`, `will.delay-seconds` and `will.message-expiry-seconds` exist only in MQTT 5.0, and the 3.1.1 connection path has nowhere to put them — the values are read and validated, then discarded. A will message configured with a delay fires immediately instead.

`mqtt-status` reports five of these at startup, by comparing the configuration against what the plugin registry actually instantiated: the missing `enabled` key, MQTT 5 keys under `protocol-version: 3`, `mqtt-rest` with an unprimed cache, an empty `mongo-sink`, and an empty `acl`. The `plugins-args` wrapper is reported by core instead. The `default-topic` one is reported by neither — it is a working ACL doing exactly what it was told, so there is nothing for a sentinel to find. Check the log before assuming a misconfiguration is a bug.

## Quick start

```yaml
mqtt-client:
  enabled: true
  broker-url: "tcp://localhost:1883"
  protocol-version: 5

mqtt-router:
  enabled: true
  subscriptions:
    - topic: "sensors/#"
      qos: 1

mqtt-sse:
  enabled: true
  default-topic: "sensors/#"

mqtt-rest:
  enabled: true

mqtt-topic-authorizer:
  acl:
    admin:
      - "sensors/#"
```

Then:

```
curl -N -u admin:secret 'http://localhost:8080/mqtt-sse?topic=sensors/temp'
curl -u admin:secret 'http://localhost:8080/mqtt?topic=sensors/temp'
```

## Try it in two minutes

[`mqtt/docker-compose.yml`](./docker-compose.yml) runs a self-contained two-container demo (RESTHeart plus a Mosquitto broker, no MongoDB) with the module already armed and a working ACL, so there is no broker or config to set up by hand. It bind-mounts `mqtt/target` into the RESTHeart container's plugins directory, so build the module first. From the `mqtt` directory:

```
../mvnw -f ../pom.xml -pl mqtt package
docker compose up
```

Then, in another shell:

```
curl -N -u admin:secret 'http://localhost:8080/mqtt-sse?topic=sensors/temp'
```

And in a third shell, publish a message:

```
docker compose exec mosquitto mosquitto_pub -t sensors/temp -m '{"value": 21.5}'
```

It runs `softinstigate/restheart-snapshot:latest` rather than a released image, because per-topic ACL enforcement on `/mqtt-sse` needs a fix that is currently only on unreleased `master`; against a released image the same demo would silently accept a topic outside the ACL instead of rejecting it with `403`.

This demo is for trying the module out, not for testing it. To exercise a locally modified module properly, run its integration tests: `./mvnw -pl mqtt -am verify -Pmqtt-it` — those launch the core you just built, rather than a published image. See "Building".

## Configuration

See "Enablement" above for each plugin's `enabled` default; the tables below cover the other keys only.

### `mqtt-client`

| key | default | notes |
|---|---|---|
| `broker-url` | `tcp://localhost:1883` | `tcp`, `ssl`, `mqtts`, `ws`, `wss` |
| `protocol-version` | `3` | `3` or `5` only; anything else fails at startup |
| `client-id` | generated | `restheart-<uuid>` when absent or blank |
| `username`, `password` | none | |
| `clean-session` | `false` | persistent session, so the broker replays what you missed — but only if `client-id` is also set explicitly. With the documented defaults `client-id` is regenerated on every start, so the broker sees a new client each boot and there is nothing to resume. |
| `keep-alive-seconds` | `60` | `< 0` fails at startup |
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
| `will.delay-seconds` | `0` | MQTT 5 only |
| `will.message-expiry-seconds` | none | MQTT 5 only |

The port follows the scheme when you do not give one: 1883 for `tcp`, 8883 for `ssl`/`mqtts`, 80 for `ws`, 443 for `wss`. Setting `tls: true` on a plaintext scheme also moves the default port (`tcp://broker` becomes port 8883, not 1883); an explicit port always wins.

`protocol-version` is a construction-time choice — `Mqtt3Client` and `Mqtt5Client` are separate class hierarchies — so changing it requires a restart.

### `mqtt-router`

| key | default | notes |
|---|---|---|
| `max-inflight-messages-per-second` | `5000` | global token bucket; excess messages are dropped, not queued. `0` or any value `<= 0` disables the rate limit entirely. |
| `last-message-cache` | `true` | backs `mqtt-rest` and the SSE replay-on-connect |
| `last-message-cache-size` | `1000` | LRU |
| `subscriptions` | `[]` | list of `{topic, qos}` subscribed at startup |

Subscriptions declared here survive a broker session reset: when the client reconnects with a new session, the router re-subscribes them.

`qos` is optional and defaults to `0`. It accepts an integer or a numeric string — `qos: "1"` works, since quoting a number in YAML is easy to do by accident — but any other value, including an out-of-range one such as `5`, fails at startup naming the offending entry rather than quietly becoming `0`. An entry whose `topic` is missing or blank is skipped with a warning; the other entries are still subscribed.

### `mqtt-sse`

| key | default | notes |
|---|---|---|
| `default-topic` | `sensors/#` | used when the request omits `?topic=` |
| `default-qos` | `1` | |
| `per-connection-queue-capacity` | `256` | full queue drops the newest message for that client only |
| `payload-envelope` | `false` | `true` wraps the payload as `{topic, payload, receivedAt, qos, cached}` |
| `last-message-cache` | `true` | on connect, replays the cached last message of **every** topic currently cached that matches the request's topic filter, sorted by `receivedAt` — not just one message |
| `max-connections-per-topic` | `0` | `0` = unlimited |
| `pipeline` | none | see below |

Query parameters: `?topic=<filter>&qos=<0-2>`. A `qos` that is unparseable or outside 0-2 falls back to `default-qos` with a warning rather than refusing the connection — by the time the service sees the request the SSE handshake has already been sent, so there is no status code left to return. `default-qos` itself is validated at startup, since it is that fallback.

Every event is sent with SSE event type `mqtt-message` — this is the field a client dispatches on (`event: mqtt-message`).

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
          field: "$.value"
```

| stage | parameters |
|---|---|
| `filter` | `jsonpath` + `condition`, or `topic-regex`, or `min-qos` |
| `map` | `extract-field` |
| `throttle` | `max-events-per-second` (default `10`) |
| `tumbling-window` | `window-ms` (default `1000`), `function` (default `count`), `field` |
| `sliding-window` | `window-size` (default `10`), `function` (default `count`), `field` |

A window's `field`, `map`'s `extract-field`, and `filter`'s `jsonpath` are **JSONPath expressions** evaluated against the message payload (e.g. `"$.value"`), not plain field names — `"temperature"` is not valid JSONPath and throws for every message, logged at WARN, so the window silently emits nothing. A `map` stage configured without `extract-field` is not rejected: it is silently dropped from the pipeline.

Aggregation functions: `count`, `sum`, `avg`, `min`, `max`, `array`, `last`. A window whose messages yield no numeric values (`avg`/`sum`/`min`/`max`) emits nothing rather than a zero or a sentinel. Tumbling windows are flushed by the connection's drain loop even when no further message arrives, so the last window of a quiet stream is still emitted.

Pipeline selection for a connection is: exact topic-filter match, then MQTT wildcard match, then no pipeline. Every entry must carry a `topic`; one without it could never be selected, so it fails at startup rather than sitting there doing nothing. Every other validation — `window-ms <= 0`, `window-size <= 0`, `max-events-per-second <= 0`, and `avg`/`min`/`max`/`sum` configured with no `field` — is checked only **per connection**, inside the SSE handshake, since pipelines are instantiated fresh for every connection: a bad value throws for that one connection rather than failing at startup.

### `mqtt-rest`

`GET /mqtt?topic=<topic>` returns the last message cached for that topic:

```json
{"topic": "sensors/temp", "payload": "{\"temp\":25}", "receivedAt": "...", "qos": 1}
```

`400` when the `topic` parameter is missing, `404` when nothing is cached for that topic — both with an `{"error": "..."}` body naming the reason. `OPTIONS` is handled for CORS; any other method returns `405`.

Requires `last-message-cache: true` on `mqtt-router`; with the cache disabled every topic returns `404`.

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

An unauthenticated request is denied with `401`. A request whose topic filter is not covered by any of the account's roles is denied with `403`.

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
| `topic-timestamp-hash` | hash of topic + timestamp | upserting `bulkWrite` |

The two deduplicating strategies write with upserts, so redelivery converges on one document instead of raising duplicate-key errors. Duplicate-key (11000) is counted as a success. `id-field` (default `messageId`) must be set and non-blank when `id-strategy` is `payload-field`; both keys are validated at startup, because a typo would otherwise disable deduplication silently.

With `payload-field`, if the payload is not valid JSON or the field is absent, the failure is swallowed: no `_id` is computed, and that one document falls back to a plain insert instead of an upsert — deduplication is lost silently, per message, rather than failing the write.

**Only `payload-field` converges across a cluster.** `topic-timestamp-hash` deduplicates redelivery within a single node, but not across nodes: its timestamp component is `receivedAt`, set independently by each node's own `Instant.now()` when the message arrives, so two nodes receiving the same broker message compute two different `_id`s and both write a document. Use `payload-field` — keyed on something the broker message itself carries — when several RESTHeart nodes receive the same messages and must converge on one document.

Every `mongo-sink` entry must carry all three of `topic`, `database` and `collection`, each a string; a missing or mistyped key fails at startup naming the entry. An absent or empty `mongo-sink` list is legal and simply means nothing is persisted.

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
        router.subscribe("sensors/#", Qos.AT_LEAST_ONCE, msg ->
            LOGGER.info("{} -> {}", msg.getTopic(), msg.getPayload()));
    }
}
```

Inject `mqtt-client` for the raw HiveMQ client when you need to publish, or need protocol features the router does not expose.

A worked example is in [`examples/mqtt-logger`](../examples/mqtt-logger).

The router's API is expressed entirely in this module's own types (`Qos`, `MqttMessage`), so a plugin that only uses the router does not compile against HiveMQ at all. Inject `mqtt-client` instead, as above, when you deliberately want the raw HiveMQ client.

## Operational notes

**Shutdown.** RESTHeart has no plugin shutdown callback, so the client and the writer's drain loop are stopped from JVM shutdown hooks. A `kill -9` will lose whatever is still buffered.

**Message loss is by design in three places**, each counted so it is visible rather than silent: the router's global rate limit, the SSE per-connection queue, and the writer's buffer under `ring-buffer` or `drop-incoming`. Only `blocking-queue` refuses to lose data.

**Clustering.** On MQTT 3.1.1 there are no shared subscriptions, so every RESTHeart node receives every message. For MongoDB persistence, use `payload-field` so the nodes converge instead of duplicating — `topic-timestamp-hash` only deduplicates within a single node (see "`mqtt-mongo-writer`" above). On MQTT 5.0, shared subscriptions are the cleaner answer — tracked in [#602](https://github.com/SoftInstigate/restheart/issues/602).

**Topic authorization on `/mqtt-sse`** is enforced end to end: a request for a topic filter granted by the ACL subscribes normally, and a request for an ungranted filter is rejected with `403` and a body of `{"msg":"Not authorized for topic: <filter>"}` before it ever reaches the router. This depends on RESTHeart running the SSE handshake through `WildcardInterceptor`s (`SseWildcardInterceptorsExecutor`, wired into `plugSseService`) — without it, SSE handshake requests pass through no interceptor at all, so `mqtt-topic-authorizer` resolves but is never invoked on that path, and `/mqtt-sse` ends up authenticated but not authorized per topic. Make sure the RESTHeart build this module is deployed against includes that fix.

## Building

```
./mvnw -pl mqtt test                      # this module's unit tests, no Docker needed
./mvnw -pl mqtt package                   # also produces the installable archive — see "Installing"
./mvnw -pl mqtt -am verify -Pmqtt-it      # plus the integration tests
```

The integration tests are opt-in, behind the `mqtt-it` profile: they need Docker, and nobody who is not working on MQTT should have to pay for it. They run against **the core built alongside this module**, not a published image — `MqttITBase` launches `core/target/restheart.jar` as a subprocess with this module staged into its own plugins directory, alongside a Mosquitto broker container. That is why `-am` is needed: it builds core first.

## Roadmap

Post-v1 work is tracked under [#601](https://github.com/SoftInstigate/restheart/issues/601): MQTT 5 shared subscriptions and user properties (#602), an HTTP → MQTT publish endpoint (#603), a WebSocket bridge (#604), polyglot pipeline stages (#605), replay from MongoDB via `Last-Event-ID` (#606), a dead-letter REST API (#607), metrics (#608), schema validation and pluggable deserializers (#609), and a distributed single-writer mode (#610).

## License

Dual-licensed, like every other RESTHeart module: AGPL-3.0 (see [LICENSE.txt](../LICENSE.txt)), or the RESTHeart COMMERCIAL LICENSE (see [COMM-LICENSE.txt](../COMM-LICENSE.txt)) for those who need it.
