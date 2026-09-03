package org.restheart.mqtt;

import java.net.Socket;

/**
 * Test helper to synchronise on an embedded broker becoming reachable.
 * <p>
 * Moquette's {@code Server.startServer()} returns before the listening socket
 * necessarily accepts connections. On a loaded CI runner a client that connects
 * immediately afterwards fails, and — because the MQTT client is configured with
 * automatic reconnect — it silently enters a back-off state instead of surfacing
 * the error, which makes the test flaky rather than red for the real reason.
 * Tests must therefore wait for the port before connecting.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
final class TestPorts {

    private TestPorts() {
    }

    /**
     * Blocks until the given TCP port on localhost accepts a connection.
     *
     * @param port      the port to probe
     * @param timeoutMs how long to keep probing before giving up
     * @throws IllegalStateException if the port is still not accepting connections
     *                               when the timeout elapses
     * @throws InterruptedException  if interrupted while waiting between probes
     */
    static void waitUntilOpen(int port, int timeoutMs) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (var socket = new Socket("localhost", port)) {
                return;
            } catch (Exception e) {
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("port " + port + " not accepting connections after " + timeoutMs + "ms");
    }
}
