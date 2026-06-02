package org.restheart.accounts.oauth;

import com.mongodb.client.MongoClient;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.bson.BsonArray;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.util.DbHelper;
import org.restheart.accounts.util.RequestOverrides;
import org.restheart.accounts.util.JwtHelper;
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.Request;
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.StringService;
import org.restheart.security.ACLRegistry;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static java.util.function.Predicate.not;

/**
 * Handles the Google OAuth 2.0 callback.
 *
 * <pre>
 *   GET /auth/oauth/callback/{provider}?code=...&state=...
 * </pre>
 *
 * <p>Flow:
 * <ol>
 *   <li>Verify state token (CSRF protection)</li>
 *   <li>Exchange authorization code for access token</li>
 *   <li>Fetch user profile from the provider</li>
 *   <li>Find or create the user in MongoDB</li>
 *   <li>Issue JWT and set {@code rh_auth} cookie</li>
 *   <li>Redirect to {@code frontendSuccessUrl}</li>
 * </ol>
 *
 * <p>On any error the browser is redirected to {@code frontendErrorUrl}.
 */
@RegisterPlugin(
        name             = "oauthCallback",
        description      = "GET /auth/oauth/callback/{provider} — handles the OAuth callback",
        defaultURI       = "/auth/oauth/callback",
        enabledByDefault = true)
public class OAuthCallback implements StringService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthCallback.class);

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("oauthService")
    private OAuthService oauthService;

    @Inject("oauthConfig")
    private OAuthConfig oauthConfig;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    private JwtHelper jwt;

    @OnInit
    public void onInit() {
        this.jwt = new JwtHelper(conf.jwtKey(), conf.jwtIssuer(), conf.jwtTtl());

        Predicate<Request<?>> isCallback = r ->
                r.getMethod() == METHOD.GET &&
                r.getPath().matches("/auth/oauth/callback/[^/]+");

        aclRegistry.registerAuthenticationRequirement(not(isCallback));
        aclRegistry.registerAllow(isCallback);

        LOGGER.info("OAuthCallback initialized at /auth/oauth/callback/{{provider}}");
    }

    @Override
    public void handle(StringRequest req, StringResponse res) throws Exception {
        if (req.isOptions()) { handleOptions(req); return; }

        if (!req.isGet()) {
            res.setInError(HttpStatus.SC_METHOD_NOT_ALLOWED, "Use GET");
            return;
        }

        // Extract provider from path
        var parts = req.getPath().split("/");
        if (parts.length < 5) {
            redirectError(res, "Invalid callback path");
            return;
        }
        var provider = parts[4].toLowerCase();

        // Check for error from provider
        var errorParam = req.getQueryParameters().get("error");
        if (errorParam != null && !errorParam.isEmpty()) {
            LOGGER.warn("OAuth provider returned error: {}", errorParam.getFirst());
            redirectError(res, "Provider error: " + errorParam.getFirst());
            return;
        }

        var codeParam  = req.getQueryParameters().get("code");
        var stateParam = req.getQueryParameters().get("state");

        if (codeParam == null || codeParam.isEmpty()) {
            redirectError(res, "Missing authorization code");
            return;
        }
        if (stateParam == null || stateParam.isEmpty()) {
            redirectError(res, "Missing state parameter");
            return;
        }

        var code  = codeParam.getFirst();
        var state = stateParam.getFirst();

        try {
            // 1. Exchange code + verify state → user profile from provider
            var profile = oauthService.handleCallback(provider, code, state);
            var email   = profile.getString("email").getValue();

            LOGGER.info("OAuth callback: authenticated {} via {}", email, provider);

            // 2. Find or create user
            var user = findOrCreateUser(req, profile, provider);

            // 3. Issue JWT + set cookie
            var roles  = extractRoles(user);
            var tenant = user.containsKey("tenant") ? user.getString("tenant").getValue() : "";
            var status = user.containsKey("status")  ? user.getString("status").getValue()  : "active";

            var jwtToken = jwt.issueToken(email, roles, Map.of("tenant", tenant, "status", status));
            res.getHeaders().add(
                    HttpString.tryFromString("Set-Cookie"),
                    JwtHelper.setCookieHeader(jwtToken, conf.cookieName(), RequestOverrides.cookieDomain(req, conf), conf.jwtTtl()));

            // 4. Redirect to app
            res.setStatusCode(HttpStatus.SC_TEMPORARY_REDIRECT);
            res.getHeaders().put(Headers.LOCATION, oauthConfig.frontendSuccessUrl());

        } catch (OAuthService.OAuthException e) {
            LOGGER.warn("OAuth callback error ({}): {}", provider, e.getMessage());
            redirectError(res, e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Unexpected error in OAuth callback ({})", provider, e);
            redirectError(res, "Internal error");
        }
    }

    // ── User creation / lookup ────────────────────────────────────────────────

    /**
     * Finds the existing user or creates a new one from the OAuth profile.
     *
     * <p>New users get {@code status: "active"} (provider has already verified the email)
     * and a default team is created for them.
     */
    private BsonDocument findOrCreateUser(StringRequest req, BsonDocument profile, String provider) {
        var email = profile.getString("email").getValue();

        var existing = db(req).findUser(email);
        if (existing.isPresent()) {
            // Optionally update name / avatar from provider on every login
            var user = existing.get();
            maybeUpdateProfile(req, email, profile);
            return user;
        }

        // ── New user ──────────────────────────────────────────────────────────
        var now  = new BsonDateTime(System.currentTimeMillis());
        var name = profile.containsKey("name") ? profile.getString("name").getValue()
                                                : email.split("@")[0];

        // Create a default team for this user
        var teamDoc = new BsonDocument()
                .append("name",      new BsonString(name + "'s Team"))
                .append("createdBy", new BsonString(email))
                .append("createdAt", now)
                .append("members",   new BsonArray());

        // Add owner membership
        teamDoc.getArray("members").add(new BsonDocument()
                .append("userId",   new BsonString(email))
                .append("role",     new BsonString("owner"))
                .append("joinedAt", now));

        var teamId = db(req).insertTeam(teamDoc);

        // Build user document
        var roles = new BsonArray();
        roles.add(new BsonString("user"));

        var profileDoc = new BsonDocument()
                .append("firstName", new BsonString(extractFirstName(name)))
                .append("lastName",  new BsonString(extractLastName(name)));

        if (profile.containsKey("avatarUrl")) {
            profileDoc.append("avatarUrl", profile.get("avatarUrl"));
        }

        var socialAuths = new BsonArray();
        socialAuths.add(new BsonDocument()
                .append("provider",   new BsonString(provider))
                .append("providerId", profile.containsKey("providerId")
                        ? profile.get("providerId") : new BsonString(""))
                .append("linkedAt",   now));

        var userDoc = new BsonDocument()
                .append("_id",         new BsonString(email))
                .append("password",    new BsonString("")) // no password for OAuth users
                .append("roles",       roles)
                .append("status",      new BsonString("active")) // Google verified the email
                .append("tenant",      new BsonString(teamId))
                .append("profile",     profileDoc)
                .append("socialAuths", socialAuths);

        db(req).insertUser(userDoc);
        LOGGER.info("New user created via {} OAuth: <{}>", provider, email);

        return userDoc;
    }

    /** Updates name/avatar from the latest OAuth profile if they have changed. */
    private void maybeUpdateProfile(StringRequest req, String email, BsonDocument profile) {
        var updates = new BsonDocument();

        if (profile.containsKey("name")) {
            var name = profile.getString("name").getValue();
            updates.append("profile.firstName", new BsonString(extractFirstName(name)));
            updates.append("profile.lastName",  new BsonString(extractLastName(name)));
        }
        if (profile.containsKey("avatarUrl")) {
            updates.append("profile.avatarUrl", profile.get("avatarUrl"));
        }

        if (!updates.isEmpty()) {
            db(req).updateUser(email, updates);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DbHelper db(StringRequest req) {
        return new DbHelper(mclient, RequestOverrides.db(req, conf));
    }

    private Set<String> extractRoles(BsonDocument user) {
        var roles = new HashSet<String>();
        if (user.containsKey("roles") && user.get("roles").isArray()) {
            user.getArray("roles").forEach(v -> roles.add(v.asString().getValue()));
        }
        if (roles.isEmpty()) roles.add("user");
        return roles;
    }

    private static String extractFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        var parts = fullName.strip().split("\\s+", 2);
        return parts[0];
    }

    private static String extractLastName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        var parts = fullName.strip().split("\\s+", 2);
        return parts.length > 1 ? parts[1] : "";
    }

    private void redirectError(StringResponse res, String reason) throws Exception {
        var url = oauthConfig.frontendErrorUrl() + "&reason="
                + URLEncoder.encode(reason, StandardCharsets.UTF_8);
        res.setStatusCode(HttpStatus.SC_TEMPORARY_REDIRECT);
        res.getHeaders().put(Headers.LOCATION, url);
    }
}
