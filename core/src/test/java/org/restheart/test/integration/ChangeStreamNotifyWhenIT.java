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

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kong.unirest.Unirest;

/**
 * Integration tests for the {@code notify_when} per-client dispatch predicate.
 *
 * <p>Two SSE clients connect to the same stream URI with different {@code ?tid=} query
 * parameters. Documents inserted with different {@code tenantId} values must be routed
 * only to the matching client, proving both per-tenant filtering and shared-cursor
 * behaviour.
 */
public class ChangeStreamNotifyWhenIT extends AbstactIT {

    private static final String BASE      = "http://localhost:8080";
    private static final String TEST_DB   = BASE + "/test-cs-notify-when";
    private static final String TEST_COLL = TEST_DB + "/coll";
    // stream with notify_when: fullDocument.tenantId must equal ?tid param
    private static final String STREAM_URI = TEST_COLL + "/_streams/by-tenant";

    private static final String ADMIN_BASIC = "Basic " +
            Base64.getEncoder().encodeToString("admin:secret".getBytes());

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeEach
    void setup() throws Exception {
        Unirest.put(TEST_DB)
               .basicAuth("admin", "secret")
               .contentType("application/json")
               .body("{}")
               .asEmpty();

        var resp = Unirest.put(TEST_COLL)
               .basicAuth("admin", "secret")
               .contentType("application/json")
               .body("""
                   {
                     "streams": [{
                       "uri": "by-tenant",
                       "stages": [],
                       "notify_when": {
                         "fullDocument::tenantId": { "$var": "tid" }
                       }
                     }]
                   }
                   """)
               .asEmpty();

        assertTrue(resp.getStatus() == 200 || resp.getStatus() == 201,
                "Collection setup failed: " + resp.getStatus());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Opens an SSE connection and collects all non-blank lines until the given
     * deadline, then closes the stream. Used for both positive and negative
     * assertions in the same test.
     */
    private List<String> collectSseLines(HttpRequest req, long durationMs) throws Exception {
        var resp   = CLIENT.send(req, BodyHandlers.ofInputStream());
        var is     = resp.body();
        var lines  = Collections.synchronizedList(new ArrayList<String>());

        var reader = CompletableFuture.runAsync(() -> {
            try (var br = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.isBlank()) lines.add(line);
                }
            } catch (Exception ignored) {}
        });

        // collect for the specified window, then close
        Thread.sleep(durationMs);
        is.close();
        reader.orTimeout(1, TimeUnit.SECONDS);

        return new ArrayList<>(lines);
    }

    private HttpRequest sseFor(String tenantId) {
        return HttpRequest.newBuilder()
                .uri(URI.create(STREAM_URI + "?tid=" + tenantId))
                .header("Accept", "text/event-stream")
                .header("Authorization", ADMIN_BASIC)
                .build();
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    /**
     * Two clients with different {@code tid} values receive only their own events.
     *
     * <p>Both clients share one MongoDB cursor (same {@code ChangeStreamWorkerKey});
     * the {@code notify_when} predicate dispatches each event to the matching client only.
     */
    @Test
    void eachClientReceivesOnlyItsOwnEvents() throws Exception {
        var linesA = Collections.synchronizedList(new ArrayList<String>());
        var linesB = Collections.synchronizedList(new ArrayList<String>());

        // Open both SSE streams in background, collecting lines for the whole test window
        var futureA = CompletableFuture.supplyAsync(() -> {
            try { return collectSseLines(sseFor("tenant-A"), 9_000); }
            catch (Exception e) { return List.<String>of(); }
        });
        var futureB = CompletableFuture.supplyAsync(() -> {
            try { return collectSseLines(sseFor("tenant-B"), 9_000); }
            catch (Exception e) { return List.<String>of(); }
        });

        Thread.sleep(1_500); // let both connections and the shared worker settle

        // Insert a document for tenant-A
        Unirest.post(TEST_COLL)
               .basicAuth("admin", "secret")
               .contentType("application/json")
               .body("{\"tenantId\": \"tenant-A\", \"msg\": \"for-A\"}")
               .asEmpty();

        Thread.sleep(1_500);

        // Insert a document for tenant-B
        Unirest.post(TEST_COLL)
               .basicAuth("admin", "secret")
               .contentType("application/json")
               .body("{\"tenantId\": \"tenant-B\", \"msg\": \"for-B\"}")
               .asEmpty();

        Thread.sleep(1_500);

        var collectedA = futureA.get(12, TimeUnit.SECONDS);
        var collectedB = futureB.get(12, TimeUnit.SECONDS);

        // Client A must have received the tenant-A event
        assertTrue(collectedA.stream().anyMatch(l -> l.contains("tenant-A")),
                "Client-A must receive the tenant-A insert event; got: " + collectedA);

        // Client A must NOT have received the tenant-B event
        assertFalse(collectedA.stream().anyMatch(l -> l.contains("tenant-B")),
                "Client-A must NOT receive the tenant-B event; got: " + collectedA);

        // Client B must have received the tenant-B event
        assertTrue(collectedB.stream().anyMatch(l -> l.contains("tenant-B")),
                "Client-B must receive the tenant-B insert event; got: " + collectedB);

        // Client B must NOT have received the tenant-A event
        assertFalse(collectedB.stream().anyMatch(l -> l.contains("tenant-A")),
                "Client-B must NOT receive the tenant-A event; got: " + collectedB);
    }

    /**
     * A client without a bound variable receives all events (pass-through mode).
     *
     * <p>When a client connects without the {@code ?tid=} parameter, the evaluator
     * cannot bind the variable and passes through all events as a broadcast.
     */
    @Test
    void clientWithoutBoundVarReceivesAllEvents() throws Exception {
        // Connect without ?tid — no bound variable → pass-through
        var reqAll = HttpRequest.newBuilder()
                .uri(URI.create(STREAM_URI))
                .header("Accept", "text/event-stream")
                .header("Authorization", ADMIN_BASIC)
                .build();

        var future = CompletableFuture.supplyAsync(() -> {
            try { return collectSseLines(reqAll, 7_000); }
            catch (Exception e) { return List.<String>of(); }
        });

        Thread.sleep(1_500);

        Unirest.post(TEST_COLL)
               .basicAuth("admin", "secret")
               .contentType("application/json")
               .body("{\"tenantId\": \"tenant-X\", \"msg\": \"broadcast\"}")
               .asEmpty();

        Thread.sleep(1_500);

        Unirest.post(TEST_COLL)
               .basicAuth("admin", "secret")
               .contentType("application/json")
               .body("{\"tenantId\": \"tenant-Y\", \"msg\": \"broadcast\"}")
               .asEmpty();

        Thread.sleep(1_000);

        var lines = future.get(10, TimeUnit.SECONDS);

        assertTrue(lines.stream().anyMatch(l -> l.contains("tenant-X")),
                "Unbound client must receive tenant-X event; got: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("tenant-Y")),
                "Unbound client must receive tenant-Y event; got: " + lines);
    }
}
