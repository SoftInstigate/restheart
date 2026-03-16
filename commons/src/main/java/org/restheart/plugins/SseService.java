/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins;

import io.undertow.server.handlers.sse.ServerSentEventConnection;

/**
 * Plugin interface for Server-Sent Events (SSE) endpoints.
 *
 * <p>Implement this interface and annotate the class with {@link RegisterPlugin}
 * to expose an SSE endpoint. The framework wires the endpoint via Undertow's
 * {@code ServerSentEventHandler} and applies the auth/authz pipeline when
 * {@code @RegisterPlugin(secure = true)}.
 *
 * <p>Unlike {@link Service}, there is no response object: the HTTP response
 * channel stays open and the plugin pushes events asynchronously via
 * {@link ServerSentEventConnection#send}. This plugin type works in standalone
 * mode with no MongoDB dependency.
 *
 * <p>Typical {@code onConnect} implementation:
 * <ol>
 *   <li>Store the connection in a registry for later broadcast</li>
 *   <li>Spawn a virtual thread to push events at a given cadence</li>
 *   <li>Register a close task via {@code connection.addCloseTask()} for cleanup</li>
 * </ol>
 *
 * <p>The connection is backed by Undertow's non-blocking I/O; {@code send()} is
 * thread-safe and can be called from any thread.
 *
 * <p>Example:
 * <pre>{@code
 * @RegisterPlugin(name = "clockSse", description = "tick every second", defaultURI = "/sse/clock")
 * public class ClockSseService implements SseService {
 *     @Override
 *     public void onConnect(ServerSentEventConnection conn, String lastEventId) {
 *         conn.setKeepAliveTime(15_000);
 *         Thread.ofVirtual().start(() -> {
 *             try {
 *                 while (conn.isOpen()) {
 *                     conn.send(Instant.now().toString(), "tick", null, null);
 *                     Thread.sleep(Duration.ofSeconds(1));
 *                 }
 *             } catch (InterruptedException e) {
 *                 Thread.currentThread().interrupt();
 *             }
 *         });
 *         conn.addCloseTask(c -> { /* cleanup *\/ });
 *     }
 * }
 * }</pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see RegisterPlugin
 * @see Plugin
 */
public interface SseService extends Plugin {

    /**
     * Called once when a client connects to the SSE endpoint.
     *
     * @param connection  the live SSE connection to the client; use
     *                    {@link ServerSentEventConnection#send} to push events
     * @param lastEventId the {@code Last-Event-ID} header value sent by the
     *                    client on reconnect, or {@code null} on first connect
     */
    void onConnect(ServerSentEventConnection connection, String lastEventId);
}
