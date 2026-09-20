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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kong.unirest.Unirest;

/**
 * Authorization parity between {@code resources/read} and the equivalent REST {@code GET}.
 *
 * <p>A read through MCP runs the composed request through the whole pipeline in-process, so the
 * ACL engine matches a real exchange and decides exactly as it would for the REST call. This suite
 * proves the two answer the same way.
 *
 * <p>It is deliberately written as a <b>parity</b> check rather than a list of expected outcomes:
 * each test asks REST and MCP the same question and asserts they agree. A test that merely
 * hard-coded "MCP denies this" would still pass if MCP started denying everything — which is
 * precisely the bug that shipped once, when every {@code /mcp} request answered 403 regardless of
 * what it was asking for.
 *
 * <p>The {@code mcpuser} role (see {@code conf-overrides.yml}) may reach {@code /mcp} and may
 * {@code GET} exactly one database, so both the allowed and the denied side are exercised by the
 * same caller — the difference is the data being asked for, not the credential.
 */
public class McpAuthorizationIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";

    private static final String ALLOWED_DB = BASE + "/test-mcp-authz-allowed";
    private static final String ALLOWED_COLL = ALLOWED_DB + "/readable";
    private static final String DENIED_DB = BASE + "/test-mcp-authz-denied";
    private static final String DENIED_COLL = DENIED_DB + "/secret";

    private static final String ADMIN_BASIC = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());
    private static final String MCPUSER_BASIC = "Basic " + Base64.getEncoder().encodeToString("mcpuser:secret".getBytes());

    private McpTestClient asMcpUser;

    @BeforeEach
    public void setUpMcpFixture() throws Exception {
        seedCollection(ALLOWED_DB, ALLOWED_COLL, "visible");
        seedCollection(DENIED_DB, DENIED_COLL, "classified");

        asMcpUser = new McpTestClient(BASE, MCPUSER_BASIC);
        asMcpUser.initialize();
        asMcpUser.awaitResource(ALLOWED_COLL);
    }

    @Test
    public void whereRestAllows_mcpAllows() throws Exception {
        var restStatus = restGetStatus(ALLOWED_COLL + "?page=1");
        assertEquals(200, restStatus, "precondition: mcpuser should be able to GET the allowed collection");

        var text = asMcpUser.readResource(ALLOWED_COLL + "?page=1");

        assertTrue(text.contains("visible"),
                "REST allowed this read but MCP did not return the data: " + text);
    }

    @Test
    public void whereRestDenies_mcpDenies() throws Exception {
        var restStatus = restGetStatus(DENIED_COLL + "?page=1");
        assertEquals(403, restStatus, "precondition: mcpuser should be refused the denied collection over REST");

        var envelope = asMcpUser.rpc("resources/read", """
                {"uri":"%s?page=1"}
                """.formatted(DENIED_COLL));

        // the read runs through the handler chain in-process (#741), so the ACL refuses it there
        // exactly as it refuses the GET, and the refusal comes back as a JSON-RPC error: no
        // descriptor is derived, nothing is replicated, and nothing at the HTTP layer knows
        assertTrue(envelope.containsKey("error"),
                "REST denied this read but MCP did not — the in-process ACL is not holding: " + envelope.toJson());
        assertEquals(-32003, envelope.getDocument("error").getInt32("code").getValue(),
                "a refused read must say it was refused, not that the resource is missing: " + envelope.toJson());
        assertFalse(envelope.toJson().contains("classified"),
                "denied data leaked through resources/read: " + envelope.toJson());
    }

    @Test
    public void denialIsAboutTheData_notAboutReachingMcp() throws Exception {
        // the regression that shipped once: the handler denied every /mcp request, so nothing
        // worked. Tools must keep working for a caller whose data permissions are narrow.
        var catalog = asMcpUser.callTool("list_apis", "{}");

        assertTrue(catalog.containsKey("resources"),
                "a caller allowed on /mcp must still be able to use the tools: " + catalog.toJson());
    }

    @Test
    public void catalogIsAclFiltered_andReadingStillIs() throws Exception {
        // This used to assert the opposite, on #616's reasoning that visibility is per-resource
        // mcp.enabled rather than per-caller ACL, and that hiding is no substitute for refusing.
        // The refusing half still holds and is asserted below. The hiding half was reconsidered:
        // a catalog entry carries the resource's URI, its actions including the writing ones, its
        // parameter names and its mcp.description — prose written to explain what the data is —
        // so an unfiltered catalog let any caller who could reach /mcp enumerate the server.
        // Filtering is now an addition to enforcement, not a substitute for it: both are pinned
        // here, in the same test, so neither can be dropped in favour of the other.
        var uris = asMcpUser.callTool("list_apis", "{}").getArray("resources").stream()
                .map(r -> r.asDocument().getString("uri").getValue())
                .toList();

        assertFalse(uris.contains(DENIED_COLL),
                "a resource this caller cannot read was listed in the catalog; got " + uris);

        // the positive control: a filter that hid everything would satisfy the assertion above
        assertTrue(uris.contains(ALLOWED_COLL),
                "the catalog hid a resource this caller may read, so it is denying rather than filtering; got " + uris);

        var envelope = asMcpUser.rpc("resources/read", """
                {"uri":"%s?page=1"}
                """.formatted(DENIED_COLL));
        assertTrue(envelope.containsKey("error"), "hidden from the catalog, and reading it must still be refused: " + envelope.toJson());
    }

    @Test
    public void adminReadsWhatMcpUserCannot() throws Exception {
        // the same URI, a different caller: proves the denial above tracks the principal rather
        // than something about the collection itself
        var asAdmin = new McpTestClient(BASE, ADMIN_BASIC);
        asAdmin.initialize();

        var text = asAdmin.readResource(DENIED_COLL + "?page=1");

        assertTrue(text.contains("classified"), "admin should read it: " + text);
    }

    @Test
    public void callApiForANarrowCaller_runsWithExactlyThatCallersGrants() throws Exception {
        // call_api must never widen privilege: the in-process request is authorized as the
        // session's own identity, so it can do exactly what the session can do over REST
        var size = asMcpUser.callTool("call_api", """
                {"resource":"%s","action":"size","args":{}}
                """.formatted(ALLOWED_COLL));
        assertEquals(200, size.getInt32("status").getValue(), "the caller's own read grant was lost in-process: " + size.toJson());
        assertTrue(size.getDocument("body").containsKey("_size"), "the API's own body comes back: " + size.toJson());

        // mcpuser may GET the allowed collection and nothing else: the write is refused by the
        // ACL, and the refusal is a result the agent reads, not a tool error
        var create = asMcpUser.callTool("call_api", """
                {"resource":"%s","action":"create","args":{"body":{"marker":"smuggled"}}}
                """.formatted(ALLOWED_COLL));
        assertEquals(403, create.getInt32("status").getValue(), "call_api granted more than the caller had: " + create.toJson());

        // the denied collection is hidden from this caller's catalog, but call_api never refuses on
        // catalogue visibility (#743): it dispatches, and the ACL that would refuse the GET refuses
        // this too, so the agent reads the real status instead of being told the resource is absent
        var denied = asMcpUser.callTool("call_api", """
                {"resource":"%s","action":"size","args":{}}
                """.formatted(DENIED_COLL));
        assertEquals(403, denied.getInt32("status").getValue(),
                "call_api reached a collection this caller may not read: " + denied.toJson());
        assertFalse(denied.toJson().contains("classified"),
                "denied data leaked through call_api: " + denied.toJson());
    }

    // ----------------------------------------------------------------- helpers

    private static void seedCollection(String db, String coll, String marker) {
        Unirest.put(db).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();

        var resp = Unirest.put(coll).basicAuth("admin", "secret").contentType("application/json")
                .body("""
                        { "mcp": { "enabled": true, "description": "Authorization IT fixture." } }
                        """)
                .asEmpty();
        assertTrue(resp.getStatus() == 200 || resp.getStatus() == 201, "setup failed for " + coll + ": " + resp.getStatus());

        Unirest.post(coll).basicAuth("admin", "secret").contentType("application/json")
                .body("{\"marker\":\"" + marker + "\"}").asEmpty();
    }

    private static int restGetStatus(String url) {
        return Unirest.get(url).header("Authorization", MCPUSER_BASIC).asString().getStatus();
    }

}
