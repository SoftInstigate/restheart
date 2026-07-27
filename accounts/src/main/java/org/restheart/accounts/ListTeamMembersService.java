package org.restheart.accounts;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.restheart.plugins.accounts.AccountsConfigData;
import org.restheart.accounts.util.Errors;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;
import org.restheart.utils.HttpStatus;

/**
 * GET /auth/team/members
 *
 * <p>Returns the member list (email, name, role, joinedAt) of the authenticated
 * user's active team, denormalized against each member's {@code profile}. There is
 * no caller-supplied team filter — always the caller's own active team, to avoid
 * needing separate authorization logic for "can I see team X's roster."
 *
 * <p>Response body example:
 * <pre>{@code
 * [
 *   { "email": "alice@example.com", "name": "Alice Smith", "role": "owner",  "joinedAt": "2026-01-01T00:00:00Z" },
 *   { "email": "bob@example.com",   "name": "Bob Jones",   "role": "member", "joinedAt": "2026-01-02T00:00:00Z" }
 * ]
 * }</pre>
 *
 * <p>This endpoint can be disabled via {@code accountsConfig.membership-endpoints-enabled: false}.
 */
@RegisterPlugin(
        name             = "listTeamMembersService",
        description      = "GET /auth/team/members — list the caller's active team's members",
        defaultURI       = "/auth/team/members",
        secure           = true,
        enabledByDefault = false)
public class ListTeamMembersService implements JsonService {

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("accountsService")
    private AccountsService accountsService;

    @OnInit
    public void onInit() {
        if (conf.membershipEndpointsEnabled()) {
            aclRegistry.registerAllow(r -> r.getPath().equals("/auth/team/members") && (r.isGet() || r.isOptions()));
        }
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        if (req.isOptions()) { handleOptions(req); return; }

        if (!conf.membershipEndpointsEnabled()) {
            Errors.error(res, HttpStatus.SC_NOT_FOUND, "Endpoint not available");
            return;
        }

        if (!req.isGet()) { res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED); return; }

        var account = req.getAuthenticatedAccount();
        var email = account.getPrincipal().getName();

        var membershipProvider = accountsService.getMembershipProvider(req);
        var membership = membershipProvider.activeMembership(email);
        var teamId = membership.map(m -> m.teamId()).orElse(null);
        if (teamId == null || teamId.isNull()) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "No team associated with your account");
            return;
        }

        var members = membershipProvider.listTeamMembers(teamId);

        var result = new JsonArray();
        for (var m : members) {
            var obj = new JsonObject();
            obj.addProperty("email", m.email());
            obj.addProperty("name", m.name());
            obj.addProperty("role", m.role());
            obj.addProperty("joinedAt", m.joinedAt() != null ? m.joinedAt().toString() : null);
            result.add(obj);
        }

        res.setContent(result);
        res.setStatusCode(HttpStatus.SC_OK);
    }
}
