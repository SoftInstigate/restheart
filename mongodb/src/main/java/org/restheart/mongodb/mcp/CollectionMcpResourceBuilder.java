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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.plugins.mcp.McpResource;

/**
 * Builds the {@code McpResource} for a MongoDB collection, honoring its own {@code mcp} block
 * (see #616): {@code enabled}, {@code description} (required), and {@code examples}.
 *
 * <p>The {@code actions} map (query/get/create/update/delete) is derived purely from the
 * collection's static metadata (its JSON Schema, for {@code create}/{@code update} body
 * schemas) — it does <b>not</b> filter by the calling principal's ACL. An action a principal
 * cannot actually perform is still described; invoking it fails with RESTHeart's normal 403,
 * exactly as it would for any other REST client. This keeps the builder a pure function of
 * collection metadata, and avoids duplicating ACL enforcement that already exists on the real
 * request path — the agent learns forbidden actions by trying them, same as a human would.
 */
public final class CollectionMcpResourceBuilder {

    private CollectionMcpResourceBuilder() {
    }

    /**
     * @param collectionUri the collection's resource URI (e.g. {@code https://host/db/coll})
     * @param mcp           the collection's own {@code mcp} block, or {@code null} if absent
     * @param jsonSchema    the collection's {@code jsonSchema} metadata, or {@code null} if absent
     * @param aggrs         the collection's {@code aggrs} array, or {@code null} if absent
     * @param streams       the collection's {@code streams} array, or {@code null} if absent
     * @return the resource, or empty if not MCP-enabled: no {@code mcp} block,
     *         {@code mcp.enabled == false}, or a missing required {@code description}
     */
    public static Optional<McpResource> build(String collectionUri, BsonDocument mcp, BsonValue jsonSchema, BsonArray aggrs, BsonArray streams) {
        if (mcp == null || isExplicitlyDisabled(mcp) || description(mcp) == null) {
            return Optional.empty();
        }

        var bodySchema = SchemaContextBuilder.toBodySchema(jsonSchema);

        var builder = McpResource.builder()
                .uri(collectionUri)
                .kind("collection")
                .description(description(mcp))
                .transport(McpResource.Transport.HTTP);

        builder.action("query", a -> {
            a.method("GET").pathTemplate("");
            a.param("filter", "object", false);
            a.param("sort", "string", false);
            a.param("keys", "object", false);
            a.param("page", "integer", false);
            a.param("pagesize", "integer", false);
        });

        builder.action("get", a -> {
            a.method("GET").pathTemplate("/{id}");
            a.param("id", "string", true);
        });

        builder.action("create", a -> {
            a.method("POST");
            if (bodySchema != null) {
                a.bodySchema(bodySchema);
            }
        });

        builder.action("update", a -> {
            a.method("PATCH").pathTemplate("/{id}");
            a.param("id", "string", true);
            if (bodySchema != null) {
                a.bodySchema(bodySchema);
            }
        });

        builder.action("delete", a -> {
            a.method("DELETE").pathTemplate("/{id}");
            a.param("id", "string", true);
        });

        examples(mcp).forEach(ex -> {
            var action = stringOrNull(ex, "action");
            builder.example(
                    stringOrNull(ex, "description"),
                    action != null ? action : "query",
                    ex.get("args") instanceof BsonDocument args ? BsonJavaConverter.toMap(args) : Map.of());
        });

        var aggregationUris = enabledLinkedUris(collectionUri, "/_aggrs/", aggrs);
        if (!aggregationUris.isEmpty()) {
            builder.extra("aggregations", aggregationUris);
        }

        var streamUris = enabledLinkedUris(collectionUri, "/_streams/", streams);
        if (!streamUris.isEmpty()) {
            builder.extra("streams", streamUris);
        }

        return Optional.of(builder.build());
    }

    private static List<String> enabledLinkedUris(String collectionUri, String pathPrefix, BsonArray entries) {
        if (entries == null) {
            return List.of();
        }
        var uris = new ArrayList<String>();
        for (var entry : entries) {
            if (!entry.isDocument()) {
                continue;
            }
            var doc = entry.asDocument();
            var uri = stringOrNull(doc, "uri");
            var entryMcp = doc.get("mcp") instanceof BsonDocument m ? m : null;
            if (uri != null && entryMcp != null && !isExplicitlyDisabled(entryMcp)) {
                uris.add(collectionUri + pathPrefix + uri);
            }
        }
        return uris;
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
