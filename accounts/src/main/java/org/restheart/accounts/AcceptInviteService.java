package org.restheart.accounts;

import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;

import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.util.DbHelper;
import org.restheart.accounts.util.RequestOverrides;

/**
 * POST /auth/accept-invite — Accept an invitation for an existing user.
 *
 * <p>Request body: {@code { "token": "<invite-token>" }}
 * <p>Requires authentication ({@code secure = true}).
 */
@RegisterPlugin(
    name = "acceptInviteService",
    description = "POST /auth/accept-invite — accept an invitation for existing users",
    defaultURI = "/auth/accept-invite",
    secure = true,
    enabledByDefault = false)
public class AcceptInviteService implements JsonService, Initializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcceptInviteService.class);
        @Inject("accountsService")
    private AccountsService accountsService;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Override
    public void init() {
        aclRegistry.registerAllow(r ->
                r.getPath().equals("/auth/accept-invite") && (r.isPost() || r.isOptions()));
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) throws Exception {
        if (!req.isPost()) {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        var account = req.getAuthenticatedAccount();
        if (account == null) {
            res.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            return;
        }
        var email = account.getPrincipal().getName();

        if (req.getContent() == null || !req.getContent().isJsonObject()) {
            sendError(res, HttpStatus.SC_BAD_REQUEST, "Request body must be a JSON object");
            return;
        }
        var jo = req.getContent().getAsJsonObject();
        if (!jo.has("token") || jo.get("token").isJsonNull()) {
            sendError(res, HttpStatus.SC_BAD_REQUEST, "token is required");
            return;
        }
        var token = jo.get("token").getAsString().trim();

        var db = new DbHelper(mclient, RequestOverrides.db(req, conf));

        // Find invitation by token in auth_invitations collection
        var inviteOpt = db.findInvitationByToken(token);
        if (inviteOpt.isEmpty()) {
            sendError(res, HttpStatus.SC_UNAUTHORIZED, "Invalid or expired invite token");
            return;
        }

        var invite = inviteOpt.get();
        var invitedEmail = invite.getString("email").getValue();

        // Verify the invitation belongs to the authenticated user
        if (!invitedEmail.equals(email)) {
            sendError(res, HttpStatus.SC_FORBIDDEN, "This invitation is not for your account");
            return;
        }

        var orgId = invite.get("orgId");
        var role = invite.getString("role").getValue();

        accountsService.getMembershipProvider().addMember(email, orgId, role);

        // Delete the invitation after acceptance
        db.deleteInvitation(invite.getObjectId("_id"));

        LOGGER.info("User <{}> accepted invitation to org={} as role={}", email, orgId, role);

        res.setStatusCode(HttpStatus.SC_OK);
        var result = new JsonObject();
        result.addProperty("message", "Invitation accepted");
        res.setContent(result);
    }

    private void sendError(JsonResponse res, int status, String message) {
        res.setStatusCode(status);
        var err = new JsonObject();
        err.addProperty("message", message);
        res.setContent(err);
    }
}
