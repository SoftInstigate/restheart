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
 * MCP-enabled resource on the server — its URI, its
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

    /** Readable by the role, and kept out of its catalogue by the resource's own declaration. */
    private static final String BLACKLISTED_COLL = BASE + "/test-mcp-acl/catalog-blacklisted";

    /** Readable by the role, announced only to a plan this caller has not got. */
    private static final String GATED_COLL = BASE + "/test-mcp-acl/catalog-gated";

    /** Readable by the role, with a condition that is true for it. */
    private static final String CONDITIONAL_COLL = BASE + "/test-mcp-acl/catalog-conditional";

    /** Readable by the role; writable only by a call carrying {@code ?ticket=golden}. */
    private static final String TICKET_DB = BASE + "/test-mcp-ticket";
    private static final String TICKET_COLL = TICKET_DB + "/tickets";

    private static final String READER_BASIC =
            "Basic " + Base64.getEncoder().encodeToString("aclowner1:secret".getBytes());
    private static final String ADMIN_BASIC =
            "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());

    private McpTestClient reader;
    private McpTestClient admin;

    @BeforeEach
    public void setUpTwoCollectionsOneReadable() throws Exception {
        createCollection(BASE + "/test-mcp-acl", VISIBLE_COLL, "Inventory the reader may read.");
        createCollection(BASE + "/test-mcp-acl", BLACKLISTED_COLL, "Underperformers, do not announce.",
                "\"hide_from_roles\": [\"aclreader\"]");
        createCollection(BASE + "/test-mcp-acl", GATED_COLL, "Gold customers only.",
                "\"show_if\": \"equals(@user.plan, 'gold')\"");
        createCollection(BASE + "/test-mcp-acl", CONDITIONAL_COLL, "Announced to whoever is authenticated.",
                "\"show_if\": \"equals(@authenticated, 'true')\"");
        createCollection(HIDDEN_DB, HIDDEN_COLL, HIDDEN_DESCRIPTION);
        createCollection(TICKET_DB, TICKET_COLL, "Writable only with a ticket.");

        reader = new McpTestClient(BASE, READER_BASIC);
        reader.initialize();

        admin = new McpTestClient(BASE, ADMIN_BASIC);
        admin.initialize();
        admin.awaitResource(HIDDEN_COLL);
    }

    private static void createCollection(String db, String collection, String description) {
        createCollection(db, collection, description, null);
    }

    /** @param extraMcp further keys of the {@code mcp} block, already as JSON, or {@code null} */
    private static void createCollection(String db, String collection, String description, String extraMcp) {
        Unirest.put(db).basicAuth("admin", "secret").contentType("application/json").body("{}").asEmpty();

        var mcp = "\"enabled\": true, \"description\": \"" + description + "\""
                + (extraMcp == null ? "" : ", " + extraMcp);

        Unirest.put(collection)
                .basicAuth("admin", "secret")
                .contentType("application/json")
                .body("{\"mcp\": { " + mcp + " }}")
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

    /**
     * An action whose rule reads a query parameter is listed like any other. A listing carries no
     * arguments, so probing it would answer no for the very caller entitled to it — and an agent
     * that reads a catalogue without {@code create} concludes the service is read-only, which is
     * how three matches of {@code examples/market-game} ended.
     */
    @Test
    public void listApis_offersAnActionWhoseRuleReadsAQueryParameter() throws Exception {
        var described = reader.callTool("list_apis", """
                {"resource":"%s"}
                """.formatted(TICKET_COLL));

        var actions = described.getDocument("actions");

        assertTrue(actions.containsKey("query"), actions.toJson());
        assertTrue(actions.containsKey("create"),
                "the write is what this collection is for, and the listing cannot know about the ticket: "
                        + actions.toJson());
    }

    @Test
    public void callApi_runsTheActionTheListingCouldNotOffer() throws Exception {
        var refused = reader.callTool("call_api", """
                {"resource":"%s","action":"create","args":{"body":{"n":1}}}
                """.formatted(TICKET_COLL));

        assertEquals(403, refused.getInt32("status").getValue(),
                "without the ticket the ACL refuses it — as a result the agent can read, not as an unknown resource: "
                        + refused.toJson());

        var allowed = reader.callTool("call_api", """
                {"resource":"%s","action":"create","args":{"ticket":"golden","body":{"n":2}}}
                """.formatted(TICKET_COLL));

        assertEquals(201, allowed.getInt32("status").getValue(),
                "with the ticket the same call must go through: " + allowed.toJson());
    }

    @Test
    public void resourcesList_stillShowsEverythingToAdmin() throws Exception {
        var listed = admin.rpc("resources/list", "{}").toJson();

        assertTrue(listed.contains(HIDDEN_COLL),
                "admin can read it, so hiding it from admin means the filter is denying rather than filtering: " + listed);
    }

    /**
     * The actions of a resource are the same for everybody, because they say what the resource is
     * rather than what this caller may do with it: a REST collection has them all, an aggregation
     * only its execution. Who may do what is decided when the call is made.
     */
    @Test
    public void aResourceIsDescribedWithEveryActionItsKindAffords() throws Exception {
        var described = reader.callTool("list_apis", """
                {"resource":"%s"}
                """.formatted(VISIBLE_COLL));

        var forReader = described.getDocument("actions").keySet();

        assertTrue(forReader.contains("query"), forReader.toString());
        assertTrue(forReader.contains("create"),
                "a collection has create whoever is asking; the 403 comes when it is called: " + forReader);

        var forAdmin = admin.callTool("list_apis", """
                {"resource":"%s"}
                """.formatted(VISIBLE_COLL));

        assertEquals(forReader, forAdmin.getDocument("actions").keySet(),
                "the actions do not depend on the caller: only the resources do");
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

    // ------------------------------------------------------------------------
    // what each action says about this caller
    // ------------------------------------------------------------------------

    /**
     * The collection is published; the operations on the collection itself are not offered to a
     * caller whose rules refuse them. The resource and its actions are decided by the same rule,
     * one level apart.
     */
    @Test
    public void theOperationsOnTheCollectionItselfAreNotOfferedToWhoCannotPerformThem() throws Exception {
        var described = reader.callTool("list_apis", "{\"resource\":\"" + VISIBLE_COLL + "\"}");
        var actions = described.getDocument("actions");

        assertTrue(actions.containsKey("query"), "the collection is published and readable: " + actions.keySet());

        for (var withheld : java.util.List.of("drop", "set_properties", "create_index", "delete_index",
                "properties", "indexes")) {
            assertFalse(actions.containsKey(withheld),
                    withheld + " needs allowManagementRequests, which no rule of this role grants: " + actions.keySet());
        }

        // and the transports do not name what the description withheld
        assertFalse(described.getArray("transports").toJson().contains("drop"),
                "the transports must agree with the actions: " + described.getArray("transports").toJson());
    }

    /**
     * The positive control, and the proof that they are published at all: the administrator's
     * permission grants management requests, so the same collection offers them.
     */
    @Test
    public void theyAreOfferedToWhoCanPerformThem_andSayTheyActOnTheCollection() throws Exception {
        var described = admin.callTool("list_apis", "{\"resource\":\"" + VISIBLE_COLL + "\"}");
        var actions = described.getDocument("actions");

        assertTrue(actions.keySet().containsAll(java.util.List.of(
                "properties", "set_properties", "drop", "indexes", "create_index", "delete_index")),
                "admin grants allowManagementRequests, so the data management API is offered: " + actions.keySet());

        assertEquals("resource", actions.getDocument("drop").getString("target").getValue(),
                "drop acts on the collection, and must say so: " + actions.getDocument("drop").toJson());
        assertFalse(actions.getDocument("delete").containsKey("target"),
                "delete acts on a document, which is the ordinary case and stays unsaid: "
                        + actions.getDocument("delete").toJson());
    }

    /** The drop needs the collection's ETag, and an action that needs one declares where it goes. */
    @Test
    public void theDropDeclaresTheEtagItNeeds_asAHeader() throws Exception {
        var described = admin.callTool("list_apis", "{\"resource\":\"" + VISIBLE_COLL + "\"}");
        var etag = described.getDocument("actions").getDocument("drop").getDocument("params").getDocument("etag");

        assertEquals("If-Match", etag.getString("header").getValue(),
                "without it the drop answers 409 and the agent cannot tell why: " + etag.toJson());
    }

    /**
     * An action whose rule decides on a query parameter is offered, because a listing cannot know
     * what the call will carry. This is the defect that filtering actions once caused (#743), and
     * the reason the answer has three values and not two.
     */
    @Test
    public void anActionWhoseRuleReadsAQueryParameterIsStillOffered() throws Exception {
        var actions = reader.callTool("list_apis", "{\"resource\":\"" + TICKET_COLL + "\"}").getDocument("actions");

        assertTrue(actions.containsKey("create"),
                "the ticket is an argument of the call, which a listing does not have: " + actions.keySet());
        assertTrue(actions.getDocument("create").containsKey("note"),
                "and the catalogue says why it could not decide: " + actions.getDocument("create").toJson());
    }

    // ------------------------------------------------------------------------
    // what the resource itself declares
    // ------------------------------------------------------------------------

    /**
     * The case the permissions cannot express: the ACL allows the read — this collection is inside
     * the prefix the role may GET — and the name is still not to be announced.
     */
    @Test
    public void hideFromRoles_keepsAResourceTheAclAllowsOutOfTheCatalogue() throws Exception {
        var catalog = admin.callTool("list_apis", "{}").toJson();
        assertTrue(catalog.contains(BLACKLISTED_COLL),
                "admin holds no blacklisted role, so it must still be announced: " + catalog);

        var forReader = reader.callTool("list_apis", "{}").toJson();
        assertFalse(forReader.contains(BLACKLISTED_COLL),
                "hide_from_roles names this caller's role, so the resource must not be announced: " + forReader);

        var listed = reader.rpc("resources/list", "{}").toJson();
        assertFalse(listed.contains(BLACKLISTED_COLL),
                "the same rule must hold on resources/list: " + listed);
    }

    /** Announcing it is refused, reading it is not: the declaration is about disclosure. */
    @Test
    public void hideFromRoles_doesNotRefuseTheReadItself() throws Exception {
        var read = Unirest.get(BLACKLISTED_COLL).basicAuth("aclowner1", "secret").asString();

        assertEquals(200, read.getStatus(),
                "hide_from_roles must not deny anything, only keep it quiet: " + read.getBody());
    }

    /**
     * A condition on the caller that does not hold: not announced, though the ACL allows the read.
     *
     * <p>{@code @user.plan} is not a property of this account, so the condition cannot be shown to
     * hold — and a declared condition announces only when it does. The absence of an answer is not
     * an answer.
     */
    @Test
    public void showIf_thatDoesNotHoldKeepsTheResourceQuiet() throws Exception {
        var forReader = reader.callTool("list_apis", "{}").toJson();

        assertFalse(forReader.contains(GATED_COLL),
                "this caller has no plan, so the condition does not hold and the resource is not announced: "
                        + forReader);
    }

    /** And one that does hold announces it, which is the positive control of the test above. */
    @Test
    public void showIf_thatHoldsAnnouncesTheResource() throws Exception {
        var forReader = reader.callTool("list_apis", "{}").toJson();

        assertTrue(forReader.contains(CONDITIONAL_COLL),
                "the caller is authenticated, so the condition holds: " + forReader);
    }
}
