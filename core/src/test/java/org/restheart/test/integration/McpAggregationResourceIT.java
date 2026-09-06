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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kong.unirest.Unirest;

/**
 * Aggregations as readable MCP resources (#617): which ones become readable at all, how their
 * {@code $var}s are advertised, and what happens when a required one is missing.
 *
 * <p>Three rules are under test:
 *
 * <ul>
 *   <li><b>Safety gates readability.</b> An aggregation is readable only if the deployment's own
 *       {@code AggregationPipelineSecurityChecker} clears its pipeline — the same checker the real
 *       {@code GET /_aggrs/} endpoint enforces. A pipeline with a blacklisted stage
 *       ({@code $out}, {@code $merge}, ... by default) stays {@code how_to_call}-only, so
 *       {@code resources/read} can never trigger it.</li>
 *   <li><b>Required-ness comes from the pipeline, not the metadata.</b> A bare
 *       <code>{"$var": "x"}</code> has no default and would throw if unbound, so it is required
 *       whether or not {@code mcp.params} says so; <code>{"$var": ["x", default]}</code> is not.</li>
 *   <li><b>Anything with a variable is a template, and only the description says whether it is
 *       required.</b> One entry per resource means required and optional vars are registered the
 *       same way, so the derivation above is visible to a client only through the template's
 *       description — which makes that description worth asserting on.</li>
 * </ul>
 */
public class McpAggregationResourceIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String TEST_DB = BASE + "/test-mcp-aggrs";
    private static final String TEST_COLL = TEST_DB + "/purchases";

    private static final String BY_STATUS = TEST_COLL + "/_aggrs/byStatus";
    private static final String WITH_DEFAULT = TEST_COLL + "/_aggrs/withDefault";
    private static final String UNSAFE = TEST_COLL + "/_aggrs/unsafe";

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
                          "mcp": { "enabled": true, "description": "Orders (aggregation IT)." },
                          "aggrs": [
                            {
                              "uri": "byStatus",
                              "stages": [
                                { "$match": { "status": { "$var": "status" } } },
                                { "$group": { "_id": "$item", "total": { "$sum": "$qty" } } }
                              ],
                              "mcp": {
                                "enabled": true,
                                "description": "Total quantity by item, for a given status.",
                                "params": { "status": { "type": "string", "enum": ["A", "D"] } }
                              }
                            },
                            {
                              "uri": "withDefault",
                              "stages": [ { "$match": { "status": { "$var": ["status", "A"] } } } ],
                              "mcp": { "enabled": true, "description": "Defaults to status A." }
                            },
                            {
                              "uri": "unsafe",
                              "stages": [
                                { "$match": {} },
                                { "$out": "copied" }
                              ],
                              "mcp": { "enabled": true, "description": "Writes its result to another collection." }
                            }
                          ]
                        }
                        """)
                .asEmpty();
        assertTrue(coll.getStatus() == 200 || coll.getStatus() == 201, "collection setup failed: " + coll.getStatus());

        Unirest.post(TEST_COLL).basicAuth("admin", "secret").contentType("application/json")
                .body("{\"item\":\"widget\",\"qty\":7,\"status\":\"A\"}").asEmpty();
        Unirest.post(TEST_COLL).basicAuth("admin", "secret").contentType("application/json")
                .body("{\"item\":\"gadget\",\"qty\":3,\"status\":\"D\"}").asEmpty();

        Thread.sleep(1_500);

        mcp = new McpTestClient(BASE, ADMIN_BASIC);
        mcp.initialize();
    }

    @Test
    public void aggregationWithARequiredVar_isATemplateNamingThatVar() throws Exception {
        var templates = templateUris();
        var template = templates.stream().filter(t -> t.startsWith(BY_STATUS)).findFirst()
                .orElseThrow(() -> new AssertionError("no template for byStatus; got " + templates));

        assertTrue(template.contains("status"), "the template must name the variable to fill in: " + template);
        assertFalse(resourceUris().contains(BY_STATUS),
                "a read with no status always fails, so it must not be offered as a concrete resource");
    }

    @Test
    public void aggregationWhoseVarHasADefault_isATemplateThatDemandsNothing() throws Exception {
        // Still a template — "status" is worth offering even when it has a default — but the
        // description must not claim it is required, because {"$var": ["status", "A"]} can
        // never be unbound. That distinction is now carried entirely by the description: under
        // one-entry-per-resource both required and optional vars produce a template, so this is
        // the only place the pipeline-shape derivation is visible to a client.
        var description = templateDescription(WITH_DEFAULT);

        assertFalse(description.contains("requires"),
                "a defaulted var must not be advertised as required: " + description);
    }

    @Test
    public void aggregationWithARequiredVar_saysSoInItsDescription() throws Exception {
        var description = templateDescription(BY_STATUS);

        assertTrue(description.contains("requires: status"),
                "a bare, defaultless $var must be advertised as required: " + description);
    }

    @Test
    public void readingWithADefaultedVarLeftBlank_usesTheDefault() throws Exception {
        // the blank-form shape against a defaulted var: nothing supplied, so the pipeline's own
        // default ("A") applies rather than the read failing
        var text = mcp.readResource(WITH_DEFAULT + "?status=");

        assertTrue(text.contains("widget"), "expected the default status A to apply: " + text);
        assertFalse(text.contains("gadget"), text);
    }

    @Test
    public void unsafeAggregation_isNeverReadable() throws Exception {
        // $out writes: the security checker rejects it, so the resources primitive must not
        // expose any way to trigger it — how_to_call remains the only route
        assertFalse(resourceUris().contains(UNSAFE), "a $out pipeline must not be a readable resource");
        assertFalse(templateUris().stream().anyMatch(t -> t.startsWith(UNSAFE)),
                "a $out pipeline must not be a readable template either; got " + templateUris());
    }

    @Test
    public void readingWithTheVarBound_executesThePipeline() throws Exception {
        var text = mcp.readResource(BY_STATUS + "?status=A");

        // the flat query param binds the $var — no avars wrapper needed
        assertTrue(text.contains("widget"), "expected the status-A grouping: " + text);
        assertFalse(text.contains("gadget"), "status D should have been filtered out: " + text);
    }

    @Test
    public void readingWithTheVarBoundViaAvars_alsoWorks() throws Exception {
        var text = mcp.readResource(BY_STATUS + "?avars=" + urlEncode("{\"status\":\"D\"}"));

        assertTrue(text.contains("gadget"), "the avars form should still bind: " + text);
        assertFalse(text.contains("widget"), text);
    }

    @Test
    public void readingWithoutTheRequiredVar_failsRatherThanDescribingTheResource() throws Exception {
        var envelope = mcp.rpc("resources/read", """
                {"uri":"%s"}
                """.formatted(BY_STATUS));

        // The rule being defended: a read either produces data or fails. It must never fall back
        // to describing the resource — the "sometimes data, sometimes a description" ambiguity
        // this design exists to remove.
        //
        // Note which failure this is. Because byStatus is registered only as a template naming
        // {status}, a bare URI matches nothing and the SDK rejects it as an unknown resource
        // before our own code runs — so the message is "unknown resource", not the more helpful
        // "variable status not bound" that the read path itself would produce. The template does
        // name the parameter, so a client following it supplies one; a client guessing the bare
        // URI gets a blunter answer.
        assertTrue(envelope.containsKey("error"), "a read with no binding for a required var must fail: " + envelope.toJson());

        var message = envelope.toJson();
        assertFalse(message.contains("\"actions\""), "must not fall back to the resource description: " + message);
        assertFalse(message.contains("widget"), "must not return data it was never given a filter for: " + message);
    }

    @Test
    public void howToCall_stillWorksForTheUnsafeAggregation() throws Exception {
        // not readable is not the same as not callable: the descriptor is still composed, and it
        // is the REST endpoint's own security check that decides at execution time
        var descriptor = mcp.callTool("how_to_call", """
                {"resource":"%s","action":"execute"}
                """.formatted(UNSAFE));

        assertTrue(descriptor.getString("url").getValue().contains("/_aggrs/unsafe"),
                "expected a composed descriptor: " + descriptor.toJson());
    }

    private List<String> resourceUris() throws Exception {
        return mcp.rpc("resources/list", null).getDocument("result").getArray("resources").stream()
                .map(r -> r.asDocument().getString("uri").getValue())
                .toList();
    }

    /** The description advertised for one template, by the URI it starts with. */
    private String templateDescription(String uriPrefix) throws Exception {
        return mcp.rpc("resources/templates/list", null).getDocument("result").getArray("resourceTemplates").stream()
                .map(org.bson.BsonValue::asDocument)
                .filter(t -> t.getString("uriTemplate").getValue().startsWith(uriPrefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no template starting with " + uriPrefix))
                .getString("description").getValue();
    }

    private List<String> templateUris() throws Exception {
        return mcp.rpc("resources/templates/list", null).getDocument("result").getArray("resourceTemplates").stream()
                .map(t -> t.asDocument().getString("uriTemplate").getValue())
                .toList();
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
