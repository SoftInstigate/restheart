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

import io.undertow.server.handlers.sse.ServerSentEventConnection;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.SseService;

/**
 * Secured SSE service used by SseIT integration tests.
 *
 * <p>Requires authentication ({@code secure = true}). Sends a single
 * {@code auth} event on connect so the test can verify a valid connection
 * is established for authenticated clients.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name            = "testSseSecure",
    description     = "Secured SSE service for integration tests",
    defaultURI      = "/test-sse-secure",
    secure          = true,
    enabledByDefault = true
)
public class TestSseSecureService implements SseService {

    @Override
    public void onConnect(ServerSentEventConnection conn, String lastEventId) {
        conn.send("connected", "auth", null, null);
    }
}
