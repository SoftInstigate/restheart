/*-
 * ========================LICENSE_START=================================
 * restheart-ai
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
package org.restheart.ai.mcp.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Validates a {@code how_to_call} {@code body} argument against the inline JSON
 * Schema declared by an action's {@code body_schema}.
 *
 * <p>This is a different validation shape from RESTHeart's {@code JsonSchemas}
 * SPI (commons {@code org.restheart.plugins.schema}): that one validates a
 * document against a schema stored <em>by reference</em> in the {@code _schemas}
 * collection, for an actual MongoDB write. Here there is no stored schema and no
 * write — {@code body_schema} is an inline JSON Schema object already present in
 * the {@code McpResource}. So this delegates directly to the same underlying
 * library RESTHeart already uses for JSON Schema (everit-org.json-schema, see
 * {@code JsonMetaSchemaChecker} in restheart-mongodb) rather than to an existing
 * RESTHeart class — no class in the codebase does "validate an arbitrary value
 * against an arbitrary inline schema" today.
 */
public final class BodyValidator {

    private BodyValidator() {
    }

    /**
     * @param bodySchema the action's {@code body_schema} (may be {@code null} or empty — nothing to validate)
     * @param body       the candidate value, typically {@code args.get("body")}
     * @return validation error messages, empty if valid (or if there is no schema to validate against)
     */
    public static List<String> validate(Map<String, Object> bodySchema, Object body) {
        if (bodySchema == null || bodySchema.isEmpty()) {
            return List.of();
        }

        try {
            var schema = SchemaLoader.load(new JSONObject(bodySchema));
            schema.validate(toJsonValue(body));
            return List.of();
        } catch (ValidationException ve) {
            var errors = new ArrayList<String>();
            errors.add(ve.getMessage());
            ve.getCausingExceptions().stream().map(ValidationException::getMessage).forEach(errors::add);
            return errors;
        }
    }

    private static Object toJsonValue(Object value) {
        return switch (value) {
            case null -> JSONObject.NULL;
            case Map<?, ?> m -> new JSONObject(m);
            case List<?> l -> new JSONArray(l);
            default -> value;
        };
    }
}
