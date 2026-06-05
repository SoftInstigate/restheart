package org.restheart.accounts;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.util.Errors;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;
import org.restheart.security.JwtAccount;
import org.restheart.security.MongoRealmAccount;
import org.restheart.utils.HttpStatus;

/**
 * GET /auth/tenants
 *
 * <p>Returns the list of tenant memberships for the authenticated user via the
 * active {@link org.restheart.plugins.accounts.MembershipProvider}.
 *
 * <p>Response body example:
 * <pre>{@code
 * [
 *   { "id": "abc123", "name": "Acme Corp", "role": "owner",  "active": true  },
 *   { "id": "def456", "name": "Other Co",  "role": "member", "active": false }
 * ]
 * }</pre>
 *
 * {@code active} marks the tenant currently encoded in the caller's JWT.
 *
 * <p>This endpoint can be disabled via {@code accountsConfig.membership-endpoints-enabled: false}.
 */
@RegisterPlugin(
        name             = "getTenantsService",
        description      = "GET /auth/tenants — list current user's tenant memberships",
        defaultURI       = "/auth/tenants",
        enabledByDefault = false)
public class GetTenantsService implements JsonService {

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("accountsService")
    private AccountsService accountsService;

    @OnInit
    public void onInit() {
        if (conf.membershipEndpointsEnabled()) {
            aclRegistry.registerAllow(r -> r.getPath().equals("/auth/tenants") && (r.isGet() || r.isOptions()));
        }
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        if (req.isOptions()) { handleOptions(req); return; }

        if (!conf.membershipEndpointsEnabled()) {
            Errors.error(res, HttpStatus.SC_NOT_FOUND, "Endpoint not available");
            return;
        }

        if (!req.isGet())    { res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED); return; }

        var account = req.getAuthenticatedAccount();
        if (account == null) {
            Errors.error(res, HttpStatus.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        var email = account.getPrincipal().getName();

        // Delegate to the MembershipProvider
        var memberships = accountsService.getMembershipProvider().listMemberships(email);

        var result = new JsonArray();
        for (var m : memberships) {
            var obj = new JsonObject();
            obj.addProperty("id",     m.tenantId());
            obj.addProperty("name",   m.displayName());
            obj.addProperty("role",   m.role());
            obj.addProperty("active", m.active());
            result.add(obj);
        }

        res.setContent(result);
        res.setStatusCode(HttpStatus.SC_OK);
    }
}
