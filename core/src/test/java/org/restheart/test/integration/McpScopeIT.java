package org.restheart.test.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import kong.unirest.Unirest;

/**
 * The MCP catalogue has to be partitioned: a caller scoped to one service must see that service's
 * resources, under its own base URL, and nothing else.
 *
 * <p><b>These tests fail today, by design.</b> They state the requirement before the feature
 * exists, so they are {@link Disabled} to keep the suite green. Remove the annotation — or run with
 * {@code -Djunit.jupiter.conditions.deactivate='*'} — to watch them fail; the day they pass, the
 * partitioning works. See {@code hidden/analysis/RESTHEART-AI-MULTI-TENANCY.md}.
 *
 * <p><b>Why they fail.</b> Two independent reasons, and the second is the more interesting one:
 *
 * <ul>
 *   <li>{@code MongoMcpAwareImpl.describeMcp} loops over {@code metadata.databaseNames()} — every
 *       non-reserved database of the MongoDB instance. On a shared process that is every
 *       customer's database.</li>
 *   <li>{@code resources/list} is answered from the MCP SDK's own registry, and
 *       {@code syncResourceRegistry} populates that registry from the <em>static</em>
 *       {@code publicBaseUrl} field. The per-request override
 *       ({@code override-ai-mcp-public-base-url}) reaches {@code resources/read} and the tools,
 *       but it cannot reach listing: the registry belongs to the server, not to the request. This
 *       is why a per-request override alone cannot make the catalogue tenant-aware, and why the
 *       design needs one server per scope.</li>
 * </ul>
 *
 * <p><b>How a scope is simulated here.</b> On Cloud Shared each service has its own hostname and
 * the deployment layer attaches the matching base URL to every request. This suite runs one
 * RESTHeart on one hostname, and a host-parametric mount ({@code {host[0]}}) would change routing
 * for all 582 scenarios — so {@code mcpBaseUrlOverrideInterceptor} attaches the same override from
 * a query parameter instead. The mechanism under test is the real one; only the trigger differs.
 */
public class McpScopeIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";

    private static final String DB_A = "test-mcp-scope-a";
    private static final String DB_B = "test-mcp-scope-b";

    /** Deliberately the same collection name in both databases — the collision of problem 3. */
    private static final String COLL = "inventory";

    private static final String HOST_A = "http://svc-a.local:8080";
    private static final String HOST_B = "http://svc-b.local:8080";

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
    @Disabled("states a requirement for MCP scope partitioning — not implemented yet")
    public void theCatalogueCarriesTheCallersOwnBaseUrl() throws Exception {
        // Every URI the catalogue advertises has to carry the caller's own host, or an agent
        // composes a request against someone else's. Today they all carry the node's static
        // public-base-url, because the SDK registry is built once, per server, not per request.
        var uris = resourceUrisFor(HOST_A);

        assertFalse(uris.isEmpty(), "empty catalogue — nothing to assert on");
        assertTrue(uris.stream().allMatch(u -> u.startsWith(HOST_A)),
                "the catalogue advertises URIs on another host; got " + uris);
    }

    @Test
    @Disabled("states a requirement for MCP scope partitioning — not implemented yet")
    public void aCatalogueScopedToOneService_doesNotListAnothersResources() throws Exception {
        var uris = resourceUrisFor(HOST_A);

        // without this an empty catalogue would satisfy the real assertion for free
        assertTrue(uris.stream().anyMatch(u -> u.contains("/" + DB_A + "/" + COLL)),
                "the caller's own collection is missing from its catalogue; got " + uris);

        assertFalse(uris.stream().anyMatch(u -> u.contains("/" + DB_B + "/")),
                "another service's database is listed in this caller's catalogue; got " + uris);
    }

    @Test
    @Disabled("states a requirement for MCP scope partitioning — not implemented yet")
    public void eachServiceGetsItsOwnCatalogue_notOneSharedList() throws Exception {
        // Also the guard for the one-shot `resourcesInitialized` flag: if the registry is
        // populated once per process instead of once per scope, whichever scope asks second
        // finds it empty or holding the first one's entries.
        var a = resourceUrisFor(HOST_A);
        var b = resourceUrisFor(HOST_B);

        assertTrue(a.stream().anyMatch(u -> u.contains("/" + DB_A + "/" + COLL)),
                "scope A lists nothing of its own; got " + a);
        assertTrue(b.stream().anyMatch(u -> u.contains("/" + DB_B + "/" + COLL)),
                "scope B lists nothing of its own; got " + b);

        assertFalse(a.stream().anyMatch(u -> u.contains("/" + DB_B + "/")), "A sees B: " + a);
        assertFalse(b.stream().anyMatch(u -> u.contains("/" + DB_A + "/")), "B sees A: " + b);
    }

    // ------------------------------------------------------------------ reading

    @Test
    @Disabled("states a requirement for MCP scope partitioning — not implemented yet")
    public void readingOutsideOwnScope_isRefused() throws Exception {
        // The URI is taken from scope B's own catalogue rather than composed here. Composing it
        // would prove nothing: an unregistered host is already refused by the SDK (problem 4,
        // covered by McpResourcesIT), so the test would pass without any scope check existing.
        var foreign = resourceUrisFor(HOST_B).stream()
                .filter(u -> u.contains("/" + DB_B + "/" + COLL))
                .filter(u -> !u.endsWith("/_size"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("scope B advertises no readable inventory URI"));

        var mcp = new McpTestClient(BASE, ADMIN_BASIC, "?_mcp-baseurl-override=" + HOST_A);
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

    private List<String> resourceUrisFor(String host) throws Exception {
        var mcp = new McpTestClient(BASE, ADMIN_BASIC, "?_mcp-baseurl-override=" + host);
        mcp.initialize();

        return mcp.rpc("resources/list", null).getDocument("result").getArray("resources").stream()
                .map(r -> r.asDocument().getString("uri").getValue())
                .toList();
    }
}
