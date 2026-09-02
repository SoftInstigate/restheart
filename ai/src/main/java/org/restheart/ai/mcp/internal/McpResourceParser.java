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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.restheart.ai.mcp.api.McpContext;
import org.restheart.ai.mcp.api.McpResource;
import org.restheart.utils.URLUtils;

/**
 * Turns a plugin's {@code defaultMcpConfig()} deep-merged with the operator's
 * {@code mcp-config} into an {@link McpResource}. Used by {@code McpAware}'s default
 * {@code describeMcp()}; exposed so a custom implementation can reuse the same merge
 * and parsing behaviour for part of its own resource construction if it wants to.
 */
public final class McpResourceParser {
    private final Map<String, Object> merged;

    private McpResourceParser(Map<String, Object> merged) {
        this.merged = merged;
    }

    /**
     * Deep-merges {@code codeDefaults} with {@code operatorConfig} (see {@link ConfigDeepMerger})
     * and returns a parser ready to build the resource. Either argument may be {@code null}.
     */
    public static McpResourceParser fromMerged(Map<String, Object> codeDefaults, Map<String, Object> operatorConfig) {
        return new McpResourceParser(ConfigDeepMerger.merge(codeDefaults, operatorConfig));
    }

    /** Builds a single {@link McpResource}, using {@code ctx.baseUrl() + ctx.pluginUri()} for its URI. */
    public McpResource buildSingle(McpContext ctx) {
        var builder = McpResource.builder().uri(resolveUri(ctx));

        if (stringValue(merged, "kind") instanceof String kind) {
            builder.kind(kind);
        }
        if (stringValue(merged, "description") instanceof String description) {
            builder.description(description);
        }

        if (merged.get("actions") instanceof Map<?, ?> actionsMap) {
            for (var entry : actionsMap.entrySet()) {
                var name = String.valueOf(entry.getKey());
                if (entry.getValue() instanceof Map<?, ?> actionDef) {
                    builder.action(name, a -> populateAction(a, actionDef));
                }
            }
        }

        if (merged.get("examples") instanceof List<?> examples) {
            for (var raw : examples) {
                if (raw instanceof Map<?, ?> exampleMap) {
                    builder.example(
                            stringValue(exampleMap, "description"),
                            stringValue(exampleMap, "action"),
                            castArgs(exampleMap.get("args")));
                }
            }
        }

        if (merged.get("auth") instanceof Map<?, ?> authMap) {
            builder.auth(castArgs(authMap));
        }

        return builder.build();
    }

    private void populateAction(McpResource.Action action, Map<?, ?> def) {
        if (stringValue(def, "method") instanceof String method) {
            action.method(method.toUpperCase());
        }
        if (aliased(def, "path_template", "path-template") instanceof String pathTemplate) {
            action.pathTemplate(pathTemplate);
        }
        if (stringValue(def, "description") instanceof String description) {
            action.description(description);
        }
        if (aliased(def, "body_schema", "body-schema") instanceof Map<?, ?> bodySchema) {
            action.bodySchema(castArgs(bodySchema));
        }
        if (def.get("params") instanceof Map<?, ?> paramsMap) {
            for (var entry : paramsMap.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> paramDef) {
                    action.param(String.valueOf(entry.getKey()), toParam(paramDef));
                }
            }
        }
    }

    private McpResource.Param toParam(Map<?, ?> def) {
        var type = stringValue(def, "type");
        var description = stringValue(def, "description");
        var required = Boolean.TRUE.equals(def.get("required"));
        var enumValues = def.get("enum") instanceof List<?> l ? new ArrayList<Object>(l) : null;
        var defaultValue = def.get("default");
        return new McpResource.Param(type, description, required, enumValues, defaultValue);
    }

    private String resolveUri(McpContext ctx) {
        var base = ctx.baseUrl() == null ? "" : URLUtils.removeTrailingSlashes(ctx.baseUrl());
        var pluginUri = ctx.pluginUri() == null ? "" : ctx.pluginUri();
        if (!pluginUri.isEmpty() && !pluginUri.startsWith("/")) {
            pluginUri = "/" + pluginUri;
        }
        return base + pluginUri;
    }

    private static String stringValue(Map<?, ?> m, String key) {
        var v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /** Reads a field that may be spelled either snake_case (JSON/API shape) or kebab-case (YAML convention). */
    private static Object aliased(Map<?, ?> m, String snakeCase, String kebabCase) {
        var v = m.get(snakeCase);
        return v != null ? v : m.get(kebabCase);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castArgs(Object value) {
        if (!(value instanceof Map<?, ?> m)) {
            return null;
        }
        if (m.keySet().stream().allMatch(String.class::isInstance)) {
            return (Map<String, Object>) m;
        }
        var result = new LinkedHashMap<String, Object>();
        m.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
}
