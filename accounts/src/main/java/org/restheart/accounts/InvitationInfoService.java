package org.restheart.accounts;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GET /auth/invitation?email=&token=
 *
 * <p>Returns metadata about a pending invitation so the frontend can render
 * the correct acceptance UI (set-password for new users, log-in for existing).
 *
 * <p>Requires both {@code email} and {@code token} query parameters — the pair
 * is known only to the invitee (delivered via private email). This prevents
 * enumeration: without both values the endpoint returns 404.
 *
 * <p>Response body (200):
 * <pre>{@code
 * {
 *   "email":     "bob@example.com",
 *   "orgName":   "Acme Corp",
 *   "role":      "member",
 *   "isNewUser": true,
 *   "expiresAt": "2026-06-24T10:00:00Z"
 * }
 * }</pre>
 */
@RegisterPlugin(
        name             = "invitationInfoService",
        description      = "GET /auth/invitation — returns invitation metadata for the acceptance UI",
        defaultURI       = "/auth/invitation",
        enabledByDefault = false)
public class InvitationInfoService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvitationInfoService.class);

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("accountsService")
    private AccountsService accountsService;

    @OnInit
    public void onInit() {
        aclRegistry.registerAllow(r -> r.getPath().equals("/auth/invitation") && (r.isGet() || r.isOptions()));
        aclRegistry.registerAuthenticationRequirement(r -> !r.getPath().equals("/auth/invitation"));
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        if (req.isOptions()) {
            handleOptions(req);
            return;
        }
        if (!req.isGet()) {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        var params = req.getExchange().getQueryParameters();

        var emailParam = params.get("email");
        var tokenParam = params.get("token");

        if (emailParam == null || emailParam.isEmpty() || tokenParam == null || tokenParam.isEmpty()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "email and token parameters are required");
            return;
        }

        var email = emailParam.getFirst().trim().toLowerCase();
        var token = tokenParam.getFirst().trim();

        var db = new DbHelper(mclient, RequestOverrides.db(req, conf));
        var inviteOpt = db.findInvitationByEmailAndToken(email, token);

        if (inviteOpt.isEmpty()) {
            Errors.error(res, HttpStatus.SC_NOT_FOUND, "Invitation not found or expired");
            return;
        }

        var invite   = inviteOpt.get();
        var orgId    = invite.get("orgId");
        var role     = invite.getString("role").getValue();
        var isNewUser = invite.containsKey("isNewUser") && invite.getBoolean("isNewUser").getValue();
        var expiresAt = invite.containsKey("expiresAt") ? invite.getDateTime("expiresAt").getValue() : 0L;

        // Resolve org display name
        var orgName = orgId.isString() ? orgId.asString().getValue()
                : orgId.isObjectId() ? orgId.asObjectId().getValue().toHexString()
                : orgId.toString();
        try {
            var teamOpt = db.findTeam(orgId);
            if (teamOpt.isPresent()) {
                var teamDoc = teamOpt.get();
                if (teamDoc.containsKey("name") && teamDoc.get("name").isString()) {
                    orgName = teamDoc.getString("name").getValue();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not resolve org name for orgId={}: {}", orgId, e.getMessage());
        }

        var body = new JsonObject();
        body.addProperty("email",     email);
        body.addProperty("orgName",   orgName);
        body.addProperty("role",      role);
        body.addProperty("isNewUser", isNewUser);
        body.addProperty("expiresAt", java.time.Instant.ofEpochMilli(expiresAt).toString());

        res.setContent(body);
        res.setStatusCode(HttpStatus.SC_OK);

        LOGGER.debug("Invitation info served for <{}> (isNewUser={})", email, isNewUser);
    }
}
