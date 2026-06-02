package org.restheart.accounts;

import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonString;
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
import org.restheart.security.JwtAccount;
import org.restheart.security.MongoRealmAccount;
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
 */
@RegisterPlugin(
        name             = "resendInviteService",
        description      = "POST /auth/resend-invite — re-sends the activation email",
        defaultURI       = "/auth/resend-invite",
        enabledByDefault = true)
public class ResendInviteService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResendInviteService.class);

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("ermes")
    private Ermes ermes;

    @OnInit
    public void onInit() {
        // NOTE: intentionally not calling aclRegistry.registerAllow —
        // this endpoint requires authentication and is restricted to owner/admin.
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
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
        var roles = account.getRoles();
        if (!roles.contains("owner") && !roles.contains("admin")) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "Requires owner or admin role");
            return;
        }
        var callerTenant = extractTenant(account);
        if (callerTenant == null || callerTenant.isBlank()) {
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
        var userTenant = user.containsKey("tenant") && user.get("tenant").isString()
                ? user.getString("tenant").getValue() : null;
        if (!callerTenant.equals(userTenant)) {
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
        String teamName = callerTenant; // fallback all'ID se il team non ha un campo "name"
        try {
            var teamOpt = db(req).findTeam(callerTenant);
            if (teamOpt.isPresent()) {
                var teamDoc = teamOpt.get();
                if (teamDoc.containsKey("name") && teamDoc.get("name").isString()) {
                    teamName = teamDoc.getString("name").getValue();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not load team for tenant '{}': {}", callerTenant, e.getMessage());
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

                ermes.sendEmail(
                        email,
                        email,
                        EmailTemplates.inviteSubject(teamName, conf.appName()),
                        EmailTemplates.inviteBody(teamName, inviterName, link, conf.appName()));

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
     * Extracts the {@code tenant} claim from the authenticated account,
     * supporting both {@link MongoRealmAccount} and {@link JwtAccount} pipelines.
     */
    private static String extractTenant(io.undertow.security.idm.Account account) {
        return switch (account) {
            case MongoRealmAccount mra -> {
                var props = mra.properties();
                if (props == null) yield null;
                var v = props.get("tenant");
                yield v != null && v.isString() ? v.asString().getValue() : null;
            }
            case JwtAccount jwt -> {
                var props = jwt.propertiesAsMap();
                if (props == null) yield null;
                var v = props.get("tenant");
                yield v instanceof String s ? s : null;
            }
            default -> null;
        };
    }
}
