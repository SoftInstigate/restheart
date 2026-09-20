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

import org.bson.Document;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ValidationOptions;

/**
 * The dead-letter collection, in the only situation that produces one: MongoDB is up and refuses
 * the document itself.
 *
 * <p>The sink collection here validates its documents and rejects everything this writer stores,
 * so the messages are retried a couple of times and then recorded in the dead-letter collection of
 * the same database, with the reason. A database that is <em>down</em> never produces a dead
 * letter: those writes are retried instead, and there would be nowhere to record them anyway —
 * a dead-letter queue lives in the server it belongs to, and a stopped server has none.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttMongoDeadLetterIT extends MqttITBase {

    private static final String TOPIC = "sensors/deadletter";
    private static final String DB = "test-mqtt-deadletter";
    private static final String COLLECTION = "sensor-events";
    private static final String DEAD_LETTER = "mqtt-dead-letter";

    private StoppableMongo mongo;

    @Override
    protected boolean standalone() {
        return false;
    }

    @Override
    protected Path overridesFile() {
        return Path.of("src", "test", "resources", "etc", "it-overrides-deadletter.yml").toAbsolutePath().normalize();
    }

    @Override
    protected void beforeRestheartStarts() throws IOException {
        mongo = new StoppableMongo();

        // a collection that refuses what arrives on one topic and takes everything else. The
        // validator matches the stored document, whose fields are topic/payload/receivedAt/...,
        // not the fields of the payload.
        var db = mongo.client().getDatabase(DB);
        db.getCollection(COLLECTION).drop();
        db.getCollection(DEAD_LETTER).drop();
        db.createCollection(COLLECTION, new CreateCollectionOptions().validationOptions(
            new ValidationOptions().validator(Filters.ne("topic", TOPIC))));
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
    void aDocumentMongoRefusesIsRecordedInTheDeadLetterCollection() throws Exception {

        publish(TOPIC, "{\"messageId\":\"dl1\",\"value\":42}");

        var deadLetters = mongo.client().getDatabase(DB).getCollection(DEAD_LETTER);
        assertTrue(awaitDocument(deadLetters, "dl1", 60_000),
            "a document MongoDB refuses must end up in the dead-letter collection; found "
                + deadLetters.countDocuments());

        var recorded = deadLetters.find(Filters.eq("_id", "dl1")).first();
        assertEquals(TOPIC, recorded.getString("topic"), "the message is kept as it was");
        var why = recorded.get("_deadLetter", Document.class);
        assertEquals(COLLECTION, why.getString("collection"), "with the collection it was meant for");
        assertTrue(why.getString("reason").toLowerCase().contains("validation"),
            "and why MongoDB refused it: " + why.getString("reason"));

        // the sink is unblocked: a message on a topic the collection accepts is written as usual
        publish("sensors/accepted", "{\"messageId\":\"ok1\",\"value\":1}");
        assertTrue(awaitDocument(mongo.client().getDatabase(DB).getCollection(COLLECTION), "ok1", 60_000),
            "one refused document must not stop the ones after it");
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
}
