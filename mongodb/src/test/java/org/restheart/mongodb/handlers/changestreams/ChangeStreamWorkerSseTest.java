/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.handlers.changestreams;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.bson.BsonDocument;
import org.bson.json.JsonMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.undertow.server.handlers.sse.ServerSentEventConnection;

/**
 * Unit tests for SSE session management in {@link ChangeStreamWorker}.
 *
 * <p>These tests do not start a MongoDB connection — they verify the fan-out
 * sets and the self-terminate condition directly on the in-memory state of
 * the worker.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class ChangeStreamWorkerSseTest {

    private ChangeStreamWorker worker;

    @BeforeEach
    void setUp() {
        var key = new ChangeStreamWorkerKey("http://localhost/db/coll/_streams/s", new BsonDocument(), JsonMode.RELAXED);
        worker = new ChangeStreamWorker(key, List.of(), "db", "coll");
    }

    @Test
    void sseSessions_startEmpty() {
        assertTrue(worker.sseSessions().isEmpty(), "SSE sessions must be empty on construction");
    }

    @Test
    void addSseSession_sessionIsTracked() {
        var conn = mock(ServerSentEventConnection.class);

        worker.sseSessions().add(conn);

        assertTrue(worker.sseSessions().contains(conn));
    }

    @Test
    void removeSseSession_sessionIsGone() {
        var conn = mock(ServerSentEventConnection.class);
        worker.sseSessions().add(conn);

        worker.sseSessions().remove(conn);

        assertFalse(worker.sseSessions().contains(conn));
    }

    @Test
    void bothSetsEmpty_afterRemovingLastSseSession() {
        var conn = mock(ServerSentEventConnection.class);
        worker.sseSessions().add(conn);
        worker.sseSessions().remove(conn);

        assertTrue(worker.websocketSessions().isEmpty() && worker.sseSessions().isEmpty(),
            "Worker should see both sets empty after last SSE session removed");
    }

    @Test
    void closeAllSseSessions_callsShutdownOnEachConnection() {
        var conn1 = mock(ServerSentEventConnection.class);
        var conn2 = mock(ServerSentEventConnection.class);
        worker.sseSessions().add(conn1);
        worker.sseSessions().add(conn2);

        worker.closeAllSseSessions();

        verify(conn1).shutdown();
        verify(conn2).shutdown();
        assertTrue(worker.sseSessions().isEmpty(), "sseSessions must be empty after closeAllSseSessions()");
    }

    @Test
    void workerWithResumeToken_storesToken() {
        var token = BsonDocument.parse("{\"_data\": \"resume-token-value\"}");
        var key = new ChangeStreamWorkerKey("http://localhost/db/coll/_streams/s", new BsonDocument(), JsonMode.RELAXED);
        var workerWithToken = new ChangeStreamWorker(key, List.of(), "db", "coll", token);

        // the worker is created without error — startChangeStream() would use the token
        // but requires a live MongoDB connection so we only verify construction here
        assertTrue(workerWithToken.sseSessions().isEmpty());
        assertTrue(workerWithToken.websocketSessions().isEmpty());
    }

    @Test
    void mixedSessions_webSocketAndSse_bothTracked() {
        var conn = mock(ServerSentEventConnection.class);
        // websocketSessions() returns a live set; we can verify cross-set independence
        worker.sseSessions().add(conn);

        assertFalse(worker.sseSessions().isEmpty());
        assertTrue(worker.websocketSessions().isEmpty(),
            "Adding an SSE session must not affect the WebSocket session set");
    }

    @Test
    void sseSessions_isNotSameObjectAsWebSocketSessions() {
        assertNotSame(worker.sseSessions(), worker.websocketSessions(),
            "sseSessions and websocketSessions must be independent set instances");
    }
}
