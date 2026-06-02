package org.restheart.accounts;

import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;
import org.bson.BsonArray;
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
import org.restheart.exchange.BadRequestException;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * POST /auth/register
 *
 * <p>Creates a new user with {@code status="pending_verification"} and a new team/tenant,
 * then sends an email-verification link (TTL 7 days).
 *
 * <p>Expected request body:
 * <pre>{@code
 * {
 *   "firstName": "Alice",
 *   "lastName":  "Smith",
 *   "teamName":  "Acme Corp",
 *   "email":     "alice@example.com",
 *   "password":  "..."
 * }
 * }</pre>
 *
 * <p>Responses:
 * <ul>
 *   <li>201 — user created, verification email sent (email errors are logged but do not
 *       block the signup response)</li>
 *   <li>400 — missing or invalid body / fields</li>
 *   <li>409 — email address already registered</li>
 * </ul>
 *
 * <p>The endpoint is public — access is granted by {@code accountsAclInitializer}.
 */
@RegisterPlugin(
        name             = "registerService",
        description      = "POST /auth/register — public user signup with email verification",
        defaultURI       = "/auth/register",
        enabledByDefault = true)
public class RegisterService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterService.class);

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("ermes")
    private Ermes ermes;


    @OnInit
    public void onInit() {
    }

    private DbHelper db(JsonRequest req) {
        return new DbHelper(mclient, RequestOverrides.db(req, conf));
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) throws Exception {
        if (req.isOptions()) { handleOptions(req); return; }
        if (!req.isPost())   { res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED); return; }

        // ── 1. Parse body ────────────────────────────────────────────────────
        JsonObject body;
        try {
            var raw = req.getContent();
            if (raw == null || !raw.isJsonObject()) {
                Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Request body must be a JSON object");
                return;
            }
            body = raw.getAsJsonObject();
        } catch (BadRequestException e) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Invalid JSON body");
            return;
        }

        // ── 2. Validate required fields ──────────────────────────────────────
        var firstName = extractString(body, "firstName");
        var lastName  = extractString(body, "lastName");
        var teamName  = extractString(body, "teamName");
        var email     = extractString(body, "email");
        var password  = extractString(body, "password");

        if (firstName == null) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Missing required field: firstName");
            return;
        }
        if (lastName == null) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Missing required field: lastName");
            return;
        }
        if (teamName == null) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Missing required field: teamName");
            return;
        }
        if (email == null) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Missing required field: email");
            return;
        }
        if (password == null) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Missing required field: password");
            return;
        }

        // ── 3. Check email uniqueness ────────────────────────────────────────
        if (db(req).findUser(email).isPresent()) {
            Errors.error(res, HttpStatus.SC_CONFLICT, "Email already registered");
            return;
        }

        // ── 4. Generate email verification token ─────────────────────────────
        var verificationToken = TokenUtils.generateToken();
        var now               = new BsonDateTime(System.currentTimeMillis());

        // ── 5. Create and insert team ────────────────────────────────────────
        var ownerMember = new BsonDocument()
                .append("userId",   new BsonString(email))
                .append("role",     new BsonString("owner"))
                .append("joinedAt", now);

        var membersList = new BsonArray();
        membersList.add(ownerMember);

        var teamDoc = new BsonDocument()
                .append("name",      new BsonString(teamName))
                .append("createdBy", new BsonString(email))
                .append("createdAt", now)
                .append("members",   membersList);

        var teamId = db(req).insertTeam(teamDoc);  // returns hex ObjectId string

        // ── 6. Create and insert user ────────────────────────────────────────
        var rolesArray = new BsonArray();
        rolesArray.add(new BsonString("owner")); // first user creates the team → is the owner

        var profile = new BsonDocument()
                .append("firstName", new BsonString(firstName))
                .append("lastName",  new BsonString(lastName));

        var userDoc = new BsonDocument()
                .append("_id",                        new BsonString(email))
                .append("password",                   new BsonString(TokenUtils.hashPassword(password)))
                .append("roles",                      rolesArray)
                .append("status",                     new BsonString("pending_verification"))
                .append("tenant",                     new BsonString(teamId))
                .append("profile",                    profile)
                .append("emailVerificationToken",     new BsonString(verificationToken))
                .append("emailVerificationCreatedAt", now);

        if (!db(req).insertUser(userDoc)) {
            // Concurrent registration or race between findUser and insertUser
            Errors.error(res, HttpStatus.SC_CONFLICT, "Email already registered");
            return;
        }

        LOGGER.info("User registered: <{}>, tenant={}", email, teamId);

        // ── 7. Send verification email (best-effort) ─────────────────────────
        try {
            var encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
            var verifyLink   = conf.frontendUrl()
                               + "/auth/verify"
                               + "?email=" + encodedEmail
                               + "&token=" + verificationToken;

            ermes.sendEmail(
                    email,
                    firstName,
                    EmailTemplates.verifyEmailSubject(conf.appName()),
                    EmailTemplates.verifyEmailBody(firstName, verifyLink, conf.appName()));
        } catch (Exception e) {
            // Log and continue — the user was created; they can request a resend later
            LOGGER.warn("Failed to send verification email to <{}>: {}", email, e.getMessage());
        }

        // ── 8. Respond 201 ───────────────────────────────────────────────────
        res.setStatusCode(HttpStatus.SC_CREATED);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts a non-blank trimmed string value from a JSON object.
     *
     * @return the trimmed value, or {@code null} if the key is absent, null, or blank
     */
    private static String extractString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        var value = obj.get(key).getAsString().strip();
        return value.isEmpty() ? null : value;
    }
}
