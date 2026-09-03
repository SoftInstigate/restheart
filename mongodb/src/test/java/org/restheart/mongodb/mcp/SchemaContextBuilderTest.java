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
import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.junit.jupiter.api.Test;

public class SchemaContextBuilderTest {

    @Test
    public void nullInput_returnsNull() {
        assertNull(SchemaContextBuilder.toBodySchema(null));
    }

    @Test
    public void nonDocumentInput_returnsNull() {
        assertNull(SchemaContextBuilder.toBodySchema(new BsonInt32(1)));
    }

    @Test
    public void flatSchema_convertedFieldByField() {
        var schema = BsonDocument.parse("""
                { "type": "object", "required": ["sku"], "additionalProperties": false }
                """);

        var result = SchemaContextBuilder.toBodySchema(schema);

        assertEquals("object", result.get("type"));
        assertEquals(List.of("sku"), result.get("required"));
        assertEquals(false, result.get("additionalProperties"));
    }

    @Test
    public void nestedProperties_convertedRecursively() {
        var schema = BsonDocument.parse("""
                {
                  "type": "object",
                  "properties": {
                    "sku": { "type": "string" },
                    "quantity": { "type": "integer", "minimum": 0 }
                  }
                }
                """);

        var result = SchemaContextBuilder.toBodySchema(schema);

        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) result.get("properties");
        @SuppressWarnings("unchecked")
        var quantity = (Map<String, Object>) properties.get("quantity");

        assertEquals("integer", quantity.get("type"));
        assertEquals(0, quantity.get("minimum"));
    }

    @Test
    public void doubleAndLongValues_preserveJavaType() {
        var schema = new BsonDocument("minimum", new BsonDouble(0.5))
                .append("maxItems", new BsonInt64(100L));

        var result = SchemaContextBuilder.toBodySchema(schema);

        assertEquals(0.5, result.get("minimum"));
        assertEquals(100L, result.get("maxItems"));
    }
}
