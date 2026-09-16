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
 * The password of a users-collection document never leaves the server, over MCP as over REST.
 *
 * <p>On the REST path {@code userPwdRemover} strips it from every {@code GET}. A documents-mode
 * {@code resources/read} never travels that pipeline, so {@code MongoMcpAwareImpl} has to apply
 * the interceptor's rule itself — including its multi-tenant half: with the
 * {@code override-users-db} attached parameter set, a {@code users} collection in <em>any</em>
 * database is a users collection. The test interceptor {@code dbOverrideInterceptor} attaches it
 * for {@code ?_db-override=<db>}, on {@code /mcp} as on any other request.
 */
public class McpUsersPasswordIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";

    /** {@code mongoRealmAuthenticator}'s {@code users-db} in conf-overrides.yml. */
    private static final String USERS_DB = BASE + "/restheart-test";
    private static final String USERS_COLL = USERS_DB + "/users";
    private static final String USER_ID = "mcp-pwd-user";

    /** Not the users db: only {@code override-users-db} makes its {@code users} a users collection. */
    private static final String OVERRIDE_DB_NAME = "test-mcp-pwd-override";
    private static final String OVERRIDE_DB = BASE + "/" + OVERRIDE_DB_NAME;
    private static final String OVERRIDE_COLL = OVERRIDE_DB + "/users";
    private static final String OVERRIDE_QUERY = "?_db-override=" + OVERRIDE_DB_NAME;

    private static final String PASSWORD = "Str0ng-Passw0rd!";
    private static final String MCP_METADATA = """
            {
              "mcp": { "enabled": true, "description": "Users (password IT)." },
              "aggrs": [
                {
                  "uri": "all",
                  "stages": [ { "$match": {} } ],
                  "mcp": { "enabled": true, "description": "Every user." }
                }
              ]
            }
            """;

    private static final String ADMIN_BASIC = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());

    private McpTestClient mcp;

    @BeforeEach
    public void setUpUsers() throws Exception {
        // the real users collection: created by other suites too, so its metadata is merged
        // rather than replaced
        Unirest.put(USERS_DB).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();
        if (Unirest.get(USERS_COLL).basicAuth("admin", "secret").asEmpty().getStatus() == 404) {
            Unirest.put(USERS_COLL).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();
        }
        var meta = Unirest.patch(USERS_COLL).basicAuth("admin", "secret").contentType("application/json").body(MCP_METADATA).asEmpty();
        assertEquals(200, meta.getStatus(), "users collection metadata");

        // userPwdHasher hashes it on the way in; what matters is that the field exists
        var user = Unirest.put(USERS_COLL + "/" + USER_ID).queryString("wm", "upsert")
                .basicAuth("admin", "secret").contentType("application/json")
                .body("{\"roles\":[\"user\"],\"password\":\"%s\"}".formatted(PASSWORD)).asEmpty();
        assertTrue(user.getStatus() == 200 || user.getStatus() == 201, "user setup failed: " + user.getStatus());

        // a users collection elsewhere: no hasher runs on this write, the password is stored as is
        Unirest.put(OVERRIDE_DB).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();
        var coll = Unirest.put(OVERRIDE_COLL).basicAuth("admin", "secret").contentType("application/json").body(MCP_METADATA).asEmpty();
        assertTrue(coll.getStatus() == 200 || coll.getStatus() == 201, "override collection setup failed: " + coll.getStatus());
        var tenantUser = Unirest.put(OVERRIDE_COLL + "/" + USER_ID).queryString("wm", "upsert")
                .basicAuth("admin", "secret").contentType("application/json")
                .body("{\"roles\":[\"user\"],\"password\":\"%s\"}".formatted(PASSWORD)).asEmpty();
        assertTrue(tenantUser.getStatus() == 200 || tenantUser.getStatus() == 201, "tenant user setup failed: " + tenantUser.getStatus());

        // past the catalog TTL (1s in conf-overrides) so the just-written mcp metadata is seen
        Thread.sleep(1_500);

        mcp = new McpTestClient(BASE, ADMIN_BASIC);
        mcp.initialize();
    }

    // ------------------------------------------------------------ the users db

    @Test
    public void query_neverReturnsThePassword() throws Exception {
        var text = mcp.readResource(USERS_COLL + "?filter=" + urlEncode("{\"_id\":\"" + USER_ID + "\"}"));

        var docs = BsonDocument.parse(text).getArray("content");
        assertEquals(1, docs.size(), "the user must be readable: " + text);
        assertNoPassword(docs.get(0).asDocument(), text);
    }

    @Test
    public void get_neverReturnsThePassword() throws Exception {
        var text = mcp.readResource(USERS_COLL + "/" + USER_ID);

        var doc = BsonDocument.parse(text);
        assertEquals(USER_ID, doc.getString("_id").getValue(), "the user must be readable: " + text);
        assertNoPassword(doc, text);
    }

    @Test
    public void aggregation_neverReturnsThePassword() throws Exception {
        var text = mcp.readResource(USERS_COLL + "/_aggrs/all");

        var docs = BsonDocument.parse(text).getArray("content");
        assertTrue(docs.stream().map(d -> d.asDocument().get("_id")).anyMatch(id -> id != null && id.isString() && USER_ID.equals(id.asString().getValue())),
                "the user must be in the aggregation output: " + text);
        docs.forEach(d -> assertNoPassword(d.asDocument(), text));
    }

    // ------------------------------------------------------- override-users-db

    @Test
    public void withoutTheOverride_anotherDbsUsersIsAnOrdinaryCollection() throws Exception {
        // the rule userPwdRemover applies on REST, so MCP must not diverge from it either way
        var rest = Unirest.get(OVERRIDE_COLL + "/" + USER_ID).basicAuth("admin", "secret").asString().getBody();
        assertTrue(BsonDocument.parse(rest).containsKey("password"), "REST parity: " + rest);

        var text = mcp.readResource(OVERRIDE_COLL + "/" + USER_ID);
        assertTrue(BsonDocument.parse(text).containsKey("password"), "MCP parity: " + text);
    }

    @Test
    public void withTheOverride_get_neverReturnsThePassword() throws Exception {
        var rest = Unirest.get(OVERRIDE_COLL + "/" + USER_ID + OVERRIDE_QUERY).basicAuth("admin", "secret").asString().getBody();
        assertNoPassword(BsonDocument.parse(rest), "REST: " + rest);

        var tenant = new McpTestClient(BASE, ADMIN_BASIC, OVERRIDE_QUERY);
        tenant.initialize();

        var text = tenant.readResource(OVERRIDE_COLL + "/" + USER_ID);
        var doc = BsonDocument.parse(text);
        assertEquals(USER_ID, doc.getString("_id").getValue(), "the user must be readable: " + text);
        assertNoPassword(doc, text);
    }

    @Test
    public void withTheOverride_query_neverReturnsThePassword() throws Exception {
        var tenant = new McpTestClient(BASE, ADMIN_BASIC, OVERRIDE_QUERY);
        tenant.initialize();

        var text = tenant.readResource(OVERRIDE_COLL);
        var docs = BsonDocument.parse(text).getArray("content");
        assertEquals(1, docs.size(), "the user must be readable: " + text);
        assertNoPassword(docs.get(0).asDocument(), text);
    }

    @Test
    public void withTheOverride_aggregation_neverReturnsThePassword() throws Exception {
        var tenant = new McpTestClient(BASE, ADMIN_BASIC, OVERRIDE_QUERY);
        tenant.initialize();

        var text = tenant.readResource(OVERRIDE_COLL + "/_aggrs/all");
        var docs = BsonDocument.parse(text).getArray("content");
        assertEquals(1, docs.size(), "the user must be in the aggregation output: " + text);
        assertNoPassword(docs.get(0).asDocument(), text);
    }

    // ------------------------------------------------------------------ helpers

    private static void assertNoPassword(BsonDocument doc, String text) {
        assertFalse(doc.containsKey("password"), "password must never be returned: " + text);
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
