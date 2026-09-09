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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.GenericContainer;

import com.github.dockerjava.api.model.ExposedPort;

/**
 * Proves automatic MQTT reconnection: subscribe, publish, stop the broker, restart it, wait for
 * the client to reconnect and re-subscribe, and prove a message published after that still
 * reaches a client that has been connected the whole time.
 * <p>
 * The SSE connection this test opens is kept open for the entire test, deliberately. A fresh
 * connection opened only after the broker restart would subscribe itself the normal way and would
 * therefore always receive the post-reconnect message, whether or not
 * {@code MqttMessageRouter#resubscribeAll()} actually did its job - hiding exactly the bug this
 * test exists to catch. Only a connection that was already subscribed before the broker went away
 * can tell the two cases apart.
 * </p>
 * <p>
 * Unlike the version of this test that ran inside {@code core}'s shared, host-networked IT setup,
 * this class holds the Mosquitto {@link GenericContainer} directly - {@link MqttITBase#mosquitto}
 * - so {@link #stopBroker()} / {@link #startBroker()} go through the Docker API
 * ({@link DockerClientFactory}) exactly as the module's original, now-deleted Testcontainers-based
 * base class did, rather than shelling out to the {@code docker} CLI the way the {@code core}
 * version had to when it had no container handle to work with.
 * </p>
 * <p>
 * The wait for reconnection is generous and log-driven rather than a fixed sleep: this RESTHeart
 * process, running on the host rather than inside a container on a Docker network, connects to
 * plain {@code localhost} rather than resolving a Docker-network alias - so no DNS-caching delay
 * of the kind a Docker-network setup could see applies here.
 * </p>
 * <p>
 * <strong>Why the broker is published on a fixed host port.</strong> A container keeps its
 * Docker-assigned identity (its container ID) across {@link #stopBroker()} /
 * {@link #startBroker()}, but a host port published <em>dynamically</em> does not: the daemon
 * reassigns it every time the container's network endpoint is reactivated, which a stop/start is.
 * Verified two ways - {@code docker run -p 0:1883 ...; docker stop; docker start; docker port}
 * yields two different host ports, and {@link GenericContainer}'s own {@code getMappedPort}
 * javadoc warns the cached value "might be outdated (for instance, after disconnecting from a
 * network and reconnecting again)", which is exactly this scenario.
 * </p>
 * <p>
 * That matters because {@code broker-url} is fixed, through {@code RHO}, when this test's
 * RESTHeart instance is launched (see {@link MqttITBase#startRestheart}) and cannot be changed
 * afterwards: a port that moved would leave the client reconnecting to a port nothing listens on,
 * and this test would fail for a reason with nothing to do with reconnection. So
 * {@link MqttITBase#startTopology()} probes a free host port and binds the broker to it
 * explicitly - the one place in this harness that does not use a dynamic port, and this class is
 * the reason. The assertion below re-checks that invariant with a live Docker inspect
 * ({@link ContainerState#getCurrentContainerInfo()}) rather than the cached {@code getMappedPort},
 * which would compare a stale value against itself and always appear to pass.
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
        // whole read timeout on every single execution. The timeout stays generous because it now
        // only bites when delivery genuinely never resumes, which is the failure this test exists
        // to catch.
        var linesFuture = subscribeAsync(
            () -> readSseLinesUntil(req, line -> line.contains("\"n\": 2"), 12, 90));

        // Let the SSE connection and its MQTT subscription settle before publishing, as every
        // other IT in this module does.
        Thread.sleep(2_500);

        publish(topic, "{\"n\": 1}");

        // Give the first message time to travel broker -> mqtt-client -> router -> SSE before the
        // broker is taken down, so the two phases of the test do not overlap.
        Thread.sleep(2_000);

        // getCurrentContainerInfo(), not the cached getMappedPort(1883): only a live Docker
        // inspect can be trusted here - see the class javadoc.
        var brokerPortBeforeOutage = currentMappedPort();

        // The offset before which a match against either log-wait below does not count. Not 0:
        // this test's own initial, pre-outage "Subscribed to topic filter: sensors/reconnect ..."
        // line, logged a few lines above, is already in the log by the time stopBroker()/
        // startBroker() below run - without this guard, the second log-wait could match that
        // stale line and pass without ever observing a real resubscription.
        var beforeOutageIdx = restheartLogs().length();

        stopBroker();
        startBroker();

        // The invariant the fixed host-port binding buys us, re-checked rather than assumed. A
        // dynamically published port would NOT survive this stop/start (see the class javadoc);
        // the explicit binding in MqttITBase.startTopology is what makes it survive. If it ever
        // stopped holding, every assertion below would fail too - the client cannot reconnect to
        // a port nothing listens on - and this check is what names the real cause instead of
        // leaving it to be inferred from a 60-second log-wait timeout.
        var brokerPortAfterOutage = currentMappedPort();
        assertTrue(brokerPortAfterOutage.equals(brokerPortBeforeOutage),
            "the broker's mapped port must not change across a stop/start of the same container "
                + "(before=" + brokerPortBeforeOutage + ", after=" + brokerPortAfterOutage + "); "
                + "otherwise RESTHeart's broker-url, fixed at launch, would now point at the wrong port. "
                + "This is a known, verified failure mode of a dynamically-published Testcontainers port "
                + "across a stop/start - see the class Javadoc.");

        // MqttMessageRouter#resubscribeAll logs this line at INFO once the client's underlying
        // MQTT session is re-established; polling for it - rather than a fixed sleep - is what
        // absorbs whatever reconnection delay does remain.
        var afterResubscribeMarker = awaitLogContainsAfter("Re-subscribing to", beforeOutageIdx, 60);
        var resubscribeIdx = afterResubscribeMarker.indexOf("Re-subscribing to", beforeOutageIdx);

        // subscribeOnBroker logs this line once the broker acknowledges the re-issued
        // subscription; it must appear strictly after the "Re-subscribing to" marker above, not
        // merely be a stale match against the topic filter's original, pre-restart subscription -
        // which, as noted above, is already sitting earlier in this same log.
        awaitLogContainsAfter("Subscribed to topic filter: " + topic, resubscribeIdx, 30);

        // Force this class's own publisher to reconnect before publishing the second message,
        // rather than trusting publish()'s ordinary lazy check. This client sat idle for the
        // whole outage (it only ever writes, on demand, when a test calls publish()), so nothing
        // prompted it to notice the broker had gone away and come back - its cached connection
        // state can still read CONNECTED for a socket the restarted broker instance has never
        // heard of. The two log waits just above are what make it safe to reconnect now: they are
        // this test's own proof that the broker is genuinely accepting connections again.
        reconnectPublisher();

        publish(topic, "{\"n\": 2}");

        // Blocks until the connection above has collected both events (or its own read timeout).
        var lines = linesFuture.get(100, TimeUnit.SECONDS);

        assertTrue(lines.stream().anyMatch(l -> l.contains("\"n\": 1")),
            "the still-open connection must have received the message published before the broker "
                + "was restarted; got: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("\"n\": 2")),
            "the still-open connection must receive a message published after the broker restarted "
                + "and the client resubscribed, proving the broker subscription was restored rather "
                + "than merely the listener still being registered in the router; got: " + lines);
    }

    /**
     * Stops the Mosquitto broker container without destroying it, via the Docker API's
     * {@code stopContainerCmd} rather than {@link GenericContainer#stop()}. {@code stop()}
     * removes the container outright; restarting from a freshly created container would get it a
     * new Docker-assigned identity and, with it, a new mapped host port - and this test needs the
     * very same container, and the very same port RESTHeart's {@code broker-url} was launched
     * with, to come back. Pair with {@link #startBroker()}.
     */
    private void stopBroker() {
        DockerClientFactory.instance().client()
            .stopContainerCmd(mosquitto.getContainerId())
            .exec();
    }

    /**
     * Restarts the same Mosquitto broker container {@link #stopBroker()} stopped, via the Docker
     * API's {@code startContainerCmd} rather than creating a new container, for the same reason
     * {@link #stopBroker()} does not use {@link GenericContainer#stop()}.
     */
    private void startBroker() {
        DockerClientFactory.instance().client()
            .startContainerCmd(mosquitto.getContainerId())
            .exec();
    }

    /**
     * The broker's current host port mapping for 1883, via a live Docker inspect
     * ({@link ContainerState#getCurrentContainerInfo()}), rather than
     * {@link GenericContainer#getMappedPort(int)}'s cached value.
     * <p>
     * {@code getMappedPort} reads a {@code containerInfo} snapshot that {@link GenericContainer}
     * populates once, in its own {@code start()}, and never refreshes on its own - it has no way
     * to know this class stopped and restarted the container underneath it via the low-level
     * Docker API. Calling it after {@link #stopBroker()} / {@link #startBroker()} would therefore
     * silently compare a stale cached value against itself, always reporting "unchanged" even
     * when the real, Docker-assigned port had actually moved - which is exactly the failure mode
     * the class Javadoc's KNOWN FAILURE note documents.
     * </p>
     *
     * @return the current host port Docker has 1883 mapped to, per a fresh inspect
     */
    private Integer currentMappedPort() {
        var bindings = mosquitto.getCurrentContainerInfo()
            .getNetworkSettings()
            .getPorts()
            .getBindings()
            .get(new ExposedPort(1883));
        return Integer.valueOf(bindings[0].getHostPortSpec());
    }

    /**
     * Polls {@link MqttITBase#restheartLogs()} until it contains {@code needle} at or after
     * {@code fromIndex}, or fails the test once {@code timeoutSec} elapses. Deliberately not a
     * fixed {@code Thread.sleep}: see the class Javadoc for why the wait this backs needs to be
     * generous.
     * <p>
     * Unlike the version of this method that ran against {@code core}'s shared, suite-long,
     * rolling {@code restheart.log}, this instance's log is per-class, freshly created for this
     * run alone, and never rolls - so the roll-clamp the earlier version needed
     * ({@code fromIndex <= logs.length() ? fromIndex : 0}) is dropped here: {@code fromIndex} is
     * always within bounds for a log that only ever grows for the lifetime of this one test
     * class's RESTHeart instance.
     * </p>
     *
     * @param needle     the log text to wait for
     * @param fromIndex  the log offset before which a match does not count, so a stale occurrence
     *                   already sitting in the log (for example this test's own initial,
     *                   pre-restart subscription) cannot be mistaken for the one this call is
     *                   actually waiting for
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
