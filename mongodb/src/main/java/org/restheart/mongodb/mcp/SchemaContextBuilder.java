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

import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonValue;

/**
 * Converts a collection's existing JSON Schema (stored as BSON in its {@code jsonSchema}
 * metadata, the same document {@code jsonSchemaBeforeWrite} validates writes against) into a
 * plain {@code Map<String,Object>} — the shape {@code McpResource.Action.bodySchema()} (and
 * the MCP framework's {@code BodyValidator}, which loads it directly into everit-org.json-schema)
 * expects. No new schema is invented for MCP; this is the same schema the collection already
 * enforces, just re-shaped from BSON to plain Java values.
 */
public final class SchemaContextBuilder {

    private SchemaContextBuilder() {
    }

    /** @return the schema as a plain object graph, or {@code null} if {@code jsonSchema} is absent/not a document */
    public static Map<String, Object> toBodySchema(BsonValue jsonSchema) {
        if (jsonSchema == null || !jsonSchema.isDocument()) {
            return null;
        }
        return toMap(jsonSchema.asDocument());
    }

    private static Map<String, Object> toMap(BsonDocument doc) {
        var result = new LinkedHashMap<String, Object>();
        doc.forEach((key, value) -> result.put(key, toJava(value)));
        return result;
    }

    private static Object toJava(BsonValue value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return switch (value.getBsonType()) {
            case DOCUMENT -> toMap(value.asDocument());
            case ARRAY -> value.asArray().stream().map(SchemaContextBuilder::toJava).toList();
            case STRING -> value.asString().getValue();
            case BOOLEAN -> value.asBoolean().getValue();
            case INT32 -> value.asInt32().getValue();
            case INT64 -> value.asInt64().getValue();
            case DOUBLE -> value.asDouble().getValue();
            // exotic BSON types (ObjectId, Date, ...) are not expected inside a JSON
            // Schema document; fall back to a string rendering rather than throwing
            default -> value.toString();
        };
    }
}
