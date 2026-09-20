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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

/**
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
class ChangeStreamPermissionsTest {

    @Test
    void fieldsArePrefixedWithFullDocument() {
        var filter = BsonDocument.parse("{\"owner\": \"alice\", \"qty\": {\"$gt\": 5}}");

        var stage = ChangeStreamPermissions.readFilterStage(filter);

        assertEquals(BsonDocument.parse(
                "{\"$match\": {\"fullDocument.owner\": \"alice\", \"fullDocument.qty\": {\"$gt\": 5}}}"), stage);
    }

    @Test
    void logicalOperatorsAreRewrittenRecursively() {
        var filter = BsonDocument.parse(
                "{\"$or\": [{\"owner\": \"alice\"}, {\"$and\": [{\"public\": true}, {\"status\": {\"$ne\": \"draft\"}}]}]}");

        var stage = ChangeStreamPermissions.readFilterStage(filter);

        assertEquals(BsonDocument.parse(
                "{\"$match\": {\"$or\": [{\"fullDocument.owner\": \"alice\"}, {\"$and\": [{\"fullDocument.public\": true}, {\"fullDocument.status\": {\"$ne\": \"draft\"}}]}]}}"),
                stage);
    }

    @Test
    void elemMatchKeepsItsRelativeFields() {
        var filter = BsonDocument.parse("{\"members\": {\"$elemMatch\": {\"id\": \"alice\"}}}");

        var stage = ChangeStreamPermissions.readFilterStage(filter);

        assertEquals(BsonDocument.parse(
                "{\"$match\": {\"fullDocument.members\": {\"$elemMatch\": {\"id\": \"alice\"}}}}"), stage);
    }

    @Test
    void operatorsThatCannotBeRewrittenAreRefused() {
        assertThrows(SecurityException.class, () -> ChangeStreamPermissions.readFilterStage(
                BsonDocument.parse("{\"$expr\": {\"$eq\": [\"$owner\", \"alice\"]}}")));
        assertThrows(SecurityException.class, () -> ChangeStreamPermissions.readFilterStage(
                BsonDocument.parse("{\"$where\": \"true\"}")));
        assertThrows(SecurityException.class, () -> ChangeStreamPermissions.readFilterStage(
                BsonDocument.parse("{\"$or\": [{\"$expr\": true}]}")));
    }

    @Test
    void exclusionProjectionRemovesThePropertiesFromTheEvent() {
        var stages = ChangeStreamPermissions.projectResponseStages(BsonDocument.parse("{\"secret\": 0}"));

        assertEquals(2, stages.size());
        assertEquals(BsonDocument.parse(
                "{\"$project\": {\"fullDocument.secret\": 0, \"fullDocumentBeforeChange.secret\": 0, \"updateDescription.updatedFields.secret\": 0}}"),
                stages.get(0));
        // updatedFields keys are paths: the literal "secret" and "secret.<nested>" are filtered out
        var set = stages.get(1).toJson();
        assertTrue(set.contains("\"$objectToArray\": \"$updateDescription.updatedFields\""), set);
        assertTrue(set.contains("\"secret.\""), set);
    }

    @Test
    void nestedKeysCoveredByAnExcludedAncestorAreDropped() {
        var stages = ChangeStreamPermissions.projectResponseStages(BsonDocument.parse("{\"profile\": 0, \"profile.ssn\": 0}"));

        assertEquals(BsonDocument.parse(
                "{\"$project\": {\"fullDocument.profile\": 0, \"fullDocumentBeforeChange.profile\": 0, \"updateDescription.updatedFields.profile\": 0}}"),
                stages.get(0));
    }

    @Test
    void inclusionProjectionIsRefused() {
        assertThrows(SecurityException.class,
                () -> ChangeStreamPermissions.projectResponseStages(BsonDocument.parse("{\"name\": 1}")));
    }
}
