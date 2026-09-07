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
import java.util.Set;

import org.bson.BsonValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The MCP protocol surface itself, independent of any exposed resource: the handshake, which
 * tools exist, and the shape of their declared input schemas.
 *
 * <p>Every other MCP IT depends on this surface, so pinning it here means a break shows up as one
 * obvious failure rather than as a scattering of confusing ones. It also guards the tool contract
 * an agent actually reads: the tool set and the arguments each accepts are what a model reasons
 * over, so silently gaining, losing or renaming one changes behaviour for every client.
 */
public class McpProtocolIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String ADMIN_BASIC = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());

    private McpTestClient mcp;

    @BeforeEach
    public void setUpMcpFixture() throws Exception {
        mcp = new McpTestClient(BASE, ADMIN_BASIC);
        mcp.initialize();
    }

    @Test
    public void toolsList_exposesExactlyTheThreeDesignedTools() throws Exception {
        var tools = mcp.rpc("tools/list", null).getDocument("result").getArray("tools").stream()
                .map(t -> t.asDocument().getString("name").getValue())
                .collect(java.util.stream.Collectors.toSet());

        // "one tool, one model" (#615): three concerns, never one tool per resource. A deployment
        // exposing hundreds of collections still shows exactly these three.
        assertEquals(Set.of("list_apis", "how_to_call", "get_token"), tools,
                "the MCP tool set is a contract an agent reasons over; got: " + tools);
    }

    @Test
    public void howToCall_declaresNoTokenArgument() throws Exception {
        var properties = toolNamed("how_to_call").getDocument("inputSchema").getDocument("properties");

        // the descriptor is credential-free by design, which is what makes it stable and reusable:
        // a token would expire inside an answer an agent is meant to keep
        assertFalse(properties.containsKey("token"),
                "how_to_call must not take a token; get_token issues one separately");
        assertTrue(properties.containsKey("resource") && properties.containsKey("action"));
    }

    @Test
    public void getToken_declaresNoArgumentsAndAnnouncesItsShortLife() throws Exception {
        var tool = toolNamed("get_token");

        assertTrue(tool.getDocument("inputSchema").getDocument("properties").isEmpty(),
                "the token is for the current session — there is nothing to parameterize");

        // the description is the only thing telling a model *when* to call this; if it stops
        // saying the token is short-lived, agents will fetch it once and cache it
        var description = tool.getString("description").getValue();
        assertTrue(description.contains("expires_in"), "must point at the field carrying the lifetime");
        assertTrue(description.contains("<token_from_get_token>"), "must quote the placeholder it fills in");
    }

    @Test
    public void listApis_describesWhenToPreferAFilteredCall() throws Exception {
        var description = toolNamed("list_apis").getString("description").getValue();

        assertTrue(description.contains("how_to_call"),
                "list_apis must point at the next step, or an agent stops at the catalog");
    }

    @Test
    public void unknownTool_isReportedAsAnError() throws Exception {
        var envelope = mcp.rpc("tools/call", """
                {"name":"no_such_tool","arguments":{}}
                """);

        var reportedFailure = envelope.containsKey("error")
                || (envelope.getDocument("result").containsKey("isError")
                && envelope.getDocument("result").getBoolean("isError").getValue());

        assertTrue(reportedFailure, "an unknown tool must fail, not return a result: " + envelope.toJson());
    }

    @Test
    public void unauthenticatedClient_cannotOpenASession() throws Exception {
        var anonymous = new McpTestClient(BASE, "Basic " + Base64.getEncoder().encodeToString("nobody:wrong".getBytes()));

        var response = anonymous.tryInitialize();

        // /mcp is `secure = true`: the transport is gated before any tool ever runs
        assertTrue(response.statusCode() == 401 || response.statusCode() == 403,
                "expected the handshake to be refused, got " + response.statusCode());
    }

    private org.bson.BsonDocument toolNamed(String name) throws Exception {
        return mcp.rpc("tools/list", null).getDocument("result").getArray("tools").stream()
                .map(BsonValue::asDocument)
                .filter(t -> name.equals(t.getString("name").getValue()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tool not exposed: " + name));
    }
}
