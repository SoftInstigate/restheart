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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kong.unirest.Unirest;

/**
 * Integration tests for the {@code notify_when} per-client dispatch predicate.
 *
 * <p>Two SSE clients connect to the same stream URI with different {@code tid} values,
 * passed as {@code ?tid=} or as {@code ?avars={"tid":...}}. Documents inserted with
 * different {@code tenantId} values must be routed only to the matching client. A client
 * that does not bind {@code tid} is rejected: the stream fails closed.
 */
public class ChangeStreamNotifyWhenIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String TEST_DB = BASE + "/test-cs-notify-when";
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

    private HttpRequest sseFor(String tenantId) {
        return sse("?tid=" + tenantId);
    }

    /** The legacy form of the variable, which must be equivalent to {@code ?tid=}. */
    private HttpRequest sseForAvars(String tenantId) {
        return sse("?avars=" + URLEncoder.encode("{\"tid\":\"" + tenantId + "\"}", StandardCharsets.UTF_8));
    }

    private HttpRequest sse(String query) {
        return HttpRequest.newBuilder()
                .uri(URI.create(STREAM_URI + query))
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
     *
     * <p>Connections are opened synchronously on the main thread: {@code CLIENT.send()}
     * blocks until the server sends HTTP 200 headers, which means the
     * {@code ServerSentEventHandler} callback has already fired and both SSE sessions
     * are registered in the {@code ChangeStreamWorker} before any insert happens.
     * This avoids the race condition that occurs when connections are opened
     * asynchronously and a fixed sleep is used as the only synchronisation point.
     */
    @Test
    void eachClientReceivesOnlyItsOwnEvents() throws Exception {
        assertEachClientReceivesOnlyItsOwnEvents(sseFor("tenant-A"), sseFor("tenant-B"));
    }

    /** The variable passed as {@code avars} filters exactly like the plain query parameter. */
    @Test
    void avarsFormFiltersLikeThePlainParameter() throws Exception {
        assertEachClientReceivesOnlyItsOwnEvents(sseForAvars("tenant-A"), sseForAvars("tenant-B"));
    }

    /** Given both ways, the {@code avars} value wins, as for the stage variables. */
    @Test
    void avarsWinsOverThePlainParameter() throws Exception {
        var a = sse("?tid=tenant-B&avars=" + URLEncoder.encode("{\"tid\":\"tenant-A\"}", StandardCharsets.UTF_8));
        assertEachClientReceivesOnlyItsOwnEvents(a, sseFor("tenant-B"));
    }

    private void assertEachClientReceivesOnlyItsOwnEvents(HttpRequest reqA, HttpRequest reqB) throws Exception {
        // Open both SSE connections synchronously: CLIENT.send() returns only after
        // the server has sent the 200 headers, so both sessions are already registered
        // in the ChangeStreamWorker by the time we proceed.
        var respA = CLIENT.send(reqA, BodyHandlers.ofInputStream());
        var respB = CLIENT.send(reqB, BodyHandlers.ofInputStream());
        assertEquals(200, respA.statusCode(), "Client-A must be accepted");
        assertEquals(200, respB.statusCode(), "Client-B must be accepted");
        var isA = respA.body();
        var isB = respB.body();

        var linesA = Collections.synchronizedList(new ArrayList<String>());
        var linesB = Collections.synchronizedList(new ArrayList<String>());

        // Read lines asynchronously in background
        var readerA = CompletableFuture.runAsync(() -> {
            try (var br = new BufferedReader(new InputStreamReader(isA))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.isBlank()) linesA.add(line);
                }
            } catch (Exception ignored) {
            }
        });
        var readerB = CompletableFuture.runAsync(() -> {
            try (var br = new BufferedReader(new InputStreamReader(isB))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.isBlank()) linesB.add(line);
                }
            } catch (Exception ignored) {
            }
        });

        // Short settle: connections are already open; we only wait for the
        // ChangeStreamWorker virtual thread to enter its forEach loop.
        Thread.sleep(500);

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

        // Close streams — causes readLine() to return null and the reader futures to complete
        isA.close();
        isB.close();
        readerA.get(2, TimeUnit.SECONDS);
        readerB.get(2, TimeUnit.SECONDS);

        // Client A must have received the tenant-A event
        assertTrue(linesA.stream().anyMatch(l -> l.contains("tenant-A")),
                "Client-A must receive the tenant-A insert event; got: " + linesA);

        // Client A must NOT have received the tenant-B event
        assertFalse(linesA.stream().anyMatch(l -> l.contains("tenant-B")),
                "Client-A must NOT receive the tenant-B event; got: " + linesA);

        // Client B must have received the tenant-B event
        assertTrue(linesB.stream().anyMatch(l -> l.contains("tenant-B")),
                "Client-B must receive the tenant-B insert event; got: " + linesB);

        // Client B must NOT have received the tenant-A event
        assertFalse(linesB.stream().anyMatch(l -> l.contains("tenant-A")),
                "Client-B must NOT receive the tenant-A event; got: " + linesB);
    }

    /**
     * A client without a bound variable is rejected: {@code notify_when} fails closed, as an
     * unbound stage variable does, instead of broadcasting every event to it.
     */
    @Test
    void clientWithoutBoundVarIsRejected() throws Exception {
        var resp = CLIENT.send(sse(""), BodyHandlers.ofInputStream());
        resp.body().close();
        assertEquals(400, resp.statusCode(), "A client without ?tid must be rejected");
    }

    /** {@code avars} without the variable does not bind it either. */
    @Test
    void clientWithAvarsWithoutTheVarIsRejected() throws Exception {
        var resp = CLIENT.send(sse("?avars=" + URLEncoder.encode("{}", StandardCharsets.UTF_8)), BodyHandlers.ofInputStream());
        resp.body().close();
        assertEquals(400, resp.statusCode(), "A client with avars={} must be rejected");
    }
}
