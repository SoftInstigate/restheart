package org.restheart.accounts.oauth;

import com.mongodb.client.MongoClient;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.bson.BsonArray;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.accounts.AccountsService;
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
import org.restheart.plugins.accounts.ConsentRecord;
import org.restheart.security.ACLRegistry;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static java.util.function.Predicate.not;

/**
 * Handles the OAuth 2.0 callback for any configured provider.
 *
 * <pre>
 *   GET /auth/oauth/callback/{provider}?code=...&amp;state=...
 * </pre>
 *
 * <p>Flow:
 * <ol>
 *   <li>Verify CSRF state token (consumed atomically from MongoDB)</li>
 *   <li>Delegate code exchange and profile fetch to the {@link org.restheart.plugins.accounts.OAuthProvider}</li>
 *   <li>Find or create the user in MongoDB</li>
 *   <li>If user has {@code status:"invited"}, invoke
 *       {@link org.restheart.plugins.accounts.MembershipProvider#activateViaOAuth} to activate</li>
 *   <li>Issue JWT and set the auth cookie ({@code conf.cookieName()})</li>
 *   <li>Redirect to {@code frontendSuccessUrl}</li>
 * </ol>
 *
 * <p>On any error the browser is redirected to {@code frontendErrorUrl}.
 *
 * <p>New user creation delegates team / tenant initialization to the active
 * {@link org.restheart.plugins.accounts.MembershipProvider} via {@link AccountsService}.
 */
@RegisterPlugin(
        name             = "oauthCallback",
        description      = "GET /auth/oauth/callback/{provider} — handles the OAuth callback",
        defaultURI       = "/auth/oauth/callback",
        enabledByDefault = false)
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

    @Inject("accountsService")
    private AccountsService accountsService;

    private JwtHelper jwt;

    @OnInit
    public void onInit() {
        this.jwt = new JwtHelper(conf.jwtKey(), conf.jwtIssuer(), conf.jwtTtl(), conf.accountPropertiesClaims());

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
            // 1. Exchange code + verify state → user profile + invite context
            var callbackResult = oauthService.handleCallback(provider, code, state);
            var profile        = callbackResult.profile();
            var email          = profile.getString("email").getValue();

            LOGGER.info("OAuth callback: authenticated {} via {}", email, provider);

            // 2. Find or create user (membership delegated to the provider)
            var user   = findOrCreateUser(req, profile, provider);

            // Check if user is unverified (invited but not yet activated)
            var userRoles = user.containsKey("roles") && user.get("roles").isArray()
                    ? user.getArray("roles") : new org.bson.BsonArray();
            boolean isUnverified = userRoles.size() == 1
                    && "$unauthenticated".equals(userRoles.get(0).asString().getValue());

            // 2b. If OAuth was initiated from an invite link, the email must match
            var pendingInviteToken = callbackResult.pendingInviteToken();
            if (pendingInviteToken != null && !pendingInviteToken.isBlank() && !isUnverified) {
                // OAuth email doesn't match the invited email — deny
                LOGGER.warn("OAuth invite mismatch: invited token present but user <{}> is not unverified (OAuth email != invited email)", email);
                redirectError(res, "The email used for social login does not match the invited email. Please use the same email address that received the invitation.");
                return;
            }

            // 3. Handle unverified invited users: give MembershipProvider a chance to activate
            if (isUnverified) {
                ConsentRecord consents = null;
                if (callbackResult.consentsAccepted()) {
                    var ip = req.getExchange().getSourceAddress().getAddress().getHostAddress();
                    consents = new ConsentRecord(conf.termsVersion(), conf.privacyVersion(),
                            ip, Instant.now());
                }

                var membership = accountsService.getMembershipProvider()
                        .activateViaOAuth(email, consents);

                if (membership.isPresent()) {
                    LOGGER.info("Invited user <{}> activated via {} OAuth", email, provider);
                    var activatedRoles = extractRoles(user);
                    var jwtToken = jwt.issueToken(email, activatedRoles,
                            RequestOverrides.db(req, conf),
                            req.attachedParams(),
                            java.util.Map.<String, Object>of(conf.tenantClaimName(), membership.get().tenantId()),
                            null);
                    setAuthCookieAndRedirect(res, req, jwtToken);
                    return;
                }
                // activateViaOAuth returned empty — redirect to frontendErrorUrl (spec: deny access)
                LOGGER.info("OAuth login denied for invited user <{}>: activateViaOAuth returned empty", email);
                redirectError(res, "Account is pending activation");
                return;
            }

            // 4. Issue JWT + set cookie for normal / non-activated users
            var roles  = extractRoles(user);
            var activeMembership = accountsService.getMembershipProvider().activeMembership(email);
            var tenantId         = activeMembership.map(m -> m.tenantId()).orElse(null);
            var jwtToken = jwt.issueToken(email, roles,
                    RequestOverrides.db(req, conf),
                    req.attachedParams(),
                    java.util.Map.<String, Object>of(conf.tenantClaimName(), tenantId),
                    null);
            setAuthCookieAndRedirect(res, req, jwtToken);

        } catch (OAuthService.OAuthException e) {
            LOGGER.warn("OAuth callback error ({}): {}", provider, e.getMessage());
            redirectError(res, e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Unexpected error in OAuth callback ({})", provider, e);
            redirectError(res, "Internal error");
        }
    }

    // ── Cookie + redirect helper ──────────────────────────────────────────────

    private void setAuthCookieAndRedirect(StringResponse res, StringRequest req, String jwtToken) {
        res.getHeaders().add(
                HttpString.tryFromString("Set-Cookie"),
                JwtHelper.setCookieHeader(jwtToken, conf.cookieName(),
                        RequestOverrides.cookieDomain(req, conf), conf.jwtTtl()));
        res.setStatusCode(HttpStatus.SC_TEMPORARY_REDIRECT);
        res.getHeaders().put(Headers.LOCATION, oauthConfig.frontendSuccessUrl());
    }

    // ── User creation / lookup ────────────────────────────────────────────────

    /**
     * Finds the existing user or creates a new one from the OAuth profile.
     *
     * <p>New users get {@code roles: ["user"]} (provider has already verified the email).
     * Team / tenant initialization is delegated to the active {@link AccountsService}
     * MembershipProvider via {@code createInitialTeam}.
     */
    private BsonDocument findOrCreateUser(StringRequest req, BsonDocument profile, String provider) {
        var email = profile.getString("email").getValue();

        var existing = db(req).findUser(email);
        if (existing.isPresent()) {
            // Optionally update name / avatar from provider on every login
            maybeUpdateProfile(req, email, profile);
            return existing.get();
        }

        // ── New user ──────────────────────────────────────────────────────────
        var now  = new BsonDateTime(System.currentTimeMillis());
        var name = profile.containsKey("name") ? profile.getString("name").getValue()
                                                : email.split("@")[0];

        // Build user document (tenant/membership fields will be set by the provider)
        var roles = new BsonArray();
        roles.add(new BsonString("user"));

        var profileDoc = new BsonDocument()
                .append("name",    new BsonString(extractFirstName(name)))
                .append("surname", new BsonString(extractLastName(name)));

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
                .append("profile",     profileDoc)
                .append("socialAuths", socialAuths);

        db(req).insertUser(userDoc);

        // Delegate team creation and membership linking to the MembershipProvider
        accountsService.getMembershipProvider().createInitialTeam(email, name + "'s Team");

        LOGGER.info("New user created via {} OAuth: <{}>", provider, email);

        // Return the updated user doc (with tenant fields set by provider)
        return db(req).findUser(email).orElse(userDoc);
    }

    /** Updates name/avatar from the latest OAuth profile if they have changed. */
    private void maybeUpdateProfile(StringRequest req, String email, BsonDocument profile) {
        var updates = new BsonDocument();

        if (profile.containsKey("name")) {
            var name = profile.getString("name").getValue();
            updates.append("profile.name",    new BsonString(extractFirstName(name)));
            updates.append("profile.surname", new BsonString(extractLastName(name)));
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
