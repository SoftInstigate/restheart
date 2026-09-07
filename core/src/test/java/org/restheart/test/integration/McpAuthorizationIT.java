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
 * Authorization parity between {@code resources/read} and the equivalent REST {@code GET}
 * (restheart#722).
 *
 * <p>Documents-mode reads execute in-process: no self-directed HTTP call, therefore no real
 * exchange for the ACL engine to match against. {@code McpService.operationsToAuthorize()}
 * bridges that by describing the equivalent REST operation as a {@code RequestDescriptor}, which
 * {@code core}'s {@code AuthorizersHandler} evaluates against every {@code
 * DescriptorAwareAuthorizer} using the same voting algorithm real requests go through.
 *
 * <p>This is the suite that proves the bridge holds, and it is deliberately written as a
 * <b>parity</b> check rather than a list of expected outcomes: each test asks REST and MCP the
 * same question and asserts they answer the same way. A test that merely hard-coded "MCP denies
 * this" would still pass if MCP started denying everything — which is precisely the bug that
 * shipped once already, when overriding {@code operationsToAuthorize()} made every {@code /mcp}
 * request 403 regardless of what it was asking for.
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

        // past CachedResourceLookup's TTL, so both collections are in the catalog
        Thread.sleep(1_500);

        asMcpUser = new McpTestClient(BASE, MCPUSER_BASIC);
        asMcpUser.initialize();
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

        var response = asMcpUser.rawRpc("resources/read", """
                {"uri":"%s?page=1"}
                """.formatted(DENIED_COLL));

        // the denial lands at the HTTP layer, before JSON-RPC: authorization runs on the
        // descriptor McpService derives, so the whole call is refused rather than answered with
        // an error payload
        assertEquals(403, response.statusCode(),
                "REST denied this read but MCP did not — the ACL bridge is not holding: " + response.body());
        assertFalse(response.body().contains("classified"),
                "denied data leaked through resources/read: " + response.body());
    }

    @Test
    public void denialIsAboutTheData_notAboutReachingMcp() throws Exception {
        // the regression that shipped once: overriding operationsToAuthorize() made the handler
        // deny every /mcp request, so nothing worked. Tools must keep working for a caller whose
        // data permissions are narrow.
        var catalog = asMcpUser.callTool("list_apis", "{}");

        assertTrue(catalog.containsKey("resources"),
                "a caller allowed on /mcp must still be able to use the tools: " + catalog.toJson());
    }

    @Test
    public void catalogIsNotAclFiltered_butReadingStillIs() throws Exception {
        // #616's deliberate choice: visibility is per-resource mcp.enabled, not per-caller ACL.
        // Worth pinning, because "the catalog hides it" is a tempting but false substitute for
        // enforcement — the guarantee is that reading is refused, not that listing is.
        var uris = asMcpUser.callTool("list_apis", "{}").getArray("resources").stream()
                .map(r -> r.asDocument().getString("uri").getValue())
                .toList();

        assertTrue(uris.contains(DENIED_COLL),
                "the catalog is not ACL-filtered by design; got " + uris);

        var response = asMcpUser.rawRpc("resources/read", """
                {"uri":"%s?page=1"}
                """.formatted(DENIED_COLL));
        assertEquals(403, response.statusCode(), "listed, but reading it must still be refused");
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
    public void tokenIssuedForANarrowCaller_carriesOnlyThatCallersRoles() throws Exception {
        // get_token must never widen privilege: the token is usable exactly where the session is
        var accessToken = asMcpUser.callTool("get_token", "{}").getString("access_token").getValue();

        var allowed = Unirest.get(ALLOWED_COLL).header("Authorization", "Bearer " + accessToken)
                .queryString("page", "1").asString();
        var denied = Unirest.get(DENIED_COLL).header("Authorization", "Bearer " + accessToken)
                .queryString("page", "1").asString();

        assertEquals(200, allowed.getStatus(), "the issued token lost the caller's own grants");
        assertEquals(403, denied.getStatus(), "the issued token granted more than the caller had");
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
