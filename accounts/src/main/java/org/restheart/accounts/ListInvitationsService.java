package org.restheart.accounts;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;
import org.restheart.plugins.accounts.AccountsConfigData;
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
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;

/**
 * GET /auth/invitations
 *
 * <p>Returns all pending (non-expired) invitations for the caller's active team.
 * Owner/admin only — members cannot list invitations.
 *
 * <p>Response body example:
 * <pre>{@code
 * [
 *   { "email": "bob@example.com", "role": "member", "isNewUser": true, "createdAt": "2026-01-01T00:00:00Z", "expiresAt": "2026-01-02T00:00:00Z" },
 *   { "email": "carol@example.com", "role": "member", "isNewUser": false, "createdAt": "2026-01-01T00:00:00Z", "expiresAt": "2026-01-02T00:00:00Z" }
 * ]
 * }</pre>
 *
 * <p>The token field is intentionally excluded from the response (sensitive — one-shot secret).
 *
 * <p>This endpoint can be disabled via {@code accountsConfig.membership-endpoints-enabled: false}.
 */
@RegisterPlugin(
        name             = "listInvitationsService",
        description      = "GET /auth/invitations — list pending invitations for the caller's active team",
        defaultURI       = "/auth/invitations",
        secure           = true,
        enabledByDefault = false)
public class ListInvitationsService implements JsonService {

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("accountsService")
    private AccountsService accountsService;

    @Inject("mclient")
    private MongoClient mclient;

    @OnInit
    public void onInit() {
        if (conf.membershipEndpointsEnabled()) {
            aclRegistry.registerAllow(r -> r.getPath().equals("/auth/invitations") && (r.isGet() || r.isOptions()));
        }
    }

    private DbHelper db(JsonRequest req) {
        return new DbHelper(mclient, RequestOverrides.db(req, conf), RequestOverrides.usersCollection(req, conf));
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) throws Exception {
        if (req.isOptions()) { handleOptions(req); return; }

        if (!conf.membershipEndpointsEnabled()) {
            Errors.error(res, HttpStatus.SC_NOT_FOUND, "Endpoint not available");
            return;
        }

        if (!req.isGet()) {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        var account = req.getAuthenticatedAccount();
        var email = account.getPrincipal().getName();
        var membership = accountsService.getMembershipProvider(req);
        var active = membership.activeMembership(email);

        if (active.isEmpty()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "No active team");
            return;
        }

        var teamId = active.get().teamId();

        var invitations = db(req).listInvitationsByTeam(teamId);

        var result = new JsonArray();
        var now = System.currentTimeMillis();
        for (var invite : invitations) {
            var obj = new JsonObject();
            obj.addProperty("email", invite.getString("email").getValue());
            obj.addProperty("role", invite.getString("role").getValue());
            obj.addProperty("isNewUser", invite.containsKey("isNewUser") && invite.getBoolean("isNewUser").getValue());
            if (invite.containsKey("createdAt")) {
                obj.addProperty("createdAt", java.time.Instant.ofEpochMilli(invite.getDateTime("createdAt").getValue()).toString());
            }
            if (invite.containsKey("expiresAt")) {
                var expiresAt = invite.getDateTime("expiresAt").getValue();
                obj.addProperty("expiresAt", java.time.Instant.ofEpochMilli(expiresAt).toString());
                obj.addProperty("expired", expiresAt < now);
            }
            result.add(obj);
        }

        res.setContent(result);
        res.setStatusCode(HttpStatus.SC_OK);
    }
}
