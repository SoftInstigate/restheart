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
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.plugins.mcp.CatalogCondition;
import org.restheart.plugins.mcp.BsonJava;
import org.restheart.plugins.mcp.McpResource;

/**
 * Builds the {@code McpResource} for a MongoDB collection, honoring its own {@code mcp} block
 * (see #616): {@code enabled}, {@code description} (required), and {@code examples}.
 *
 * <p>The {@code actions} map (query/get/create/update/delete) is derived purely from the
 * collection's static metadata (its JSON Schema, for {@code create}/{@code update} body
 * schemas) — it does <b>not</b> filter by the calling principal's ACL, which keeps the builder a
 * pure function of that metadata and duplicates no enforcement: invoking an action a principal
 * cannot perform fails with RESTHeart's normal 403, exactly as it would for any other REST client.
 *
 * <p>What a caller is <em>shown</em> is narrowed later, once, where the catalogue is rendered:
 * {@code list_apis} describes a resource with only the actions that caller could invoke (see
 * {@code CatalogVisibility.invokableActions} in restheart-ai). Advertising a write the ACL refuses
 * reads as an offer; refusing it is still the real request path's job, not this builder's.
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
        return build(collectionUri, mcp, jsonSchema, aggrs, streams, null);
    }

    /**
     * @param constraints the collection's {@code constraints} array, or {@code null} if absent. With
     *                    rules declared, every write to the collection runs in a transaction and can
     *                    fail in two more ways than a plain one, both as {@code 409}; the write actions
     *                    say so, since an agent cannot tell the two apart from the status alone.
     */
    public static Optional<McpResource> build(String collectionUri, BsonDocument mcp, BsonValue jsonSchema, BsonArray aggrs, BsonArray streams, BsonArray constraints) {
        if (mcp == null || isExplicitlyDisabled(mcp) || description(mcp) == null) {
            return Optional.empty();
        }

        var bodySchema = SchemaContextBuilder.toBodySchema(jsonSchema);

        var builder = McpResource.builder()
                .uri(collectionUri)
                .kind("collection")
                // MongoMcpAwareImpl.watch opens a change stream on the collection; an aggregation
                // over it shares that same stream. What stays unsubscribable is what has no
                // collection to watch or nothing to re-read — a GraphQL app, a service, a change
                // stream — and there resources/subscribe is refused rather than left silent.
                .subscribable(true)
                .description(description(mcp))
                .showIf(CatalogCondition.showIf(mcp))
                .hideFromRoles(CatalogCondition.hideFromRoles(mcp))
                .transport(McpResource.Transport.HTTP);

        builder.action("query", a -> {
            a.method("GET").pathTemplate("").readable(true);
            a.param("filter", "object", false);
            a.param("sort", "string", false);
            a.param("keys", "object", false);
            a.param("page", "integer", false);
            a.param("pagesize", "integer", false);
            a.param("jsonMode", "string", false);
        });

        builder.action("get", a -> {
            a.method("GET").pathTemplate("/{id}").readable(true);
            a.param("id", "string", true);
            a.param("jsonMode", "string", false);
        });

        // The count is its own entry, exactly as it is its own endpoint in the REST API — not a
        // flag on the read. It takes the same filter the read does: GET /<coll>/_size?filter={...}
        // counts that query, which is the count an agent usually wants.
        builder.action("size", a -> {
            a.method("GET").pathTemplate("/_size").readable(true).target(McpResource.Target.RESOURCE);
            a.description("Number of documents matching the filter, or in the whole collection if no filter is given.");
            a.param("filter", "object", false);
            a.param("count", new McpResource.Param("string",
                    "Optional. 'estimated' counts from collection metadata in constant time instead of scanning; "
                            + "ignored when a filter is given, since only an exact count can apply one.",
                    false, List.of("estimated"), null));
        });

        var rules = ruleNames(constraints);

        builder.action("create", a -> {
            a.method("POST");
            a.description(rules.isEmpty() ? DUPLICATE_ID : writeGuidance(rules));
            if (bodySchema != null) {
                a.bodySchema(bodySchema);
            }
        });

        builder.action("update", a -> {
            a.method("PATCH").pathTemplate("/{id}");
            a.param("id", "string", true);
            if (!rules.isEmpty()) {
                a.description(writeGuidance(rules));
            }
            if (bodySchema != null) {
                a.bodySchema(bodySchema);
            }
        });

        builder.action("delete", a -> {
            a.method("DELETE").pathTemplate("/{id}");
            a.param("id", "string", true);
            a.description("Deletes one document. To delete the collection itself, with every document in it, "
                    + "the action is 'drop'."
                    + (rules.isEmpty() ? "" : " " + writeGuidance(rules)));
        });

        bulkActions(builder, bodySchema, rules);
        managementActions(builder);

        examples(mcp).forEach(ex -> {
            var action = stringOrNull(ex, "action");
            builder.example(
                    stringOrNull(ex, "description"),
                    action != null ? action : "query",
                    ex.get("args") instanceof BsonDocument args ? BsonJava.toMap(args) : Map.of());
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

    /**
     * Writing many documents at once: {@code PATCH} and {@code DELETE} on {@code /<coll>/*} with a
     * filter, which the REST API answers and the ACL decides like any other request.
     *
     * <p>They take two conditions, both necessary, exactly as the management ones do: a permission
     * matching the method and the path — an exact {@code path('/<coll>')} does not, a
     * {@code path-prefix} does — and the switch of that permission's {@code mongo} block, off
     * unless written. The filter is required here and not optional: a bulk write without one is a
     * write to every document in the collection.
     */
    private static void bulkActions(McpResource.Builder builder, Map<String, Object> bodySchema, List<String> rules) {
        builder.action("update_many", a -> {
            a.method("PATCH").pathTemplate("/*").requires("mongo.allowBulkPatch");
            a.description("Applies this change to every document matching the filter, in one request."
                    + (rules.isEmpty() ? "" : " " + writeGuidance(rules)));
            a.param("filter", new McpResource.Param("object",
                    "Which documents to change. Required: without it this would change all of them.",
                    true, null, null));
            if (bodySchema != null) {
                a.bodySchema(bodySchema);
            }
        });

        builder.action("delete_many", a -> {
            a.method("DELETE").pathTemplate("/*").requires("mongo.allowBulkDelete");
            a.description("Deletes every document matching the filter, in one request. Not reversible.");
            a.param("filter", new McpResource.Param("object",
                    "Which documents to delete. Required: without it this would delete all of them.",
                    true, null, null));
        });
    }

    /**
     * The operations that act on the collection itself rather than on its documents — the data
     * management API, <a href="https://restheart.org/docs/mongodb-rest/dbs-collections">documented
     * here</a> and for <a href="https://restheart.org/docs/mongodb-rest/indexes">indexes here</a>.
     *
     * <p>Published like any other action, because they are legitimate operations and the ACL is
     * what decides whether they may be performed. They take two conditions, both necessary: a
     * permission matching the method and the path, <em>and</em> {@code allowManagementRequests} in
     * that permission's {@code mongo} block, which is off unless written. Neither grants on its own.
     */
    private static void managementActions(McpResource.Builder builder) {
        builder.action("properties", a -> {
            a.method("GET").pathTemplate("/_meta").target(McpResource.Target.RESOURCE).requires("mongo.allowManagementRequests");
            a.description("The collection's own properties, metadata included: jsonSchema, aggrs, streams, "
                    + "constraints, mcp. Carries the _etag that 'drop' requires.");
        });

        builder.action("set_properties", a -> {
            a.method("PATCH").pathTemplate("").target(McpResource.Target.RESOURCE).requires("mongo.allowManagementRequests");
            a.description("Merges these properties into the collection's own. This changes how the collection "
                    + "behaves — jsonSchema, aggrs, streams, constraints, and the mcp block that publishes it — "
                    + "not the documents in it.");
        });

        builder.action("drop", a -> {
            a.method("DELETE").pathTemplate("").target(McpResource.Target.RESOURCE).requires("mongo.allowManagementRequests");
            a.description("Deletes the collection and every document in it. Not reversible. Requires the "
                    + "collection's current _etag, which 'properties' returns: without it the answer is 409.");
            a.param("etag", new McpResource.Param("string",
                    "The collection's current _etag, read with the 'properties' action.",
                    true, null, null).asHeader("If-Match"));
        });

        builder.action("indexes", a -> {
            a.method("GET").pathTemplate("/_indexes").target(McpResource.Target.RESOURCE).requires("mongo.allowManagementRequests");
            a.description("The collection's indexes.");
        });

        builder.action("create_index", a -> {
            a.method("PUT").pathTemplate("/_indexes/{name}").target(McpResource.Target.RESOURCE).requires("mongo.allowManagementRequests");
            a.description("Creates an index under this name. An index is never updated: to change one, delete it "
                    + "and create it again. Options that do not hold for the data — 'unique' on a property that "
                    + "is not — answer 406.");
            a.param("name", "string", true);
            a.bodySchema(Map.of(
                    "type", "object",
                    "required", List.of("keys"),
                    "properties", Map.of(
                            "keys", Map.of("type", "object", "description", "The indexed properties, as MongoDB names them: {\"qty\": 1}."),
                            "ops", Map.of("type", "object", "description", "Index options, as MongoDB names them: {\"unique\": true}."))));
        });

        builder.action("delete_index", a -> {
            a.method("DELETE").pathTemplate("/_indexes/{name}").target(McpResource.Target.RESOURCE).requires("mongo.allowManagementRequests");
            a.description("Deletes the index of this name.");
            a.param("name", "string", true);
        });
    }

    private static final String DUPLICATE_ID = "409 Conflict means a document with this _id already exists.";

    /**
     * What a write's {@code 409} means here — the one thing an agent cannot work out from the
     * status, and the thing it most needs to get right: one of the three answers must be retried
     * as it is, one must never be, and the third is somebody else's document.
     *
     * <p>Told from the write action itself, so that {@code how_to_call} hands it over in the same
     * descriptor as the request, at the moment the agent is about to send it.
     */
    static String writeGuidance(List<String> rules) {
        return "409 Conflict means one of three things, and the body says which. "
                + "\"retryable\": true — two writes collided and this one was NOT applied: send exactly the same request again. "
                + "\"constraint\": \"<name>\" — the write broke one of this collection's rules (" + String.join(", ", rules) + "): "
                + "\"message\" says why and \"violations\" lists the documents; do not retry, the answer will not change. "
                + "Neither — a document with this _id already exists.";
    }

    /** The names of the declared rules, for the guidance; an entry without one is skipped. */
    private static List<String> ruleNames(BsonArray constraints) {
        if (constraints == null) {
            return List.of();
        }
        return constraints.stream()
                .filter(c -> c.isDocument() && c.asDocument().get("name") instanceof BsonString)
                .map(c -> c.asDocument().getString("name").getValue())
                .toList();
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
