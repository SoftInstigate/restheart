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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kong.unirest.Unirest;

/**
 * A read through {@code /mcp} must surface exactly the rows and exactly the fields the equivalent
 * REST {@code GET} surfaces for the same principal (restheart#722).
 *
 * <p>Base allow/deny alone is not enough, and that is the whole point of this class. An MCP
 * documents-mode read is executed in-process: it never travels the HTTP pipeline, so neither
 * {@code mongoPermissionFilters} nor {@code mongoPermissionProjectResponse} — the interceptors
 * that apply {@code mongo.readFilter} and {@code mongo.projectResponse} — runs for it. Until the
 * permission that authorized the operation was carried into the read itself, both silently did not
 * apply: a principal restricted to their own rows read everyone's through {@code /mcp}, and the
 * properties the ACL hides came back in full. Every assertion here is written as an equivalence
 * against REST rather than against a hardcoded expectation, so the two can never drift apart
 * without a failure.
 *
 * <p>The ACL entry behind these tests (see {@code conf-overrides.yml}, role {@code aclreader})
 * restricts reads to {@code {"owner": "@user.userid"}} and hides {@code secret}.
 */
public class McpAclEnforcementIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String TEST_DB = BASE + "/test-mcp-acl";
    private static final String TEST_COLL = TEST_DB + "/inventory";

    private static final String OWNER1_BASIC = "Basic " + Base64.getEncoder().encodeToString("aclowner1:secret".getBytes());

    private McpTestClient mcp;

    @BeforeEach
    public void setUpAclFixture() throws Exception {
        Unirest.put(TEST_DB).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();

        var coll = Unirest.put(TEST_COLL)
                .basicAuth("admin", "secret")
                .contentType("application/json")
                .body("""
                        {
                          "mcp": { "enabled": true, "description": "Inventory (ACL enforcement IT)." },
                          "aggrs": [
                            {
                              "uri": "everything",
                              "stages": [ { "$sort": { "item": 1 } } ],
                              "mcp": { "enabled": true, "description": "Every document, unfiltered." }
                            }
                          ]
                        }
                        """)
                .asEmpty();
        assertTrue(coll.getStatus() == 200 || coll.getStatus() == 201, "collection setup failed: " + coll.getStatus());

        post("{\"item\":\"notebook\",\"owner\":\"aclowner1\",\"secret\":\"n1\"}");
        post("{\"item\":\"journal\",\"owner\":\"aclowner1\",\"secret\":\"n2\"}");
        post("{\"item\":\"stapler\",\"owner\":\"aclowner2\",\"secret\":\"n3\"}");

        // past CachedResourceLookup's TTL (conf-overrides sets it to 1s)
        Thread.sleep(1_500);

        mcp = new McpTestClient(BASE, OWNER1_BASIC);
        mcp.initialize();
    }

    @Test
    public void collectionRead_surfacesTheSameRowsAsTheEquivalentRestGet() throws Exception {
        var viaMcp = itemsIn(mcp.readResource(TEST_COLL));
        var viaRest = itemsIn(restGet(TEST_COLL));

        assertEquals(viaRest, viaMcp, "MCP and REST disagree on which rows this principal may read");
        assertEquals(List.of("journal", "notebook"), viaMcp, "the read filter did not restrict the rows");
    }

    @Test
    public void collectionRead_doesNotSurfaceAnotherOwnersRows() throws Exception {
        var text = mcp.readResource(TEST_COLL);

        assertFalse(text.contains("stapler"), "another owner's document came back through /mcp: " + text);
    }

    @Test
    public void collectionRead_hidesTheSamePropertiesTheRestGetHides() throws Exception {
        var viaMcp = mcp.readResource(TEST_COLL);

        assertFalse(restGet(TEST_COLL).contains("\"secret\""), "precondition: REST must hide it too");
        assertFalse(viaMcp.contains("\"secret\""), "projectResponse was not applied to the MCP read: " + viaMcp);
        // proof the documents really are there — a filter that removed everything would also pass
        assertTrue(viaMcp.contains("notebook"), viaMcp);
    }

    @Test
    public void filteredRead_appliesBothTheCallersFilterAndTheAclFilter() throws Exception {
        // the caller's own filter must narrow within what the ACL allows, never widen past it
        var text = mcp.readResource(TEST_COLL + "?filter=" + urlEncode("{\"owner\":\"aclowner2\"}"));

        assertFalse(text.contains("stapler"),
                "a caller-supplied filter escaped the ACL read filter: " + text);
    }

    @Test
    public void count_countsOnlyTheRowsTheReadFilterAllows() throws Exception {
        var viaMcp = BsonDocument.parse(mcp.readResource(TEST_COLL + "/_size")).getNumber("size").intValue();
        var viaRest = BsonDocument.parse(restGet(TEST_COLL + "/_size")).getNumber("_size").intValue();

        assertEquals(viaRest, viaMcp, "MCP and REST disagree on how many rows this principal may read");
        assertEquals(2, viaMcp, "the count included another owner's documents");
    }

    @Test
    public void singleDocumentRead_ofAnotherOwnersDocument_returnsNothing() throws Exception {
        var id = idOfStapler();

        var text = mcp.readResource(TEST_COLL + "/" + id);

        assertFalse(text.contains("stapler"),
                "the read filter did not apply to a single-document read: " + text);
    }

    @Test
    public void aggregationRead_hidesWhatProjectResponseHides() throws Exception {
        var text = mcp.readResource(TEST_COLL + "/_aggrs/everything");

        assertFalse(text.contains("\"secret\""),
                "projectResponse was not applied to the aggregation result: " + text);
    }

    // ----------------------------------------------------------------- helpers

    private static void post(String body) {
        Unirest.post(TEST_COLL).basicAuth("admin", "secret").contentType("application/json").body(body).asEmpty();
    }

    /** {@code rep=s} so the body is a plain array of documents, directly comparable with the MCP payload. */
    private static String restGet(String url) {
        return Unirest.get(url).basicAuth("aclowner1", "secret").queryString("rep", "s").asString().getBody();
    }

    /** The {@code item} values in a payload, sorted — the shape-independent way to compare two readers. */
    private static List<String> itemsIn(String payload) {
        var trimmed = payload.trim();
        BsonArray docs;
        if (trimmed.startsWith("[")) {
            docs = BsonArray.parse(trimmed);
        } else {
            docs = BsonDocument.parse(trimmed).getArray("content");
        }
        return docs.stream()
                .map(d -> d.asDocument().getString("item").getValue())
                .sorted()
                .toList();
    }

    private static String idOfStapler() {
        var body = Unirest.get(TEST_COLL).basicAuth("admin", "secret")
                .queryString("filter", "{\"item\":\"stapler\"}").queryString("rep", "s").asString().getBody();
        return BsonArray.parse(body).get(0).asDocument().getObjectId("_id").getValue().toHexString();
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
