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
package org.restheart.mongodb.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.junit.jupiter.api.Test;

public class BsonJavaConverterTest {

    @Test
    public void nullValue_convertsToNull() {
        assertNull(BsonJavaConverter.toJava(BsonNull.VALUE));
    }

    @Test
    public void scalarArray_convertsToList() {
        var arr = BsonArray.parse("[1, \"a\", true]");
        assertEquals(List.of(1, "a", true), BsonJavaConverter.toJava(arr));
    }

    @Test
    public void document_convertsToMapRecursively() {
        var doc = BsonDocument.parse("{\"a\": {\"b\": 1}}");
        var result = BsonJavaConverter.toMap(doc);

        @SuppressWarnings("unchecked")
        var nested = (java.util.Map<String, Object>) result.get("a");
        assertEquals(1, nested.get("b"));
    }
}
