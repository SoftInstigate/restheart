/*-
 * ========================LICENSE_START=================================
 * restheart-test-plugins
 * %%
 * Copyright (C) 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.test.plugins.services;

import java.time.Instant;

import io.undertow.server.handlers.sse.ServerSentEventConnection;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.SseService;

/**
 * Open SSE service used by SseIT integration tests.
 *
 * <p>Sends a {@code tick} event every 100 ms. If the client reconnects with a
 * {@code Last-Event-ID} header the value is echoed back as a
 * {@code last-event-id-echo} event so the test can verify the header is
 * forwarded to {@code onConnect}.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name            = "testSse",
    description     = "Open SSE service for integration tests",
    defaultURI      = "/test-sse",
    secure          = false,
    enabledByDefault = true
)
public class TestSseService implements SseService {

    @Override
    public void onConnect(ServerSentEventConnection conn, String lastEventId) {
        conn.setKeepAliveTime(30_000);

        // echo lastEventId so the test can verify forwarding
        if (lastEventId != null) {
            conn.send(lastEventId, "last-event-id-echo", "0", null);
        }

        Thread.ofVirtual().start(() -> {
            try {
                int i = 0;
                while (conn.isOpen()) {
                    conn.send(Instant.now().toString(), "tick", String.valueOf(i++), null);
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        conn.addCloseTask(c -> { /* close task registered for testing */ });
    }
}
