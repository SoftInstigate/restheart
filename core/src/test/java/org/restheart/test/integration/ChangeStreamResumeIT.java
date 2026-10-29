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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restheart.mongodb.RHMongoClients;

import com.mongodb.MongoCommandException;

/**
 * A change stream keeps delivering events, none lost and none repeated, when a {@code getMore}
 * fails with a resumable error.
 *
 * <p>After an election, MongoDB 9.0 returns {@code InterruptedDueToReplStateChange} (11602) from
 * the {@code getMore} of a change stream opened with read preference primary or secondary. The
 * driver should resume from the last resume token by itself. This test makes the server return
 * that error with the {@code failCommand} fail point, so it needs a mongod started with
 * {@code --setParameter enableTestCommands=1}, as the {@code mongodb-classic} profile does. It is
 * skipped where the fail point is not available.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class ChangeStreamResumeIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String TEST_DB = BASE + "/test-cs-resume";
    private static final String TEST_COLL = TEST_DB + "/coll";
    private static final String STREAM_URI = TEST_COLL + "/_streams/cs";

    /** The application name RESTHeart's MongoClient uses: the fail point only hits its connections. */
    private static final String RESTHEART_APP_NAME = "restheart (sync)";

    private static final String ADMIN_BASIC = "Basic "
            + Base64.getEncoder().encodeToString((ADMIN_ID + ":" + ADMIN_PWD).getBytes());

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private InputStream sseBody;

    @BeforeAll
    static void requireFailCommand() {
        assumeTrue(failCommandAvailable(),
                "the failCommand fail point needs a mongod started with --setParameter enableTestCommands=1");
    }

    @BeforeEach
    void setupCollection() throws Exception {
        assertCreated(put(TEST_DB, "{}"));
        assertCreated(put(TEST_COLL, "{\"streams\": [{\"stages\": [], \"uri\": \"cs\"}]}"));
    }

    @AfterEach
    void cleanup() throws Exception {
        failPoint("off", null);
        if (sseBody != null) {
            sseBody.close();
        }
    }

    @Test
    void resumableGetMoreErrorLosesAndRepeatsNoEvent() throws Exception {
        var data = openSseStream();

        awaitStreamOpen(data);

        // A: delivered before the error
        insert("resume-A");
        awaitReceived(data, "resume-A", 10);

        // the next getMore of RESTHeart's change stream fails with 11602, labelled resumable
        failPoint(new Document("times", 1), new Document("failCommands", List.of("getMore"))
                .append("errorCode", 11602)
                .append("errorLabels", List.of("ResumableChangeStreamError"))
                .append("appName", RESTHEART_APP_NAME));

        // B and C are written around the failing getMore (a getMore already in flight may still
        // deliver B). A stream re-opened from "now", instead of resumed from the last token,
        // misses the events written in between: without the resumable label it loses C.
        insert("resume-B");
        awaitReceived(data, "resume-B", 20);

        var fired = failPoint("off", null).getInteger("count", 0);
        assertTrue(fired >= 1, "the fail point never fired: the getMore error was not exercised");

        insert("resume-C");
        awaitReceived(data, "resume-C", 20);

        for (var marker : List.of("resume-A", "resume-B", "resume-C")) {
            assertEquals(1, count(data, marker),
                    "event " + marker + " must arrive exactly once; got: " + data);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static boolean failCommandAvailable() {
        try {
            failPoint("off", null);
            return true;
        } catch (MongoCommandException e) {
            return false;
        }
    }

    /**
     * Sets the failCommand fail point and returns the server's reply, whose {@code count} is the
     * number of times it fired since it was last set.
     */
    private static Document failPoint(Object mode, Document data) {
        var cmd = new Document("configureFailPoint", "failCommand").append("mode", mode);
        if (data != null) {
            cmd.append("data", data);
        }
        return RHMongoClients.mclient().getDatabase("admin").runCommand(cmd);
    }

    /** Opens the SSE stream and collects its data lines in the background. */
    private List<String> openSseStream() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(STREAM_URI))
                .header("Accept", "text/event-stream")
                .header("Authorization", ADMIN_BASIC)
                .build();

        var resp = HTTP_CLIENT.send(req, BodyHandlers.ofInputStream());
        assertEquals(200, resp.statusCode(), "opening the SSE stream failed");
        this.sseBody = resp.body();

        var data = Collections.synchronizedList(new ArrayList<String>());
        Thread.ofVirtual().start(() -> {
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(sseBody))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        data.add(line);
                    }
                }
            } catch (Exception ignored) {
                // the stream is closed in cleanup()
            }
        });
        return data;
    }

    /**
     * Inserts throwaway documents until one is delivered. The change stream opens asynchronously
     * after the SSE connection, so an event written too early is legitimately not delivered.
     */
    private void awaitStreamOpen(List<String> data) throws Exception {
        for (var i = 0; i < 10; i++) {
            insert("warmup-" + i);
            if (waitFor(data, l -> l.contains("warmup-"), Duration.ofSeconds(2))) {
                return;
            }
        }
        throw new AssertionError("the change stream never delivered an event");
    }

    private void awaitReceived(List<String> data, String marker, int seconds) throws Exception {
        assertTrue(waitFor(data, l -> l.contains(marker), Duration.ofSeconds(seconds)),
                "event " + marker + " not received within " + seconds + "s; got: " + data);
    }

    private static boolean waitFor(List<String> data, Predicate<String> p, Duration timeout) throws InterruptedException {
        var deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            synchronized (data) {
                if (data.stream().anyMatch(p)) {
                    return true;
                }
            }
            Thread.sleep(100);
        }
        return false;
    }

    private static long count(List<String> data, String marker) {
        synchronized (data) {
            return data.stream().filter(l -> l.contains("\"" + marker + "\"")).count();
        }
    }

    private void insert(String marker) throws Exception {
        var resp = HTTP_CLIENT.send(request(TEST_COLL)
                .POST(HttpRequest.BodyPublishers.ofString("{\"marker\": \"" + marker + "\"}"))
                .build(), BodyHandlers.discarding());
        assertEquals(201, resp.statusCode(), "insert of " + marker + " failed");
    }

    private HttpResponse<Void> put(String uri, String body) throws Exception {
        return HTTP_CLIENT.send(request(uri).PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
                BodyHandlers.discarding());
    }

    private static HttpRequest.Builder request(String uri) {
        return HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Authorization", ADMIN_BASIC)
                .header("Content-Type", "application/json");
    }

    private static void assertCreated(HttpResponse<Void> resp) {
        assertTrue(resp.statusCode() == 200 || resp.statusCode() == 201,
                resp.request().method() + " " + resp.request().uri() + " failed with status " + resp.statusCode());
    }
}
