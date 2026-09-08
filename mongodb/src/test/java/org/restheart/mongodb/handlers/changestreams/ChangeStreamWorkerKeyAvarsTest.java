/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
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
package org.restheart.mongodb.handlers.changestreams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.json.JsonMode;
import org.junit.jupiter.api.Test;

/**
 * The variable bound per-client by {@code notify_when} must not reach the
 * {@link ChangeStreamWorkerKey}.
 *
 * <p>The key is {@code (url without query string, avars, jsonMode)}, and clients of a
 * {@code notify_when} stream differ precisely by that variable: if it stays in the avars, each
 * value gets its own worker — one MongoDB cursor and one pooled connection per client — which
 * is the opposite of what the feature is for. The predicate is applied per session at dispatch
 * time instead, on one shared cursor.
 */
public class ChangeStreamWorkerKeyAvarsTest {

    private static final String URL = "/db/coll/_streams/by-tenant";

    private static NotifyWhenEvaluator evaluatorOn(String varName) {
        return NotifyWhenEvaluator.from(BsonDocument.parse(
                "{ \"fullDocument::tenantId\": { \"$var\": \"" + varName + "\" } }"));
    }

    /** The avars a request carries: the bound variable plus what injectAvars always adds. */
    private static BsonDocument avarsWith(String tenant) {
        var avars = new BsonDocument();
        avars.put("tid", new BsonString(tenant));
        avars.put("@page", new BsonInt32(1));
        avars.put("@pagesize", new BsonInt32(100));
        avars.put("@user", BsonNull.VALUE);
        return avars;
    }

    @Test
    void notifyWhenVariableIsStrippedAndTheRestIsKept() {
        var keyAvars = GetChangeStreamHandler.keyAvars(avarsWith("acme"), evaluatorOn("tid"));

        assertFalse(keyAvars.containsKey("tid"), "the notify_when variable must not identify the cursor");
        assertEquals(new BsonInt32(1), keyAvars.get("@page"));
        assertEquals(new BsonInt32(100), keyAvars.get("@pagesize"));
        assertTrue(keyAvars.containsKey("@user"));
    }

    @Test
    void clientsDifferingOnlyByTheBoundVariableShareOneWorker() {
        var evaluator = evaluatorOn("tid");

        var acme = new ChangeStreamWorkerKey(URL,
                GetChangeStreamHandler.keyAvars(avarsWith("acme"), evaluator), JsonMode.RELAXED);
        var globex = new ChangeStreamWorkerKey(URL,
                GetChangeStreamHandler.keyAvars(avarsWith("globex"), evaluator), JsonMode.RELAXED);

        assertEquals(acme, globex, "clients of a notify_when stream must resolve to the same worker key");
        assertEquals(acme.hashCode(), globex.hashCode());
    }

    @Test
    void withoutNotifyWhenTheAvarsStillIdentifyTheCursor() {
        var acme = new ChangeStreamWorkerKey(URL, GetChangeStreamHandler.keyAvars(avarsWith("acme"), null),
                JsonMode.RELAXED);
        var globex = new ChangeStreamWorkerKey(URL, GetChangeStreamHandler.keyAvars(avarsWith("globex"), null),
                JsonMode.RELAXED);

        assertNotEquals(acme, globex,
                "a stream filtering by $var in its stages needs one cursor per value — that pipeline is per-client");
    }

    @Test
    void avarsAreLeftAloneWhenTheVariableIsNotBound() {
        var avars = avarsWith("acme");

        // the client connected without binding the variable: nothing to strip, and the
        // evaluator will pass every event through to it
        assertSame(avars, GetChangeStreamHandler.keyAvars(avars, evaluatorOn("other")));
        assertSame(avars, GetChangeStreamHandler.keyAvars(avars, null));
    }

    @Test
    void otherKeyComponentsStillSeparateWorkers() {
        var evaluator = evaluatorOn("tid");
        var keyAvars = GetChangeStreamHandler.keyAvars(avarsWith("acme"), evaluator);

        assertNotEquals(new ChangeStreamWorkerKey(URL, keyAvars, JsonMode.RELAXED),
                new ChangeStreamWorkerKey("/db/coll/_streams/other", keyAvars, JsonMode.RELAXED),
                "different streams are different cursors");

        assertNotEquals(new ChangeStreamWorkerKey(URL, keyAvars, JsonMode.RELAXED),
                new ChangeStreamWorkerKey(URL, keyAvars, JsonMode.EXTENDED),
                "the events are serialised per worker, so jsonMode still separates them");
    }
}
