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
 * Recursively converts a {@link BsonValue} into the plain {@code Map}/{@code List}/scalar
 * shape the MCP framework's {@code McpResource} (and everit-org.json-schema, for
 * {@code body_schema}) expect — used wherever this package reads a value out of collection
 * metadata that needs to cross into that plain-Java world.
 */
final class BsonJavaConverter {

    private BsonJavaConverter() {
    }

    static Map<String, Object> toMap(BsonDocument doc) {
        var result = new LinkedHashMap<String, Object>();
        doc.forEach((key, value) -> result.put(key, toJava(value)));
        return result;
    }

    static Object toJava(BsonValue value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return switch (value.getBsonType()) {
            case DOCUMENT -> toMap(value.asDocument());
            case ARRAY -> value.asArray().stream().map(BsonJavaConverter::toJava).toList();
            case STRING -> value.asString().getValue();
            case BOOLEAN -> value.asBoolean().getValue();
            case INT32 -> value.asInt32().getValue();
            case INT64 -> value.asInt64().getValue();
            case DOUBLE -> value.asDouble().getValue();
            // exotic BSON types (ObjectId, Date, ...) are not expected in schema/mcp
            // metadata; fall back to a string rendering rather than throwing
            default -> value.toString();
        };
    }
}
