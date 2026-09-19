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
package org.restheart.ai.mcp.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.BsonDocument;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.utils.BsonUtils;

/**
 * Checks a call's args against an action's {@code params} declarations: required/missing, type,
 * and enum constraints. Deliberately knows nothing about {@code body}: an action's
 * {@code body_schema} describes the document the resource stores, not what a caller sends, so the
 * body is left to the service that will store it — see {@code HowToCallTool.resolve}.
 */
public final class ParamValidator {

    private ParamValidator() {
    }

    /**
     * Whether an object param with named sub-properties was supplied one property at a time,
     * rather than as the object.
     *
     * <p>Both shapes are offered to the caller, so both must count as supplied. A resource
     * template advertises the flat one and nothing else — an aggregation's is
     * {@code .../byStatus{?status}}, not {@code {?avars}} — so a caller that fills in exactly what
     * the template asked for would otherwise be told it is missing the object it was never shown.
     * What each shape then binds to is the service's business, not this validator's.
     */
    private static boolean suppliedFlat(McpResource.Param param, Map<String, Object> args) {
        return param.properties() != null
                && param.properties().keySet().stream().anyMatch(args::containsKey);
    }

    /**
     * Whether a variable declared as a param of its own was supplied the legacy way, inside
     * {@code avars}.
     *
     * <p>The mirror of {@link #suppliedFlat}. Since 9.9 an aggregation's variables are declared
     * one by one, because that is the shape RESTHeart binds from a bare query parameter — but it
     * still binds them from {@code ?avars={"name":...}} too, and refusing that here would make the
     * MCP layer stricter than the endpoint it dispatches to, turning a call the server would have
     * answered into a validation error.
     */
    private static boolean suppliedInAvars(String name, Map<String, Object> args) {
        var avars = args.get("avars");

        if (avars instanceof Map<?, ?> asMap) {
            return asMap.containsKey(name);
        }

        // From a resources/read URI the whole query string arrives as text, and `avars` is not a
        // declared param any more, so nothing coerced it into an object on the way in.
        if (avars instanceof String raw) {
            try {
                return BsonUtils.parse(raw) instanceof BsonDocument doc && doc.containsKey(name);
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    /** @return human-readable error messages, empty if {@code args} satisfies every declared param */
    public static List<String> validate(McpResource.Action action, Map<String, Object> args) {
        var errors = new ArrayList<String>();
        var effectiveArgs = args == null ? Map.<String, Object>of() : args;

        action.params().forEach((name, param) -> {
            var value = effectiveArgs.get(name);

            if (value == null) {
                if (param.required() && param.defaultValue() == null
                        && !suppliedFlat(param, effectiveArgs)
                        && !suppliedInAvars(name, effectiveArgs)) {
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
