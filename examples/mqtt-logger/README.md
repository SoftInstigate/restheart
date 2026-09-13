# mqtt-logger

A minimal RESTHeart plugin example that consumes messages from an MQTT broker topic, logs them to the console, and serves them via REST.

Works in **standalone mode** — no MongoDB connection required. Requires an MQTT broker (Mosquitto 2.x is the reference implementation).

## Build

From the repository root, install the two artifacts the plugin compiles against first (if not already done), then build the plugin:

```bash
./mvnw install -pl commons,mqtt -DskipTests
cd examples/mqtt-logger && ../../mvnw package -DskipTests
```

`mqtt` is needed as well as `commons`: the plugin depends on `restheart-mqtt`, and without it in the local repository the build cannot resolve `MqttMessageRouter`.

### Dependencies

The plugin compiles against two `provided` dependencies—both supplied by the RESTHeart server at runtime, not bundled:

- **restheart-commons** — RESTHeart's base plugin API and utilities
- **restheart-mqtt** — The MQTT router provider and message model

Note what is *not* here: `hivemq-mqtt-client`. The router's API is expressed entirely in `restheart-mqtt`'s own types (`Qos`, `MqttMessage`), so a plugin that consumes messages never compiles against the MQTT client library. Add it only if you inject `mqtt-client` to reach the raw HiveMQ client for protocol features the router does not expose.

Since these are declared with `provided` scope, the plugin JAR contains no runtime dependencies; the server supplies them from `restheart-mqtt.jar` and the jars in its `lib/` directory.

## Run

Copy the plugin JAR, the mqtt module JAR **and the mqtt module's `lib/` directory** into a subdirectory of RESTHeart's plugins directory, then start the server in standalone mode:

```bash
mkdir -p core/target/plugins/mqtt
cp -r mqtt/target/restheart-mqtt.jar mqtt/target/lib core/target/plugins/mqtt/
cp examples/mqtt-logger/target/mqtt-logger.jar core/target/plugins/mqtt/
java -jar core/target/restheart.jar -s
```

The `lib/` directory is not optional. It holds `hivemq-mqtt-client` and its Netty and RxJava dependencies; without them `mqtt-client` cannot be loaded. The subdirectory keeps those jars out of the shared `plugins/lib`, and the plugin scanner treats any `lib` directory as classpath-only, so the layout above is picked up as it is — it is the same layout the module's installable archive and its integration tests use.

The server requires an MQTT broker running on `localhost:1883` (the default), and configuration to enable the `mqtt-client` and `mqtt-router` providers and this plugin. See `mqtt/README.md` for full configuration details.

## Test

### Prerequisites

1. Start an MQTT broker (e.g. Mosquitto):

```bash
docker run -d --rm -p 1883:1883 eclipse-mosquitto:2
```

2. Ensure `mqtt-client` and `mqtt-router` are enabled in RESTHeart's configuration.

### Publish a test message

Using `mosquitto_pub`:

```bash
mosquitto_pub -h localhost -t sensors/temperature -m '{"celsius": 23.5}'
```

Or using any MQTT client that can publish to `sensors/temperature`.

### Read collected messages

Using curl:

```bash
curl http://localhost:8080/mqtt-logger
```

Expected response (JSON):

```json
{
  "topic": "sensors/#",
  "messages": [
    {
      "topic": "sensors/temperature",
      "payload": "{\"celsius\": 23.5}",
      "qos": 1,
      "receivedAt": "2026-03-16T16:41:00.123456Z"
    }
  ]
}
```

The plugin maintains a bounded buffer of the last 100 messages received. Older messages are dropped when the buffer fills.

## How it works

`MqttLoggerService` implements the `JsonService` interface and is registered with `@RegisterPlugin`:

```java
@RegisterPlugin(
    name = "mqtt-logger",
    description = "logs messages received from an MQTT topic",
    defaultURI = "/mqtt-logger",
    secure = true
)
public class MqttLoggerService implements JsonService {
    @Inject("mqtt-router")
    private MqttMessageRouter router;

    @OnInit
    public void init() {
        String topic = (String) config.getOrDefault("topic", "sensors/#");
        router.subscribe(topic, Qos.AT_LEAST_ONCE, msg -> {
            // Buffer and log each received message
        });
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        // GET returns subscribed topic and message buffer
        // Other methods return 405
    }
}
```

At startup, `init()` reads the configured topic filter (defaults to `sensors/#`) and subscribes to it via the `mqtt-router` provider. For each incoming message, a listener appends it to a thread-safe bounded list (max 100 entries, FIFO) and logs it to the console at INFO level.

GET requests return a JSON object with the subscribed topic and the current message buffer. Any other HTTP method returns 405 Method Not Allowed.
