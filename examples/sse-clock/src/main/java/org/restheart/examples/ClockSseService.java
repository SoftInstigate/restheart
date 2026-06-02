package org.restheart.examples;

import java.time.Duration;
import java.time.Instant;

import io.undertow.server.handlers.sse.ServerSentEventConnection;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.SseService;

/**
 * SSE clock example — pushes a {@code tick} event with the current UTC
 * timestamp every second.
 *
 * <p>Works in standalone mode: no MongoDB connection is required.
 *
 * <pre>
 * # Build
 * mvn package
 * cp target/sse-clock.jar /path/to/restheart/plugins/
 *
 * # Run
 * java -jar restheart.jar -s
 * </pre>
 *
 * <p>Client-side (browser):
 * <pre>{@code
 * const es = new EventSource('/sse/clock');
 * es.addEventListener('tick', e => console.log(e.data));
 * }</pre>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
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

        conn.addCloseTask(c -> { /* cleanup if needed */ });
    }
}
