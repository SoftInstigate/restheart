package org.restheart.accounts;

import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;

import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.util.DbHelper;

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
public class AcceptInviteService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcceptInviteService.class);
    private static final long INVITE_TTL_MS = 7L * 24 * 60 * 60 * 1000;

    @Inject("accountsService")
    private AccountsService accountsService;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

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

        var db = new DbHelper(mclient, conf.db());
        var userOpt = db.findUser(email);
        if (userOpt.isEmpty()) {
            sendError(res, HttpStatus.SC_NOT_FOUND, "User not found");
            return;
        }

        var user = userOpt.get();
        if (!user.containsKey("inviteToken")
                || !user.getString("inviteToken").getValue().equals(token)) {
            sendError(res, HttpStatus.SC_UNAUTHORIZED, "Invalid or expired invite token");
            return;
        }

        if (user.containsKey("inviteCreatedAt") && user.get("inviteCreatedAt").isDateTime()) {
            var createdAt = user.getDateTime("inviteCreatedAt").getValue();
            if (System.currentTimeMillis() - createdAt > INVITE_TTL_MS) {
                sendError(res, HttpStatus.SC_UNAUTHORIZED, "Invitation has expired");
                return;
            }
        }

        if (!user.containsKey("inviteOrgId")) {
            sendError(res, HttpStatus.SC_BAD_REQUEST, "No pending invitation found");
            return;
        }

        var orgId = user.get("inviteOrgId");
        var role = user.containsKey("inviteRole")
                ? user.getString("inviteRole").getValue()
                : conf.memberRoleName();

        accountsService.getMembershipProvider().addMember(email, orgId, role);

        db.unsetUserFields(email, java.util.List.of("inviteToken", "inviteCreatedAt", "inviteOrgId", "inviteRole"));

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
