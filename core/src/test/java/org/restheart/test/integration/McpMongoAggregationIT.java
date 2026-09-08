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
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kong.unirest.Unirest;

/**
 * Integration test for #616: an aggregation with both a declared (in {@code mcp.params}) and an
 * undeclared {@code $var} reference, exercised end-to-end through {@code /mcp} — confirms
 * {@code AggregationMcpResourceBuilder}'s {@code avars} bundling, the undeclared-variable
 * fallback-with-warning, and that the composed descriptor actually executes.
 */
public class McpMongoAggregationIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String TEST_DB = BASE + "/test-mcp-aggregation";
    private static final String TEST_COLL = TEST_DB + "/sales";
    private static final String AGGR_URI = TEST_COLL + "/_aggrs/byRegion";

    private static final String ADMIN_BASIC = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());

    private McpTestClient mcp;

    @BeforeEach
    void setupCollectionAndSession() throws Exception {
        Unirest.put(TEST_DB).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();

        var resp = Unirest.put(TEST_COLL)
                .basicAuth("admin", "secret")
                .contentType("application/json")
                .body("""
                        {
                          "aggrs": [
                            {
                              "uri": "byRegion",
                              "type": "pipeline",
                              "stages": [
                                { "$match": { "region": { "$var": "region" }, "amount": { "$gte": { "$var": "minAmount" } } } },
                                { "$group": { "_id": "$item", "total": { "$sum": "$amount" } } }
                              ],
                              "mcp": {
                                "enabled": true,
                                "description": "Total sales amount by item, for a region above a minimum amount.",
                                "params": { "region": { "type": "string", "enum": ["EU", "US"] } }
                              }
                            }
                          ]
                        }
                        """)
                .asEmpty();
        assertTrue(resp.getStatus() == 200 || resp.getStatus() == 201, "collection setup failed with status " + resp.getStatus());

        for (var doc : List.of(
                "{\"region\": \"EU\", \"item\": \"widget\", \"amount\": 100}",
                "{\"region\": \"EU\", \"item\": \"widget\", \"amount\": 50}",
                "{\"region\": \"US\", \"item\": \"widget\", \"amount\": 200}")) {
            Unirest.post(TEST_COLL).basicAuth("admin", "secret").contentType("application/json").body(doc).asEmpty();
        }

        // see McpGraphqlAppIT: wait past CachedResourceLookup's TTL so this class's own
        // just-created resource isn't served from a stale cache entry
        Thread.sleep(1_500);

        mcp = new McpTestClient(BASE, ADMIN_BASIC);
        mcp.initialize();
    }

    @Test
    void context_showsAvarsWithDeclaredAndAutoDiscoveredParamsPlusWarning() throws Exception {
        var context = mcp.callTool("list_apis", "{\"resource\": \"" + AGGR_URI + "\"}");

        assertEquals("aggregation", context.getString("kind").getValue());

        var avars = context.getDocument("actions").getDocument("execute").getDocument("params").getDocument("avars");
        var properties = avars.getDocument("properties");

        var region = properties.getDocument("region");
        assertEquals("string", region.getString("type").getValue());
        assertEquals(List.of("EU", "US"), region.getArray("enum").stream().map(v -> v.asString().getValue()).toList());

        // "minAmount" is referenced by the pipeline but not declared in mcp.params
        var minAmount = properties.getDocument("minAmount");
        assertEquals("string", minAmount.getString("type").getValue());

        var warnings = context.getArray("warnings").stream().map(v -> v.asString().getValue()).toList();
        assertTrue(warnings.stream().anyMatch(w -> w.contains("minAmount")), "warnings must mention the undeclared variable; got: " + warnings);
    }

    @Test
    void howToCallExecute_composedDescriptorActuallyExecutes() throws Exception {
        var descriptor = mcp.callTool("how_to_call",
                "{\"resource\": \"" + AGGR_URI + "\", \"action\": \"execute\", \"args\": {\"avars\": {\"region\": \"EU\", \"minAmount\": 0}}}");

        assertEquals("GET", descriptor.getString("method").getValue());
        var url = descriptor.getString("url").getValue();

        var resp = Unirest.get(url).basicAuth("admin", "secret").asString();

        assertEquals(200, resp.getStatus());
        assertTrue(resp.getBody().contains("\"total\""), "aggregation result must contain a total; got: " + resp.getBody());
        assertTrue(resp.getBody().contains("150") || resp.getBody().contains("150.0"),
                "EU widget total must be 100+50=150; got: " + resp.getBody());
    }
}
