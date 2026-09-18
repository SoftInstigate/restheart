/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.plugins.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonValue;

/**
 * Converts a {@link BsonValue} into the plain {@code Map}/{@code List}/scalar shape
 * {@link McpResource} is built from.
 *
 * <p>Every {@code McpAware} implementation whose metadata is BSON needs this same crossing, and
 * each used to carry its own copy: the MongoDB service reads collection metadata and JSON schemas,
 * the GraphQL one reads {@code gql-apps} documents. One copy here, because two copies of a
 * conversion drift, and the one that drifts is the one nobody is looking at.
 */
public final class BsonJava {
    private BsonJava() {
    }

    /** @return the document as a plain map, keys in the order the document has them */
    public static Map<String, Object> toMap(BsonDocument doc) {
        var result = new LinkedHashMap<String, Object>();
        doc.forEach((key, value) -> result.put(key, toJava(value)));
        return result;
    }

    /**
     * @return the value as a plain Java object: a map, a list, a string, a boolean or a number.
     *         An exotic BSON type (an {@code ObjectId}, a date) is not expected in the metadata
     *         this reads and comes back as its string rendering rather than throwing, since a
     *         description that loses a field is worse than one that renders it oddly.
     */
    public static Object toJava(BsonValue value) {
        if (value == null || value.isNull()) {
            return null;
        }

        return switch (value.getBsonType()) {
            case DOCUMENT -> toMap(value.asDocument());
            case ARRAY -> value.asArray().stream().map(BsonJava::toJava).toList();
            case STRING -> value.asString().getValue();
            case BOOLEAN -> value.asBoolean().getValue();
            case INT32 -> value.asInt32().getValue();
            case INT64 -> value.asInt64().getValue();
            case DOUBLE -> value.asDouble().getValue();
            default -> value.toString();
        };
    }
}
