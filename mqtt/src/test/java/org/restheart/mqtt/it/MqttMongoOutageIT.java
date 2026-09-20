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
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

/**
 * What a MongoDB outage does to the module, with a real database stopped and started again.
 *
 * <p>Two commitments of the design are checked here, not only the building blocks:
 * <ul>
 * <li>the live paths ({@code /mqtt-sse}, {@code /mqtt}) keep working while the database is down,
 * and the messages received meanwhile are written once it is back, each once;</li>
 * <li>ingestion does not stop. Messages are acknowledged to the broker as they are buffered, so
 * the broker's in-flight window (20 messages with Mosquitto's defaults) never fills. Were they
 * held until MongoDB took them, ordered MQTT acknowledgements would block behind the oldest one
 * and the broker would stop delivering to this client at all, SSE included.</li>
 * </ul>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttMongoOutageIT extends MqttITBase {

    private static final String DB = "test-mqtt-outage";
    private static final String COLLECTION = "sensor-events";

    private StoppableMongo mongo;

    @Override
    protected boolean standalone() {
        return false;
    }

    @Override
    protected Path overridesFile() {
        return Path.of("src", "test", "resources", "etc", "it-overrides-outage.yml").toAbsolutePath().normalize();
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

    /**
     * Each test starts MongoDB again before it ends; this waits for the writer to have caught up,
     * so the next test starts with an empty buffer.
     */
    @AfterEach
    void awaitBufferDrained() throws Exception {
        // also when the test failed before getting there, so the next one finds a database
        mongo.start();

        var deadline = System.currentTimeMillis() + 180_000L;
        while (System.currentTimeMillis() < deadline) {
            if (metric("mqtt_buffer_size") == 0) {
                return;
            }
            StoppableMongo.sleep(500);
        }
        throw new IllegalStateException("the writer's buffer did not drain after MongoDB came back");
    }

    @Test
    void messagesReceivedDuringARestartAreWrittenOnceMongoIsBack() throws Exception {
        var topic = "sensors/outage-restart";
        var collection = collection();
        collection.deleteMany(Filters.regex("_id", "^restart-"));

        // every message is dispatched on its own virtual thread, so the three can reach an SSE
        // client in any order: read them all rather than stopping at one of them
        var sse = subscribeAsync(() -> readSseLines(
            authedRequest("/mqtt-sse?topic=" + topic).build(), 9, 30));
        StoppableMongo.sleep(1_500);

        mongo.stop();

        for (var n = 1; n <= 3; n++) {
            publish(topic, "{\"messageId\":\"restart-" + n + "\",\"value\":" + n + "}");
        }

        // the live paths do not depend on the database
        var lines = sse.get(35, TimeUnit.SECONDS);
        for (var n = 1; n <= 3; n++) {
            var id = "restart-" + n;
            assertTrue(lines.stream().anyMatch(l -> l.contains(id)),
                "SSE must deliver " + id + " while MongoDB is down; got " + lines);
        }

        var last = httpClient().send(authedRequest("/mqtt?topic=" + topic).build(), BodyHandlers.ofString());
        assertEquals(200, last.statusCode(), "/mqtt must answer while MongoDB is down: " + last.body());
        assertTrue(last.body().contains("restart-3"), "/mqtt must return the latest message: " + last.body());

        mongo.start();

        assertTrue(awaitCount(collection, Filters.regex("_id", "^restart-"), 3, 150_000),
            "the three messages must be written once MongoDB is back; found "
                + collection.countDocuments(Filters.regex("_id", "^restart-")));
        StoppableMongo.sleep(2_000);
        assertEquals(3, collection.countDocuments(Filters.regex("_id", "^restart-")),
            "each message must be written once");
    }

    @Test
    void ingestionContinuesThroughAnOutageBeyondTheBrokerInFlightWindow() throws Exception {
        var topic = "sensors/outage-backpressure";
        var count = 40; // twice Mosquitto's in-flight window
        var droppedBefore = metric("mqtt_buffer_dropped");
        var collection = collection();
        collection.deleteMany(Filters.regex("_id", "^bp-"));

        var sse = subscribeAsync(() -> readSseLines(
            authedRequest("/mqtt-sse?topic=" + topic).build(), count * 3, 60));
        StoppableMongo.sleep(1_500);

        mongo.stop();

        // They pile up in the buffer, acknowledged as they arrive. Were they acknowledged only
        // once written, delivery would stop at the 20th and this assertion would fail.
        IntStream.rangeClosed(1, count).forEach(n ->
            publish(topic, "{\"messageId\":\"bp-" + n + "\",\"value\":" + n + "}"));

        var lines = sse.get(65, TimeUnit.SECONDS);
        var received = IntStream.rangeClosed(1, count)
            .filter(n -> lines.stream().anyMatch(l -> l.contains("\"bp-" + n + "\"")))
            .count();
        assertEquals(count, received,
            "every message must reach the SSE client although the buffer is full; got " + lines.size() + " lines");

        assertEquals(0, metric("mqtt_buffer_dropped") - droppedBefore,
            "the buffer is far from full: an outage must not drop anything");

        mongo.start();

        assertTrue(awaitCount(collection, Filters.regex("_id", "^bp-"), count, 180_000),
            "every message received during the outage must be written once MongoDB is back; found "
                + collection.countDocuments(Filters.regex("_id", "^bp-")));
    }


    /** Reads a gauge from {@code GET /metrics/<name>}, in the Prometheus text format. */
    private long metric(String name) throws Exception {
        var resp = httpClient().send(authedRequest("/metrics/" + name).build(), BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), "GET /metrics/" + name + ": " + resp.body());
        return resp.body().lines()
            .filter(l -> l.startsWith(name))
            .map(l -> l.substring(l.lastIndexOf(' ') + 1))
            .mapToLong(v -> (long) Double.parseDouble(v))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no " + name + " in " + resp.body()));
    }

    private boolean awaitCount(MongoCollection<Document> collection, org.bson.conversions.Bson filter,
            long expected, long timeoutMillis) {
        var deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (collection.countDocuments(filter) >= expected) {
                    return true;
                }
            } catch (RuntimeException e) {
                // MongoDB still coming back
            }
            StoppableMongo.sleep(500);
        }
        return false;
    }

    private MongoCollection<Document> collection() {
        return mongo.client().getDatabase(DB).getCollection(COLLECTION);
    }
}
