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

import org.restheart.ai.mcp.api.McpResource;

/**
 * Checks {@code how_to_call} args against an action's {@code params} declarations:
 * required/missing, type, and enum constraints. Does not know about {@code body} —
 * that argument is validated separately by {@link BodyValidator} against the
 * action's {@code body_schema}.
 */
public final class ParamValidator {

    private ParamValidator() {
    }

    /** @return human-readable error messages, empty if {@code args} satisfies every declared param */
    public static List<String> validate(McpResource.Action action, Map<String, Object> args) {
        var errors = new ArrayList<String>();
        var effectiveArgs = args == null ? Map.<String, Object>of() : args;

        action.params().forEach((name, param) -> {
            var value = effectiveArgs.get(name);

            if (value == null) {
                if (param.required() && param.defaultValue() == null) {
                    errors.add("missing required param '" + name + "'");
                }
                return;
            }

            if (param.type() != null && !matchesType(value, param.type())) {
                errors.add("param '" + name + "' must be of type " + param.type());
            } else if (param.enumValues() != null && !param.enumValues().contains(value)) {
                errors.add("param '" + name + "' must be one of " + param.enumValues());
            }
        });

        return errors;
    }

    private static boolean matchesType(Object value, String type) {
        return switch (type) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Integer || value instanceof Long || value instanceof Short;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map;
            case "array" -> value instanceof List;
            // an undeclared/unknown type name is not this validator's business to enforce
            default -> true;
        };
    }
}
