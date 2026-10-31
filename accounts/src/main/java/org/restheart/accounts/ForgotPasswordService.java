package org.restheart.accounts;

import org.restheart.accounts.config.AccountsEmailPolicy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.plugins.accounts.AccountsConfigData;
import org.restheart.emails.EmailSender;
import org.restheart.accounts.util.DbHelper;
import org.restheart.emails.EmailRenderer;
import org.restheart.emails.EmailTemplateLoader;
import org.restheart.plugins.schema.JsonSchemas;
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
 * <p>An account that has <strong>not</strong> completed email verification yet (still
 * {@code $unauthenticated}) gets the same reset link, and {@link ResetPasswordService}
 * verifies it as it sets the new password: following the link proves ownership of the
 * address just as the verification link would. This is the account owner's only self-service
 * way to recover from a lost or expired verification email, since registration rejects the
 * email as already taken. It deliberately does not re-send the verification link: that link
 * would activate the account with the password chosen at registration, which the person
 * following the link may not know, since anyone can register any address. A third party
 * who pre-registered the address would then have a working password for the activated
 * account (GHSA-m7m7-gjxg-cjmm). The reset link makes the owner choose the password.
 *
 * <p><strong>Anti-enumeration</strong>: always responds {@code 202 Accepted} regardless of
 * whether the requested email exists, is verified, or anything else. All internal outcomes
 * are logged at DEBUG level so that sensitive details never leak via logs in production.
 *
 * <p>Expected body: {@code { "email": "user@example.com" }}
 */
@RegisterPlugin(
        name = "forgotPasswordService",
        description = "POST /auth/forgot-password — initiates password reset (always 202)",
        defaultURI = "/auth/forgot-password",
        enabledByDefault = false)
public class ForgotPasswordService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForgotPasswordService.class);

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("accountsEmailPolicy")
    private AccountsEmailPolicy emailPolicy;

    @Inject("emails")
    private EmailSender emails;

    @Inject("json-schemas")
    private JsonSchemas jsonSchemas;


    @OnInit
    public void onInit() {
        aclRegistry.registerAllow(r -> r.getPath().equals("/auth/forgot-password") && (r.isPost() || r.isOptions()));
        aclRegistry.registerAuthenticationRequirement(r -> !r.getPath().equals("/auth/forgot-password"));
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) throws Exception {
        if (req.isOptions()) {
            handleOptions(req);
            return;
        }

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
        var obj = body.getAsJsonObject();
        var email = obj.has("email") ? obj.get("email").getAsString() : null;
        if (email == null || email.isBlank()) {
            Errors.error(res, 400, "email is required");
            return;
        }

        // 2. Always respond 202 — outcome must not be visible to the caller
        res.setStatusCode(HttpStatus.SC_ACCEPTED);

        // 3. Async logic on the same thread; any exception is swallowed after logging
        var dbName = RequestOverrides.db(req, conf);
        var usersColl = RequestOverrides.usersCollection(req, conf);
        try {
            processResetRequest(req, email, dbName, usersColl);
        } catch (Exception e) {
            LOGGER.warn("forgotPassword: unexpected error during reset processing", e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Sends the reset link to an existing account, verified or not (see the class javadoc for
     * why an unverified account gets a reset link rather than the verification link again).
     * Exceptions bubble up to {@link #handle} where they are caught and logged,
     * leaving the already-set 202 response intact.
     */
    private void processResetRequest(JsonRequest req, String email, String dbName, String usersColl) throws java.io.IOException {
        // a. Locate user
        var userOpt = new DbHelper(mclient, dbName, usersColl, jsonSchemas).findUser(email);
        if (userOpt.isEmpty()) {
            LOGGER.debug("forgotPassword: no account found for the requested email address");
            return;
        }

        var user = userOpt.get();

        // At most one email per address per cooldown. Without it, this endpoint re-sends to an
        // address as often as anyone calls it — and an address seeded through /auth/register can
        // be mailed again and again. The verification email sent at registration counts too.
        // The response is the same 202 either way.
        if (!emailPolicy.resendAllowed(user, "passwordResetCreatedAt")
                || !emailPolicy.resendAllowed(user, "emailVerificationCreatedAt")) {
            LOGGER.info("forgotPassword: an email was sent to this address less than {}s ago, not sending another",
                    emailPolicy.resendCooldown().toSeconds());
            return;
        }

        sendPasswordReset(req, email, dbName, usersColl, user);
    }

    /**
     * Generate a one-shot {@code passwordResetToken} and email the reset link.
     */
    private void sendPasswordReset(JsonRequest req, String email, String dbName, String usersColl, BsonDocument user) throws java.io.IOException {
        // a. Generate one-shot reset token and record its creation time
        var token = TokenUtils.generateToken();
        var now = new BsonDateTime(Instant.now().toEpochMilli());

        // b. Persist token on the user document
        var updates = new BsonDocument();
        updates.put("passwordResetToken", new BsonString(token));
        updates.put("passwordResetCreatedAt", now);
        new DbHelper(mclient, dbName, usersColl, jsonSchemas).updateUser(email, updates);

        // c. Build reset link and send email
        var firstName = user.containsKey("profile") && user.get("profile").isDocument()
                && user.getDocument("profile").containsKey("name")
                ? user.getDocument("profile").getString("name").getValue()
                : email;
        var encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        var resetLink = RequestOverrides.frontendUrl(req, conf)
                + "/auth/reset-password?email=" + encodedEmail
                + "&token=" + token;

        // X-Skip-Email header, honoured only where accountsEmailPolicy allows it (tests)
        if (emailPolicy.skipEmail(req)) {
            LOGGER.debug("Skipping password reset email to <{}> (X-Skip-Email header)", email);
        } else {
            var tmpl = EmailTemplateLoader.loadWithFallback(
                    RequestOverrides.templatePasswordReset(req), conf.passwordResetTemplatePath(), "password-reset.html");
            var vars = java.util.Map.of(
                    "app-name", RequestOverrides.appName(req, conf),
                    "year", String.valueOf(java.time.Year.now().getValue()),
                    "first-name", firstName != null ? firstName : "",
                    "email", email,
                    "frontend-url", RequestOverrides.frontendUrl(req, conf),
                    "reset-url", resetLink);
            var rendered = EmailRenderer.render(tmpl, vars, conf.defaultLocale());
            emails.sendEmailAsync(req, email, firstName, rendered.subject(), rendered.htmlBody());
        }

        // d. Audit log — no PII at INFO level
        LOGGER.info("forgotPassword: password reset email dispatched");
    }
}
