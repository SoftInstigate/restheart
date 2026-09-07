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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Proves message fanout from the broker to {@code /mqtt-sse} clients over the real RESTHeart /
 * Mosquitto stack started by {@link MqttITBase}, replacing what {@code MqttRouterBrokerTest} used
 * to cover against an embedded Moquette broker that never involved RESTHeart at all: a published
 * message arriving over SSE, a wildcard filter matching it, two clients on the same filter both
 * receiving it, and one client disconnecting not affecting another that stays connected.
 * <p>
 * Every topic used here is under {@code sensors/}, the only prefix {@link MqttITBase#rho()}'s ACL
 * grants the {@code admin} role, and every test uses a topic no other test in this class publishes
 * to: {@code /mqtt-sse} replays a topic's last message to a newly connecting client (documented on
 * {@code MqttSseService}), so two tests sharing a literal topic could see each other's payload.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttSseStreamIT extends MqttITBase {

    @Test
    void publishedMessageArrivesOverSse() throws Exception {
        var req = authedRequest("/mqtt-sse?topic=sensors/temp").build();

        // Three non-blank lines per event: MqttSseService calls Undertow's
        // conn.send(payload, "mqtt-message", eventId, null), which emits an "id:",
        // an "event:" and a "data:" line. Asking for fewer returns before the payload.
        var linesFuture = subscribeAsync(() -> readSseLines(req, 3, 15));

        // Small delay to let the SSE connection and its MQTT subscription settle, as
        // ChangeStreamSseIT does for the analogous MongoDB change-stream worker.
        Thread.sleep(1_500);

        publish("sensors/temp", "{\"value\": 21.5}");

        var lines = linesFuture.get(20, TimeUnit.SECONDS);

        assertTrue(lines.stream().anyMatch(l -> l.startsWith("event:mqtt-message")),
            "SSE event must be typed 'mqtt-message'; got: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("data:")),
            "SSE connection must receive a 'data:' line after publish; got: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("21.5")),
            "Received event must carry the published payload; got: " + lines);
    }

    @Test
    void wildcardFilterMatches() throws Exception {
        // '#' must be percent-encoded in the query string, or the request line is malformed.
        var req = authedRequest("/mqtt-sse?topic=sensors/%23").build();

        var linesFuture = subscribeAsync(() -> readSseLines(req, 3, 15));

        Thread.sleep(1_500);

        // A topic distinct from every other test in this class: sensors/temp already carries
        // its own message in publishedMessageArrivesOverSse, and a newly connecting client
        // like this one would otherwise be replayed that message's cached value instead of
        // waiting for this test's own live publish below.
        publish("sensors/wildcard-temp", "{\"value\": 19.0}");

        var lines = linesFuture.get(20, TimeUnit.SECONDS);

        assertTrue(lines.stream().anyMatch(l -> l.startsWith("event:mqtt-message")),
            "wildcard filter 'sensors/#' must match a message published to 'sensors/wildcard-temp'; got: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("19.0")),
            "Received event must carry the published payload; got: " + lines);
    }

    @Test
    void twoClientsOnSameFilterBothReceiveTheMessage() throws Exception {
        var reqA = authedRequest("/mqtt-sse?topic=sensors/shared").build();
        var reqB = authedRequest("/mqtt-sse?topic=sensors/shared").build();

        var linesA = subscribeAsync(() -> readSseLines(reqA, 3, 15));
        var linesB = subscribeAsync(() -> readSseLines(reqB, 3, 15));

        Thread.sleep(1_500);

        publish("sensors/shared", "{\"shared\": true}");

        var a = linesA.get(20, TimeUnit.SECONDS);
        var b = linesB.get(20, TimeUnit.SECONDS);

        assertTrue(a.stream().anyMatch(l -> l.contains("shared")),
            "client A must receive the message published once both clients are subscribed; got: " + a);
        assertTrue(b.stream().anyMatch(l -> l.contains("shared")),
            "client B must receive the same message; got: " + b);
    }

    @Test
    void disconnectingClientDoesNotAffectAnotherThatStaysConnected() throws Exception {
        var topic = "sensors/keepalive";
        var reqA = authedRequest("/mqtt-sse?topic=" + topic).build();
        var reqB = authedRequest("/mqtt-sse?topic=" + topic).build();

        // Client A reads exactly one event's three lines and returns; readSseLines's own
        // try/finally then closes A's underlying InputStream, which is how this test disconnects
        // A without reaching into MqttMessageRouter's internals (which an IT must not touch).
        var linesA = subscribeAsync(() -> readSseLines(reqA, 3, 15));

        // Client B stays connected for both events published below. Budgeted for up to three
        // events (nine lines), not two: MqttSseService replays a topic's cached last message to a
        // connection that finishes subscribing just as that message is dispatched, so a
        // still-settling connection can legitimately see one extra copy of the first message; the
        // assertion below is on content ("n": 2 must appear), not on the exact line count, so the
        // extra headroom does not weaken what is actually being verified.
        var linesB = subscribeAsync(() -> readSseLines(reqB, 9, 30));

        // A longer settle than the 1500 ms MqttTopologyIT used for its single-connection case:
        // this test opens two SSE connections that must each finish the full request pipeline
        // (security, mqtt-topic-authorizer, subscribe) before the first publish, and the extra
        // margin makes that reliable even when the container is under load from earlier tests.
        Thread.sleep(2_500);

        publish(topic, "{\"n\": 1}");

        // Blocks until A has received its one event and, as a result, disconnected.
        var firstA = linesA.get(15, TimeUnit.SECONDS);
        assertTrue(firstA.stream().anyMatch(l -> l.contains("\"n\": 1")),
            "client A must receive the first message before it disconnects; got: " + firstA);

        // A brief pause for the server to notice the now-closed connection before the next publish.
        Thread.sleep(500);

        publish(topic, "{\"n\": 2}");

        var allB = linesB.get(25, TimeUnit.SECONDS);
        assertTrue(allB.stream().anyMatch(l -> l.contains("\"n\": 2")),
            "client B, which stayed connected, must still receive a message published after another "
                + "client on the same filter disconnected; got: " + allB);
    }

}
