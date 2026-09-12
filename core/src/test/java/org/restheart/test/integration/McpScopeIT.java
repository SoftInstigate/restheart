package org.restheart.test.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kong.unirest.Unirest;

/**
 * The MCP catalogue has to be partitioned: a caller scoped to one service must see that service's
 * resources, under its own base URL, and nothing else.
 *
 * <p>Written before the feature existed and disabled until it did (restheart#733, #734). What they
 * defend, now that they pass, is the two mechanisms that had to meet for any of it to work:
 *
 * <ul>
 *   <li>{@code MongoMcpAwareImpl} describes only the databases in scope, and filters before URIs
 *       are built — a mount can hide the database name, so filtering afterwards would filter
 *       whichever entry had already overwritten the other.</li>
 *   <li>{@code resources/list} is answered from the MCP SDK's own registry, which belongs to a
 *       server and not to a request. No per-request override can reach it, which is why each scope
 *       gets a server of its own.</li>
 * </ul>
 *
 * <p><b>How a scope is simulated here.</b> A real partitioned deployment resolves the scope from
 * the hostname and attaches the caller's own base URL to every request. This suite runs one
 * RESTHeart on one hostname, and a host-parametric mount ({@code {host[0]}}) would change routing
 * for all 582 scenarios, so two query parameters stand in for it:
 *
 * <ul>
 *   <li>{@code ?mcpScope=<db>} — read by {@code testMcpScopeProvider}, a real
 *       {@code Provider<McpScopeProvider>} that answers {@code UNPARTITIONED} to any request
 *       without it. That is what lets this class exercise a partitioned instance while the other
 *       eighty-odd MCP tests, against the same running server, keep seeing today's behaviour.</li>
 *   <li>{@code ?_mcp-baseurl-override=<url>} — read by {@code mcpBaseUrlOverrideInterceptor},
 *       standing in for the deployment attaching the caller's own base URL.</li>
 * </ul>
 *
 * <p>Both mechanisms under test are the real ones; only their triggers differ.
 */
public class McpScopeIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";

    private static final String DB_A = "test-mcp-scope-a";
    private static final String DB_B = "test-mcp-scope-b";

    /** Deliberately the same collection name in both databases — the collision of problem 3. */
    private static final String COLL = "inventory";

    private static final String HOST_A = "http://svc-a.local:8080";
    private static final String HOST_B = "http://svc-b.local:8080";

    /** The scope is the database name — that is how {@code MongoService} reads it. */
    private static String query(String host, String scope) {
        return "?mcpScope=" + scope + "&_mcp-baseurl-override=" + host;
    }

    private static final String ADMIN_BASIC = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());

    @BeforeEach
    public void setUpTwoScopes() throws Exception {
        seed(DB_A, "notebook");
        seed(DB_B, "hammer");

        // past CachedResourceLookup's TTL (1s in conf-overrides), so the catalogue these tests
        // read is one built after the fixture existed
        Thread.sleep(1_500);
    }

    private static void seed(String db, String item) throws Exception {
        Unirest.put(BASE + "/" + db).basicAuth(ADMIN_ID, ADMIN_PWD).contentType("application/json").body("{}").asEmpty();

        var coll = Unirest.put(BASE + "/" + db + "/" + COLL)
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .contentType("application/json")
                .body("""
                        {
                          "mcp": { "enabled": true, "description": "Inventory of %s." }
                        }
                        """.formatted(db))
                .asEmpty();
        assertTrue(coll.getStatus() == 200 || coll.getStatus() == 201,
                "collection setup failed for " + db + ": " + coll.getStatus());

        Unirest.post(BASE + "/" + db + "/" + COLL)
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .contentType("application/json")
                .body("{\"item\":\"%s\",\"qty\":7}".formatted(item))
                .asEmpty();
    }

    // ------------------------------------------------------------------ listing

    @Test
    public void theCatalogueCarriesTheCallersOwnBaseUrl() throws Exception {
        // Every URI the catalogue advertises has to carry the caller's own host, or an agent
        // composes a request against someone else's. Today they all carry the node's static
        // public-base-url, because the SDK registry is built once, per server, not per request.
        var uris = resourceUrisFor(HOST_A, DB_A);

        assertFalse(uris.isEmpty(), "empty catalogue — nothing to assert on");
        assertTrue(uris.stream().allMatch(u -> u.startsWith(HOST_A)),
                "the catalogue advertises URIs on another host; got " + uris);
    }

    @Test
    public void aCatalogueScopedToOneService_doesNotListAnothersResources() throws Exception {
        var uris = resourceUrisFor(HOST_A, DB_A);

        // without this an empty catalogue would satisfy the real assertion for free
        assertTrue(uris.stream().anyMatch(u -> u.contains("/" + DB_A + "/" + COLL)),
                "the caller's own collection is missing from its catalogue; got " + uris);

        assertFalse(uris.stream().anyMatch(u -> u.contains("/" + DB_B + "/")),
                "another service's database is listed in this caller's catalogue; got " + uris);
    }

    @Test
    public void eachServiceGetsItsOwnCatalogue_notOneSharedList() throws Exception {
        // Also the guard for the one-shot `resourcesInitialized` flag: if the registry is
        // populated once per process instead of once per scope, whichever scope asks second
        // finds it empty or holding the first one's entries.
        var a = resourceUrisFor(HOST_A, DB_A);
        var b = resourceUrisFor(HOST_B, DB_B);

        assertTrue(a.stream().anyMatch(u -> u.contains("/" + DB_A + "/" + COLL)),
                "scope A lists nothing of its own; got " + a);
        assertTrue(b.stream().anyMatch(u -> u.contains("/" + DB_B + "/" + COLL)),
                "scope B lists nothing of its own; got " + b);

        assertFalse(a.stream().anyMatch(u -> u.contains("/" + DB_B + "/")), "A sees B: " + a);
        assertFalse(b.stream().anyMatch(u -> u.contains("/" + DB_A + "/")), "B sees A: " + b);
    }

    // ------------------------------------------------------------------ refusal

    @Test
    public void aRequestWhoseScopeCannotBeResolved_isRefusedWithBadRequest() throws Exception {
        // Not served an empty catalogue, which reads as "you have not created anything yet", and
        // not served the whole one either. 400 rather than 403: the request does not carry what
        // the provider needs, which is the caller's to fix, not a permission it lacks.
        var mcp = new McpTestClient(BASE, ADMIN_BASIC, "?mcpScope=%3F");

        var response = mcp.tryInitialize();

        assertEquals(400, response.statusCode(), "body: " + response.body());
        assertTrue(response.body().contains("scope_unresolved"),
                "the client is given nothing to act on: " + response.body());
    }

    // ------------------------------------------------------------------ reading

    @Test
    public void readingOutsideOwnScope_isRefused() throws Exception {
        // The URI is taken from scope B's own catalogue rather than composed here. Composing it
        // would prove nothing: an unregistered host is already refused by the SDK (problem 4,
        // covered by McpResourcesIT), so the test would pass without any scope check existing.
        var foreign = resourceUrisFor(HOST_B, DB_B).stream()
                .filter(u -> u.contains("/" + DB_B + "/" + COLL))
                .filter(u -> !u.endsWith("/_size"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("scope B advertises no readable inventory URI"));

        var mcp = new McpTestClient(BASE, ADMIN_BASIC, query(HOST_A, DB_A));
        mcp.initialize();

        // admin may read this collection over REST, so a refusal here can only come from the
        // scope — authorization and scope are separate gates, and this asserts the second one.
        var response = mcp.rawRpc("resources/read", """
                {"uri":"%s"}
                """.formatted(foreign));

        var refused = response.statusCode() != 200 || response.body().contains("\"error\"");

        assertTrue(refused,
                "a caller scoped to " + DB_A + " read " + foreign + "; body: " + response.body());
    }

    // ------------------------------------------------------------------ helpers

    private List<String> resourceUrisFor(String host, String scope) throws Exception {
        var mcp = new McpTestClient(BASE, ADMIN_BASIC, query(host, scope));
        mcp.initialize();

        return mcp.rpc("resources/list", null).getDocument("result").getArray("resources").stream()
                .map(r -> r.asDocument().getString("uri").getValue())
                .toList();
    }
}
