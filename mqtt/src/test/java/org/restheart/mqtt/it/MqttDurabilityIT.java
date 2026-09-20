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
import java.util.List;
import java.util.stream.IntStream;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

/**
 * An orderly shutdown: RESTHeart stops taking messages and writes what it is holding.
 *
 * <p>A message is acknowledged to the broker once it is buffered, not once it is in MongoDB —
 * see {@link MqttMongoOutageIT} for why — so the buffer is the only place those messages exist.
 * A clean stop must therefore drain it into the database rather than exit on top of it. From the
 * moment the shutdown begins nothing new is taken: messages that arrive are left unacknowledged,
 * so the broker keeps them for the next instance instead of racing the shutdown deadline.
 *
 * <p>What this does not cover, deliberately, is a {@code SIGKILL}: there is no hook to run, and
 * what was in memory is lost. That is the module's stated guarantee, at-least-once up to the
 * buffer.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttDurabilityIT extends MqttITBase {

    private static final String TOPIC = "sensors/durability";
    private static final String DB = "test-mqtt";
    private static final String COLLECTION = "sensor-events";

    private StoppableMongo mongo;

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
        mongo = new StoppableMongo();
    }

    @Override
    protected List<String> extraRho() {
        return List.of("/mclient/connection-string->\"" + mongo.uri() + "\"");
    }

    @Override
    protected void afterRestheartStops() {
        if (mongo != null) {
            mongo.close();
        }
    }

    @Test
    void aCleanShutdownWritesWhatIsStillBuffered() throws Exception {

        var collection = collection();
        collection.deleteMany(Filters.exists("_id"));

        // The drain runs every two seconds in this configuration, so these are still in the
        // buffer - acknowledged to the broker, and nowhere else - when the shutdown starts.
        var count = 50;
        IntStream.rangeClosed(1, count).forEach(n ->
            publish(TOPIC, "{\"messageId\":\"sd-" + n + "\",\"value\":" + n + "}"));
        StoppableMongo.sleep(500);

        // SIGTERM, which runs the shutdown hook, unlike the SIGKILL of a crash
        restheart.close();

        assertEquals(count, collection.countDocuments(Filters.regex("_id", "^sd-")),
            "a clean shutdown must write what it had buffered, not exit on top of it");

        // and the next instance carries on
        restheart = startRestheart(overridesFile(), allRho(), getClass().getSimpleName() + "-restarted.log",
            standalone());
        awaitBrokerConnected();
        publish(TOPIC, "{\"messageId\":\"after\",\"value\":1}");
        assertTrue(awaitDocument(collection, "after", 60_000),
            "the restarted instance must store what it receives");
    }


    private boolean awaitDocument(MongoCollection<Document> collection, String id, long timeoutMillis) {
        var deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (collection.countDocuments(Filters.eq("_id", id)) > 0) {
                return true;
            }
            StoppableMongo.sleep(500);
        }
        return false;
    }

    private MongoCollection<Document> collection() {
        return mongo.client().getDatabase(DB).getCollection(COLLECTION);
    }
}
