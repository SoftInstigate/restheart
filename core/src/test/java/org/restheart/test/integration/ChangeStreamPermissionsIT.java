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
 * Since 9.16 change streams do not apply the {@code readFilter} and {@code projectResponse} of the
 * caller's permission (#810): the stream segregates the events itself, in its stages or with
 * {@code notify_when} on a {@code @user} variable the server resolves for the session (#807), on a
 * cursor shared by every client of the stream. A stream that does not segregate visibly, under a
 * permission with a {@code readFilter}, is refused, so that an upgrade does not silently send every
 * event to everyone.
 *
 * <p>aclowner1 and aclowner2 share the {@code aclreader} role, whose permission on
 * {@code /test-cs-acl} carries {@code readFilter: {"owner": "@user.userid"}} and
 * {@code projectResponse: {"secret": 0}} (see {@code etc/conf-overrides.yml}). They are accounts of
 * the file realm: they have {@code userid}, and no {@code _id}.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class ChangeStreamPermissionsIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String TEST_DB = BASE + "/test-cs-acl";
    private static final String TEST_COLL = TEST_DB + "/coll";
    private static final String STREAMS = TEST_COLL + "/_streams/";

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

        var streams = """
                {"streams": [
                  {"uri": "all", "stages": []},
                  {"uri": "mine", "stages": [], "notify_when": {"fullDocument::owner": {"$var": "@user.userid"}}},
                  {"uri": "everything", "stages": [], "segregation": "none"},
                  {"uri": "byId", "stages": [], "notify_when": {"fullDocument::owner": {"$var": "@user._id"}}}
                ]}""";

        var status = send("PUT", TEST_COLL, streams);
        assertTrue(status == 200 || status == 201, "Collection setup failed with status " + status);
    }

    /** The status of opening {@code stream} as {@code user}, and the start of its body. */
    private static String open(String user, String stream) throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(STREAMS + stream))
                .header("Accept", "text/event-stream")
                .header("Authorization", basic(user))
                .timeout(Duration.ofSeconds(5))
                .build();

        var resp = CLIENT.send(req, BodyHandlers.ofInputStream());
        var status = resp.statusCode();
        var body = status == 200 ? "" : new String(resp.body().readNBytes(2_000));
        resp.body().close();
        return status + " " + body;
    }

    /**
     * Opens an SSE connection to {@code stream} as {@code user} and collects its non-blank
     * {@code data:} lines until {@code timeoutSec} elapses.
     */
    private CompletableFuture<List<String>> subscribe(String user, String stream, int timeoutSec) {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(STREAMS + stream))
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
    void aStreamThatDoesNotSegregateIsRefusedUnderAReadFilter() throws Exception {
        var answer = open("aclowner1", "all");

        assertTrue(answer.startsWith("403"), "a stream with no segregation must be refused; got: " + answer);
        // what to write goes to the log, not to the client
        assertTrue(answer.contains("not allowed") && !answer.contains("readFilter"),
                "the client must get a short refusal that says nothing of the permission; got: " + answer);
    }

    @Test
    void notifyWhenOnTheUserSendsEachUserOnlyTheirDocuments() throws Exception {
        var alice = subscribe("aclowner1", "mine", 8);
        // a query parameter cannot pick another user's events: the variable is the server's
        var bob = subscribe("aclowner2", "mine?owner=aclowner1&userid=aclowner1", 8);

        // let the SSE connections and the shared ChangeStreamWorker settle
        Thread.sleep(1_500);

        send("POST", TEST_COLL, "{\"owner\": \"aclowner2\", \"marker\": \"bob-doc\"}");
        send("POST", TEST_COLL, "{\"marker\": \"orphan-doc\"}");
        send("POST", TEST_COLL, "{\"owner\": \"aclowner1\", \"marker\": \"alice-doc\"}");

        var aliceLines = alice.get(12, TimeUnit.SECONDS);
        var bobLines = bob.get(12, TimeUnit.SECONDS);

        assertTrue(aliceLines.stream().anyMatch(l -> l.contains("alice-doc")),
                "aclowner1 must receive the event of its own document; got: " + aliceLines);
        assertFalse(aliceLines.stream().anyMatch(l -> l.contains("bob-doc") || l.contains("orphan-doc")),
                "aclowner1 must receive only its own documents; got: " + aliceLines);
        assertTrue(bobLines.stream().anyMatch(l -> l.contains("bob-doc")),
                "aclowner2 must receive the event of its own document; got: " + bobLines);
        assertFalse(bobLines.stream().anyMatch(l -> l.contains("alice-doc") || l.contains("orphan-doc")),
                "aclowner2 must receive only its own documents, whatever it passes; got: " + bobLines);
    }

    @Test
    void segregationNoneSendsEveryEventAndNoPermissionProjection() throws Exception {
        var alice = subscribe("aclowner1", "everything", 8);

        Thread.sleep(1_500);

        send("POST", TEST_COLL, "{\"owner\": \"aclowner2\", \"marker\": \"bob-doc\", \"secret\": \"s-bob\"}");
        send("POST", TEST_COLL, "{\"owner\": \"aclowner1\", \"marker\": \"alice-doc\"}");

        var lines = alice.get(12, TimeUnit.SECONDS);

        assertTrue(lines.stream().anyMatch(l -> l.contains("alice-doc")) && lines.stream().anyMatch(l -> l.contains("bob-doc")),
                "a stream declaring segregation none must receive every event; got: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("s-bob")),
                "the projectResponse of the permission is not applied to change streams; got: " + lines);
    }

    @Test
    void anUnboundUserVariableRefusesTheConnection() throws Exception {
        // accounts of the file realm have no _id
        var answer = open("aclowner1", "byId");

        assertTrue(answer.startsWith("400"), "an unbound @user._id must refuse the connection; got: " + answer);
    }
}
