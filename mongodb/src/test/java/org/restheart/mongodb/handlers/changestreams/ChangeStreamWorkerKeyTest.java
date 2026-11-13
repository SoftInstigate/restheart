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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.bson.BsonArray;
import org.bson.json.JsonMode;
import org.junit.jupiter.api.Test;

/**
 * One cursor per stream, never per client (#807): the key of a worker is the URL, the resolved
 * stages and the JSON mode, nothing of who the client is.
 */
class ChangeStreamWorkerKeyTest {

    private static final String URL = "http://localhost/db/coll/_streams/mine";

    @Test
    void twoClientsOfTheSameStreamShareTheKey() {
        var stages = BsonArray.parse("[{\"$match\": {\"operationType\": \"insert\"}}]");

        assertEquals(new ChangeStreamWorkerKey(URL, stages, JsonMode.RELAXED),
                new ChangeStreamWorkerKey(URL, BsonArray.parse("[{\"$match\": {\"operationType\": \"insert\"}}]"), JsonMode.RELAXED));
    }

    @Test
    void stagesThatResolveDifferentlyGetTheirOwnKey() {
        assertNotEquals(new ChangeStreamWorkerKey(URL, BsonArray.parse("[{\"$match\": {\"region\": \"eu\"}}]"), JsonMode.RELAXED),
                new ChangeStreamWorkerKey(URL, BsonArray.parse("[{\"$match\": {\"region\": \"us\"}}]"), JsonMode.RELAXED));
    }
}
