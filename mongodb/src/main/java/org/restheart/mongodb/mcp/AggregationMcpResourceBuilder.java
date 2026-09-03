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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.plugins.mcp.McpResource;

/**
 * Builds the {@code McpResource} for one aggregation pipeline declared in a collection's
 * {@code aggrs} metadata, honoring its own {@code mcp} block (see #616): {@code enabled},
 * {@code description} (required — an aggregation with no description is not surfaced),
 * {@code params} (per-variable overrides for {@code $var} references, keyed by variable name),
 * {@code examples}, and {@code pipeline_summary} — an operator-supplied override for the
 * otherwise auto-generated {@link PipelineSummarizer} heuristic, which can't meaningfully
 * describe non-linear pipelines ({@code $lookup}/{@code $facet}/...).
 *
 * <p>The declared per-variable types surface as a single {@code avars} object param (with a
 * {@code properties} entry per variable), not one param per variable name — RESTHeart binds
 * {@code $var} references from one JSON query param, {@code ?avars={"name":"value",...}}, so
 * that's the shape {@code how_to_call} must render.
 */
public final class AggregationMcpResourceBuilder {

    private AggregationMcpResourceBuilder() {
    }

    /**
     * @param collectionUri the owning collection's resource URI (e.g. {@code https://host/db/coll})
     * @param aggrUri       the aggregation's own {@code uri} ({@code aggrs[].uri})
     * @param stages        the aggregation's pipeline ({@code aggrs[].stages})
     * @param mcp           the aggregation entry's own {@code mcp} block, or {@code null} if absent
     * @return the resource, or empty if not MCP-enabled: no {@code mcp} block,
     *         {@code mcp.enabled == false}, or a missing required {@code description}
     */
    public static Optional<McpResource> build(String collectionUri, String aggrUri, BsonValue stages, BsonDocument mcp) {
        if (mcp == null || isExplicitlyDisabled(mcp) || description(mcp) == null) {
            return Optional.empty();
        }

        var pathTemplate = "/_aggrs/" + aggrUri;
        var declaredParams = mcp.get("params") instanceof BsonDocument pd ? pd : new BsonDocument();
        var referencedNames = PipelineParamScanner.scan(stages);
        var warnings = new ArrayList<String>();

        var builder = McpResource.builder()
                .uri(collectionUri + pathTemplate)
                .kind("aggregation")
                .description(description(mcp));

        var avarsProperties = new LinkedHashMap<String, McpResource.Param>();
        referencedNames.forEach(name -> {
            if (declaredParams.get(name) instanceof BsonDocument paramDef) {
                avarsProperties.put(name, toParam(paramDef));
            } else {
                avarsProperties.put(name, new McpResource.Param("string", null, false, null, null));
                warnings.add("$var '" + name + "' is not declared in mcp.params; defaulted to an optional string");
            }
        });

        declaredParams.keySet().stream()
                .filter(name -> !referencedNames.contains(name))
                .forEach(name -> warnings.add("mcp.params declares '" + name + "' but the pipeline does not reference it"));

        builder.action("execute", a -> {
            a.method("GET");
            // the resource's own uri already IS this aggregation's full address (built just above
            // as collectionUri + pathTemplate) — how_to_call composes url = resource.uri() +
            // action.pathTemplate(), so the action's own path_template must be relative (empty),
            // not repeat pathTemplate, or the rendered URL doubles up "/_aggrs/<uri>"
            a.pathTemplate("");
            a.description(description(mcp));

            // RESTHeart binds $var references from a single JSON query param named "avars"
            // (e.g. ?avars={"status":"A"}), not one query param per variable name — declaring
            // them individually here would make how_to_call render "?status=A", which RESTHeart
            // rejects with QueryVariableNotBoundException
            if (!avarsProperties.isEmpty()) {
                var required = avarsProperties.values().stream().anyMatch(McpResource.Param::required);
                a.param("avars", new McpResource.Param("object", "MongoDB $var bindings for this pipeline.", required, null, null, avarsProperties));
            }
        });

        examples(mcp).forEach(ex -> builder.example(
                stringOrNull(ex, "description"),
                "execute",
                ex.get("args") instanceof BsonDocument args ? BsonJavaConverter.toMap(args) : Map.of()));

        builder.extra("pipeline_summary", pipelineSummary(mcp, stages));
        if (!warnings.isEmpty()) {
            builder.extra("warnings", warnings);
        }

        return Optional.of(builder.build());
    }

    private static boolean isExplicitlyDisabled(BsonDocument mcp) {
        var enabled = mcp.get("enabled");
        return enabled != null && enabled.isBoolean() && !enabled.asBoolean().getValue();
    }

    private static String description(BsonDocument mcp) {
        return stringOrNull(mcp, "description");
    }

    private static String pipelineSummary(BsonDocument mcp, BsonValue stages) {
        var override = stringOrNull(mcp, "pipeline_summary");
        return override != null ? override : PipelineSummarizer.summarize(stages);
    }

    private static List<BsonDocument> examples(BsonDocument mcp) {
        if (!(mcp.get("examples") instanceof BsonArray arr)) {
            return List.of();
        }
        return arr.stream().filter(BsonValue::isDocument).map(BsonValue::asDocument).toList();
    }

    private static String stringOrNull(BsonDocument doc, String key) {
        var v = doc.get(key);
        return v != null && v.isString() ? v.asString().getValue() : null;
    }

    private static McpResource.Param toParam(BsonDocument def) {
        var type = stringOrNull(def, "type");
        var description = stringOrNull(def, "description");
        var required = def.get("required") != null && def.get("required").isBoolean() && def.get("required").asBoolean().getValue();
        List<Object> enumValues = def.get("enum") instanceof BsonArray arr
                ? arr.stream().map(BsonJavaConverter::toJava).toList()
                : null;
        var defaultValue = def.get("default") != null ? BsonJavaConverter.toJava(def.get("default")) : null;
        return new McpResource.Param(type, description, required, enumValues, defaultValue);
    }
}
