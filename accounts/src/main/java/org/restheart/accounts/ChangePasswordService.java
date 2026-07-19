package org.restheart.accounts;

import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.plugins.accounts.AccountsConfigData;
import org.restheart.accounts.util.DbHelper;
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

/**
 * PATCH /auth/change-password
 *
 * <p>Lets an authenticated user change their own password in-session, given their
 * current password — unlike {@link ResetPasswordService}, which is the public,
 * unauthenticated "forgot password" flow (emailed one-shot token). Before this
 * endpoint, a logged-in user could only change their password by calling
 * {@code /auth/forgot-password} on themselves and completing the email round-trip,
 * which is unusual UX for a deliberate change from an account-settings page.
 *
 * <p>Expected body:
 * <pre>{@code
 * { "currentPassword": "...", "newPassword": "..." }
 * }</pre>
 *
 * <p>Does not invalidate other active sessions/JWTs — out of scope for JWT-based
 * auth unless a token-versioning/blacklist mechanism is added separately.
 */
@RegisterPlugin(
        name             = "changePasswordService",
        description      = "PATCH /auth/change-password — in-session password change for authenticated users",
        defaultURI       = "/auth/change-password",
        secure           = true,
        enabledByDefault = false)
public class ChangePasswordService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChangePasswordService.class);
    private static final int PASSWORD_MIN_LENGTH = 8;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @OnInit
    public void onInit() {
        aclRegistry.registerAllow(r -> r.getPath().equals("/auth/change-password") && (r.isPatch() || r.isOptions()));
    }

    private DbHelper db(JsonRequest req) {
        return new DbHelper(mclient, RequestOverrides.db(req, conf));
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        if (req.isOptions()) { handleOptions(req); return; }

        if (!req.isPatch()) { res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED); return; }

        var account = req.getAuthenticatedAccount();
        var email = account.getPrincipal().getName();

        var body = req.getContent();
        if (body == null || !body.isJsonObject()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Request body must be a JSON object");
            return;
        }
        var jo = body.getAsJsonObject();

        if (!jo.has("currentPassword") || jo.get("currentPassword").isJsonNull()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "currentPassword is required");
            return;
        }
        if (!jo.has("newPassword") || jo.get("newPassword").isJsonNull()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "newPassword is required");
            return;
        }
        var currentPassword = jo.get("currentPassword").getAsString();
        var newPassword = jo.get("newPassword").getAsString();

        if (newPassword.length() < PASSWORD_MIN_LENGTH) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Password too short");
            return;
        }

        var userOpt = db(req).findUser(email);
        if (userOpt.isEmpty()) {
            // Authenticated but the user document vanished — treat as invalid credentials
            Errors.error(res, HttpStatus.SC_UNAUTHORIZED, "Invalid current password");
            return;
        }
        var user = userOpt.get();
        var storedHash = user.containsKey("password") && user.get("password").isString()
                ? user.getString("password").getValue() : null;
        if (storedHash == null || !TokenUtils.checkPassword(currentPassword, storedHash)) {
            Errors.error(res, HttpStatus.SC_UNAUTHORIZED, "Invalid current password");
            return;
        }

        var hashed = TokenUtils.hashPassword(newPassword);
        db(req).updateUser(email, new BsonDocument("password", new BsonString(hashed)));

        LOGGER.info("Password changed for <{}>", email);

        var responseBody = new JsonObject();
        responseBody.addProperty("message", "Password updated successfully");
        res.setContent(responseBody);
        res.setStatusCode(HttpStatus.SC_OK);
    }
}
