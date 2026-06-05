package org.restheart.accounts;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.email.Ermes;
import org.restheart.accounts.util.DbHelper;
import org.restheart.accounts.util.EmailTemplates;
import org.restheart.accounts.util.Errors;
import org.restheart.accounts.util.RequestOverrides;
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

import com.mongodb.client.MongoClient;

/**
 * POST /auth/forgot-password
 *
 * <p>Generates a one-shot {@code passwordResetToken} (256-bit, TTL 1 hour) stored on the
 * user document and sends a password-reset email.
 *
 * <p><strong>Anti-enumeration</strong>: always responds {@code 202 Accepted} regardless of
 * whether the requested email exists or not. All internal outcomes are logged at DEBUG level
 * so that sensitive details never leak via logs in production.
 *
 * <p>Expected body: {@code { "email": "user@example.com" }}
 */
@RegisterPlugin(
        name             = "forgotPasswordService",
        description      = "POST /auth/forgot-password — initiates password reset (always 202)",
        defaultURI       = "/auth/forgot-password",
        enabledByDefault = false)
public class ForgotPasswordService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForgotPasswordService.class);

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("ermes")
    private Ermes ermes;


    @OnInit
    public void onInit() {
        aclRegistry.registerAllow(r -> r.getPath().equals("/auth/forgot-password") && (r.isPost() || r.isOptions()));
        aclRegistry.registerAuthenticationRequirement(r -> !r.getPath().equals("/auth/forgot-password"));
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) throws Exception {
        if (req.isOptions()) { handleOptions(req); return; }

        if (!req.isPost()) {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        // 1. Validate body — email field is required
        var body = req.getContent();
        if (body == null || !body.isJsonObject()) {
            Errors.error(res, 400, "Invalid request body");
            return;
        }
        var obj   = body.getAsJsonObject();
        var email = obj.has("email") ? obj.get("email").getAsString() : null;
        if (email == null || email.isBlank()) {
            Errors.error(res, 400, "email is required");
            return;
        }

        // 2. Always respond 202 — outcome must not be visible to the caller
        res.setStatusCode(HttpStatus.SC_ACCEPTED);

        // 3. Async logic on the same thread; any exception is swallowed after logging
        var dbName = RequestOverrides.db(req, conf);
        try {
            processResetRequest(email, dbName);
        } catch (Exception e) {
            LOGGER.warn("forgotPassword: unexpected error during reset processing", e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Performs the actual password-reset flow.
     * Exceptions bubble up to {@link #handle} where they are caught and logged,
     * leaving the already-set 202 response intact.
     */
    private void processResetRequest(String email, String dbName) {
        // a. Locate user
        var userOpt = new DbHelper(mclient, dbName).findUser(email);
        if (userOpt.isEmpty()) {
            LOGGER.debug("forgotPassword: no account found for the requested email address");
            return;
        }

        var user   = userOpt.get();
        var status = user.containsKey("status")
                ? user.getString("status").getValue()
                : "";

        // b. Only send reset emails for active accounts
        if (!"active".equals(status)) {
            LOGGER.debug("forgotPassword: account status is '{}', skipping reset", status);
            return;
        }

        // c. Generate one-shot reset token and record its creation time
        var token = TokenUtils.generateToken();
        var now   = new BsonDateTime(Instant.now().toEpochMilli());

        // d. Persist token on the user document
        var updates = new BsonDocument();
        updates.put("passwordResetToken",   new BsonString(token));
        updates.put("passwordResetCreatedAt", now);
        new DbHelper(mclient, dbName).updateUser(email, updates);

        // e. Build reset link and send email
        var firstName  = user.containsKey("firstName")
                ? user.getString("firstName").getValue()
                : email;
        var encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        var resetLink    = conf.frontendUrl()
                + "/auth/reset-password?email=" + encodedEmail
                + "&token=" + token;

        ermes.sendEmail(
                email,
                firstName,
                EmailTemplates.resetPasswordSubject(conf.appName()),
                EmailTemplates.resetPasswordBody(firstName, resetLink, conf.appName()));

        // f. Audit log — no PII at INFO level
        LOGGER.info("forgotPassword: password reset email dispatched");
    }
}
