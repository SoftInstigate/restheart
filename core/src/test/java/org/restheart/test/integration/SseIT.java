/*-
 * ========================LICENSE_START=================================
 * restheart-core
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
package org.restheart.test.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for Server-Sent Events support.
 *
 * <p>Requires a running RESTHeart instance on {@code localhost:8080} with the
 * {@code restheart-test-plugins} JAR in its plugins directory.
 * The tests are standalone — no MongoDB connection is needed.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class SseIT {

    private static final String BASE = "http://localhost:8080";
    private static final String OPEN_URI   = BASE + "/test-sse";
    private static final String SECURE_URI = BASE + "/test-sse-secure";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Basic-auth header value for admin:secret */
    private static final String ADMIN_BASIC = "Basic " +
            Base64.getEncoder().encodeToString("admin:secret".getBytes());

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Opens an SSE connection and reads non-empty lines until {@code count}
     * lines have been collected or the {@code timeoutSec} deadline is reached.
     * The connection is closed after reading.
     */
    private List<String> readLines(HttpRequest req, int count, int timeoutSec) throws Exception {
        var resp = CLIENT.send(req, BodyHandlers.ofInputStream());
        var is   = resp.body();

        var future = new CompletableFuture<List<String>>();
        Thread.ofVirtual().start(() -> {
            var lines = new ArrayList<String>();
            try (var reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null && lines.size() < count) {
                    if (!line.isBlank()) lines.add(line);
                }
                future.complete(lines);
            } catch (Exception e) {
                future.complete(lines);  // return what we got so far
            }
        });

        try {
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } finally {
            try { is.close(); } catch (Exception ignored) {}
        }
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void contentTypeIsTextEventStream() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(OPEN_URI))
                .header("Accept", "text/event-stream")
                .build();

        var resp = CLIENT.send(req, BodyHandlers.ofInputStream());
        try {
            assertEquals(200, resp.statusCode(), "SSE endpoint must return 200");
            assertTrue(
                resp.headers().firstValue("content-type").orElse("").contains("text/event-stream"),
                "Content-Type must contain text/event-stream"
            );
        } finally {
            resp.body().close();
        }
    }

    @Test
    public void clientReceivesDataEvents() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(OPEN_URI))
                .header("Accept", "text/event-stream")
                .build();

        var lines = readLines(req, 4, 5);

        assertTrue(lines.stream().anyMatch(l -> l.startsWith("data:")),
                "Must receive at least one data: line; got: " + lines);
    }

    @Test
    public void lastEventIdIsForwardedToOnConnect() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(OPEN_URI))
                .header("Accept", "text/event-stream")
                .header("Last-Event-ID", "test-resume-id-42")
                .build();

        var lines = readLines(req, 6, 5);

        assertTrue(lines.stream().anyMatch(l -> l.contains("last-event-id-echo")),
                "Must receive event: last-event-id-echo; got: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("test-resume-id-42")),
                "Must receive echoed Last-Event-ID value; got: " + lines);
    }

    @Test
    public void unauthenticatedRequestToSecuredEndpointReturns401() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(SECURE_URI))
                .header("Accept", "text/event-stream")
                .build();

        var resp = CLIENT.send(req, BodyHandlers.discarding());
        assertEquals(401, resp.statusCode(),
                "Unauthenticated request to secure SSE endpoint must return 401");
    }

    @Test
    public void authenticatedRequestToSecuredEndpointSucceeds() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(SECURE_URI))
                .header("Accept", "text/event-stream")
                .header("Authorization", ADMIN_BASIC)
                .build();

        var lines = readLines(req, 2, 5);

        assertTrue(lines.stream().anyMatch(l -> l.contains("auth")),
                "Must receive 'auth' event after successful auth; got: " + lines);
    }

    @Test
    public void reconnectAfterDisconnectReceivesNewStream() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(OPEN_URI))
                .header("Accept", "text/event-stream")
                .build();

        // first connection — read 3 lines to cover id+event+data of one tick event
        var first = readLines(req, 3, 5);
        assertTrue(first.stream().anyMatch(l -> l.startsWith("data:")),
                "First connection must receive data events");

        // second connection — server must accept a fresh connection
        var second = readLines(req, 3, 5);
        assertTrue(second.stream().anyMatch(l -> l.startsWith("data:")),
                "Second connection must receive data events after reconnect");
    }
}
