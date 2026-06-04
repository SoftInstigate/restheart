package org.restheart.accounts;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;
import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.util.DbHelper;
import org.restheart.accounts.util.Errors;
import org.restheart.accounts.util.RequestOverrides;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;
import org.restheart.utils.HttpStatus;

/**
 * GET /auth/tenants
 *
 * <p>Returns the list of tenant memberships for the authenticated user.
 *
 * <p>Response body example:
 * <pre>{@code
 * [
 *   { "id": "abc123", "name": "Acme Corp", "role": "owner",  "active": true  },
 *   { "id": "def456", "name": "Other Co",  "role": "user",   "active": false }
 * ]
 * }</pre>
 *
 * {@code active} marks the tenant currently encoded in the caller's JWT.
 */
@RegisterPlugin(
        name             = "getTenantsService",
        description      = "GET /auth/tenants — list current user's tenant memberships",
        defaultURI       = "/auth/tenants",
        enabledByDefault = true)
public class GetTenantsService implements JsonService {

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @OnInit
    public void onInit() {
        aclRegistry.registerAllow(r -> r.getPath().equals("/auth/tenants") && (r.isGet() || r.isOptions()));
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        if (req.isOptions()) { handleOptions(req); return; }
        if (!req.isGet())    { res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED); return; }

        var account = req.getAuthenticatedAccount();
        if (account == null) {
            Errors.error(res, HttpStatus.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        var email = account.getPrincipal().getName();
        var db    = new DbHelper(mclient, RequestOverrides.db(req, conf));

        var userOpt = db.findUser(email);
        if (userOpt.isEmpty()) {
            Errors.error(res, HttpStatus.SC_NOT_FOUND, "User not found");
            return;
        }

        // Determine active tenant from the account properties (JWT claim)
        var activeTenant = extractTenant(account);

        var result = new JsonArray();
        var userDoc = userOpt.get();

        if (userDoc.containsKey("tenants") && userDoc.get("tenants").isArray()) {
            for (var entry : userDoc.getArray("tenants")) {
                if (!entry.isDocument()) continue;
                var e        = entry.asDocument();
                var tenantId = e.containsKey("id")   && e.get("id").isString()   ? e.getString("id").getValue()   : null;
                var role     = e.containsKey("role")  && e.get("role").isString()  ? e.getString("role").getValue()  : "user";
                if (tenantId == null) continue;

                // Load team name (best-effort)
                var teamName = tenantId;
                try {
                    teamName = db.findTeam(tenantId)
                            .filter(t -> t.containsKey("name") && t.get("name").isString())
                            .map(t -> t.getString("name").getValue())
                            .orElse(tenantId);
                } catch (Exception ex) { /* keep fallback */ }

                var obj = new JsonObject();
                obj.addProperty("id",     tenantId);
                obj.addProperty("name",   teamName);
                obj.addProperty("role",   role);
                obj.addProperty("active", tenantId.equals(activeTenant));
                result.add(obj);
            }
        }

        res.setContent(result);
        res.setStatusCode(HttpStatus.SC_OK);
    }

    private static String extractTenant(io.undertow.security.idm.Account account) {
        return switch (account) {
            case org.restheart.security.MongoRealmAccount mra -> {
                var props = mra.properties();
                yield props != null && props.get("tenant") != null && props.get("tenant").isString()
                        ? props.get("tenant").asString().getValue() : null;
            }
            case org.restheart.security.JwtAccount jwt -> {
                var props = jwt.propertiesAsMap();
                yield props != null && props.get("tenant") instanceof String s ? s : null;
            }
            default -> null;
        };
    }
}
