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

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.bson.Document;
import com.github.dockerjava.api.exception.NotModifiedException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

/**
 * A MongoDB container that a test can stop and start again, for the ITs that take the database
 * away on purpose.
 *
 * <p>The host port is fixed for the container's lifetime, like the broker's in
 * {@link MqttITBase}: Docker reassigns a dynamically published port on every start, which would
 * leave RESTHeart, whose connection string is fixed at launch, talking to a port nothing listens
 * on.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
final class StoppableMongo implements AutoCloseable {
    private final GenericContainer<?> container;
    private final int port;
    private final MongoClient client;

    StoppableMongo() throws IOException {
        var started = new GenericContainer<?>[1];
        var boundPort = new int[1];

        // the same probe-then-bind race the broker has: another process can take the port in
        // between, and Docker then refuses to start the container
        MqttITBase.retryingOnPortCollision("MongoDB", candidatePort -> {
            var candidate = new GenericContainer<>(System.getProperty("mongodb.image", "mongodb/mongodb-atlas-local")
                    + ":" + System.getProperty("mongodb.version", "preview"))
                .withExposedPorts(27017)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(90)));
            candidate.setPortBindings(List.of(candidatePort + ":27017"));
            try {
                candidate.start();
            } catch (RuntimeException e) {
                candidate.stop();
                throw e;
            }
            started[0] = candidate;
            boundPort[0] = candidatePort;
        }, StoppableMongo::freePort);

        this.container = started[0];
        this.port = boundPort[0];
        this.client = MongoClients.create(uri());
        awaitReady();
    }

    private static int freePort() {
        try {
            return MqttITBase.freePort();
        } catch (IOException e) {
            throw new IllegalStateException("cannot probe a free port", e);
        }
    }

    /** @return the connection string for this container, as seen from the host */
    String uri() {
        return "mongodb://localhost:" + port;
    }

    /** @return a client the test itself uses to look at what was written */
    MongoClient client() {
        return client;
    }

    void stop() {
        container.getDockerClient().stopContainerCmd(container.getContainerId()).exec();
    }

    /** Starts the container if it is stopped; a container already running is left alone. */
    void start() {
        try {
            container.getDockerClient().startContainerCmd(container.getContainerId()).exec();
        } catch (NotModifiedException alreadyRunning) {
            // Docker answers 304 when there is nothing to start
        }
        awaitReady();
    }

    /**
     * Blocks until the database is a writable primary, so a restart is not raced.
     *
     * <p>Not a {@code ping}: the atlas-local image answers one while it is still initialising its
     * single-node replica set, and it restarts {@code mongod} to do so - a test that started
     * writing on the strength of a ping got "interrupted at shutdown" moments later.</p>
     */
    private void awaitReady() {
        var deadline = System.currentTimeMillis() + 90_000L;
        RuntimeException last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                var hello = client.getDatabase("admin").runCommand(new Document("hello", 1));
                if (Boolean.TRUE.equals(hello.getBoolean("isWritablePrimary"))) {
                    return;
                }
                sleep(500);
                continue;
            } catch (RuntimeException e) {
                last = e;
                sleep(500);
            }
        }
        throw new IllegalStateException("MongoDB was not a writable primary within 90s", last);
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    @Override
    public void close() {
        try {
            client.close();
        } finally {
            container.stop();
        }
    }
}
