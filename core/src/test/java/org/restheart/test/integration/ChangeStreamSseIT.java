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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
                return readSseLines(req, 2, 10);
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
}
