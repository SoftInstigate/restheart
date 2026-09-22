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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.restheart.plugins.mcp.McpResource;

public class CollectionMcpResourceBuilderTest {

    private static final String COLLECTION_URI = "https://host/db/orders";

    @Test
    public void noMcpBlock_notBuilt() {
        assertTrue(CollectionMcpResourceBuilder.build(COLLECTION_URI, null, null, null, null).isEmpty());
    }

    @Test
    public void explicitlyDisabled_notBuilt() {
        var mcp = BsonDocument.parse("{\"enabled\": false, \"description\": \"Orders.\"}");
        assertTrue(CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null).isEmpty());
    }

    @Test
    public void missingDescription_notBuilt() {
        assertTrue(CollectionMcpResourceBuilder.build(COLLECTION_URI, new BsonDocument(), null, null, null).isEmpty());
    }

    @Test
    public void enabledWithDescription_buildsAllFiveActions() {
        var mcp = BsonDocument.parse("{\"description\": \"Customer orders.\"}");

        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null).orElseThrow();

        assertEquals(COLLECTION_URI, resource.uri());
        assertEquals("collection", resource.kind());
        assertEquals("Customer orders.", resource.description());

        var actions = resource.actions();
        assertEquals("GET", actions.get("query").method());
        assertEquals("", actions.get("query").pathTemplate());
        assertEquals("GET", actions.get("get").method());
        assertEquals("/{id}", actions.get("get").pathTemplate());
        assertEquals("POST", actions.get("create").method());
        assertEquals("PATCH", actions.get("update").method());
        assertEquals("/{id}", actions.get("update").pathTemplate());
        assertEquals("PUT", actions.get("replace").method());
        assertEquals("/{id}", actions.get("replace").pathTemplate());
        assertEquals("DELETE", actions.get("delete").method());
        assertEquals("/{id}", actions.get("delete").pathTemplate());
    }

    @Test
    public void queryAction_declaresStandardParams() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null).orElseThrow();

        var params = resource.actions().get("query").params();
        assertEquals("object", params.get("filter").type());
        assertEquals("string", params.get("sort").type());
        assertEquals("object", params.get("keys").type());
        assertEquals("integer", params.get("page").type());
        assertEquals("integer", params.get("pagesize").type());
        // every query param is optional: a bare read is a legitimate request (first page, no
        // filter), which is what lets a collection be offered as a concrete resource as well as
        // a template -- see McpService.syncResourceRegistry
        assertFalse(params.get("page").required());
        assertFalse(params.get("filter").required());
    }

    @Test
    public void jsonSchemaPresent_usedAsBodySchemaForTheWholeDocumentWritesOnly() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var jsonSchema = BsonDocument.parse("""
                { "type": "object", "properties": { "sku": { "type": "string" } } }
                """);

        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, jsonSchema, null, null).orElseThrow();

        assertEquals("object", resource.actions().get("create").bodySchema().get("type"));
        assertEquals("object", resource.actions().get("replace").bodySchema().get("type"));
        // a PATCH body is a partial document or an update document: validated against the schema
        // of a whole one, it fails on what it rightly leaves out
        assertNull(resource.actions().get("update").bodySchema());
        assertNull(resource.actions().get("update_many").bodySchema());
        assertFalse(resource.actions().get("create").description().contains("no schema"));
    }

    @Test
    public void noJsonSchema_noBodySchemaOnCreateOrUpdate() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null).orElseThrow();

        assertNull(resource.actions().get("create").bodySchema());
        assertNull(resource.actions().get("update").bodySchema());

        // without a schema the agent is sent to the data before writing
        for (var name : List.of("create", "replace", "update")) {
            assertTrue(resource.actions().get(name).description().contains("read some existing documents first"), name);
        }
    }

    /**
     * The schema store takes a schema whole — PATCH answers 405 — and its writes say how a BSON
     * type is declared, since $date inside a schema is read as a date value and refused.
     */
    @Test
    public void theSchemaStore_hasNoPatchActions_andSaysHowToDeclareBsonTypes() {
        var mcp = BsonDocument.parse("{\"description\": \"The schemas.\"}");
        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null, null, true).orElseThrow();

        assertNull(resource.actions().get("update"));
        assertNull(resource.actions().get("update_many"));
        assertNull(resource.actions().get("delete_many"));

        for (var name : List.of("create", "replace")) {
            var description = resource.actions().get(name).description();
            assertTrue(description.contains("JSON Schema"), name);
            assertTrue(description.contains("_$date"), name);
            assertFalse(description.contains("read some existing documents first"), name);
        }
    }

    /** With a schema, the Extended JSON sentence defers to its types. */
    @Test
    public void withASchema_theWritesDeferToItsTypes() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var jsonSchema = BsonDocument.parse("{ \"type\": \"object\" }");
        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, jsonSchema, null, null).orElseThrow();

        assertTrue(resource.actions().get("create").description().contains("follow its types"));
        assertTrue(resource.actions().get("replace").description().contains("follow its types"));
    }

    /** Each write says what its body is, and that its values are Extended JSON. */
    @Test
    public void writesSayWhatTheBodyIs() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null).orElseThrow();

        assertTrue(resource.actions().get("create").description().contains("the whole document"));
        assertTrue(resource.actions().get("replace").description().contains("the whole document"));
        assertTrue(resource.actions().get("replace").description().contains("fields the body omits are removed"));
        assertTrue(resource.actions().get("update").description().contains("a partial document"));
        assertTrue(resource.actions().get("update_many").description().contains("a partial document"));

        for (var name : List.of("create", "replace", "update", "update_many")) {
            var description = resource.actions().get(name).description();
            assertTrue(description.contains("{\"$date\": <epoch millis>}"), name);
            assertTrue(description.contains("{\"$oid\": \"<24 hex digits>\"}"), name);
        }
    }

    @Test
    public void examples_convertedWithDeclaredAction() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "examples": [
                    {"description": "Find low-stock", "action": "query", "args": {"filter": {"quantity": {"$lt": 10}}}}
                ]}
                """);

        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null).orElseThrow();

        assertEquals(1, resource.examples().size());
        assertEquals("Find low-stock", resource.examples().get(0).description());
        assertEquals("query", resource.examples().get(0).action());
    }

    @Test
    public void exampleWithNoDeclaredAction_defaultsToQuery() {
        var mcp = BsonDocument.parse("""
                {"description": "x", "examples": [{"description": "y", "args": {}}]}
                """);
        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null).orElseThrow();
        assertEquals("query", resource.examples().get(0).action());
    }

    @Test
    public void enabledAggrsAndStreams_linkedInExtra() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var aggrs = BsonArray.parse("""
                [{"uri": "byStatus", "stages": [], "mcp": {"enabled": true, "description": "y"}}]
                """);
        var streams = BsonArray.parse("""
                [{"uri": "lowStock", "stages": [], "mcp": {"enabled": true, "description": "z"}}]
                """);

        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, aggrs, streams).orElseThrow();

        assertEquals(List.of(COLLECTION_URI + "/_aggrs/byStatus"), resource.extra().get("aggregations"));
        assertEquals(List.of(COLLECTION_URI + "/_streams/lowStock"), resource.extra().get("streams"));
    }

    @Test
    public void disabledOrMcpLessAggrsAndStreams_notLinked() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var aggrs = BsonArray.parse("""
                [
                    {"uri": "byStatus", "stages": [], "mcp": {"enabled": false, "description": "y"}},
                    {"uri": "noMcp", "stages": []}
                ]
                """);

        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, aggrs, null).orElseThrow();

        assertNull(resource.extra().get("aggregations"));
    }

    @Test
    public void noAggrsOrStreams_extraOmitsBothKeys() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null).orElseThrow();

        assertTrue(resource.extra().isEmpty());
    }

    @Test
    public void constraintsDeclared_writeActionsExplainTheThree409s() {
        var mcp = BsonDocument.parse("{\"description\": \"A ledger.\"}");
        var constraints = BsonArray.parse("[{\"name\": \"noNegativeHoldings\", \"stages\": []}, {\"name\": \"noSelfDealing\", \"stages\": []}, {\"stages\": []}]");

        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null, constraints).orElseThrow();

        for (var action : List.of("create", "replace", "update", "delete")) {
            var guidance = resource.actions().get(action).description();
            assertTrue(guidance.contains("\"retryable\": true"), action + " must say what retryable means");
            assertTrue(guidance.contains("\"constraint\""), action + " must say what a violated rule looks like");
            assertTrue(guidance.contains("noNegativeHoldings, noSelfDealing"), action + " must name the rules, and skip the nameless one");
            assertTrue(guidance.contains("do not retry"), action + " must say a violation is not to be retried");
        }
    }

    @Test
    public void noConstraints_createSaysOnlyThatTheIdIsTaken() {
        var mcp = BsonDocument.parse("{\"description\": \"Orders.\"}");

        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null, null).orElseThrow();

        var create = resource.actions().get("create").description();
        assertTrue(create.contains("_id already exists"));
        assertFalse(create.contains("retryable"), "no transaction, so no write conflict to retry");
        // update says what PATCH actually does, which is the thing most likely to be assumed wrongly
        var update = resource.actions().get("update").description();
        assertTrue(update.contains("does not exist is not created"), update);
        assertTrue(update.contains("$inc"), "the body may use update operators: " + update);
        assertFalse(update.contains("wm"), "the write mode is not offered: it needs its own permission");

        // delete always says what it deletes: on a collection resource the name reads as "delete
        // the collection", and the action that does that is a different one
        var delete = resource.actions().get("delete").description();
        assertTrue(delete.contains("one document"), delete);
        assertTrue(delete.contains("'drop'"), delete);
        assertFalse(delete.contains("retryable"), "no transaction, so no write conflict to retry");
    }

    /**
     * The operations on the collection itself, which the ACL decides like any other — published
     * with what tells them apart from the ones on its documents.
     */
    @Test
    public void theDataManagementApiIsPublished_markedAsActingOnTheResource() {
        var mcp = BsonDocument.parse("{\"description\": \"Orders.\"}");

        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null, null).orElseThrow();

        for (var name : List.of("properties", "set_properties", "drop", "indexes", "create_index",
                "delete_index")) {
            var action = resource.actions().get(name);

            assertNotNull(action, name + " is not published");
            assertEquals(McpResource.Target.RESOURCE, action.target(), name + " acts on the collection itself");
            assertTrue(action.requires().contains("mongo.allowManagementRequests"),
                    name + " must name the switch its permission needs: " + action.requires());
        }

        // and the ones on documents stay as they are
        for (var name : List.of("query", "get", "create", "replace", "update", "delete")) {
            assertEquals(McpResource.Target.ELEMENT, resource.actions().get(name).target(), name);
        }
    }

    /** Writing many documents at once is published too, gated by its own switch and needing a filter. */
    @Test
    public void theBulkWritesArePublished_withTheFilterRequired() {
        var mcp = BsonDocument.parse("{\"description\": \"Orders.\"}");

        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null, null).orElseThrow();

        for (var entry : java.util.Map.of("update_many", "mongo.allowBulkPatch",
                "delete_many", "mongo.allowBulkDelete").entrySet()) {
            var action = resource.actions().get(entry.getKey());

            assertNotNull(action, entry.getKey() + " is not published");
            assertEquals("/*", action.pathTemplate());
            assertTrue(action.requires().contains(entry.getValue()), action.requires().toString());
            assertTrue(action.params().get("filter").required(),
                    "without a filter this writes every document in the collection");
        }
    }

    /**
     * Dropping a collection needs its current ETag in {@code If-Match}; as a query parameter it
     * would be a request missing a condition, answered with a 409 the agent cannot interpret.
     */
    @Test
    public void theDropDeclaresItsEtagAsAHeader() {
        var mcp = BsonDocument.parse("{\"description\": \"Orders.\"}");

        var resource = CollectionMcpResourceBuilder.build(COLLECTION_URI, mcp, null, null, null, null).orElseThrow();
        var etag = resource.actions().get("drop").params().get("etag");

        assertNotNull(etag);
        assertTrue(etag.required());
        assertEquals("If-Match", etag.header());
    }
}
