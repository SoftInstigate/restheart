package org.restheart.accounts;

import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.email.Ermes;
import org.restheart.accounts.util.DbHelper;
import org.restheart.accounts.util.RequestOverrides;
import org.restheart.accounts.util.EmailTemplates;
import org.restheart.accounts.util.Errors;
import org.restheart.accounts.util.TokenUtils;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;
import org.restheart.security.JwtAccount;
import org.restheart.security.MongoRealmAccount;
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
 * <p>Requires authentication. Callable by roles: {@code owner}, {@code admin}.
 * The target user must belong to the same tenant as the caller.
 *
 * <p>Expected body: {@code { "email": "invited@example.com" }}
 *
 * <p>This endpoint can be disabled via {@code accountsConfig.membership-endpoints-enabled: false}.
 */
@RegisterPlugin(
        name             = "resendInviteService",
        description      = "POST /auth/resend-invite — re-sends the activation email",
        defaultURI       = "/auth/resend-invite",
        enabledByDefault = false)
public class ResendInviteService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResendInviteService.class);

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
            // Allow all requests to reach the service; auth and role enforcement is done in handle()
            aclRegistry.registerAllow(r -> r.getPath().equals("/auth/resend-invite") && (r.isPost() || r.isOptions()));
            aclRegistry.registerAuthenticationRequirement(r -> !r.getPath().equals("/auth/resend-invite"));
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

        // 1. Verifica autenticazione e ruolo
        var account = req.getAuthenticatedAccount();
        if (account == null) {
            Errors.error(res, HttpStatus.SC_UNAUTHORIZED, "Authentication required");
            return;
        }
        var callerEmail = account.getPrincipal().getName();
        var roles = account.getRoles();
        if (!roles.contains("owner") && !roles.contains("admin")) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "Requires owner or admin role");
            return;
        }
        var callerTenant = accountsService.getMembershipProvider()
                .activeMembership(callerEmail)
                .map(m -> m.tenantId())
                .orElse(null);
        if (callerTenant == null || callerTenant.isNull()) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "No tenant associated with your account");
            return;
        }

        // 2. Leggi email dal body
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

        // 3. Cerca l'utente
        var userOpt = db(req).findUser(email);
        if (userOpt.isEmpty()) {
            Errors.error(res, HttpStatus.SC_NOT_FOUND, "User not found");
            return;
        }
        var user = userOpt.get();

        // 4. Verifica status == "invited"
        var status = user.containsKey("status") && user.get("status").isString()
                ? user.getString("status").getValue() : null;
        if (!"invited".equals(status)) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST,
                    "Invite can only be resent to users in 'invited' status");
            return;
        }

        // 5. Verifica che il tenant dell'utente corrisponda al tenant del chiamante
        var userTenant = accountsService.getMembershipProvider()
                .activeMembership(email)
                .map(m -> m.tenantId())
                .orElse(null);
        if (userTenant == null || !callerTenant.equals(userTenant)) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "User belongs to a different team");
            return;
        }

        // 6. Genera nuovo inviteToken e inviteCreatedAt (invalida il vecchio)
        var newToken = TokenUtils.generateToken();
        var now      = new BsonDateTime(System.currentTimeMillis());

        var updateDoc = new BsonDocument();
        updateDoc.put("inviteToken",     new BsonString(newToken));
        updateDoc.put("inviteCreatedAt", now);

        // 7. Aggiorna l'utente
        if (!db(req).updateUser(email, updateDoc)) {
            Errors.error(res, HttpStatus.SC_INTERNAL_SERVER_ERROR, "Failed to update user");
            return;
        }

        // 8. Carica nome del team e reinvia email
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
                var link = conf.frontendUrl().replaceAll("/$", "")
                        + "/auth/activate"
                        + "?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                        + "&token=" + URLEncoder.encode(newToken, StandardCharsets.UTF_8);

                var inviterName = account.getPrincipal() != null
                        ? account.getPrincipal().getName()
                        : email;

                // Check X-Skip-Email header for integration tests
                if ("true".equalsIgnoreCase(req.getHeader("X-Skip-Email"))) {
                    LOGGER.debug("Skipping re-send invite email to <{}> (X-Skip-Email header)", email);
                } else {
                    ermes.sendEmail(
                            email,
                            email,
                            EmailTemplates.inviteSubject(teamName, conf.appName()),
                            EmailTemplates.inviteBody(teamName, inviterName, link, conf.appName()));
                }

                LOGGER.info("Invite email re-sent to <{}> by {} (tenant={})",
                        email, inviterName, callerTenant);
            } catch (Exception e) {
                LOGGER.error("Failed to re-send invite email to <{}>", email, e);
            }
        } else {
            LOGGER.warn("Ermes disabled — invite email not re-sent to <{}>", email);
        }

        // 9. Risponde 200
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

    /**
     * Extracts the active tenant claim from the authenticated account using the
     * configured claim name, supporting both {@link MongoRealmAccount} and
     * {@link JwtAccount} pipelines.
     */
    private static String extractTenant(io.undertow.security.idm.Account account, String claimName) {
        return switch (account) {
            case MongoRealmAccount mra -> {
                var props = mra.properties();
                if (props == null) yield null;
                var v = props.get(claimName);
                yield v != null && v.isString() ? v.asString().getValue() : null;
            }
            case JwtAccount jwt -> {
                var props = jwt.propertiesAsMap();
                if (props == null) yield null;
                var v = props.get(claimName);
                yield v instanceof String s ? s : null;
            }
            default -> null;
        };
    }
}
