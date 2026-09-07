/*-
 * ========================LICENSE_START=================================
 * restheart-mqtt
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

package org.restheart.mqtt;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Proves automatic MQTT reconnection over the real RESTHeart / Mosquitto stack started by
 * {@link MqttITBase}, replacing what {@code MqttReconnectBrokerTest} used to cover against an
 * embedded Moquette broker that never involved RESTHeart at all: subscribe, publish, stop the
 * broker, restart it, wait for the client to reconnect and re-subscribe, and prove a message
 * published after that still reaches a client that has been connected the whole time.
 * <p>
 * The SSE connection this test opens is kept open for the entire test, deliberately. A fresh
 * connection opened only after the broker restart would subscribe itself the normal way and would
 * therefore always receive the post-reconnect message, whether or not
 * {@link MqttMessageRouter#resubscribeAll()} actually did its job - hiding exactly the bug this
 * test exists to catch. Only a connection that was already subscribed before the broker went away
 * can tell the two cases apart.
 * </p>
 * <p>
 * The wait for reconnection is generous and log-driven rather than a fixed sleep: a container
 * restarted via {@link MqttITBase#startBroker()} can come back with a different IP, and the JVM
 * inside the RESTHeart container caches successful DNS lookups for a while, so reconnection can
 * legitimately take tens of seconds beyond Mosquitto's own startup time.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttReconnectIT extends MqttITBase {

    @Test
    void clientReconnectsResubscribesAndDeliveryResumes() throws Exception {
        var topic = "sensors/reconnect";
        var req = authedRequest("/mqtt-sse?topic=" + topic).build();

        // One single connection, open for the whole test. The read stops on the second payload
        // rather than on a line count: the connection yields 6 lines (2 events x "id:", "event:",
        // "data:"), but a duplicate cache replay could make it 9, and padding the count to 9 for
        // that possibility would mean the far more common 6-line run never reaches it and pays the
        // whole 190s timeout on every single execution. The timeout stays generous because it now
        // only bites when delivery genuinely never resumes, which is the failure this test exists
        // to catch.
        var linesFuture = subscribeAsync(
            () -> readSseLinesUntil(req, line -> line.contains("\"n\": 2"), 12, 190));

        // Let the SSE connection and its MQTT subscription settle before publishing, as every
        // other IT in this module does.
        Thread.sleep(2_500);

        publish(topic, "{\"n\": 1}");

        // Give the first message time to travel broker -> mqtt-client -> router -> SSE before the
        // broker is taken down, so the two phases of the test do not overlap.
        Thread.sleep(2_000);

        stopBroker();
        startBroker();

        // MqttMessageRouter#resubscribeAll logs this line at INFO once the client's underlying
        // MQTT session is re-established; polling for it - rather than a fixed sleep - is what
        // absorbs the "tens of seconds" reconnection risk documented on the class.
        var afterResubscribeMarker = awaitLogContainsAfter("Re-subscribing to", 0, 120);
        var resubscribeIdx = afterResubscribeMarker.indexOf("Re-subscribing to");

        // subscribeOnBroker logs this line once the broker acknowledges the re-issued
        // subscription; it must appear strictly after the "Re-subscribing to" marker above, not
        // merely be a stale match against the topic filter's original, pre-restart subscription.
        awaitLogContainsAfter("Subscribed to topic filter: " + topic, resubscribeIdx, 30);

        publish(topic, "{\"n\": 2}");

        // Blocks until the connection above has collected both events (or its own 190s timeout).
        var lines = linesFuture.get(200, TimeUnit.SECONDS);

        assertTrue(lines.stream().anyMatch(l -> l.contains("\"n\": 1")),
            "the still-open connection must have received the message published before the broker "
                + "was restarted; got: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("\"n\": 2")),
            "the still-open connection must receive a message published after the broker restarted "
                + "and the client resubscribed, proving the broker subscription was restored rather "
                + "than merely the listener still being registered in the router; got: " + lines);
    }

    /**
     * Polls {@link MqttITBase#restheartLogs()} until it contains {@code needle} at or after
     * {@code fromIndex}, or fails the test once {@code timeoutSec} elapses. Deliberately not a
     * fixed {@code Thread.sleep}: see the class Javadoc for why the wait this backs can
     * legitimately take tens of seconds.
     *
     * @param needle     the log text to wait for
     * @param fromIndex  the log offset before which a match does not count, so a stale occurrence
     *                   already sitting in the accumulated log (for example this test's own
     *                   initial, pre-restart subscription) cannot be mistaken for the one this call
     *                   is actually waiting for
     * @param timeoutSec how long to keep polling before failing the test
     * @return the full captured log, at the point {@code needle} was found
     */
    private String awaitLogContainsAfter(String needle, int fromIndex, int timeoutSec) throws InterruptedException {
        var deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        String logs;
        do {
            logs = restheartLogs();
            if (logs.indexOf(needle, fromIndex) >= 0) {
                return logs;
            }
            Thread.sleep(1_000);
        } while (System.currentTimeMillis() < deadline);

        fail("restheart logs never contained \"" + needle + "\" (at or after log offset " + fromIndex
            + ") within " + timeoutSec + "s; got:\n" + logs);
        return logs; // unreachable: fail() always throws
    }
}
