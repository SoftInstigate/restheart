# restheart-mqtt, four steps at a time

This is one walkthrough in four parts, not four independent recipes. Each part starts from the
environment the previous one left running and adds exactly one capability, so nothing is set up
twice:

| Part | What you add | What you learn |
|---|---|---|
| [1](#part-1--watch-a-message-arrive) | a live stream | how a broker message reaches a browser, and what the topic ACL does |
| [2](#part-2--ask-for-the-last-value-instead-of-waiting-for-the-next-one) | a REST endpoint | why a correctly configured endpoint can still answer `404` forever |
| [3](#part-3--keep-the-messages) | MongoDB | what the durability guarantee covers — by stopping the database on purpose |
| [4](#part-4--consume-the-messages-from-your-own-plugin) | your own plugin | how to consume MQTT from your own code without depending on an MQTT library |

Work through them in order. Part 4 takes about forty minutes from a cold start, most of it waiting
for Docker.

Everything below was run end to end against the files in this directory, and the outputs shown are
real, not illustrative. Where something surprising happens, it is called out rather than smoothed
over.

## Found a bug?

Please open an issue at [SoftInstigate/restheart/issues](https://github.com/SoftInstigate/restheart/issues),
and say **which part of this tutorial** you were on. What makes an MQTT report actionable:

- the `RHO` you were running (or the compose file, if unmodified), since almost every surprise in
  this module turns out to be a configuration one;
- the `mqtt module active: ... inactive: ...` line from the startup log — it tells us in one line
  which plugins were really on, which is rarely what people expect;
- the server log from startup to the failure, not only the error;
- whether you published at QoS 0 or 1 (`mosquitto_pub` defaults to **0**, which matters in part 3).

Do not worry about deciding whether something is a bug or a misconfiguration. If the documentation
let you configure it wrongly without complaining, that is worth knowing about too.

## Before you start

You need Docker and a JDK 21+. From this directory:

```
../mvnw -f ../pom.xml -pl commons,mqtt install -DskipTests
```

That builds `target/restheart-mqtt.jar` and `target/lib`, which the compose files mount into the
RESTHeart container. The module is not bundled with RESTHeart — see "Not bundled" in
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
  `{topic, payload, receivedAt, qos, replay, retain}`, which you will need as soon as one stream
  carries more than one topic — and which is the only way to tell a new event from the topic's
  stored last value. With the raw format there is nowhere to put that, so everything looks live.
  `replay` means the event came from RESTHeart's cache, `retain` that the broker handed it over as
  last-known-state on subscribe; a genuinely new event is neither. See "Durability" in
  [README.md](./README.md).
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

And, more usefully: understand why this endpoint is the one people most often report as broken.

### Enable it

`mqtt-rest` is off in part 1 — the status line said so. Open `docker-compose.yml` and add one line
to the `RHO` block:

```yaml
        /mqtt-rest/enabled->true;
```

Then, in terminal 1, `Ctrl-C` and:

```
docker compose up
```

The status line now reads `... mqtt-sse, mqtt-rest, mqtt-topic-authorizer ...`.

### Publish, then poll

With **no stream open** — close terminal 2's curl if it is still running — publish and ask for the
last value:

```
docker compose exec -T mosquitto mosquitto_pub -q 1 -t sensors/temp -m '{"value":99}'
curl -u admin:secret 'http://localhost:8080/mqtt?topic=sensors/temp'
```

```
{"error":"No message cached for topic: sensors/temp"}
```

`404`. The plugin is enabled, the message was published, the ACL allows the topic, and the answer
is still "nothing here". **This is the single most reported problem with this module, and it is not
a bug.**

### Why

`mqtt-rest` never talks to the broker. It only ever reads the router's last-message cache, and the
router only caches what something actually subscribed to. In part 1 the cache had an entry because
your SSE client was subscribed to `sensors/temp`; with that client gone, nothing is subscribed, the
broker delivers nothing, and there is nothing to cache.

So `mqtt-rest` needs a subscription that exists whether or not anyone is connected. That is what
`mqtt-router.subscriptions` is for. Add it to the `RHO` block:

```yaml
        /mqtt-router/subscriptions->[{"topic":"sensors/#","qos":1}];
```

Restart, and you will now see the subscription in the log at startup:

```
Subscribed to topic filter: sensors/# with QoS AT_LEAST_ONCE
```

(You will see that line **twice**. It is one broker subscription, issued once before the connection
was established and re-issued once the broker reported a brand-new session. A repeated SUBSCRIBE
for the same filter replaces the previous one, so this is noise in the log rather than a second
subscription.)

Publish and poll again:

```
docker compose exec -T mosquitto mosquitto_pub -q 1 -t sensors/temp -m '{"value":21.5}'
curl -u admin:secret 'http://localhost:8080/mqtt?topic=sensors/temp'
```

```
{"topic":"sensors/temp","payload":"{\"value\":21.5}","receivedAt":"2026-09-11T08:53:52.658422453Z","qos":1}
```

Note `"qos":1`. That is the QoS the message was *delivered* at, which is the lower of what the
publisher asked for and what the subscription asked for — it matters in part 3. Drop the `-q 1` from
`mosquitto_pub` and this reads `"qos":0`, because `mosquitto_pub` defaults to QoS 0.

Two more responses worth seeing, since both are easy to mistake for something else:

```
curl -u admin:secret 'http://localhost:8080/mqtt'
{"error":"Missing required query parameter: topic"}       # 400 - unlike /mqtt-sse, there is no default
curl -u admin:secret 'http://localhost:8080/mqtt?topic=traffic/x'
{"msg":"Not authorized for topic: traffic/x"}             # 403 - the same ACL guards both endpoints
```

### A remedy worth knowing, if you control the publishers

The cache lives in memory, so every restart puts you back at `404` until the next message arrives —
on a slow topic, a long blind window. There is a broker-side fix that costs nothing: publish the
latest state **retained**.

```
docker compose exec -T mosquitto mosquitto_pub -r -q 1 -t sensors/temp -m '{"value":21.5}'
```

Now restart RESTHeart (`docker compose up -d --force-recreate restheart`) and poll again **without
publishing anything**. It answers `200`. The broker replays its retained value on the SUBSCRIBE the
module issues at every startup, so the cache is correct immediately. Drop the `-r` and repeat: `404`.

This does not replace `mqtt-router.subscriptions` — with no subscription there is no SUBSCRIBE and
nothing is replayed — it removes the window after each restart. With `payload-envelope: true` such a
message arrives flagged `retain: true`, which is your warning that `receivedAt` is when *this
instance* received it, not when the reading was taken.

### One surprise you may hit instead of the 404

If you restart **RESTHeart** but not the broker, you may find `/mqtt` answering `200` at a point in
this tutorial where the text above says `404`. That is not the cache surviving — it is the broker's
*session* surviving.

The module connects with `clean-session: false` and a stable client id (derived from the RESTHeart
instance name), because that is what makes the broker redeliver what a crashed instance never
acknowledged — the whole basis of part 3. The cost is that subscriptions live in that session, not
in the process: a subscription made by the RESTHeart you just killed is still there, and the broker
keeps delivering it to the replacement. MQTT offers no way to list or clear a session's
subscriptions, and the only way to drop them is to discard the session, which would throw away the
undelivered messages the session exists to protect.

Restart the broker (`docker compose restart mosquitto`) if you want a genuinely clean slate. Keep
this in mind when a topic seems to be subscribed and nothing in your configuration says it should
be.

### Leave it running

Keep both the `mqtt-rest` and `mqtt-router.subscriptions` lines — part 3's environment has them
already.

---

## Part 3 — Keep the messages

**Goal:** write incoming messages to MongoDB, then **stop MongoDB on purpose** while messages are
still arriving. This is the part worth your time: it is where the module's design commitments become
visible, and where you can check whether you believe them.

### Switch environments

Stop part 2's environment — both compose files claim ports 8080 and 1883:

```
docker compose down
docker compose -f docker-compose-mongodb.yml up
```

Three containers now. The extra one is MongoDB; the RESTHeart configuration is part 2's plus
`mqtt-mongo-writer`, so nothing you did is lost.

Two of its settings are deliberate and worth reading before you go on.

**`id-strategy: payload-field`, `id-field: messageId`.** At-least-once delivery means duplicates are
possible: a message redelivered after a crash is delivered *again*. Keying the document on something
the message itself carries makes a redelivery converge on one document instead of adding a second.
With the default `auto`, every redelivery is a new document. This is the knob that decides which way
the trade goes, so publish messages with a `messageId` from here on.

**Authentication and authorization are taken out of MongoDB** (`fileRealmAuthenticator` and
`fileAclAuthorizer` instead of the `mongo*` ones). Without this, stopping MongoDB would also stop
every request being authenticated, `GET /mqtt-sse` included — and a demo whose streaming endpoint
dies with the database would demonstrate precisely the opposite of the point.

### Check the happy path first

```
docker compose -f docker-compose-mongodb.yml exec -T mosquitto \
  mosquitto_pub -q 1 -t sensors/temp -m '{"messageId":"m1","value":21.5}'

docker compose -f docker-compose-mongodb.yml exec -T mongodb \
  mongosh --quiet --eval 'db.getSiblingDB("iot")["sensor-events"].find().toArray()'
```

```
[ { _id: 'm1', topic: 'sensors/temp', payload: '{"messageId":"m1","value":21.5}', ... } ]
```

`_id` is `m1`, taken from the payload. Publish the same message again and the collection still holds
one document.

### Now break it

Open a stream in terminal 2 and leave it running:

```
curl -N -u admin:secret 'http://localhost:8080/mqtt-sse?topic=sensors/temp'
```

In terminal 3, stop the database and keep publishing:

```
docker compose -f docker-compose-mongodb.yml stop mongodb

for n in 1 2 3 4 5; do
  docker compose -f docker-compose-mongodb.yml exec -T mosquitto \
    mosquitto_pub -q 1 -t sensors/temp -m "{\"messageId\":\"dn$n\",\"value\":$n}"
  sleep 1
done
```

**Terminal 2 keeps printing all five events.** `GET /mqtt?topic=sensors/temp` still answers `200`
with the latest value. The live paths do not know or care that the database is gone — and that is
the deliberate design: SSE and REST are live consumers and never hold up an acknowledgement, so a
dashboard can keep working while persistence is degraded.

Meanwhile the five messages are sitting in `mqtt-mongo-writer`'s in-memory buffer, unacknowledged to
the broker.

### Bring it back

```
docker compose -f docker-compose-mongodb.yml start mongodb

docker compose -f docker-compose-mongodb.yml exec -T mongodb \
  mongosh --quiet --eval 'db.getSiblingDB("iot")["sensor-events"].find({},{_id:1}).toArray()'
```

```
[{"_id":"m1"},{"_id":"dn1"},{"_id":"dn2"},{"_id":"dn3"},{"_id":"dn4"},{"_id":"dn5"}]
```

All five arrived, within a couple of seconds of the database accepting connections again. Nothing
was lost and nothing was duplicated.

### What you have just established, and what you have not

You have established that the module absorbs a database restart. You have **not** established that
it absorbs a database outage, and it does not claim to.

`mqtt-mongo-writer`'s buffer waits up to `buffer.max-wait-ms` — **30 seconds** by default — for room
before giving up on a message; past that it drops it, counts it in `mqtt_buffer_dropped`, and
acknowledges it so the broker can move on. Try the same exercise with MongoDB down for two minutes
and you will see that happen. That ceiling is the honest boundary of what an in-memory buffer is
good for: **a prolonged database outage is not something an application layer can bridge, and the
defence against it is a properly sized replica set, not a longer queue.**

The ceiling also exists for a second reason, which matters more in practice than it sounds.
Without it, a MongoDB problem would take the live stream down with it: an unbounded wait parks the
thread dispatching a message, that message therefore never gets acknowledged, and once enough of
them pile up the broker's in-flight window fills and it stops delivering to this client *at all* —
SSE included, even though SSE never touches the database. Bounding the wait is what keeps the two
independent. Set `buffer.max-wait-ms: 0` to wait forever instead, accepting that coupling.

### The part that is easy to get wrong

Publish with `-q 0` — which is `mosquitto_pub`'s **default**, so this is what you get by forgetting
the flag — and none of the above guarantee applies. QoS 0 has no acknowledgement in the protocol at
all, so there is nothing for the module to withhold and nothing for the broker to redeliver. The
messages in this exercise survived because RESTHeart stayed up and its buffer held them; had you
killed RESTHeart instead of MongoDB, QoS 0 messages would simply be gone.

At-least-once end to end needs all of: **QoS 1 or 2 from the publisher**, `clean-session: false`
(the default), and a **stable client id** — and that last one must be *unique* across concurrently
connected instances, because a broker disconnects an existing client when another connects with the
same id. The default is derived from the RESTHeart instance name, so several instances sharing
`/core/name: default` will knock each other off the broker in a loop. You will see this warned about
in the log of every environment in this tutorial:

```
mqtt-client has no client-id and this instance still carries the stock name 'default' ...
```

Harmless here, where there is one instance. Not harmless in production.

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

- **`@Inject("config")` gives you `null`, not an empty map**, when the configuration has no block
  named after your plugin — the ordinary case for a plugin that is enabled by default and has
  working defaults. Throwing inside `@OnInit` aborts plugin instantiation and **stops RESTHeart from
  starting at all**, so one optional plugin takes the whole server down. Null-check it. (This
  example did not, until writing this tutorial found out; whether the framework should hand out an
  empty map instead is tracked in
  [#732](https://github.com/SoftInstigate/restheart/issues/732).)
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
