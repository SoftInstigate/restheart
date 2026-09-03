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
 * Integration test for #616: a change stream with an {@code mcp} block, exercised end-to-end
 * through {@code /mcp} — confirms {@code ChangeStreamMcpResourceBuilder}'s dual
 * websocket/SSE transport declaration, and that the SSE descriptor {@code how_to_call} composes
 * actually receives a real change event (not just shape-checked).
 */
public class McpMongoChangeStreamIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String TEST_DB = BASE + "/test-mcp-changestream";
    private static final String TEST_COLL = TEST_DB + "/notifications";
    private static final String STREAM_URI = TEST_COLL + "/_streams/highValue";

    private static final String ADMIN_BASIC = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());

    private static final HttpClient SSE_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private McpTestClient mcp;

    @BeforeEach
    void setupCollectionAndSession() throws Exception {
        Unirest.put(TEST_DB).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();

        var resp = Unirest.put(TEST_COLL)
                .basicAuth("admin", "secret")
                .contentType("application/json")
                .body("""
                        {
                          "streams": [
                            {
                              "uri": "highValue",
                              "stages": [ { "$match": { "fullDocument.amount": { "$gte": { "$var": "minAmount" } } } } ],
                              "mcp": {
                                "enabled": true,
                                "description": "Notifies on high-value events.",
                                "event_type": "Insert events where amount >= minAmount"
                              }
                            }
                          ]
                        }
                        """)
                .asEmpty();
        assertTrue(resp.getStatus() == 200 || resp.getStatus() == 201, "collection setup failed with status " + resp.getStatus());

        // see McpGraphqlAppIT: wait past CachedResourceLookup's TTL so this class's own
        // just-created resource isn't served from a stale cache entry
        Thread.sleep(1_500);

        mcp = new McpTestClient(BASE, ADMIN_BASIC);
        mcp.initialize();
    }

    @Test
    void context_declaresBothWebSocketAndSseTransports() throws Exception {
        var context = mcp.callTool("list_apis", "{\"resource\": \"" + STREAM_URI + "\"}");

        assertEquals("change-stream", context.getString("kind").getValue());
        assertEquals("Insert events where amount >= minAmount", context.getString("event_type").getValue());

        var transports = context.getArray("transports").stream().map(v -> v.asDocument()).toList();
        assertEquals(2, transports.size());

        var websocket = transports.stream().filter(t -> "websocket".equals(t.getString("name").getValue())).findFirst().orElseThrow();
        assertEquals("wss", websocket.getString("url_scheme").getValue());

        var sse = transports.stream().filter(t -> "sse".equals(t.getString("name").getValue())).findFirst().orElseThrow();
        assertEquals("text/event-stream", sse.getString("media_type").getValue());
    }

    @Test
    void howToCallSubscribe_websocketDescriptor_usesWsScheme() throws Exception {
        var descriptor = mcp.callTool("how_to_call",
                "{\"resource\": \"" + STREAM_URI + "\", \"action\": \"subscribe\", \"transport\": \"websocket\", \"args\": {\"avars\": {\"minAmount\": 100}}}");

        assertEquals("websocket", descriptor.getString("transport").getValue());
        assertTrue(descriptor.getString("url").getValue().startsWith("ws://"), "descriptor url must use ws:// scheme; got: " + descriptor.toJson());
    }

    @Test
    void howToCallSubscribe_sseDescriptor_actuallyReceivesRealEvent() throws Exception {
        var descriptor = mcp.callTool("how_to_call",
                "{\"resource\": \"" + STREAM_URI + "\", \"action\": \"subscribe\", \"transport\": \"sse\", \"args\": {\"avars\": {\"minAmount\": 100}}}");

        assertEquals("sse", descriptor.getString("transport").getValue());
        assertEquals("GET", descriptor.getString("method").getValue());
        var url = descriptor.getString("url").getValue();

        var req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "text/event-stream")
                .header("Authorization", ADMIN_BASIC)
                .build();

        var linesFuture = CompletableFuture.supplyAsync(() -> {
            try {
                // this stream's events carry a resume token, so each one is 3 non-blank lines
                // (event: + data: + id:), not 2 — confirmed live: capturing only 2 got event:/id:
                // and missed the data: line the assertion below actually needs
                return readSseLines(req, 3, 10);
            } catch (Exception e) {
                return List.<String>of();
            }
        });

        Thread.sleep(1_500); // let the SSE connection and ChangeStreamWorker settle

        Unirest.post(TEST_COLL).basicAuth("admin", "secret").contentType("application/json")
                .body("{\"amount\": 150}").asEmpty();

        var lines = linesFuture.get(12, TimeUnit.SECONDS);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("data:") && l.contains("insert")),
                "composed SSE descriptor must actually receive the real insert event; got: " + lines);
    }

    private static List<String> readSseLines(HttpRequest req, int count, int timeoutSec) throws Exception {
        var resp = SSE_CLIENT.send(req, BodyHandlers.ofInputStream());
        InputStream is = resp.body();

        var lines = Collections.synchronizedList(new ArrayList<String>());
        var future = new CompletableFuture<List<String>>();
        Thread.ofVirtual().start(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null && lines.size() < count) {
                    if (!line.isBlank()) {
                        lines.add(line);
                    }
                }
                future.complete(lines);
            } catch (Exception e) {
                future.complete(lines);
            }
        });

        try {
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return new ArrayList<>(lines);
        } finally {
            try {
                is.close();
            } catch (Exception ignored) {
                // NOSONAR
            }
        }
    }
}
