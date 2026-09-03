/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.graphql.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.plugins.mcp.McpResource;

/**
 * Builds the {@code McpResource} for a GraphQL app, honoring its own top-level {@code mcp} block
 * (see #616): {@code enabled}, {@code description} (required), {@code examples}.
 *
 * <p>Unlike the MongoDB kinds, a GraphQL app exposes a single, fixed {@code execute} action —
 * every query and mutation goes through the same {@code POST} with a standard GraphQL-over-HTTP
 * body ({@code query}/{@code variables}/{@code operationName}, see {@code GraphQLRequest} in
 * commons) — so {@code body_schema} documents that fixed envelope, not any one operation. The
 * SDL-derived {@code Query}/{@code Mutation} field list (parsed via {@link SdlContextBuilder})
 * is documentation of what can go in the {@code query} string, not invocation params in the
 * REST sense, so it surfaces under {@code extra("operations", ...)} rather than {@code params} —
 * the same pattern MongoDB's builders use for pipeline-specific extras
 * ({@code org.restheart.mongodb.mcp.AggregationMcpResourceBuilder}).
 */
public final class GraphqlAppMcpResourceBuilder {

    private GraphqlAppMcpResourceBuilder() {
    }

    /**
     * @param appUri the app's resource URI (e.g. {@code https://host/graphql/warehouse})
     * @param mcp    the app document's own top-level {@code mcp} block, or {@code null} if absent
     * @param sdl    the app's raw SDL ({@code schema} field), used to list its queries/mutations
     * @return the resource, or empty if not MCP-enabled: no {@code mcp} block,
     *         {@code mcp.enabled == false}, or a missing required {@code description}
     */
    public static Optional<McpResource> build(String appUri, BsonDocument mcp, String sdl) {
        if (mcp == null || isExplicitlyDisabled(mcp) || description(mcp) == null) {
            return Optional.empty();
        }

        var builder = McpResource.builder()
                .uri(appUri)
                .kind("graphql-app")
                .description(description(mcp))
                .transport(McpResource.Transport.HTTP);

        builder.action("execute", a -> {
            a.method("POST");
            // the resource's own uri already is this app's full address, same convention as
            // MongoDB's aggregation/change-stream actions
            a.pathTemplate("");
            a.description(description(mcp));
            a.bodySchema(bodySchema());
        });

        examples(mcp).forEach(ex -> builder.example(
                stringOrNull(ex, "description"),
                "execute",
                ex.get("args") instanceof BsonDocument args ? BsonJavaConverter.toMap(args) : Map.of()));

        var operations = new LinkedHashMap<String, Object>();
        var queries = SdlContextBuilder.queries(sdl);
        var mutations = SdlContextBuilder.mutations(sdl);
        if (!queries.isEmpty()) {
            operations.put("queries", queries.stream().map(GraphqlAppMcpResourceBuilder::toMap).toList());
        }
        if (!mutations.isEmpty()) {
            operations.put("mutations", mutations.stream().map(GraphqlAppMcpResourceBuilder::toMap).toList());
        }
        if (!operations.isEmpty()) {
            builder.extra("operations", operations);
        }

        return Optional.of(builder.build());
    }

    private static Map<String, Object> bodySchema() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("query", Map.of("type", "string", "description", "GraphQL query or mutation document."));
        properties.put("variables", Map.of("type", "object", "description", "Variables referenced by the query."));
        properties.put("operationName", Map.of("type", "string", "description",
                "Name of the operation to execute, if the document defines more than one."));

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("required", List.of("query"));
        schema.put("properties", properties);
        return schema;
    }

    private static Map<String, Object> toMap(SdlContextBuilder.Operation op) {
        var m = new LinkedHashMap<String, Object>();
        m.put("name", op.name());
        m.put("args", op.args().stream().map(GraphqlAppMcpResourceBuilder::toMap).toList());
        m.put("return_type", op.returnType());
        return m;
    }

    private static Map<String, Object> toMap(SdlContextBuilder.Arg arg) {
        var m = new LinkedHashMap<String, Object>();
        m.put("name", arg.name());
        m.put("type", arg.type());
        m.put("required", arg.required());
        return m;
    }

    private static boolean isExplicitlyDisabled(BsonDocument mcp) {
        var enabled = mcp.get("enabled");
        return enabled != null && enabled.isBoolean() && !enabled.asBoolean().getValue();
    }

    private static String description(BsonDocument mcp) {
        return stringOrNull(mcp, "description");
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
}
