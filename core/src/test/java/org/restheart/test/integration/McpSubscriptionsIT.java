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

import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kong.unirest.Unirest;

/**
 * {@code resources/subscribe} end to end (#617): a client subscribes, somebody writes, and the
 * server says so on the stream it opened for exactly that.
 *
 * <p>The notification carries no payload — it means "re-read", not "here is what changed" — so
 * what these tests pin down is that it arrives when it should, stops when the subscription does,
 * and does not arrive once per write.
 */
public class McpSubscriptionsIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String TEST_DB = BASE + "/test-mcp-subs";
    private static final String TEST_COLL = TEST_DB + "/watched";

    private static final String ADMIN_BASIC = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());

    private static final String UPDATED = "notifications/resources/updated";

    private McpTestClient mcp;
    private McpTestClient.Notifications notifications;

    @BeforeEach
    public void setUpSubscriptionFixture() throws Exception {
        Unirest.put(TEST_DB).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();

        var coll = Unirest.put(TEST_COLL).basicAuth("admin", "secret").contentType("application/json")
                .body("{ \"mcp\": { \"enabled\": true, \"description\": \"Watched collection.\" } }")
                .asEmpty();
        assertTrue(coll.getStatus() == 200 || coll.getStatus() == 201, "collection setup failed: " + coll.getStatus());

        // past CachedResourceLookup's TTL (conf-overrides sets it to 1s) so the resource exists
        Thread.sleep(1_500);

        mcp = new McpTestClient(BASE, ADMIN_BASIC);
        mcp.initialize();
        notifications = mcp.openNotificationStream();
    }

    @AfterEach
    public void closeStream() {
        if (notifications != null) {
            notifications.close();
        }
    }

    /** {@code subscription-notify-interval-seconds} in conf-overrides.yml. */
    private static final Duration NOTIFY_INTERVAL = Duration.ofSeconds(1);

    /** One request, many documents — a burst of change events with no HTTP round trips between them. */
    private static void writeMany(int count) {
        var docs = new StringBuilder("[");
        for (var i = 0; i < count; i++) {
            docs.append(i == 0 ? "" : ",").append("{\"item\":\"burst-").append(i).append("\"}");
        }
        docs.append("]");

        Unirest.post(TEST_COLL).basicAuth("admin", "secret").contentType("application/json")
                .body(docs.toString()).asEmpty();
    }

    private static void write(String item) {
        Unirest.post(TEST_COLL).basicAuth("admin", "secret").contentType("application/json")
                .body("{\"item\":\"" + item + "\"}").asEmpty();
    }

    @Test
    public void subscribingThenWriting_notifiesTheSubscriber() throws Exception {
        mcp.rpc("resources/subscribe", "{ \"uri\": \"" + TEST_COLL + "\" }");

        write("first");

        var notification = notifications.await(UPDATED, Duration.ofSeconds(10));

        assertEquals(TEST_COLL, notification.getDocument("params").getString("uri").getValue());
    }

    @Test
    public void aBurstOfWritesDoesNotBecomeABurstOfNotifications() throws Exception {
        mcp.rpc("resources/subscribe", "{ \"uri\": \"" + TEST_COLL + "\" }");

        // one request, fifty documents: fifty change events inside a few milliseconds, which is
        // the shape this is about. Fifty separate POSTs would spread the burst over seconds and
        // measure the speed of the HTTP client rather than the rate limiting.
        var start = System.currentTimeMillis();
        writeMany(50);

        notifications.await(UPDATED, Duration.ofSeconds(10));
        // let the rate-limiting interval pass, so any trailing notification has been sent
        Thread.sleep(NOTIFY_INTERVAL.toMillis() * 3);

        // What is bounded is notifications per unit of time, not per write: one sent immediately
        // plus at most one closing each interval the burst spanned. Derived rather than guessed,
        // so a slower machine widens the bound instead of failing.
        var intervalsSpanned = (System.currentTimeMillis() - start) / NOTIFY_INTERVAL.toMillis() + 1;
        var max = intervalsSpanned + 1;

        var count = notifications.matching(UPDATED).size();
        assertTrue(count <= max,
                "50 writes produced " + count + " notifications over " + intervalsSpanned
                        + " interval(s); at most " + max + " is the point of rate-limiting them");
        assertTrue(count < 50, "the writes were not coalesced at all");
    }

    @Test
    public void unsubscribingStopsTheNotifications() throws Exception {
        mcp.rpc("resources/subscribe", "{ \"uri\": \"" + TEST_COLL + "\" }");
        write("before");
        notifications.await(UPDATED, Duration.ofSeconds(10));

        mcp.rpc("resources/unsubscribe", "{ \"uri\": \"" + TEST_COLL + "\" }");
        Thread.sleep(3_000);

        var seenBefore = notifications.matching(UPDATED).size();
        write("after");

        Thread.sleep(3_000);
        assertEquals(seenBefore, notifications.matching(UPDATED).size(),
                "a write after unsubscribing must not reach a client that said it stopped listening");
    }

    @Test
    public void writingWithoutSubscribingNotifiesNobody() throws Exception {
        // the stream is open, but nothing was subscribed: the server has nothing to say on it
        write("unwatched");

        notifications.awaitNone(UPDATED, Duration.ofSeconds(4));
    }
}
