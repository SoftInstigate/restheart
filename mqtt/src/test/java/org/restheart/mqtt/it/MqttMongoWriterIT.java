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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

/**
 * End-to-end coverage for {@code mqtt-mongo-writer}: proves that a message published to the
 * broker is actually persisted to MongoDB, and that a message on a topic outside the configured
 * sink is not.
 * <p>
 * {@code mqtt-mongo-writer} is the only one of this module's seven plugins {@code
 * MqttPluginLoadingIT} exercises no further than confirming it stays inactive when nothing opts
 * in - every other mqtt IT runs RESTHeart with {@code --standalone}, which loads {@code
 * restheart-default-config-no-mongodb.yml} and has no MongoDB at all. This class is the one
 * exception: it overrides {@link #standalone()} to {@code false} so the full {@code
 * restheart-default-config.yml} (with MongoDB) is loaded, and starts its own MongoDB container
 * via {@link #beforeRestheartStarts()}.
 * </p>
 * <p>
 * The container is a plain standalone {@code mongod}, not a replica set - unlike every other
 * MongoDB container elsewhere in this repository. That is deliberate and intentional here:
 * {@code mqtt-mongo-writer} writes with the plain MongoDB driver (see {@code
 * MqttMongoWriter#insertWithRetry}) - simple {@code insertMany}/{@code bulkWrite} calls, no
 * change streams and no transactions - none of which need a replica set to work.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttMongoWriterIT extends MqttITBase {

    /** How long a positive assertion polls for a document to appear before giving up. */
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(20);

    /** How often {@link #awaitDocument(String, Duration)} re-checks MongoDB while polling. */
    private static final Duration POLL_INTERVAL = Duration.ofMillis(300);

    /**
     * The MongoDB image to run, from the root pom's {@code mongodb.image}/{@code mongodb.version}
     * properties by way of failsafe's systemPropertyVariables. The defaults only matter to an IDE
     * runner, which sets no system properties; they mirror the root pom so the two cannot drift
     * silently.
     */
    private static final String MONGO_IMAGE =
        System.getProperty("mongodb.image", "mongodb/mongodb-atlas-local")
            + ":" + System.getProperty("mongodb.version", "preview");

    private GenericContainer<?> mongo;
    private MongoClient testMongoClient;

    @Override
    protected boolean standalone() {
        return false;
    }

    @Override
    protected Path overridesFile() {
        return Path.of("src", "test", "resources", "etc", "it-overrides-mongo.yml").toAbsolutePath().normalize();
    }

    @Override
    protected void beforeRestheartStarts() throws IOException {
        // The same image the rest of this repository tests against, passed in by failsafe from the
        // root pom's mongodb.image/mongodb.version properties, so -Dmongodb.version=8.0 works here
        // exactly as it does for core's suite and nobody has to remember a second place to bump.
        //
        // Not a hardcoded "mongo:8": mongod 8 refuses to start at all on a Linux kernel 6.19 or
        // newer (SERVER-121912), which current Docker Desktop VMs already are, and a test that
        // cannot run on a maintainer's laptop is not much of a test.
        mongo = new GenericContainer<>(MONGO_IMAGE)
            .withExposedPorts(27017)
            // A TCP wait only, deliberately. The default image is mongodb/mongodb-atlas-local,
            // whose entrypoint also brings up mongot and self-initializes a single-node replica
            // set - so both "port is open" and any single log line are reached before it can
            // actually serve a write. Readiness is therefore established below, by the driver.
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(90)));
        mongo.start();

        testMongoClient = MongoClients.create("mongodb://localhost:" + mongo.getMappedPort(27017));
        awaitMongoReady();
    }

    /**
     * Blocks until the database answers a {@code ping}, or fails the setup once the deadline
     * expires.
     * <p>
     * RESTHeart is launched immediately after this returns and connects at startup, so a
     * database that is listening but not yet electable would fail the whole class in
     * {@code mclient} rather than in a test. Asking the driver is the only check that means
     * "ready to serve" rather than "ready to accept a socket".
     * </p>
     */
    private void awaitMongoReady() {
        var deadline = System.currentTimeMillis() + 90_000L;
        RuntimeException last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                testMongoClient.getDatabase("admin").runCommand(new Document("ping", 1));
                return;
            } catch (RuntimeException e) {
                last = e;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted waiting for " + MONGO_IMAGE, ie);
                }
            }
        }
        throw new IllegalStateException(MONGO_IMAGE + " never answered a ping within 90s", last);
    }

    @Override
    protected List<String> extraRho() {
        // The container's host port is assigned by Testcontainers in beforeRestheartStarts(),
        // above, and cannot be known before it starts - see it-overrides-mongo.yml's own comment
        // for why /mclient/connection-string is not set there statically.
        return List.of("/mclient/connection-string->\"mongodb://localhost:" + mongo.getMappedPort(27017) + "\"");
    }

    @Override
    protected void afterRestheartStops() {
        if (testMongoClient != null) {
            testMongoClient.close();
        }
        if (mongo != null) {
            mongo.stop();
        }
    }

    @Test
    void publishedMessageIsPersistedToMongo() {
        var topic = "sensors/mongo-writer-it-persisted";
        var payload = "{\"value\": 42}";

        publish(topic, payload);

        var doc = awaitDocument(topic, POLL_TIMEOUT);

        assertNotNull(doc, "a message published to " + topic + ", which matches the configured "
            + "mongo-sink filter \"sensors/#\", must be persisted to MongoDB within "
            + POLL_TIMEOUT.toSeconds() + "s");
        assertTrue(doc.getString("payload").contains("42"),
            "the persisted document must carry the published payload; got: " + doc.toJson());
    }

    @Test
    void messageOutsideSinkIsNotPersisted() {
        // "other/..." is outside the configured mongo-sink filter "sensors/#" - deliberately not
        // even a topic prefix of "sensors", so MqttTopicMatcher.matches cannot accidentally match
        // it (see that class's own edge cases around a filter prefix that must not match a longer
        // sibling topic).
        var topic = "other/mongo-writer-it-not-persisted";

        publish(topic, "{\"value\": 1}");

        // Polled for exactly as long as publishedMessageIsPersistedToMongo waits for a positive
        // result, so "nothing arrived" here means "did not arrive", not "we did not wait".
        var doc = awaitDocument(topic, POLL_TIMEOUT);

        assertNull(doc, "a message published to " + topic + ", which does not match the "
            + "configured mongo-sink filter \"sensors/#\", must never be persisted to MongoDB; got: "
            + (doc == null ? null : doc.toJson()));
    }

    /**
     * Polls the {@code test-mqtt.sensor-events} collection - the database and collection {@code
     * it-overrides-mongo.yml}'s {@code mongo-sink} entry configures - for a document with the
     * given {@code topic}, until one appears or {@code timeout} elapses.
     * <p>
     * Poll, not sleep-then-assert: {@code mqtt-mongo-writer} buffers and drains on its own loop
     * (500ms by default), so a fixed sleep is either flaky or unnecessarily slow. The timeout only
     * exists to fail a hang, mirroring {@link MqttITBase#readSseLinesUntil}.
     * </p>
     *
     * @param topic the exact {@code topic} field to match
     * @param timeout the deadline after which this method gives up and returns {@code null}
     * @return the matching document, or {@code null} if none appeared within {@code timeout}
     */
    private Document awaitDocument(String topic, Duration timeout) {
        MongoCollection<Document> coll = testMongoClient.getDatabase("test-mqtt").getCollection("sensor-events");
        var deadline = System.currentTimeMillis() + timeout.toMillis();
        Document found;
        do {
            found = coll.find(Filters.eq("topic", topic)).first();
            if (found != null) {
                return found;
            }
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        } while (System.currentTimeMillis() < deadline);
        return found;
    }
}
