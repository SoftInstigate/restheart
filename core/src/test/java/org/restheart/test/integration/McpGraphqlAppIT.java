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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kong.unirest.Unirest;

/**
 * Integration test for #616: a GraphQL app with a top-level {@code mcp} block, exercised
 * end-to-end through {@code /mcp} — confirms {@code GraphqlAppMcpResourceBuilder}'s fixed
 * {@code execute} action/body_schema, the SDL-derived {@code operations.queries} list, and that
 * the composed descriptor actually executes a real GraphQL query.
 *
 * <p>The app document lives in {@code test-graphql.gql-apps} — {@code core/src/test/resources/
 * etc/conf-overrides.yml} overrides {@code graphql.db} to {@code test-graphql} for the whole IT
 * suite (the shipped default is {@code restheart}; the code-level default is {@code restheart}/
 * {@code gqlapps}, itself overridden by {@code restheart-default-config.yml} to {@code gql-apps}
 * with a hyphen — three different values across three layers, confirmed by reading each). Its
 * own {@code mongo-mounts} override exposes every database at {@code /db/coll} (a {@code
 * what: "*", where: "/"} wildcard mount), unlike a from-scratch default install where a single
 * database can be flattened at root — so the URL shape here is specific to the IT environment,
 * not a general RESTHeart default. {@code test-graphql} is a shared fixture database (existing
 * karate GraphQL features use it too), so this test deletes only its own document in
 * {@code @AfterEach} rather than relying on {@link AbstactIT}'s blanket {@code test-*}
 * database drop, which would wipe out other tests' data.
 */
public class McpGraphqlAppIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String GQL_APPS = BASE + "/test-graphql/gql-apps";
    private static final String APP_ID = "test-mcp-warehouse";
    private static final String DATA_DB = BASE + "/test-mcp-graphqlapp";
    private static final String DATA_COLL = DATA_DB + "/products";
    private static final String APP_URI = BASE + "/graphql/" + APP_ID;

    private static final String ADMIN_BASIC = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());

    private McpTestClient mcp;

    @BeforeEach
    void setupAppAndSession() throws Exception {
        Unirest.put(DATA_DB).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();
        Unirest.put(DATA_COLL).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();
        Unirest.post(DATA_COLL).basicAuth("admin", "secret").contentType("application/json")
                .body("{\"sku\": \"widget\", \"qty\": 7}").asEmpty();

        // test-graphql is a shared fixture db (existing karate GraphQL features use it too) but
        // its gql-apps collection isn't guaranteed to already exist — create both defensively
        Unirest.put(BASE + "/test-graphql").basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();
        Unirest.put(GQL_APPS).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();

        var resp = Unirest.post(GQL_APPS)
                .basicAuth("admin", "secret")
                .contentType("application/json")
                .body("""
                        {
                          "_id": "%s",
                          "descriptor": { "description": "Warehouse GraphQL app (MCP IT)", "enabled": true, "uri": "%s" },
                          "schema": "type Product { sku: String, qty: Int } type Query { items(limit: Int = 10): [Product] }",
                          "mappings": { "Query": { "items": { "db": "test-mcp-graphqlapp", "collection": "products", "limit": { "$arg": "limit" } } } },
                          "mcp": {
                            "enabled": true,
                            "description": "Query the warehouse via GraphQL (MCP IT).",
                            "examples": [ { "description": "List items", "args": { "body": { "query": "{ items(limit: 5) { sku qty } }" } } } ]
                          }
                        }
                        """.formatted(APP_ID, APP_ID))
                .asEmpty();
        assertTrue(resp.getStatus() == 200 || resp.getStatus() == 201, "gql-app setup failed with status " + resp.getStatus());

        // CachedResourceLookup (#616) caches the catalog for catalog-ttl-seconds (1s in
        // conf-overrides.yml); this waits past that so this test's own just-created resource is
        // never served from a stale entry populated by an earlier test class
        Thread.sleep(1_500);

        mcp = new McpTestClient(BASE, ADMIN_BASIC);
        mcp.initialize();
    }

    @AfterEach
    void deleteTestApp() {
        Unirest.delete(GQL_APPS + "/" + APP_ID).basicAuth("admin", "secret").asEmpty();
    }

    @Test
    void catalog_includesTheMcpEnabledApp() throws Exception {
        var catalog = mcp.callTool("list_apis", "{}");

        var found = catalog.getArray("resources").stream()
                .map(v -> v.asDocument())
                .anyMatch(r -> APP_URI.equals(r.getString("uri").getValue())
                        && "graphql-app".equals(r.getString("kind").getValue()));

        assertTrue(found, "catalog must include " + APP_URI + "; got: " + catalog.toJson());
    }

    @Test
    void resourceContext_showsFixedBodySchemaAndSdlDerivedQueries() throws Exception {
        var context = mcp.callTool("list_apis", "{\"resource\": \"" + APP_URI + "\"}");

        var execute = context.getDocument("actions").getDocument("execute");
        assertEquals("POST", execute.getString("method").getValue());
        assertTrue(execute.getDocument("body_schema").getDocument("properties").containsKey("query"));

        var queries = context.getDocument("operations").getArray("queries");
        assertEquals(1, queries.size());
        assertEquals("items", queries.get(0).asDocument().getString("name").getValue());
        assertEquals("[Product]", queries.get(0).asDocument().getString("return_type").getValue());

        assertTrue(context.getDocument("operations").get("mutations") == null,
                "RESTHeart's GraphQL API is read-only; no mutations key should ever appear");
    }

    @Test
    void howToCallExecute_composedDescriptorActuallyExecutesAGraphqlQuery() throws Exception {
        var descriptor = mcp.callTool("how_to_call",
                "{\"resource\": \"" + APP_URI + "\", \"action\": \"execute\", \"args\": {\"body\": {\"query\": \"{ items(limit: 5) { sku qty } }\"}}}");

        assertEquals("POST", descriptor.getString("method").getValue());
        var url = descriptor.getString("url").getValue();
        var body = descriptor.getDocument("body");

        var resp = Unirest.post(url).basicAuth("admin", "secret").contentType("application/json")
                .body(body.toJson()).asString();

        assertEquals(200, resp.getStatus());
        assertTrue(resp.getBody().contains("widget"), "GraphQL response must contain the inserted product; got: " + resp.getBody());
    }
}
