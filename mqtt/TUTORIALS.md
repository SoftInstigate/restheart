# restheart-mqtt, four steps at a time

> [!WARNING]
> **Experimental — not generally available yet.** The module is not part of any RESTHeart release,
> and everything below runs on snapshot builds of `master`. Details may still change before it is
> released. See [README.md](./README.md) for its status and how to report what you find.

This is one walkthrough in four parts, not four independent recipes. Each part starts from the
environment the previous one left running and adds exactly one capability, so nothing is set up
twice:

| Part | What you add | What you learn |
|---|---|---|
| [1](#part-1--watch-a-message-arrive) | a live stream | how a broker message reaches a browser, and what the topic ACL does |
| [2](#part-2--ask-for-the-last-value-instead-of-waiting-for-the-next-one) | a REST endpoint | how to read the latest value of a topic on demand |
| [3](#part-3--keep-the-messages) | MongoDB | how messages are stored, and why each one is stored once |
| [4](#part-4--consume-the-messages-from-your-own-plugin) | your own plugin | how to consume MQTT from your own code without depending on an MQTT library |

Work through them in order. Part 4 takes about forty minutes from a cold start, most of it waiting
for Docker.

Everything below was run end to end against the files in this directory, and the outputs shown are
real, not illustrative.

## Before you start

You need Docker and a JDK 25+. From this directory:

```
../mvnw -f ../pom.xml -pl mqtt -am install -DskipTests
```

That builds `target/restheart-mqtt.jar` and `target/lib`, which the compose files mount into the
RESTHeart container. `-am` also installs `restheart-parent`, which part 4 needs to build a plugin
against this module. With an older JDK the build stops straight away and says so. The module is not bundled with RESTHeart — see "Not bundled" in
[README.md](./README.md) — so nothing here works until you have built it.

Both compose files bind host ports **8080** and **1883**, so run one at a time and make sure
nothing else is using them.

The container runs `softinstigate/restheart-snapshot:latest`, not a released image. Per-topic ACL
enforcement on `/mqtt-sse` depends on a fix that is only on unreleased `master`; against a released
image part 1's `403` is silently a `200` instead, which would teach you the opposite of the truth.

You will want three terminals: one for the server's logs, one holding a stream open, one to publish
on. Commands below say which.

---

## Part 1 — Watch a message arrive

**Goal:** publish a message to a broker and see it come out of an HTTP endpoint, then see the topic
ACL refuse a topic you are not entitled to.

### Start the environment

In terminal 1:

```
docker compose up
```

Two containers: RESTHeart and a Mosquitto broker. No MongoDB — streaming and REST need none, and
`mqtt-mongo-writer`, the only plugin that does, is left off until part 3. That is also why
RESTHeart runs with `--standalone`.

Wait for `RESTHeart started`, then find this line a little above it:

```
mqtt module active: mqtt-client, mqtt-router, mqtt-sse, mqtt-topic-authorizer, mqtt-connector; inactive: mqtt-rest, mqtt-mongo-writer
```

**Read that line whenever something does not work.** Every plugin in this module except the
diagnostic ones ships disabled, and a configuration block with no `enabled: true` is silently
ignored rather than rejected — so "I configured it" and "it is running" are different claims, and
this line settles which. It is printed by `mqtt-status`, a plugin whose only job is to compare your
configuration against what actually got instantiated.

### Open a stream

In terminal 2:

```
curl -N -u admin:secret 'http://localhost:8080/mqtt-sse?topic=sensors/temp'
```

Nothing happens, and that is correct: the response is an open Server-Sent Events stream waiting for
something to arrive. `-N` disables curl's buffering, without which you would see nothing until the
connection closed.

### Publish something

In terminal 3:

```
docker compose exec -T mosquitto mosquitto_pub -q 1 -t sensors/temp -m '{"value": 21.5}'
```

Terminal 2 prints:

```
id:sensors/temp-1789116644402-0
event:mqtt-message
data:{"value": 21.5}
```

Three things worth noticing in that output:

- **`event:mqtt-message`** is the event type every message is sent with. In a browser this is what
  you dispatch on: `source.addEventListener('mqtt-message', ...)`. Listening only for the default
  `message` event gets you nothing.
- **`data:` is your payload, untouched.** The module does not wrap or reinterpret it; set
  `payload-envelope: true` if you would rather receive
  `{topic, payload, payloadEncoding, receivedAt, qos, replay, retain}`, which you will need as soon
  as one stream carries more than one topic — and which is the only way to tell a new event from the
  topic's stored last value. With the raw format there is nowhere to put any of that, so everything
  looks live and text. `replay` means the event came from RESTHeart's cache, `retain` that the broker
  handed it over as last-known-state on subscribe — a genuinely new event is neither — and
  `payloadEncoding` is `text` or `base64`, because an MQTT payload is arbitrary bytes and JSON cannot
  carry bytes. See [README.md](./README.md).
- **The id is not resumable.** It is unique within this stream and meaningless outside it;
  `Last-Event-ID` is currently ignored, so a reconnecting client does not get a replay.

### Now ask for a topic you are not allowed to read

```
curl -u admin:secret 'http://localhost:8080/mqtt-sse?topic=traffic/%23'
```

```
{"msg":"Not authorized for topic: traffic/#"}
```

with status `403`. The compose file grants the `admin` role exactly `sensors/#`, and
`mqtt-topic-authorizer` **fails closed**: a role with no entry has access to no topic at all, and
with no `acl` configured at all every request to `/mqtt-sse` and `/mqtt` is refused. There is no
permissive default to forget to tighten.

Two traps here that are worth more than they look:

- **`%23` is `#`.** Unescaped in a shell, `#` starts a comment and your topic silently becomes
  `traffic/`, which is a different filter and will be refused for a reason you did not intend.
- **A request with no `?topic=` is not an unauthorized request.** It subscribes to `mqtt-sse`'s
  `default-topic` (`sensors/#` here), so the ACL must grant *that* filter too. Granting the specific
  topics your dashboards use while leaving `default-topic` untouched is the usual mistake.

Drop the credentials entirely and you get `401`, not `403`: `/mqtt-sse` and `/mqtt` are both
registered `secure = true`, so authentication happens before the ACL is ever consulted.

### Leave it running

Keep terminal 1 up. Part 2 builds on this same environment.

---

## Part 2 — Ask for the last value instead of waiting for the next one

**Goal:** add `GET /mqtt?topic=...`, which answers immediately with the most recent message on a
topic rather than holding a stream open. This is what a polling client or a health check wants.

### Enable it

`mqtt-rest` is off in part 1 — the status line said so. It answers from the router's cache of the
last message received on each topic, and the router only receives what something subscribed to. In
part 1 that was your SSE client; for an endpoint that works whether or not anyone is streaming, the
router needs a standing subscription of its own.

Open `docker-compose.yml` and add two lines to the `RHO` block:

```yaml
        /mqtt-rest/enabled->true;
        /mqtt-router/subscriptions->[{"topic":"sensors/#","qos":1}];
```

Then, in terminal 1, `Ctrl-C` and:

```
docker compose up
```

The status line now reads `... mqtt-sse, mqtt-rest, mqtt-topic-authorizer ...`, and the log shows
the subscription:

```
Subscribed to topic filter: sensors/# with QoS AT_LEAST_ONCE
```

### Publish, then poll

```
docker compose exec -T mosquitto mosquitto_pub -q 1 -t sensors/temp -m '{"value":21.5}'
curl -u admin:secret 'http://localhost:8080/mqtt?topic=sensors/temp'
```

```
{"topic":"sensors/temp","payload":"{\"value\":21.5}","payloadEncoding":"text","receivedAt":"2026-09-11T08:53:52.658422453Z","qos":1,"retain":false}
```

`qos` is the QoS the message was delivered at: the lower of what the publisher asked for and what
the subscription asked for. `mosquitto_pub` defaults to QoS 0, which is why the commands here pass
`-q 1`.

The same topic ACL as part 1 guards this endpoint:

```
curl -u admin:secret 'http://localhost:8080/mqtt?topic=traffic/x'
{"msg":"Not authorized for topic: traffic/x"}             # 403
```

> [!TIP]
> The cache lives in memory, so after a restart `/mqtt` answers `404` until the next message
> arrives. If your publishers send the latest state as a **retained** message
> (`mosquitto_pub -r ...`), the broker replays it when the module subscribes at startup, and the
> endpoint answers straight away. See "The traps" in [README.md](./README.md) for this and the
> other reasons `/mqtt` can answer `404`.

### Leave it running

Keep both new lines — part 3's environment has them already.

---

## Part 3 — Keep the messages

**Goal:** write incoming messages to MongoDB, so they are kept after they have been delivered.

### Switch environments

Stop part 2's environment — both compose files claim ports 8080 and 1883:

```
docker compose down
docker compose -f docker-compose-mongodb.yml up
```

Three containers now. The extra one is MongoDB; the RESTHeart configuration is part 2's plus
`mqtt-mongo-writer`, which writes every message on `sensors/#` to the `iot.sensor-events`
collection.

One of its settings is worth reading before you go on: **`id-strategy: payload-field`,
`id-field: messageId`**. MQTT delivers at least once, so the same message can arrive twice, for
example when the broker redelivers after a reconnect. Keying the document on something the message
itself carries makes a second delivery overwrite the first instead of adding a copy. With the
default `auto`, every delivery is a new document. So publish messages with a `messageId` from here
on.

### Store a message

```
docker compose -f docker-compose-mongodb.yml exec -T mosquitto \
  mosquitto_pub -q 1 -t sensors/temp -m '{"messageId":"m1","value":21.5}'

docker compose -f docker-compose-mongodb.yml exec -T mongodb \
  mongosh --quiet --eval 'db.getSiblingDB("iot")["sensor-events"].find().toArray()'
```

```
[
  {
    _id: 'm1',
    topic: 'sensors/temp',
    payload: '{"messageId":"m1","value":21.5}',
    receivedAt: ISODate('2026-09-13T18:02:30.090Z'),
    receivedAtNanos: 295068,
    qos: 1,
    retain: false
  }
]
```

`_id` is `m1`, taken from the payload. Run the same `mosquitto_pub` again and query once more: the
collection still holds one document.

The other fields are BSON types, not strings, so the collection can be queried as what it is.
`receivedAt` is a date, which has millisecond precision, so the sub-millisecond remainder is kept
next to it in `receivedAtNanos`: two messages inside one millisecond are ordinary at sensor rates,
and without it the order they arrived in would be lost. A payload that is not valid UTF-8 would be
stored as BSON binary, byte for byte, instead of a string.

### Publish at QoS 1

A message is acknowledged to the broker once RESTHeart has it in memory, on its way to MongoDB.
Until then the broker still owes it, and redelivers it if RESTHeart never takes it — but only at
**QoS 1 or 2**: QoS 0, the `mosquitto_pub` default, has no acknowledgement at all, so a message
lost before RESTHeart takes it is gone. See "Durability" in [README.md](./README.md) for the whole
guarantee, including what a crash costs and how to size the broker's own queue.

> [!NOTE]
> **What if MongoDB restarts?** Try it: stop the database, publish a message, start it again.
>
> ```
> docker compose -f docker-compose-mongodb.yml stop mongodb
> docker compose -f docker-compose-mongodb.yml exec -T mosquitto \
>   mosquitto_pub -q 1 -t sensors/temp -m '{"messageId":"m2","value":22}'
> docker compose -f docker-compose-mongodb.yml start mongodb
> ```
>
> A stream open on `/mqtt-sse` keeps receiving messages while the database is down, and within a
> few seconds of MongoDB coming back `m2` is in the collection: it waits in the writer's buffer
> until MongoDB takes it. Nothing is dropped and nothing stops arriving — how long that holds
> depends on how much the buffer and the broker's queue can keep, which "Durability" in
> [README.md](./README.md) explains.

### Leave it running

Part 4 adds to this same environment.

---

## Part 4 — Consume the messages from your own plugin

**Goal:** receive MQTT messages in your own code. SSE, REST and MongoDB are three consumers of the
same router; this is how you become a fourth.

### Build the example

[`examples/mqtt-logger`](../examples/mqtt-logger) is a ~100-line service that subscribes to a topic
filter, keeps the last 100 messages, and serves them over HTTP. From the repository root:

```
cd examples && ../mvnw -pl mqtt-logger package -DskipTests
```

Look at its `pom.xml` before you look at the code. It depends on `restheart-commons` and
`restheart-mqtt`, both `provided` — and **not** on `hivemq-mqtt-client`. The router's API is
expressed entirely in this module's own types (`Qos`, `MqttMessage`), so a plugin that consumes
messages never compiles against an MQTT library at all. You only need `mqtt-client` injected, and
the HiveMQ types with it, if you want to publish or reach protocol features the router does not
expose.

### Mount it

Add one line to `docker-compose-mongodb.yml`, under the RESTHeart service's existing `volumes`:

```yaml
      - ../examples/mqtt-logger/target/mqtt-logger.jar:/opt/restheart/plugins/custom/mqtt-logger.jar:ro
```

Mount the **jar**, not the directory it lives in. `target/` also holds `it-plugins/`, a full copy of
core's plugins staged for this module's integration tests, and the plugin scanner recurses into
subdirectories — so mounting `./target` loads those too and startup fails with a
`ClassNotFoundException` naming a class you never asked for.

Restart RESTHeart:

```
docker compose -f docker-compose-mongodb.yml up -d --force-recreate restheart
```

The log shows the jar being scanned and then:

```
MqttLoggerService initialized, subscribed to: sensors/#
```

### Use it

```
docker compose -f docker-compose-mongodb.yml exec -T mosquitto \
  mosquitto_pub -q 1 -t sensors/temp -m '{"messageId":"p1","value":30}'

curl -u admin:secret 'http://localhost:8080/mqtt-logger'
```

```json
{
  "topic": "sensors/#",
  "messages": [
    {
      "topic": "sensors/temp",
      "payload": "{\"messageId\":\"p1\",\"value\":30}",
      "qos": 1,
      "receivedAt": "2026-09-11T08:49:31.571974900Z"
    }
  ]
}
```

One message, once — even though four things are now subscribed to overlapping filters
(`mqtt-router.subscriptions` on `sensors/#`, your SSE client on `sensors/temp`, the MongoDB writer
on `sensors/#`, and this plugin on `sensors/#`). The router subscribes on the broker only to filters
that no other registered filter already covers, and fans out locally. It has to: MQTT 3.1.1 permits
a broker to deliver one copy of a message per matching subscription, and Mosquitto does.

### The whole of the subscription

```java
@Inject("mqtt-router")
private MqttMessageRouter router;

@OnInit
public void init() {
    router.subscribe("sensors/#", Qos.AT_LEAST_ONCE, msg ->
        LOGGER.info("{} -> {}", msg.getTopic(), msg.getPayload()));
}
```

Three things to take from the real file rather than from this excerpt:

- **`@Inject("config")` is an empty map when your plugin has no configuration block**, which is
  the ordinary case for a plugin that is enabled by default and has working defaults, so
  `config.getOrDefault(...)` is enough. It was not always so: before RESTHeart 10 it was `null`,
  and since a plugin that throws inside `@OnInit` aborts plugin instantiation, this very example
  stopped the whole server from starting when dropped in with no block. Writing this tutorial found
  that out; it is fixed in core
  ([#732](https://github.com/SoftInstigate/restheart/issues/732)). If you target an older
  RESTHeart, null-check it.
- **`subscribe` is for live consumers.** Your listener never holds up an acknowledgement to the
  broker: throw, block, or fall behind, and ingestion carries on without you. If you need the
  message *kept*, that is `subscribeDurable`, which does not acknowledge until you say you have
  taken responsibility — the contract `mqtt-mongo-writer` is built on. Use it only if you genuinely
  persist somewhere, because a durable listener that never reports back stops acknowledgements
  altogether.
- **Unsubscribe when you are done.** A listener registered and never removed keeps being called
  forever, and keeps the filter subscribed on the broker.

### Where to go next

**A browser client is this, plus authentication — and authentication is the part that needs
thought:**

```html
<script>
  const s = new EventSource('/mqtt-sse?topic=sensors/temp', { withCredentials: true });
  s.addEventListener('mqtt-message', e => console.log(JSON.parse(e.data)));
</script>
```

`EventSource` **cannot set request headers**, so the `-u admin:secret` you have used throughout this
tutorial has no browser equivalent: there is nowhere to put an `Authorization` header. `/mqtt-sse` is
`secure = true` and will answer `401`. What a browser can send is a cookie, and RESTHeart's
`AuthCookieHandler` exists for exactly this — it reconstructs the `Authorization` header from an auth
cookie, so the page authenticates once by other means and the `EventSource` rides on the cookie
afterwards. Configure that before expecting the snippet above to work, and serve the page from the
same origin so the cookie is sent at all.

Then read the `pipeline` section of [README.md](./README.md): per-topic throttling and tumbling
windows are configuration, not code, and a dashboard that wants one averaged reading per second out
of a thousand per second needs no plugin at all.

## Tear down

```
docker compose -f docker-compose-mongodb.yml down -v
```

`-v` removes the MongoDB volume too, so a later run starts from an empty collection.

## Found a bug?

The module is experimental, so reports are welcome. Open an issue at
[SoftInstigate/restheart/issues](https://github.com/SoftInstigate/restheart/issues), say which part
of this tutorial you were on, and include the `RHO` you were running and the
`mqtt module active: ... inactive: ...` line from the startup log. [README.md](./README.md) lists
what else helps under "Reporting bugs".
