/*-
 * ========================LICENSE_START=================================
 * restheart-core
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
package org.restheart.test.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kong.unirest.Unirest;

/**
 * Integration test for #616: a plain MongoDB collection with an {@code mcp} block, exercised
 * end-to-end through the real {@code /mcp} JSON-RPC endpoint — {@code list_apis} (catalog +
 * resource context) and {@code how_to_call} (composed descriptor, then actually executed
 * against the real REST API, not just shape-checked).
 *
 * <p>Requires a running RESTHeart instance connected to MongoDB. The test database is prefixed
 * with {@code test-} so {@link AbstactIT}'s teardown cleans it up automatically.
 */
public class McpMongoCollectionIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String TEST_DB = BASE + "/test-mcp-collection";
    private static final String TEST_COLL = TEST_DB + "/products";
    private static final String COLL_URI = TEST_COLL;

    private static final String ADMIN_BASIC = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());

    private McpTestClient mcp;

    @BeforeEach
    void setupCollectionAndSession() throws Exception {
        Unirest.put(TEST_DB).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();

        // RESTHeart's jsonSchema metadata is always a {schemaId} pointer, never an inline
        // schema — the actual schema body lives in the collection's own _schemas store
        // the _schemas collection itself needs explicit creation first, exactly like any other
        // collection — confirmed against core/src/test/java/karate/json-schema.feature
        Unirest.put(TEST_DB + "/_schemas").basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();

        var schemaResp = Unirest.post(TEST_DB + "/_schemas")
                .basicAuth("admin", "secret")
                .contentType("application/json")
                .body("""
                        {
                          "_id": "product",
                          "$schema": "http://json-schema.org/draft-04/schema#",
                          "type": "object",
                          "required": ["sku"],
                          "properties": { "sku": { "type": "string" }, "qty": { "type": "integer" } }
                        }
                        """)
                .asEmpty();
        assertTrue(schemaResp.getStatus() == 200 || schemaResp.getStatus() == 201, "schema setup failed with status " + schemaResp.getStatus());

        var resp = Unirest.put(TEST_COLL)
                .basicAuth("admin", "secret")
                .contentType("application/json")
                .body("""
                        {
                          "jsonSchema": { "schemaId": "product" },
                          "mcp": {
                            "enabled": true,
                            "description": "Product catalog (MCP IT).",
                            "examples": [ { "description": "Find all products", "action": "query", "args": { "filter": {} } } ]
                          }
                        }
                        """)
                .asEmpty();
        assertTrue(resp.getStatus() == 200 || resp.getStatus() == 201, "collection setup failed with status " + resp.getStatus());

        // see McpGraphqlAppIT: wait past CachedResourceLookup's TTL so this class's own
        // just-created resource isn't served from a stale cache entry
        Thread.sleep(1_500);

        mcp = new McpTestClient(BASE, ADMIN_BASIC);
        mcp.initialize();
    }

    @Test
    void catalog_includesTheMcpEnabledCollection() throws Exception {
        var catalog = mcp.callTool("list_apis", "{}");

        var found = catalog.getArray("resources").stream()
                .map(v -> v.asDocument())
                .anyMatch(r -> COLL_URI.equals(r.getString("uri").getValue())
                        && "collection".equals(r.getString("kind").getValue()));

        assertTrue(found, "catalog must include " + COLL_URI + "; got: " + catalog.toJson());
    }

    @Test
    void resourceContext_showsBodySchemaActionsAndExamples() throws Exception {
        var context = mcp.callTool("list_apis", "{\"resource\": \"" + COLL_URI + "\"}");

        assertEquals("collection", context.getString("kind").getValue());
        assertEquals("Product catalog (MCP IT).", context.getString("description").getValue());

        var actions = context.getDocument("actions");
        assertTrue(actions.containsKey("query"));
        assertTrue(actions.containsKey("get"));
        assertTrue(actions.containsKey("create"));
        assertTrue(actions.containsKey("update"));
        assertTrue(actions.containsKey("delete"));

        var createBodySchema = actions.getDocument("create").getDocument("body_schema");
        assertTrue(createBodySchema.getDocument("properties").containsKey("sku"));

        assertEquals(1, context.getArray("examples").size());
    }

    @Test
    void howToCallQuery_composedDescriptorActuallyWorks() throws Exception {
        Unirest.post(TEST_COLL).basicAuth("admin", "secret").contentType("application/json")
                .body("{\"sku\": \"widget-1\", \"qty\": 5}").asEmpty();

        var descriptor = mcp.callTool("how_to_call",
                "{\"resource\": \"" + COLL_URI + "\", \"action\": \"query\", \"args\": {\"filter\": {\"sku\": \"widget-1\"}}}");

        assertEquals("http", descriptor.getString("transport").getValue());
        assertEquals("GET", descriptor.getString("method").getValue());

        var url = descriptor.getString("url").getValue();
        var resp = Unirest.get(url).basicAuth("admin", "secret").asString();

        assertEquals(200, resp.getStatus());
        assertTrue(resp.getBody().contains("widget-1"), "query result must contain the inserted document; got: " + resp.getBody());
    }

    @Test
    void howToCallCreate_composedDescriptorActuallyCreatesADocument() throws Exception {
        var descriptor = mcp.callTool("how_to_call",
                "{\"resource\": \"" + COLL_URI + "\", \"action\": \"create\", \"args\": {\"body\": {\"sku\": \"widget-2\", \"qty\": 10}}}");

        assertEquals("POST", descriptor.getString("method").getValue());
        var url = descriptor.getString("url").getValue();

        var resp = Unirest.post(url).basicAuth("admin", "secret").contentType("application/json")
                .body("{\"sku\": \"widget-2\", \"qty\": 10}").asEmpty();
        assertTrue(resp.getStatus() == 200 || resp.getStatus() == 201, "create must succeed, got " + resp.getStatus());

        var verify = Unirest.get(TEST_COLL).basicAuth("admin", "secret").queryString("filter", "{\"sku\":\"widget-2\"}").asString();
        assertTrue(verify.getBody().contains("widget-2"), "created document must be retrievable; got: " + verify.getBody());
    }
}
