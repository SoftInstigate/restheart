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

/**
 * The {@code readFilter} and {@code projectResponse} of the caller's permission apply to a change
 * stream as they do to a REST {@code GET}: a user whose reads are restricted to their own
 * documents must not receive the change events of anybody else's, nor the properties the
 * permission hides.
 *
 * <p>aclowner1 and aclowner2 share the {@code aclreader} role, whose permission on
 * {@code /test-cs-acl} carries {@code readFilter: {"owner": "@user.userid"}} and
 * {@code projectResponse: {"secret": 0}} (see {@code etc/conf-overrides.yml}).
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class ChangeStreamPermissionsIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String TEST_DB = BASE + "/test-cs-acl";
    private static final String TEST_COLL = TEST_DB + "/coll";
    private static final String STREAM_URI = TEST_COLL + "/_streams/all";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static String basic(String user) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":secret").getBytes());
    }

    private static int send(String method, String uri, String body) throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Authorization", basic("admin"))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();

        return CLIENT.send(req, BodyHandlers.discarding()).statusCode();
    }

    @BeforeEach
    void setupCollection() throws Exception {
        send("PUT", TEST_DB, "{}");

        // a stream with no stages of its own: every event would reach every subscriber
        var status = send("PUT", TEST_COLL, "{\"streams\": [{\"stages\": [], \"uri\": \"all\"}]}");
        assertTrue(status == 200 || status == 201, "Collection setup failed with status " + status);
    }

    /**
     * Opens an SSE connection as {@code user} and collects its non-blank {@code data:} lines
     * until {@code timeoutSec} elapses.
     */
    private CompletableFuture<List<String>> subscribe(String user, int timeoutSec) {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(STREAM_URI))
                .header("Accept", "text/event-stream")
                .header("Authorization", basic(user))
                .build();

        var lines = Collections.synchronizedList(new ArrayList<String>());

        return CompletableFuture.supplyAsync(() -> {
            try {
                var resp = CLIENT.send(req, BodyHandlers.ofInputStream());
                assertEquals(200, resp.statusCode(), "SSE subscription as " + user);

                var is = resp.body();
                var reader = Thread.ofVirtual().start(() -> {
                    try (var br = new BufferedReader(new InputStreamReader(is))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (line.startsWith("data:")) {
                                lines.add(line);
                            }
                        }
                    } catch (Exception ignored) {
                        // closed at the deadline
                    }
                });

                reader.join(Duration.ofSeconds(timeoutSec));
                is.close();
                return new ArrayList<>(lines);
            } catch (Exception e) {
                return new ArrayList<>(lines);
            }
        });
    }

    @Test
    void streamDeliversOnlyTheDocumentsTheReadFilterAllows() throws Exception {
        var alice = subscribe("aclowner1", 8);

        // let the SSE connection and its ChangeStreamWorker settle
        Thread.sleep(1_500);

        send("POST", TEST_COLL, "{\"owner\": \"aclowner2\", \"marker\": \"bob-doc\"}");
        send("POST", TEST_COLL, "{\"marker\": \"orphan-doc\"}");
        send("POST", TEST_COLL, "{\"owner\": \"aclowner1\", \"marker\": \"alice-doc\"}");

        var lines = alice.get(12, TimeUnit.SECONDS);

        assertTrue(lines.stream().anyMatch(l -> l.contains("alice-doc")),
                "aclowner1 must receive the event of its own document; got: " + lines);
        assertFalse(lines.stream().anyMatch(l -> l.contains("bob-doc")),
                "aclowner1 must not receive the event of aclowner2's document; got: " + lines);
        assertFalse(lines.stream().anyMatch(l -> l.contains("orphan-doc")),
                "aclowner1 must not receive the event of a document without owner; got: " + lines);
    }

    @Test
    void streamHidesThePropertiesTheProjectResponseExcludes() throws Exception {
        var alice = subscribe("aclowner1", 8);

        Thread.sleep(1_500);

        var id = "proj-" + System.nanoTime();
        send("POST", TEST_COLL, "{\"_id\": \"" + id + "\", \"owner\": \"aclowner1\", \"marker\": \"proj-insert\", \"secret\": {\"a\": \"s-insert\"}}");
        // updatedFields gets the literal key "secret.a", which a $project cannot address
        send("PATCH", TEST_COLL + "/" + id, "{\"$set\": {\"secret.a\": \"s-update\", \"note\": \"proj-update\"}}");

        var lines = alice.get(12, TimeUnit.SECONDS);

        assertTrue(lines.stream().anyMatch(l -> l.contains("proj-insert")),
                "aclowner1 must receive the insert event; got: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("proj-update")),
                "aclowner1 must receive the update event; got: " + lines);
        assertFalse(lines.stream().anyMatch(l -> l.contains("s-insert") || l.contains("s-update")),
                "the events must not carry the excluded property; got: " + lines);
    }
}
