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

import org.bson.BsonDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kong.unirest.Unirest;

/**
 * The MCP {@code resources} primitive (#617) end to end: {@code resources/list},
 * {@code resources/templates/list} and documents-mode {@code resources/read}.
 *
 * <p>Two design rules are load-bearing here and are what these tests actually defend:
 *
 * <ul>
 *   <li><b>Documents-only.</b> {@code resources/read} returns real data or a clear error — never
 *       a description of the resource. Describing is {@code list_apis}/{@code how_to_call}'s job,
 *       exclusively. An earlier design returned context for a bare read and data for a filtered
 *       one; two different answers to the same request shape is exactly the confusion this
 *       forbids.</li>
 *   <li><b>One entry per resource.</b> Anything with parameters worth filling in is offered as a
 *       template and nothing else; only a resource with no parameters at all — an aggregation
 *       declaring no {@code $var} — is a concrete resource. Two entries under one name would
 *       leave the agent choosing between them for no gain, since the template already covers
 *       every shape a client builds from it.</li>
 * </ul>
 */
public class McpResourcesIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String TEST_DB = BASE + "/test-mcp-resources";
    private static final String TEST_COLL = TEST_DB + "/inventory";

    private static final String ADMIN_BASIC = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());

    private McpTestClient mcp;

    @BeforeEach
    public void setUpMcpFixture() throws Exception {
        Unirest.put(TEST_DB).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();

        var coll = Unirest.put(TEST_COLL)
                .basicAuth("admin", "secret")
                .contentType("application/json")
                .body("""
                        {
                          "mcp": { "enabled": true, "description": "Inventory (resources IT)." },
                          "aggrs": [
                            {
                              "uri": "totals",
                              "stages": [ { "$group": { "_id": "$item", "total": { "$sum": "$qty" } } } ],
                              "mcp": { "enabled": true, "description": "Total quantity per item." }
                            }
                          ]
                        }
                        """)
                .asEmpty();
        assertTrue(coll.getStatus() == 200 || coll.getStatus() == 201, "collection setup failed: " + coll.getStatus());

        Unirest.post(TEST_COLL).basicAuth("admin", "secret").contentType("application/json")
                .body("{\"item\":\"notebook\",\"qty\":50,\"status\":\"A\"}").asEmpty();
        Unirest.post(TEST_COLL).basicAuth("admin", "secret").contentType("application/json")
                .body("{\"item\":\"journal\",\"qty\":10,\"status\":\"D\"}").asEmpty();

        // past CachedResourceLookup's TTL (conf-overrides sets it to 1s) so this class's own
        // just-created resources aren't served from a stale catalog entry
        Thread.sleep(1_500);

        mcp = new McpTestClient(BASE, ADMIN_BASIC);
        mcp.initialize();
    }

    // ---------------------------------------------------------------- listing

    @Test
    public void collection_isAdvertisedOnlyAsATemplate() throws Exception {
        // one snapshot each, reused: the catalog TTL is 1s in tests, so two calls could otherwise
        // observe different states and produce a self-contradicting failure message
        var templates = templateUris();
        var resources = resourceUris();

        // One entry per resource. Registering the bare URI as a concrete resource as well would
        // put two things with the same name in front of the agent, and the template already
        // covers every shape a client builds from it — including a form left blank.
        assertTrue(templates.stream().anyMatch(t -> t.startsWith(TEST_COLL + "{?")),
                "no parameterised template for the collection; got " + templates);
        assertFalse(resources.contains(TEST_COLL),
                "the collection must not also be a concrete resource; got " + resources);
    }

    @Test
    public void readingACollectionWithEveryTemplateFieldBlank_returnsTheFirstPage() throws Exception {
        // What a client produces from the template when the user fills nothing in — and the
        // reason the collection needs no concrete resource beside its template. An empty value
        // means "not supplied": reading it as a supplied empty string turned the most ordinary
        // request there is into "'' is not an object, '' is not an integer".
        var text = mcp.readResource(TEST_COLL + "?filter=&sort=&keys=&page=&pagesize=&jsonMode=");

        assertTrue(text.contains("notebook"),
                "blank template fields must read like a bare read, not fail: " + text);
    }

    @Test
    public void collectionTemplate_namesTheParametersAReadAccepts() throws Exception {
        var templates = templateUris();
        var template = templates.stream().filter(t -> t.startsWith(TEST_COLL + "{?")).findFirst()
                .orElseThrow(() -> new AssertionError("no collection template; got " + templates));

        // the template URI is the only place a client learns what it may fill in
        assertTrue(template.contains("filter"), template);
        assertTrue(template.contains("page"), template);
        assertTrue(template.contains("jsonMode"), template);
    }

    @Test
    public void singleDocument_isAdvertisedAsATemplate() throws Exception {
        // documents are not enumerable, so this is the one genuinely generic shape
        var templates = templateUris();
        // no {?jsonMode} suffix: a template whose only parameter is cosmetic advertises nothing
        // worth filling in, and two adjacent capture groups over one path segment would be a
        // misleading thing to publish
        assertTrue(templates.contains(TEST_COLL + "/{id}"),
                "no single-document template; got " + templates);
    }

    @Test
    public void parameterlessAggregation_isAConcreteResource() throws Exception {
        // "totals" declares no $var: a bare read always works, so it needs no template
        var aggrUri = TEST_COLL + "/_aggrs/totals";
        var resources = resourceUris();

        assertTrue(resources.contains(aggrUri),
                "a parameterless aggregation should be directly readable; got " + resources);
    }

    @Test
    public void database_isNotAdvertisedAtAll() throws Exception {
        // a database is a container, not something document-shaped: it has no readable action, so
        // it stays list_apis/how_to_call-only rather than becoming an unreadable resource
        var resources = resourceUris();
        var templates = templateUris();

        assertFalse(resources.contains(TEST_DB), "the database must not be a resource; got " + resources);
        assertFalse(templates.stream().anyMatch(t -> t.startsWith(TEST_DB + "{?")),
                "the database must not be a template either; got " + templates);
    }

    // ------------------------------------------------------------- read: data

    @Test
    public void readingACollection_returnsRealDocuments() throws Exception {
        var text = mcp.readResource(TEST_COLL + "?page=1");

        // real data, not a description of how to get it
        assertTrue(text.contains("notebook"), "expected the actual documents, got: " + text);
        assertTrue(text.contains("journal"), text);
        assertFalse(text.contains("\"actions\""), "a read must never return the resource's action map");
    }

    @Test
    public void readingACollection_honoursTheFilter() throws Exception {
        var text = mcp.readResource(TEST_COLL + "?page=1&filter=" + urlEncode("{\"status\":\"A\"}"));

        assertTrue(text.contains("notebook"), text);
        assertFalse(text.contains("journal"), "the filter was ignored: " + text);
    }

    @Test
    public void readingACollection_rendersProperExtendedJson() throws Exception {
        var text = mcp.readResource(TEST_COLL + "?page=1");

        // BSON must come back as Extended JSON, not as a Java toString — the bug that produced
        // "_id": "BsonObjectId{value=...}" instead of {"$oid": "..."}
        assertTrue(text.contains("$oid"), "_id should be Extended JSON: " + text);
        assertFalse(text.contains("BsonObjectId{"), "raw Java toString leaked into the payload: " + text);
    }

    @Test
    public void readingASingleDocument_returnsThatDocument() throws Exception {
        var id = firstDocumentId();

        var text = mcp.readResource(TEST_COLL + "/" + id);

        assertTrue(text.contains(id), "expected the document with _id " + id + ", got: " + text);
    }

    @Test
    public void readingAParameterlessAggregation_executesIt() throws Exception {
        var text = mcp.readResource(TEST_COLL + "/_aggrs/totals");

        // the pipeline groups by item and sums qty — proof it actually ran, not just resolved
        assertTrue(text.contains("notebook"), text);
        assertTrue(text.contains("50"), "expected the summed quantity in the result: " + text);
    }

    // ------------------------------------------------------------ read: error

    @Test
    public void readingAnUnknownResource_isAnError() throws Exception {
        var envelope = mcp.rpc("resources/read", """
                {"uri":"%s/does-not-exist?page=1"}
                """.formatted(TEST_DB));

        assertTrue(isFailure(envelope), "an unknown URI must fail, not return empty content: " + envelope.toJson());
    }

    @Test
    public void readingAnMcpDisabledCollection_isAnError() throws Exception {
        // opting in is per-resource: a collection with no mcp block does not exist for an agent,
        // even though the URI is otherwise perfectly valid
        Unirest.put(TEST_DB + "/private").basicAuth("admin", "secret")
                .contentType("application/json").body("{}").asEmpty();
        Thread.sleep(1_500);

        var envelope = mcp.rpc("resources/read", """
                {"uri":"%s/private?page=1"}
                """.formatted(TEST_DB));

        assertTrue(isFailure(envelope), "a non-opted-in collection must not be readable: " + envelope.toJson());
    }

    // ----------------------------------------------------------------- helpers

    private List<String> resourceUris() throws Exception {
        return mcp.rpc("resources/list", null).getDocument("result").getArray("resources").stream()
                .map(r -> r.asDocument().getString("uri").getValue())
                .toList();
    }

    private List<String> templateUris() throws Exception {
        return mcp.rpc("resources/templates/list", null).getDocument("result").getArray("resourceTemplates").stream()
                .map(t -> t.asDocument().getString("uriTemplate").getValue())
                .toList();
    }

    private static boolean isFailure(BsonDocument envelope) {
        if (envelope.containsKey("error")) {
            return true;
        }
        var contents = envelope.getDocument("result").getArray("contents");
        if (contents.isEmpty()) {
            return true;
        }
        // the framework reports a failed read as an error payload in the content, since
        // resources/read has no other way to say "real resource, but nothing to give you"
        var text = contents.get(0).asDocument().getString("text").getValue();
        return text.contains("Error:") || text.contains("\"error\"");
    }

    /**
     * {@code rep=s} so the body is a plain array of documents rather than the default
     * representation. {@code _id} arrives as Extended JSON, which the BSON parser turns straight
     * into an {@code ObjectId} — not into a {@code {"$oid": ...}} document.
     */
    private static String firstDocumentId() {
        var body = Unirest.get(TEST_COLL).basicAuth("admin", "secret")
                .queryString("pagesize", "1").queryString("rep", "s").asString().getBody();
        var docs = org.bson.BsonArray.parse(body);
        return docs.get(0).asDocument().getObjectId("_id").getValue().toHexString();
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
