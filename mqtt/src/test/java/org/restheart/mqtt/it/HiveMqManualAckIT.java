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
package org.restheart.mqtt.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

/**
 * Measures whether manual acknowledgement in hivemq-mqtt-client 1.4.0 actually holds a message
 * until it is acknowledged, and whether the broker redelivers it to a reconnecting session.
 * <p>
 * <strong>This test exists to de-risk a design, not to cover a feature.</strong> The module
 * currently acknowledges every message the instant the client hands it to the consumer, which
 * discards the broker's redelivery guarantee before the message reaches storage; the intended fix
 * is to acknowledge only once a durable consumer has taken responsibility. That fix rests entirely
 * on manual acknowledgement working, and the client has open issues against it —
 * hivemq-mqtt-client #481 (2021, not acknowledged after a reconnect) and #627 (2024, "manual
 * acknowledgement doesn't work") — neither of which is confirmed fixed in the pinned 1.4.0.
 * </p>
 * <p>
 * Rather than assume, this measures it against the real Mosquitto the harness already starts. It
 * is worth keeping afterwards: it pins the one library behaviour the durability design depends
 * on, so a future client upgrade that regresses it fails here rather than silently losing data.
 * </p>
 * <p>
 * It uses the exact form the module would adopt — a single global publish consumer registered
 * with {@code publishes(ALL, handler, true)}, not the per-subscription callback the HiveMQ
 * documentation shows — because that is what preserves the property the router relies on: a
 * message matching two subscribed filters is received once, not once per filter.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class HiveMqManualAckIT extends MqttITBase {

    private static final String TOPIC = "sensors/manual-ack";

    /**
     * A fixed identifier, and {@code cleanSession(false)}: without both, the broker has no session
     * to resume and nothing to redeliver. This is exactly the prerequisite the module's own
     * default defeats today, where the client id is a fresh UUID on every start.
     */
    private static final String CLIENT_ID = "restheart-manual-ack-it";

    private static final String TOPIC_TAKEOVER = "sensors/manual-ack-takeover";
    private static final String CLIENT_ID_TAKEOVER = "restheart-manual-ack-takeover-it";

    @Test
    void unacknowledgedMessagesAreRedeliveredToAResumedSession() throws Exception {
        var received = new CopyOnWriteArrayList<String>();

        // --- first session: receive three, acknowledge only the first ---
        var first = newClient();
        var gotThree = new CountDownLatch(3);
        first.publishes(MqttGlobalPublishFilter.ALL, publish -> {
            var payload = payloadOf(publish);
            received.add(payload);
            if (payload.equals("1")) {
                publish.acknowledge();
            }
            gotThree.countDown();
        }, true);

        first.connectWith().cleanSession(false).send().get(10, TimeUnit.SECONDS);
        first.subscribeWith().topicFilter(TOPIC).qos(MqttQos.AT_LEAST_ONCE).send().get(10, TimeUnit.SECONDS);

        for (var n : List.of("1", "2", "3")) {
            publish(TOPIC, n);
        }

        assertTrue(gotThree.await(20, TimeUnit.SECONDS),
            "the first session must receive all three; got " + received);
        first.disconnect().get(10, TimeUnit.SECONDS);

        // --- second session, same identifier: what does the broker replay? ---
        var redelivered = new CopyOnWriteArrayList<String>();
        var second = newClient();
        var gotRedelivery = new CountDownLatch(2);
        second.publishes(MqttGlobalPublishFilter.ALL, publish -> {
            redelivered.add(payloadOf(publish));
            publish.acknowledge();
            gotRedelivery.countDown();
        }, true);

        second.connectWith().cleanSession(false).send().get(10, TimeUnit.SECONDS);

        var replayed = gotRedelivery.await(20, TimeUnit.SECONDS);
        second.disconnect().get(10, TimeUnit.SECONDS);

        // The claim under test: what was never acknowledged is still owed to us. If this fails,
        // the durability design has to change - the broker cannot be relied on as the buffer, and
        // the module would have to own a durable spool from the first message rather than only
        // under pressure.
        assertTrue(replayed,
            "messages 2 and 3 were never acknowledged, so a resumed session must be given them "
                + "again; the broker replayed " + redelivered + " instead");
        assertEquals(List.of("2", "3"), redelivered.stream().sorted().toList(),
            "exactly the unacknowledged messages must come back - not the acknowledged one, and "
                + "not nothing; got " + redelivered);
    }

    /**
     * Measures whether unacknowledged messages survive a session takeover, which simulates a crash.
     * <p>
     * A crashed RESTHeart does not send a DISCONNECT packet to the broker; instead, the TCP
     * connection is simply dropped, and after the broker's timeout the old session is replaced when
     * a new client connects with the same identifier. This test reproduces that scenario by
     * connecting a second client with the same id without first disconnecting the first, which
     * causes the broker to take over the session and drop the older connection — the nearest
     * reproducible equivalent of a crash inside a single JVM.
     * </p>
     * <p>
     * The outcome determines whether the module's durability guarantee covers crashes or only
     * clean shutdowns: if the messages come back, the broker's session is durable across the
     * takeover and the design is sound; if they do not, the module must maintain its own durable
     * queue from the moment a message arrives.
     * </p>
     */
    @Test
    void unacknowledgedMessagesSurviveASessionTakeover() throws Exception {
        Mqtt3AsyncClient first = null;
        Mqtt3AsyncClient second = null;
        try {
            var received = new CopyOnWriteArrayList<String>();

            // --- first session: receive three, acknowledge only the first ---
            first = newClientWithId(CLIENT_ID_TAKEOVER);
            var gotThree = new CountDownLatch(3);
            first.publishes(MqttGlobalPublishFilter.ALL, publish -> {
                var payload = payloadOf(publish);
                received.add(payload);
                if (payload.equals("1")) {
                    publish.acknowledge();
                }
                gotThree.countDown();
            }, true);

            first.connectWith().cleanSession(false).send().get(10, TimeUnit.SECONDS);
            first.subscribeWith().topicFilter(TOPIC_TAKEOVER).qos(MqttQos.AT_LEAST_ONCE).send().get(10, TimeUnit.SECONDS);

            for (var n : List.of("1", "2", "3")) {
                publish(TOPIC_TAKEOVER, n);
            }

            assertTrue(gotThree.await(20, TimeUnit.SECONDS),
                "the first session must receive all three; got " + received);
            // Note: first.disconnect() is intentionally omitted to simulate a crash

            // --- second session, same identifier: session takeover, what does the broker replay? ---
            var redelivered = new CopyOnWriteArrayList<String>();
            second = newClientWithId(CLIENT_ID_TAKEOVER);
            var gotRedelivery = new CountDownLatch(2);
            second.publishes(MqttGlobalPublishFilter.ALL, publish -> {
                redelivered.add(payloadOf(publish));
                publish.acknowledge();
                gotRedelivery.countDown();
            }, true);

            second.connectWith().cleanSession(false).send().get(10, TimeUnit.SECONDS);

            var replayed = gotRedelivery.await(20, TimeUnit.SECONDS);
            second.disconnect().get(10, TimeUnit.SECONDS);

            // The claim under test: unacknowledged messages survive a session takeover (crash).
            // If this fails, the durability design has to change.
            assertTrue(replayed,
                "messages 2 and 3 were never acknowledged, so a session takeover must still deliver them "
                    + "again; the broker replayed " + redelivered + " instead");
            assertEquals(List.of("2", "3"), redelivered.stream().sorted().toList(),
                "exactly the unacknowledged messages must come back after session takeover; got " + redelivered);
        } finally {
            // Clean up both clients, ignoring exceptions from the taken-over connection
            if (first != null) {
                try {
                    first.disconnect().get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // The first connection was taken over, so it is expected to be gone
                }
            }
            if (second != null) {
                try {
                    second.disconnect().get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    /**
     * @return an unconnected MQTT 3.1.1 client on the harness's broker, with the fixed identifier
     *         a resumable session requires
     */
    private Mqtt3AsyncClient newClient() {
        return newClientWithId(CLIENT_ID);
    }

    /**
     * @param clientId the MQTT client identifier to use
     * @return an unconnected MQTT 3.1.1 client on the harness's broker with the given identifier
     */
    private Mqtt3AsyncClient newClientWithId(String clientId) {
        return MqttClient.builder()
            .useMqttVersion3()
            .identifier(clientId)
            .serverHost("localhost")
            .serverPort(brokerPort)
            .buildAsync();
    }

    private static String payloadOf(Mqtt3Publish publish) {
        return new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
    }
}
