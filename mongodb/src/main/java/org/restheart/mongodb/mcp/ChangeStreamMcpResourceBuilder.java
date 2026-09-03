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
 * Builds the {@code McpResource} for one change stream declared in a collection's
 * {@code streams} metadata, honoring its own {@code mcp} block — the same shape
 * {@link AggregationMcpResourceBuilder} uses for {@code aggrs}: {@code enabled},
 * {@code description} (required), {@code params} (per-variable overrides for the stream's
 * filter {@code stages}' {@code $var} references), {@code examples}, {@code pipeline_summary}
 * (an operator-supplied override for the {@link PipelineSummarizer} heuristic), and
 * {@code event_type} (a free-text description of the change-event payload this stream emits).
 *
 * <p>Unlike an aggregation, a change stream is a long-lived subscription — RESTHeart serves
 * the same {@code _streams/<uri>} endpoint over both WebSocket and SSE — so the resource
 * declares a single {@code subscribe} action over both the {@code WEBSOCKET} and {@code SSE}
 * transports rather than {@code execute} over {@code HTTP}.
 *
 * <p>As with {@link AggregationMcpResourceBuilder}, declared variable types surface as a single
 * {@code avars} object param (one {@code properties} entry per variable), matching how RESTHeart
 * actually binds {@code $var} references — {@code ?avars={"name":"value",...}}.
 */
public final class ChangeStreamMcpResourceBuilder {

    private ChangeStreamMcpResourceBuilder() {
    }

    /**
     * @param collectionUri the owning collection's resource URI (e.g. {@code https://host/db/coll})
     * @param streamUri     the change stream's own {@code uri} ({@code streams[].uri})
     * @param stages        the change stream's filter pipeline ({@code streams[].stages})
     * @param mcp           the change stream entry's own {@code mcp} block, or {@code null} if absent
     * @return the resource, or empty if not MCP-enabled: no {@code mcp} block,
     *         {@code mcp.enabled == false}, or a missing required {@code description}
     */
    public static Optional<McpResource> build(String collectionUri, String streamUri, BsonValue stages, BsonDocument mcp) {
        if (mcp == null || isExplicitlyDisabled(mcp) || description(mcp) == null) {
            return Optional.empty();
        }

        var pathTemplate = "/_streams/" + streamUri;
        var declaredParams = mcp.get("params") instanceof BsonDocument pd ? pd : new BsonDocument();
        var referencedNames = PipelineParamScanner.scan(stages);
        var warnings = new ArrayList<String>();

        var builder = McpResource.builder()
                .uri(collectionUri + pathTemplate)
                .kind("change-stream")
                .description(description(mcp))
                .transport(McpResource.Transport.WEBSOCKET, "subscribe")
                .transport(McpResource.Transport.SSE, "subscribe");

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

        builder.action("subscribe", a -> {
            a.method("GET");
            // see AggregationMcpResourceBuilder: the resource's own uri already is this stream's
            // full address, so the action's path_template must be relative (empty)
            a.pathTemplate("");
            a.description(description(mcp));

            // same avars convention as AggregationMcpResourceBuilder: RESTHeart's change-stream
            // handler binds $var references via StagesInterpolator, identically to aggregations
            if (!avarsProperties.isEmpty()) {
                var required = avarsProperties.values().stream().anyMatch(McpResource.Param::required);
                a.param("avars", new McpResource.Param("object", "MongoDB $var bindings for this pipeline.", required, null, null, avarsProperties));
            }
        });

        examples(mcp).forEach(ex -> builder.example(
                stringOrNull(ex, "description"),
                "subscribe",
                ex.get("args") instanceof BsonDocument args ? BsonJavaConverter.toMap(args) : Map.of()));

        builder.extra("pipeline_summary", pipelineSummary(mcp, stages));
        if (stringOrNull(mcp, "event_type") != null) {
            builder.extra("event_type", stringOrNull(mcp, "event_type"));
        }
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
