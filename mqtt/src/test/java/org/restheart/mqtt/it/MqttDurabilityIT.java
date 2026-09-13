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
 * Proves the property the whole acknowledgement rework exists for: a message the broker delivered
 * at QoS 1 reaches MongoDB even though the RESTHeart that received it was killed before it could
 * write, because it was never acknowledged and the broker still owed it.
 * <p>
 * <strong>This test cannot pass against the module as it was.</strong> The client acknowledged
 * every message the instant it was handed over, so a RESTHeart killed with anything still buffered
 * lost it outright — the broker had been told it was safe and had already forgotten it. That is
 * what made the module at-most-once end to end whatever QoS was configured.
 * </p>
 * <p>
 * The kill is deliberately ungraceful. A clean shutdown drains the buffer, and since that was
 * fixed it would write the messages by itself and prove nothing about acknowledgement; only
 * destroying the process outright leaves the question to the broker.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttDurabilityIT extends MqttITBase {

    private static final String TOPIC = "sensors/durability";
    private static final String DB = "test-mqtt";
    private static final String COLLECTION = "sensor-events";

    /**
     * The log both instances write to, set by {@code it-overrides-durability.yml}. Logback writes
     * it with immediate flush, unlike the harness's stdout capture, which this test's SIGKILL
     * would lose - and which is also what makes it usable as a readiness signal.
     */
    private static final Path SERVER_LOG = Path.of("target", "it-logs", "MqttDurabilityIT-server.log");

    private GenericContainer<?> mongo;
    private MongoClient testMongoClient;

    /**
     * A fixed host port for MongoDB, for the same reason the broker has one: this test stops and
     * restarts the container, and Docker reassigns a dynamically published port on every start -
     * which would leave both this test's own client and the restarted RESTHeart talking to a port
     * nothing listens on.
     */
    private int mongoPort;

    @Override
    protected boolean standalone() {
        return false;
    }

    @Override
    protected Path overridesFile() {
        return Path.of("src", "test", "resources", "etc", "it-overrides-durability.yml").toAbsolutePath().normalize();
    }

    @Override
    protected void beforeRestheartStarts() throws IOException {
        // Both instances append to this file, and it survives between runs - so the connection
        // count this test waits on would start already satisfied by the previous run's lines.
        java.nio.file.Files.createDirectories(SERVER_LOG.getParent());
        java.nio.file.Files.deleteIfExists(SERVER_LOG);

        mongoPort = freePort();
        mongo = new GenericContainer<>(System.getProperty("mongodb.image", "mongodb/mongodb-atlas-local")
                + ":" + System.getProperty("mongodb.version", "preview"))
            .withExposedPorts(27017)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(90)));
        mongo.setPortBindings(List.of(mongoPort + ":27017"));
        mongo.start();
        testMongoClient = MongoClients.create(mongoUri());
        awaitMongoReady();
    }

    @Override
    protected List<String> extraRho() {
        return List.of("/mclient/connection-string->\"" + mongoUri() + "\"");
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
    void aMessageSurvivesTheDeathOfTheInstanceThatReceivedIt() throws Exception {
        // Not /ping: that answers while AFTER_STARTUP initializers are still running, and
        // mqtt-connector is deliberately the last of them. Proceeding on /ping alone let this test
        // stop MongoDB and publish against an instance whose MQTT client had not connected - and
        // stopping MongoDB then stalls core's changeStreamActivator for its 30 s server-selection
        // timeout, so the connector never ran before the kill. The message was never received,
        // never held, and nothing was owed.
        awaitBrokerConnections(SERVER_LOG, 1, 90);

        var collection = collection();
        collection.deleteMany(Filters.exists("_id"));

        // Stop MongoDB before publishing. The writer will take the message into its buffer and
        // never manage to write it, so it never reports it taken and the router never acknowledges
        // it - which is exactly the state this test needs the broker to be left holding.
        mongo.getDockerClient().stopContainerCmd(mongo.getContainerId()).exec();

        publish(TOPIC, "{\"value\": 42}");

        // Long enough for the message to have travelled broker -> client -> router -> writer and
        // for the writer to be sitting in insertWithRetry against the stopped database. The
        // overrides give it 1000 retries two seconds apart precisely so it is still trying, and
        // therefore has still not reported the message as taken, when it is killed below.
        Thread.sleep(15_000);

        // kill(), not close(): a clean shutdown now drains the buffer and would write the message
        // itself once MongoDB returns, which would prove nothing about acknowledgement.
        restheart.kill();

        mongo.getDockerClient().startContainerCmd(mongo.getContainerId()).exec();
        awaitMongoReady();

        // A new RESTHeart, same client id, resuming the session the old one left behind. The
        // broker still owes it the message.
        // allRho(), not extraRho(): the broker's port is probed at startup and lives only in the
        // base class's composition. Passing half of it leaves this instance looking for a broker
        // on 1883, where nothing listens.
        restheart = startRestheart(overridesFile(), allRho(), getClass().getSimpleName() + "-restarted.log",
            standalone());

        // The second connection: the resumed session is only owed anything once this happens.
        awaitBrokerConnections(SERVER_LOG, 2, 90);

        assertTrue(awaitDocument(collection, 60_000),
            "the message was never acknowledged, so the broker owed it to the resumed session and "
                + "it must have been written after the restart; found "
                + collection.countDocuments() + " documents");

        var stored = collection.find().first();
        assertEquals(TOPIC, stored.getString("topic"),
            "and it must be the message that was published, not something else");
    }

    /**
     * @param collection    where the document should appear
     * @param timeoutMillis how long to wait
     * @return whether at least one document arrived before the deadline
     */
    private boolean awaitDocument(MongoCollection<Document> collection, long timeoutMillis)
            throws InterruptedException {
        var deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (collection.countDocuments() > 0) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }

    private MongoCollection<Document> collection() {
        return testMongoClient.getDatabase(DB).getCollection(COLLECTION);
    }

    private String mongoUri() {
        return "mongodb://localhost:" + mongoPort;
    }

    /**
     * Blocks until the database answers a {@code ping}, so the restart is not raced.
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
                    throw new IllegalStateException("interrupted waiting for MongoDB", ie);
                }
            }
        }
        throw new IllegalStateException("MongoDB never answered a ping within 90s", last);
    }
}
