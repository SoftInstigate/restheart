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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kong.unirest.Unirest;

/**
 * Integration tests for SSE support on MongoDB Change Streams.
 *
 * <p>Requires a running RESTHeart instance connected to a MongoDB replica set.
 * The test database is prefixed with {@code test-} so it is automatically
 * cleaned up after the suite.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class ChangeStreamSseIT extends AbstactIT {

    private static final String BASE     = "http://localhost:8080";
    private static final String TEST_DB  = BASE + "/test-cs-sse";
    private static final String TEST_COLL = TEST_DB + "/coll";
    private static final String STREAM_URI = TEST_COLL + "/_streams/cs";

    private static final String ADMIN_BASIC = "Basic " +
            Base64.getEncoder().encodeToString("admin:secret".getBytes());

    private static final HttpClient SSE_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeEach
    void setupCollection() throws Exception {
        // create the test database
        Unirest.put(TEST_DB)
               .basicAuth("admin", "secret")
               .contentType("application/json")
               .body("{}")
               .asEmpty();

        // create the collection with a change stream definition
        var resp = Unirest.put(TEST_COLL)
               .basicAuth("admin", "secret")
               .contentType("application/json")
               .body("{\"streams\": [{\"stages\": [], \"uri\": \"cs\"}]}")
               .asEmpty();

        assertTrue(resp.getStatus() == 200 || resp.getStatus() == 201,
            "Collection setup failed with status " + resp.getStatus());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private HttpRequest sseRequest() {
        return HttpRequest.newBuilder()
                .uri(URI.create(STREAM_URI))
                .header("Accept", "text/event-stream")
                .header("Authorization", ADMIN_BASIC)
                .build();
    }

    /**
     * Opens an SSE connection and collects non-blank lines until {@code count}
     * lines have been read or the {@code timeoutSec} deadline is reached.
     * Returns whatever was collected so far if the deadline expires.
     */
    private List<String> readSseLines(HttpRequest req, int count, int timeoutSec) throws Exception {
        var resp = SSE_CLIENT.send(req, BodyHandlers.ofInputStream());
        InputStream is = resp.body();

        // shared so partial results survive a timeout
        var lines = Collections.synchronizedList(new ArrayList<String>());
        var future = new CompletableFuture<List<String>>();
        Thread.ofVirtual().start(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null && lines.size() < count) {
                    if (!line.isBlank()) lines.add(line);
                }
                future.complete(lines);
            } catch (Exception e) {
                future.complete(lines);
            }
        });

        try {
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return new ArrayList<>(lines); // return whatever arrived before the deadline
        } finally {
            try { is.close(); } catch (Exception ignored) {} // NOSONAR
        }
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void plainGetWithoutUpgradeOrSseHeaderReturns400() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(STREAM_URI))
                .header("Authorization", ADMIN_BASIC)
                .GET()
                .build();

        var resp = SSE_CLIENT.send(req, BodyHandlers.discarding());
        assertEquals(400, resp.statusCode(),
            "Plain GET to a change stream endpoint must return 400");
    }

    @Test
    void sseConnectionReceivesInsertEvent() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(STREAM_URI))
                .header("Accept", "text/event-stream")
                .header("Authorization", ADMIN_BASIC)
                .build();

        // open SSE stream in background; each change event produces 2 non-blank lines
        // (event:<type> + data:<json>) because no id is set
        var linesFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return readSseLines(req, 3, 10);
            } catch (Exception e) {
                return List.<String>of();
            }
        });

        // small delay to let the SSE connection and ChangeStreamWorker settle
        Thread.sleep(1_500);

        // insert a document to trigger a change event
        Unirest.post(TEST_COLL)
               .basicAuth("admin", "secret")
               .contentType("application/json")
               .body("{\"sse\": true, \"value\": 42}")
               .asEmpty();

        var lines = linesFuture.get(12, TimeUnit.SECONDS);

        assertTrue(lines.stream().anyMatch(l -> l.startsWith("data:")),
            "SSE connection must receive a 'data:' line after document insert; got: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("insert")),
            "Received event must contain operationType 'insert'; got: " + lines);
    }

    @Test
    void sseResponseContentTypeIsTextEventStream() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(STREAM_URI))
                .header("Accept", "text/event-stream")
                .header("Authorization", ADMIN_BASIC)
                .build();

        var resp = SSE_CLIENT.send(req, BodyHandlers.ofInputStream());
        try {
            assertEquals(200, resp.statusCode(),
                "SSE upgrade to change stream must return 200");
            assertTrue(
                resp.headers().firstValue("content-type").orElse("").contains("text/event-stream"),
                "Content-Type must contain text/event-stream");
        } finally {
            resp.body().close();
        }
    }

    // -----------------------------------------------------------------------
    // New tests for issue #628
    // -----------------------------------------------------------------------

    @Test
    void unauthenticatedSseRequestReturns401() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(STREAM_URI))
                .header("Accept", "text/event-stream")
                .build(); // no Authorization header

        var resp = SSE_CLIENT.send(req, BodyHandlers.discarding());
        assertEquals(401, resp.statusCode(),
                "Unauthenticated SSE request to a change stream must return 401");
    }

    @Test
    void websocketAndSseClientsBothReceiveEvent() throws Exception {
        // SSE client in background
        var sseFuture = CompletableFuture.supplyAsync(() -> {
            try { return readSseLines(sseRequest(), 3, 12); }
            catch (Exception e) { return List.<String>of(); }
        });

        // WebSocket client
        var wsMessages = Collections.synchronizedList(new ArrayList<String>());
        var wsLatch    = new CountDownLatch(1);
        var ws = SSE_CLIENT.newWebSocketBuilder()
                .header("Authorization", ADMIN_BASIC)
                .buildAsync(URI.create(STREAM_URI.replace("http://", "ws://")),
                        new WebSocket.Listener() {
                            private final StringBuilder buf = new StringBuilder();
                            @Override
                            public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
                                buf.append(data);
                                if (last) {
                                    wsMessages.add(buf.toString());
                                    buf.setLength(0);
                                    wsLatch.countDown();
                                }
                                w.request(1);
                                return null;
                            }
                        }).join();

        Thread.sleep(1_500); // let both connections and the shared worker settle

        Unirest.post(TEST_COLL)
               .basicAuth("admin", "secret")
               .contentType("application/json")
               .body("{\"both\": true}")
               .asEmpty();

        var sseLines = sseFuture.get(12, TimeUnit.SECONDS);
        assertTrue(sseLines.stream().anyMatch(l -> l.contains("insert")),
                "SSE must receive the insert event; got: " + sseLines);

        assertTrue(wsLatch.await(8, TimeUnit.SECONDS),
                "WebSocket must receive at least one event within 8 s");
        assertTrue(wsMessages.stream().anyMatch(m -> m.contains("insert")),
                "WebSocket must receive the insert event; got: " + wsMessages);

        ws.abort();
    }

    @Test
    void workerSelfTerminatesWhenAllSessionsClose() throws Exception {
        // 1. Open an SSE connection and confirm it is alive
        var resp1 = SSE_CLIENT.send(sseRequest(), BodyHandlers.ofInputStream());
        assertEquals(200, resp1.statusCode(), "First SSE connection must return 200");

        Thread.sleep(1_000);
        Unirest.post(TEST_COLL).basicAuth("admin", "secret")
               .contentType("application/json").body("{\"v\":1}").asEmpty();
        Thread.sleep(500);

        // 2. Close the only session — the worker should self-terminate
        resp1.body().close();

        // 3. Wait for the worker's virtual thread to exit and deregister itself
        Thread.sleep(2_500);

        // 4. A fresh SSE connection must succeed and serve new events,
        //    which proves the old worker was removed and a new one was created.
        var secondLines = CompletableFuture.supplyAsync(() -> {
            try { return readSseLines(sseRequest(), 3, 10); }
            catch (Exception e) { return List.<String>of(); }
        });
        Thread.sleep(1_000);
        Unirest.post(TEST_COLL).basicAuth("admin", "secret")
               .contentType("application/json").body("{\"v\":2}").asEmpty();

        var lines = secondLines.get(12, TimeUnit.SECONDS);
        assertTrue(lines.stream().anyMatch(l -> l.contains("insert")),
                "Fresh SSE connection after worker cleanup must receive events; got: " + lines);
    }

    @Test
    void lastEventIdResumesContinuesFromToken() throws Exception {
        // 1. First connection: receive one event and capture its resume token (the SSE id: field)
        var firstLines = CompletableFuture.supplyAsync(() -> {
            try { return readSseLines(sseRequest(), 3, 12); } // event: + data: + id:
            catch (Exception e) { return List.<String>of(); }
        });

        Thread.sleep(1_000);
        Unirest.post(TEST_COLL).basicAuth("admin", "secret")
               .contentType("application/json").body("{\"seq\":1}").asEmpty();

        var lines1 = firstLines.get(15, TimeUnit.SECONDS);
        var resumeToken = lines1.stream()
                .filter(l -> l.startsWith("id:"))
                .map(l -> l.substring(3).trim())
                .findFirst()
                .orElse(null);

        assertNotNull(resumeToken,
                "SSE change event must include an 'id:' resume token; got lines: " + lines1);

        Thread.sleep(2_500); // wait for first worker to self-terminate (connection closed by readSseLines)

        // 2. Reconnect with Last-Event-ID — stream should resume AFTER the first event
        var resumeReq = HttpRequest.newBuilder()
                .uri(URI.create(STREAM_URI))
                .header("Accept", "text/event-stream")
                .header("Authorization", ADMIN_BASIC)
                .header("Last-Event-ID", resumeToken)
                .build();

        var secondLines = CompletableFuture.supplyAsync(() -> {
            try { return readSseLines(resumeReq, 3, 12); }
            catch (Exception e) { return List.<String>of(); }
        });

        Thread.sleep(1_000);
        Unirest.post(TEST_COLL).basicAuth("admin", "secret")
               .contentType("application/json").body("{\"seq\":2}").asEmpty();

        var lines2 = secondLines.get(15, TimeUnit.SECONDS);
        assertTrue(lines2.stream().anyMatch(l -> l.contains("insert")),
                "Resumed SSE connection must receive new events; got: " + lines2);
        assertTrue(lines2.stream().noneMatch(l -> l.contains("\"seq\":1")),
                "Resumed stream must NOT replay seq:1 (already seen); got: " + lines2);
    }
}
