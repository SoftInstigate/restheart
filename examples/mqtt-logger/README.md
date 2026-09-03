# mqtt-logger

A minimal RESTHeart plugin example that consumes messages from an MQTT broker topic, logs them to the console, and serves them via REST.

Works in **standalone mode** — no MongoDB connection required. Requires an MQTT broker (Mosquitto 2.x is the reference implementation).

## Build

From the repository root, install the commons artifact first (if not already done), then build the plugin:

```bash
./mvnw install -pl commons -DskipTests
cd examples/mqtt-logger && ../../mvnw package -DskipTests
```

### Dependencies

The plugin compiles against three `provided` dependencies—all supplied by the RESTHeart server at runtime, not bundled:

- **restheart-commons** — RESTHeart's base plugin API and utilities
- **restheart-mqtt** — The MQTT router provider and message model
- **hivemq-mqtt-client** — The underlying MQTT client library

Since these are declared with `provided` scope, the plugin JAR contains no runtime dependencies; the server supplies them from `plugins/restheart-mqtt.jar` and its transitive dependencies.

## Run

Copy the plugin JAR and the mqtt module JAR to RESTHeart's `plugins/` directory and start the server in standalone mode:

```bash
cp examples/mqtt-logger/target/mqtt-logger.jar core/target/plugins/
cp mqtt/target/restheart-mqtt.jar core/target/plugins/
java -jar core/target/restheart.jar -s
```

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
        router.subscribe(topic, MqttQos.AT_LEAST_ONCE, msg -> {
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
