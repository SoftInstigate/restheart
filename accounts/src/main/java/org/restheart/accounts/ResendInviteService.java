package org.restheart.accounts;

import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;
import org.bson.BsonValue;
import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.email.Ermes;
import org.restheart.accounts.util.DbHelper;
import org.restheart.accounts.util.RequestOverrides;
import org.restheart.accounts.util.EmailRenderer;
import org.restheart.accounts.util.EmailTemplateLoader;
import org.restheart.accounts.util.Errors;
import org.restheart.accounts.util.TokenUtils;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * POST /auth/resend-invite
 *
 * <p>Regenerates the {@code inviteToken} (invalidating the previous one) and
 * re-sends the activation email to a user who has not yet completed the
 * activation flow.
 *
 * <p>Requires authentication. Callable by membership role: {@code <ownershipRole>} only.
 * The target user must belong to the same tenant as the caller.
 *
 * <p>Expected body: {@code { "email": "invited@example.com" }}
 *
 * <p>This endpoint can be disabled via {@code accountsConfig.membership-endpoints-enabled: false}.
 */
@RegisterPlugin(
        name             = "resendInviteService",
        description      = "POST /auth/resend-invite \u2014 re-sends the activation email",
        defaultURI       = "/auth/resend-invite",
        secure           = true,
        enabledByDefault = false)
public class ResendInviteService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResendInviteService.class);
    private static final long INVITE_TTL_MS = 7L * 24 * 60 * 60 * 1000;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("ermes")
    private Ermes ermes;

    @Inject("accountsService")
    private AccountsService accountsService;

    @OnInit
    public void onInit() {
        if (conf.membershipEndpointsEnabled()) {
            aclRegistry.registerAllow(r -> r.getPath().equals("/auth/resend-invite") && (r.isPost() || r.isOptions()));
        }
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        if (req.isOptions()) {
            handleOptions(req);
            return;
        }

        if (!conf.membershipEndpointsEnabled()) {
            Errors.error(res, HttpStatus.SC_NOT_FOUND, "Endpoint not available");
            return;
        }

        if (!req.isPost()) {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        // 1. Get authenticated account (enforced by secure=true)
        var account = req.getAuthenticatedAccount();
        var callerEmail = account.getPrincipal().getName();

        // 2. Verify membership role: must be ownership-role or admin
        var membership = accountsService.getMembershipProvider()
                .activeMembership(callerEmail);
        var membershipRole = membership.map(m -> m.role()).orElse(null);
        var ownershipRole = conf.ownershipRole();
        if (membershipRole == null || !membershipRole.equals(ownershipRole)) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "Requires " + ownershipRole + " role");
            return;
        }
        var callerTenant = membership.map(m -> m.tenantId()).orElse(null);
        if (callerTenant == null || callerTenant.isNull()) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "No tenant associated with your account");
            return;
        }

        // 3. Read email from body
        var body = req.getContent();
        if (body == null || !body.isJsonObject()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Request body must be a JSON object");
            return;
        }
        var jo = body.getAsJsonObject();
        if (!jo.has("email") || jo.get("email").isJsonNull()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "email is required");
            return;
        }
        var email = jo.get("email").getAsString().trim().toLowerCase();

        // 4. Find pending invitation in auth_invitations for this user in the caller's org
        var inviteOpt = db(req).findInvitation(email, callerTenant);
        if (inviteOpt.isEmpty()) {
            Errors.error(res, HttpStatus.SC_NOT_FOUND, "No pending invitation found for this user in your team");
            return;
        }
        var invite = inviteOpt.get();

        // 7. Read the role from the stored invitation
        var inviteRole = invite.containsKey("role") && invite.get("role").isString()
                ? invite.getString("role").getValue()
                : conf.memberRoleName();
        var isNewUser = invite.containsKey("isNewUser") && invite.getBoolean("isNewUser").getValue();

        // 5. Generate new token (invalidates the previous one)
        var newToken = TokenUtils.generateToken();

        if (!db(req).renewInvitation(invite.getObjectId("_id"), newToken, INVITE_TTL_MS)) {
            Errors.error(res, HttpStatus.SC_INTERNAL_SERVER_ERROR, "Failed to renew invitation");
            return;
        }

        // 6. Load team name and resend email
        var teamNameFallback = callerTenant.isString() ? callerTenant.asString().getValue() : BsonUtils.toJson(callerTenant);
        String teamName = teamNameFallback;
        try {
            var teamOpt = db(req).findTeam(callerTenant);
            if (teamOpt.isPresent()) {
                var teamDoc = teamOpt.get();
                if (teamDoc.containsKey("name") && teamDoc.get("name").isString()) {
                    teamName = teamDoc.getString("name").getValue();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not load team for tenant '{}': {}", teamNameFallback, e.getMessage());
        }

        if (ermes != null && ermes.isEnabled()) {
            try {
                var basePath = isNewUser ? "/auth/activate" : "/invitations/accept";
                var link = conf.frontendUrl().replaceAll("/$", "")
                        + basePath
                        + "?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                        + "&token=" + URLEncoder.encode(newToken, StandardCharsets.UTF_8);

                var inviterName = account.getPrincipal() != null
                        ? account.getPrincipal().getName()
                        : email;

                // Check X-Skip-Email header for integration tests
                if ("true".equalsIgnoreCase(req.getHeader("X-Skip-Email"))) {
                    LOGGER.debug("Skipping re-send invite email to <{}> (X-Skip-Email header)", email);
                } else {
                    var tmpl = EmailTemplateLoader.loadWithFallback(
                            RequestOverrides.templateInvite(req), conf.inviteTemplatePath(), "invite.html");
                    var vars = java.util.Map.of(
                            "app-name", conf.appName(),
                            "year", String.valueOf(java.time.Year.now().getValue()),
                            "first-name", inviterName != null ? inviterName : "",
                            "email", email,
                            "frontend-url", conf.frontendUrl(),
                            "invite-url", link,
                            "inviter-name", inviterName != null ? inviterName : "",
                            "team-name", teamName != null ? teamName : "",
                            "role", inviteRole.substring(0, 1).toUpperCase() + inviteRole.substring(1));
                    var rendered = EmailRenderer.render(tmpl, vars, conf.defaultLocale());
                    ermes.sendEmail(email, email, rendered.subject(), rendered.htmlBody());
                }

                LOGGER.info("Invite email re-sent to <{}> by {} (tenant={})",
                        email, inviterName, callerTenant);
            } catch (Exception e) {
                LOGGER.error("Failed to re-send invite email to <{}>", email, e);
            }
        } else {
            LOGGER.warn("Ermes disabled — invite email not re-sent to <{}>", email);
        }

        // 10. Respond 200
        var responseBody = new JsonObject();
        responseBody.addProperty("message", "Invite resent successfully");
        res.setContent(responseBody);
        res.setStatusCode(HttpStatus.SC_OK);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DbHelper db(JsonRequest req) {
        return new DbHelper(mclient, RequestOverrides.db(req, conf));
    }

}
