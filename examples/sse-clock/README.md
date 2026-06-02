# sse-clock

A minimal RESTHeart plugin example that pushes a UTC timestamp `tick` event to clients every second over Server-Sent Events (SSE).

Works in **standalone mode** — no MongoDB connection required.

## Build

From the repository root, install the commons artifact first (if not already done), then build the plugin:

```bash
./mvnw install -pl commons -DskipTests
cd examples/sse-clock && ../../mvnw package -DskipTests
```

## Run

Copy the plugin JAR to RESTHeart's `plugins/` directory and start the server in standalone mode:

```bash
cp examples/sse-clock/target/sse-clock.jar core/target/plugins/
java -jar core/target/restheart.jar -s
```

## Test

Using [httpie](https://httpie.io):

```bash
http --stream GET http://localhost:8080/sse/clock Accept:text/event-stream
```

Using curl:

```bash
curl -N -H "Accept: text/event-stream" http://localhost:8080/sse/clock
```

Using a browser (open the browser console and paste):

```javascript
const es = new EventSource('/sse/clock');
es.addEventListener('tick', e => console.log(e.data));
```

Expected output (one event per second):

```
event: tick
data: 2026-03-16T16:41:00.123456Z

event: tick
data: 2026-03-16T16:41:01.124789Z
```

Press `Ctrl+C` to stop.

## How it works

`ClockSseService` implements the `SseService` interface and is registered with `@RegisterPlugin`:

```java
@RegisterPlugin(
    name        = "clockSse",
    description = "Pushes a UTC timestamp tick event every second",
    defaultURI  = "/sse/clock",
    secure      = false
)
public class ClockSseService implements SseService {

    @Override
    public void onConnect(ServerSentEventConnection conn, String lastEventId) {
        conn.setKeepAliveTime(15_000);

        Thread.ofVirtual().start(() -> {
            try {
                while (conn.isOpen()) {
                    conn.send(Instant.now().toString(), "tick", null, null);
                    Thread.sleep(Duration.ofSeconds(1));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
```

`onConnect` is called once per client connection. A virtual thread loops until the connection closes, sending a `tick` event every second. The `keep-alive` ping fires every 15 seconds to prevent idle proxies from dropping the connection.
