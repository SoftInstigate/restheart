package org.restheart.accounts;

import com.mongodb.client.MongoClient;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.util.DbHelper;
import org.restheart.accounts.util.JwtHelper;
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GET /auth/verify?email=...&token=...
 *
 * <p>Validates the email-verification token, activates the account, issues a JWT, sets
 * the {@code rh_auth} cookie, and redirects the browser to the application.
 *
 * <p>All outcomes are expressed as 302 redirects (the request arrives via a link in an
 * email, so a browser navigation is always in progress):
 * <ul>
 *   <li>Success     → 302 to {@code frontendAppUrl} with {@code rh_auth} cookie set</li>
 *   <li>Invalid/missing token or email mismatch
 *                   → 302 to {@code frontendUrl}/auth/login?error=invalid_token</li>
 *   <li>Expired token (TTL 7 days)
 *                   → 302 to {@code frontendUrl}/auth/login?error=token_expired</li>
 * </ul>
 *
 * <p>The endpoint is public — registered via {@link ACLRegistry#registerAllow} in
 * {@link #onInit()}.
 */
@RegisterPlugin(
        name             = "emailVerificationService",
        description      = "GET /auth/verify — validates email verification token and activates user",
        defaultURI       = "/auth/verify",
        enabledByDefault = true)
public class EmailVerificationService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationService.class);

    /** Token TTL: 7 days expressed in hours. */
    private static final int VERIFICATION_TTL_HOURS = 7 * 24;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    private JwtHelper jwt;

    @OnInit
    public void onInit() {
        this.jwt = new JwtHelper(conf.jwtKey(), conf.jwtIssuer(), conf.jwtTtl());
        // Allow unauthenticated access — the link comes from an email, no session yet
        aclRegistry.registerAllow(r -> "/auth/verify".equals(r.getPath()));
    }

    private DbHelper db(JsonRequest req) {
        return new DbHelper(mclient, RequestOverrides.db(req, conf));
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) throws Exception {
        if (req.isOptions()) { handleOptions(req); return; }
        if (!req.isGet())    { res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED); return; }

        final var loginErrorBase = conf.frontendUrl() + "/auth/login";

        // ── 1. Read query parameters ─────────────────────────────────────────
        var email = req.getQueryParameterOrDefault("email", null);
        var token = req.getQueryParameterOrDefault("token", null);

        if (email == null || email.isBlank() || token == null || token.isBlank()) {
            redirect(res, loginErrorBase + "?error=invalid_token");
            return;
        }

        // ── 2. Look up user by token ─────────────────────────────────────────
        var userOpt = db(req).findUserByToken("emailVerificationToken", token);
        if (userOpt.isEmpty()) {
            LOGGER.debug("Email verification failed: token not found");
            redirect(res, loginErrorBase + "?error=invalid_token");
            return;
        }

        var user = userOpt.get();

        // ── 3. Anti-token-swap: confirm email matches document _id ────────────
        var storedEmail = user.getString("_id").getValue();
        if (!email.equalsIgnoreCase(storedEmail)) {
            LOGGER.warn("Email verification: email mismatch — param={}, doc={}", email, storedEmail);
            redirect(res, loginErrorBase + "?error=invalid_token");
            return;
        }

        // ── 4. Check TTL ─────────────────────────────────────────────────────
        if (!user.containsKey("emailVerificationCreatedAt")) {
            // Document is in an unexpected state — treat as invalid
            redirect(res, loginErrorBase + "?error=invalid_token");
            return;
        }
        var createdAt = user.getDateTime("emailVerificationCreatedAt");
        if (TokenUtils.isExpired(createdAt, VERIFICATION_TTL_HOURS)) {
            LOGGER.info("Email verification: token expired for <{}>", storedEmail);
            redirect(res, loginErrorBase + "?error=token_expired");
            return;
        }

        // ── 5a. Remove verification token fields ─────────────────────────────
        db(req).unsetUserFields(storedEmail,
                List.of("emailVerificationToken", "emailVerificationCreatedAt"));

        // ── 5b. Activate account ─────────────────────────────────────────────
        db(req).updateUser(storedEmail, new BsonDocument("status", new BsonString("active")));

        // ── 5c. Build roles set from document ────────────────────────────────
        Set<String> roles = new HashSet<>();
        if (user.containsKey("roles") && user.get("roles").isArray()) {
            for (var rv : user.getArray("roles")) {
                roles.add(rv.asString().getValue());
            }
        }
        if (roles.isEmpty()) {
            roles.add("user");
        }

        // ── 5d. Issue JWT ─────────────────────────────────────────────────────
        var tenant = user.containsKey("tenant")
                ? user.getString("tenant").getValue()
                : "";

        var jwtToken = jwt.issueToken(
                storedEmail,
                roles,
                Map.of("tenant", tenant, "status", "active"));

        // ── 5e. Set auth cookie ───────────────────────────────────────────────
        var cookieHeader = JwtHelper.setCookieHeader(jwtToken, conf.cookieName(), RequestOverrides.cookieDomain(req, conf));
        res.getHeaders().add(HttpString.tryFromString("Set-Cookie"), cookieHeader);

        LOGGER.info("Email verified — user activated: <{}>", storedEmail);

        // ── 5f. Redirect to app ───────────────────────────────────────────────
        redirect(res, conf.frontendAppUrl());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void redirect(JsonResponse res, String url) {
        res.setStatusCode(HttpStatus.SC_MOVED_TEMPORARILY);
        res.getHeaders().put(Headers.LOCATION, url);
    }
}
