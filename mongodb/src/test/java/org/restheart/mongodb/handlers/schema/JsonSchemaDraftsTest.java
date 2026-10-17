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
package org.restheart.mongodb.handlers.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.bson.BsonInt32;
import org.bson.BsonString;
import org.everit.json.schema.ValidationException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class JsonSchemaDraftsTest {

    @Test
    void theDeclaredDraftIsRecognised_withOrWithoutTheFragmentAndOverHttps() {
        assertEquals(Optional.of("http://json-schema.org/draft-04/schema"),
                JsonSchemaDrafts.draftOf(new BsonString("http://json-schema.org/draft-04/schema#")));
        assertEquals(Optional.of("http://json-schema.org/draft-06/schema"),
                JsonSchemaDrafts.draftOf(new BsonString("https://json-schema.org/draft-06/schema")));
        assertEquals(Optional.of("http://json-schema.org/draft-07/schema"),
                JsonSchemaDrafts.draftOf(new BsonString("https://json-schema.org/draft-07/schema#")));
    }

    @Test
    void noDeclaredDraftIsDraft07() {
        assertEquals(Optional.of("http://json-schema.org/draft-07/schema"), JsonSchemaDrafts.draftOf(null));
    }

    @Test
    void aDraftEveritDoesNotValidateIsNotAccepted() {
        assertTrue(JsonSchemaDrafts.draftOf(new BsonString("https://json-schema.org/draft/2020-12/schema")).isEmpty());
        assertTrue(JsonSchemaDrafts.draftOf(new BsonInt32(7)).isEmpty());
    }

    @Test
    void draft04KeepsIdWhileTheLaterDraftsUseDollarId() {
        assertFalse(JsonSchemaDrafts.usesDollarId("http://json-schema.org/draft-04/schema"));
        assertTrue(JsonSchemaDrafts.usesDollarId("http://json-schema.org/draft-07/schema"));
    }

    /** Each draft is checked against its own metaschema: a draft-04 exclusiveMaximum is a boolean, a draft-07 one a number. */
    @Test
    void eachDraftIsCheckedAgainstItsOwnMetaschema() {
        var draft04 = new JSONObject("{\"maximum\": 10, \"exclusiveMaximum\": true}");
        var draft07 = new JSONObject("{\"exclusiveMaximum\": 10}");

        JsonSchemaDrafts.metaschema("http://json-schema.org/draft-04/schema").validate(draft04);
        JsonSchemaDrafts.metaschema("http://json-schema.org/draft-07/schema").validate(draft07);

        assertThrows(ValidationException.class,
                () -> JsonSchemaDrafts.metaschema("http://json-schema.org/draft-07/schema").validate(draft04));
    }
}
