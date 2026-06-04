package org.restheart.accounts;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.util.DbHelper;
import org.restheart.accounts.util.RequestOverrides;
import org.restheart.accounts.util.Errors;
import org.restheart.accounts.util.JwtHelper;
import org.restheart.accounts.util.TokenUtils;
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

import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;

import io.undertow.util.Headers;

/**
 * PATCH /auth/reset-password
 *
 * <p>Validates a one-shot {@code passwordResetToken} and replaces the user's password.
 * On success the token fields are removed ({@code $unset}) and a fresh JWT is issued so
 * the user is automatically logged in after the reset.
 *
 * <p>Expected body:
 * <pre>{@code
 * {
 *   "email":    "user@example.com",
 *   "token":    "<passwordResetToken>",
 *   "password": "<new-plain-text-password>"
 * }
 * }</pre>
 *
 * <p>Error responses deliberately use the same message for token-not-found, token-expired,
 * and email-mismatch conditions to prevent oracle attacks.
 */
@RegisterPlugin(
        name             = "resetPasswordService",
        description      = "PATCH /auth/reset-password — validates token and sets new password",
        defaultURI       = "/auth/reset-password",
        enabledByDefault = true)
public class ResetPasswordService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResetPasswordService.class);
    private static final int PASSWORD_MIN_LENGTH = 8;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;


    private JwtHelper  jwt;

    @OnInit
    public void onInit() {
        this.jwt = new JwtHelper(conf.jwtKey(), conf.jwtIssuer(), conf.jwtTtl());
        aclRegistry.registerAllow(r -> r.getPath().equals("/auth/reset-password") && (r.isPatch() || r.isOptions()));
        aclRegistry.registerAuthenticationRequirement(r -> !r.getPath().equals("/auth/reset-password"));
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) throws Exception {
        if (req.isOptions()) { handleOptions(req); return; }

        if (!req.isPatch()) {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        // 1. Validate body — all three fields are required
        var body = req.getContent();
        if (body == null || !body.isJsonObject()) {
            Errors.error(res, 400, "Invalid request body");
            return;
        }
        var obj      = body.getAsJsonObject();
        var email    = obj.has("email")    ? obj.get("email").getAsString()    : null;
        var token    = obj.has("token")    ? obj.get("token").getAsString()    : null;
        var password = obj.has("password") ? obj.get("password").getAsString() : null;

        if (email == null || email.isBlank()) {
            Errors.error(res, 400, "email is required");
            return;
        }
        if (token == null || token.isBlank()) {
            Errors.error(res, 400, "token is required");
            return;
        }
        if (password == null || password.isBlank()) {
            Errors.error(res, 400, "password is required");
            return;
        }

        // Fail-fast: check length before any DB round-trip
        if (password.length() < PASSWORD_MIN_LENGTH) {
            Errors.error(res, 400, "Password too short");
            return;
        }

        // 2. Find user by token (avoids timing leaks — same lookup regardless of email)
        var userOpt = db(req).findUserByToken("passwordResetToken", token);
        if (userOpt.isEmpty()) {
            Errors.error(res, 401, "Invalid or expired token");
            return;
        }
        var user = userOpt.get();

        // 3. Anti-token-swap: confirm the email on the document matches the request
        var storedEmail = user.getString("_id").getValue();
        if (!storedEmail.equalsIgnoreCase(email)) {
            LOGGER.warn("resetPassword: token/email mismatch — possible token-swap attempt");
            Errors.error(res, 401, "Invalid or expired token");
            return;
        }

        // 4. Check token expiry (TTL: 1 hour)
        if (!user.containsKey("passwordResetCreatedAt")) {
            Errors.error(res, 401, "Invalid or expired token");
            return;
        }
        var createdAt = user.getDateTime("passwordResetCreatedAt");
        if (TokenUtils.isExpired(createdAt, 1)) {
            Errors.error(res, 401, "Invalid or expired token");
            return;
        }

        // 5. Only active accounts may reset their password
        var status = user.containsKey("status")
                ? user.getString("status").getValue()
                : "";
        if (!"active".equals(status)) {
            Errors.error(res, 400, "Account not active");
            return;
        }

        // 6a. Persist the new hashed password
        var hashed  = TokenUtils.hashPassword(password);
        var updates = new BsonDocument("password", new BsonString(hashed));
        db(req).updateUser(storedEmail, updates);

        // 6b. One-shot: remove the token fields immediately
        db(req).unsetUserFields(storedEmail, List.of("passwordResetToken", "passwordResetCreatedAt"));

        // 7. Auto-login: issue a fresh JWT and set the auth cookie
        var tenant    = user.containsKey("tenant") ? user.getString("tenant").getValue() : "";
        var roles     = extractRoles(user);
        var jwtToken  = jwt.issueToken(storedEmail, roles,
                Map.of("tenant", tenant, "status", "active"));

        res.getHeaders().add(Headers.SET_COOKIE,
                JwtHelper.setCookieHeader(jwtToken, conf.cookieName(), RequestOverrides.cookieDomain(req, conf)));

        // 8. Success response
        var responseBody = new JsonObject();
        responseBody.addProperty("message", "Password updated successfully");
        res.setContent(responseBody);
        res.setStatusCode(HttpStatus.SC_OK);

        LOGGER.info("resetPassword: password reset completed successfully");
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private DbHelper db(JsonRequest req) {
        return new DbHelper(mclient, RequestOverrides.db(req, conf));
    }

    /** Extracts the {@code roles} array from a user document as a {@link Set}. */
    private Set<String> extractRoles(BsonDocument user) {
        var roles = new HashSet<String>();
        if (user.containsKey("roles") && user.get("roles").isArray()) {
            for (var role : user.getArray("roles")) {
                if (role.isString()) {
                    roles.add(role.asString().getValue());
                }
            }
        }
        return roles;
    }
}
