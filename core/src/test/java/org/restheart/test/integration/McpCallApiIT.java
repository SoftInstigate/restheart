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

import org.bson.BsonDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kong.unirest.Unirest;

/**
 * {@code call_api} (restheart#741): the one executable road. An action runs on the server, as
 * the session's own identity, through the whole handler chain, and the agent gets back what
 * the API answered.
 */
public class McpCallApiIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String TEST_DB = BASE + "/test-mcp-callapi";
    private static final String TEST_COLL = TEST_DB + "/todos";
    private static final String STREAM = TEST_COLL + "/_streams/all";

    private static final String ADMIN_BASIC = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());

    private McpTestClient mcp;

    @BeforeEach
    public void setUpFixture() throws Exception {
        Unirest.put(TEST_DB).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();

        var coll = Unirest.put(TEST_COLL)
                .basicAuth("admin", "secret")
                .contentType("application/json")
                .body("""
                        {
                          "mcp": { "enabled": true, "description": "Todos (call_api IT)." },
                          "streams": [
                            { "uri": "all", "stages": [], "mcp": { "enabled": true, "description": "Every change." } }
                          ]
                        }
                        """)
                .asEmpty();
        assertTrue(coll.getStatus() == 200 || coll.getStatus() == 201, "collection setup failed: " + coll.getStatus());

        // past CachedResourceLookup's TTL (conf-overrides sets it to 1s)
        Thread.sleep(1_500);

        mcp = new McpTestClient(BASE, ADMIN_BASIC);
        mcp.initialize();
    }

    @Test
    public void create_runsTheWriteAndAnswersWithWhatTheApiAnswered() throws Exception {
        var created = mcp.callTool("call_api", """
                {"resource":"%s","action":"create","args":{"body":{"title":"buy milk","done":false}}}
                """.formatted(TEST_COLL));

        assertEquals(201, created.getInt32("status").getValue(), "create failed: " + created.toJson());

        // the API's own headers travel back: Location names the new document
        var location = created.getDocument("headers").getString("Location").getValue();
        assertTrue(location.startsWith(TEST_COLL + "/"), "Location must name the created document: " + location);

        // and resources/read, the other channel, sees it
        var text = mcp.readResource(TEST_COLL);
        assertTrue(text.contains("buy milk"), "the created document is not readable: " + text);
    }

    @Test
    public void updateAndDelete_reachTheDocumentThroughItsId() throws Exception {
        var created = mcp.callTool("call_api", """
                {"resource":"%s","action":"create","args":{"body":{"title":"call mom","done":false}}}
                """.formatted(TEST_COLL));
        var id = idFromLocation(created.getDocument("headers").getString("Location").getValue());

        var updated = mcp.callTool("call_api", """
                {"resource":"%s","action":"update","args":{"id":"%s","body":{"done":true}}}
                """.formatted(TEST_COLL, id));
        assertEquals(200, updated.getInt32("status").getValue(), "update failed: " + updated.toJson());

        var read = BsonDocument.parse(mcp.readResource(TEST_COLL + "/" + id));
        assertTrue(read.getBoolean("done").getValue(), "the update did not land: " + read.toJson());

        var deleted = mcp.callTool("call_api", """
                {"resource":"%s","action":"delete","args":{"id":"%s"}}
                """.formatted(TEST_COLL, id));
        assertEquals(204, deleted.getInt32("status").getValue(), "delete failed: " + deleted.toJson());
        assertFalse(deleted.containsKey("body"), "a 204 carries no body");

        var viaRest = Unirest.get(TEST_COLL + "/" + id).basicAuth("admin", "secret").asString();
        assertEquals(404, viaRest.getStatus(), "the document is still there after delete");
    }

    @Test
    public void aNon2xxStatus_isAResultTheAgentReads_notAToolError() throws Exception {
        // a duplicate _id is a 409 from the API: the agent must see it, with the body, to decide
        // whether to retry
        mcp.callTool("call_api", """
                {"resource":"%s","action":"create","args":{"body":{"_id":"dup","title":"first"}}}
                """.formatted(TEST_COLL));
        var again = mcp.callTool("call_api", """
                {"resource":"%s","action":"create","args":{"body":{"_id":"dup","title":"second"}}}
                """.formatted(TEST_COLL));

        assertEquals(409, again.getInt32("status").getValue(), "a duplicate must be a 409 result: " + again.toJson());
        assertTrue(again.containsKey("body"), "the API's own error body must come back: " + again.toJson());
    }

    @Test
    public void theInterceptorChainRuns_forAnInProcessCallExactlyAsForRest() throws Exception {
        var result = mcp.callTool("call_api", """
                {"resource":"%s","action":"size","args":{}}
                """.formatted(TEST_COLL));

        assertEquals(200, result.getInt32("status").getValue(), result.toJson());
        assertEquals("in-process", result.getDocument("headers").getString("X-Test-Interceptor").getValue(),
                "the response interceptor did not run for the in-process request: " + result.toJson());

        var viaRest = Unirest.get(TEST_COLL + "/_size").basicAuth("admin", "secret").asString();
        assertEquals("listener", viaRest.getHeaders().getFirst("X-Test-Interceptor"),
                "the same interceptor stamps the REST request too");
    }

    @Test
    public void aStream_isRefusedWithAPointerToTheRightChannel() throws Exception {
        var error = mcp.callToolExpectingError("call_api", """
                {"resource":"%s","action":"subscribe","args":{}}
                """.formatted(STREAM));

        assertTrue(error.contains("resources/subscribe"), "must point at the channel that carries streams: " + error);
    }

    @Test
    public void invalidArguments_areAToolError_beforeAnythingRuns() throws Exception {
        var error = mcp.callToolExpectingError("call_api", """
                {"resource":"%s","action":"update","args":{"body":{"done":true}}}
                """.formatted(TEST_COLL));

        assertTrue(error.contains("id"), "the missing required param must be named: " + error);
    }

    private static String idFromLocation(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
