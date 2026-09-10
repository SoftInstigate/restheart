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
 * A caller must not be able to enumerate what it cannot read.
 *
 * <p>Reads are authorized one at a time, and {@link McpAclEnforcementIT} covers that. The catalog
 * used to be the same for everybody, so a caller allowed to reach {@code /mcp} could list every
 * MCP-enabled resource on the server — its URI, its actions including the writing ones, its
 * parameter names, and its {@code mcp.description}, which is prose written to explain what the data
 * is. This class pins that it no longer can, on every channel that lists or describes:
 * {@code resources/list}, {@code resources/templates/list}, {@code list_apis} both as a catalog and
 * as a drill-down, and {@code how_to_call}.
 *
 * <p>Every test carries a positive control. A filter that hid everything would satisfy "the private
 * collection is absent" perfectly, and would be just as broken as one that hid nothing.
 */
public class McpCatalogVisibilityIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";

    /** Inside the prefix the {@code aclreader} role may GET. */
    private static final String VISIBLE_COLL = BASE + "/test-mcp-acl/catalog-visible";

    /** Outside it: MCP-enabled, so it is in the server's catalog, but this role may not read it. */
    private static final String HIDDEN_DB = BASE + "/test-mcp-hidden";
    private static final String HIDDEN_COLL = HIDDEN_DB + "/catalog-hidden";

    private static final String HIDDEN_DESCRIPTION = "Salary review notes, pending approval.";

    private static final String READER_BASIC =
            "Basic " + Base64.getEncoder().encodeToString("aclowner1:secret".getBytes());
    private static final String ADMIN_BASIC =
            "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());

    private McpTestClient reader;
    private McpTestClient admin;

    @BeforeEach
    public void setUpTwoCollectionsOneReadable() throws Exception {
        createCollection(BASE + "/test-mcp-acl", VISIBLE_COLL, "Inventory the reader may read.");
        createCollection(HIDDEN_DB, HIDDEN_COLL, HIDDEN_DESCRIPTION);

        // past CachedResourceLookup's TTL (conf-overrides sets it to 1s)
        Thread.sleep(1_500);

        reader = new McpTestClient(BASE, READER_BASIC);
        reader.initialize();

        admin = new McpTestClient(BASE, ADMIN_BASIC);
        admin.initialize();
    }

    private static void createCollection(String db, String collection, String description) {
        Unirest.put(db).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();

        Unirest.put(collection)
                .basicAuth("admin", "secret")
                .contentType("application/json")
                .body("{\"mcp\": { \"enabled\": true, \"description\": \"" + description + "\" }}")
                .asEmpty();
    }

    @Test
    public void resourcesList_omitsWhatTheCallerCannotRead() throws Exception {
        var listed = reader.rpc("resources/list", "{}").toJson();

        assertTrue(listed.contains(VISIBLE_COLL), "the readable collection is missing: " + listed);
        assertFalse(listed.contains(HIDDEN_COLL), "a collection this role cannot read was listed: " + listed);
    }

    @Test
    public void resourcesTemplatesList_omitsWhatTheCallerCannotRead() throws Exception {
        var listed = reader.rpc("resources/templates/list", "{}").toJson();

        assertTrue(listed.contains(VISIBLE_COLL), "the readable collection's templates are missing: " + listed);
        assertFalse(listed.contains(HIDDEN_COLL), "templates of a collection this role cannot read were listed: " + listed);
    }

    @Test
    public void resourcesList_stillShowsEverythingToAdmin() throws Exception {
        var listed = admin.rpc("resources/list", "{}").toJson();

        assertTrue(listed.contains(HIDDEN_COLL),
                "admin can read it, so hiding it from admin means the filter is denying rather than filtering: " + listed);
    }

    @Test
    public void listApisCatalog_omitsWhatTheCallerCannotRead() throws Exception {
        var catalog = reader.callTool("list_apis", "{}").toJson();

        assertTrue(catalog.contains(VISIBLE_COLL), "the readable collection is missing from list_apis: " + catalog);
        assertFalse(catalog.contains(HIDDEN_COLL), "list_apis offered a collection this role cannot read: " + catalog);
    }

    /**
     * The description is the point: it is the one field written in prose to say what the data is,
     * so leaving the URI out while letting the text through would defeat the exercise.
     */
    @Test
    public void listApisCatalog_doesNotLeakTheDescriptionOfAHiddenResource() throws Exception {
        var catalog = reader.callTool("list_apis", "{}").toJson();

        assertFalse(catalog.contains(HIDDEN_DESCRIPTION), "a hidden resource's description leaked: " + catalog);
    }

    /**
     * Asking for one resource by URI has to obey the same filter as the catalog, or the drill-down
     * hands back in full — actions, parameters, body schema — exactly what the listing withheld.
     */
    @Test
    public void listApisDrillDown_treatsAHiddenResourceAsUnknown() throws Exception {
        var error = reader.callToolExpectingError("list_apis", "{\"resource\": \"" + HIDDEN_COLL + "\"}");

        assertFalse(error.contains(HIDDEN_DESCRIPTION), "the drill-down leaked the description: " + error);
    }

    @Test
    public void listApisDrillDown_stillDescribesItToAdmin() throws Exception {
        var described = admin.callTool("list_apis", "{\"resource\": \"" + HIDDEN_COLL + "\"}").toJson();

        assertTrue(described.contains(HIDDEN_DESCRIPTION),
                "admin may read it, so the drill-down must still describe it: " + described);
    }

    /**
     * {@code how_to_call} composes a ready-to-send request. For a resource the caller cannot invoke
     * that is the same disclosure as the drill-down, one step further along.
     */
    @Test
    public void howToCall_refusesToComposeForAHiddenResource() throws Exception {
        var error = reader.callToolExpectingError("how_to_call",
                "{\"resource\": \"" + HIDDEN_COLL + "\", \"action\": \"query\"}");

        assertFalse(error.contains(HIDDEN_COLL + "?"), "how_to_call composed a request for a hidden resource: " + error);
        assertTrue(error.toLowerCase().contains("unknown resource"),
                "expected the hidden resource to be reported as unknown, got: " + error);
    }

    /**
     * A subscription is delivered on a stream the client opens with a {@code GET}; a caller granted
     * only {@code POST} on the endpoint would get a subscription that succeeds and never fires.
     * Refusing it up front is the difference between an answer and a silent forever-wait.
     */
    @Test
    public void subscribe_isRefusedWhenTheCallerCannotOpenTheNotificationStream() throws Exception {
        var error = reader.rawRpc("resources/subscribe", """
                {"uri":"%s"}
                """.formatted(VISIBLE_COLL));

        // aclreader holds path-prefix /mcp with no method restriction, so this one CAN open it:
        // the subscription must be accepted, which is what makes the negative case meaningful
        assertEquals(200, error.statusCode(), "subscribing should have been accepted: " + error.body());
        assertFalse(error.body().contains("not authorized to open it"),
                "a caller that may open the stream was refused: " + error.body());
    }

    @Test
    public void howToCall_stillComposesForAResourceTheCallerMayRead() throws Exception {
        var descriptor = reader.callTool("how_to_call",
                "{\"resource\": \"" + VISIBLE_COLL + "\", \"action\": \"query\"}").toJson();

        assertTrue(descriptor.contains(VISIBLE_COLL),
                "the reader may read this collection, so how_to_call must compose for it: " + descriptor);
    }
}
