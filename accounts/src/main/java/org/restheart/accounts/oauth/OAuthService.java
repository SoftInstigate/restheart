package org.restheart.accounts.oauth;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.util.RequestOverrides;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.accounts.OAuthProvider;
import org.restheart.plugins.accounts.OAuthProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Core OAuth service: manages provider registration, CSRF state tokens, and
 * authorization-code exchange.
 *
 * <p>State tokens are persisted in the MongoDB {@code oauth_codes} collection
 * (TTL index: 600 s) so they survive pod restarts and work correctly in
 * multi-instance deployments. Each token is one-time-use: it is atomically
 * consumed during {@link #handleCallback} via {@code findOneAndDelete}.
 *
 * <h2>Multi-tenant / per-db persistence</h2>
 * <p>When a per-tenant database override is active (via {@link RequestOverrides#db}),
 * the state token is stored in <em>that tenant's</em> {@code oauth_codes} collection,
 * not in the default database. The correct database is encoded directly in the state
 * string so that the callback can retrieve the token from the right collection without
 * needing access to the original HTTP request.
 *
 * <p>State string format:
 * <pre>
 *   base64url(dbName) + "." + base64url(32-random-bytes)
 * </pre>
 * The {@code .} separator is safe because it is not part of the base64url alphabet.
 *
 * <p>Document schema ({@code oauth_codes} collection):
 * <pre>{@code
 * {
 *   "_id":                ObjectId,
 *   "code":               "<state string>",    // unique index
 *   "providerName":       "google",
 *   "created_at":         ISODate(...),         // TTL index (600 s)
 *   "pendingInviteToken": "abc123",             // optional
 *   "consentsAccepted":   true                  // optional, default false
 * }
 * }</pre>
 */
@RegisterPlugin(
        name             = "oauthService",
        description      = "Core OAuth 2.0 service for restheart-accounts",
        enabledByDefault = false,
        priority         = 30)  // after mclient (11), accountsConfig (20), accountsService (25)
public class OAuthService implements Provider<OAuthService>, OAuthProviderRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    /**
     * Application-level TTL guard (ms). MongoDB's TTL task fires every ~60 s,
     * leaving a small window where a document past its TTL still exists in the DB.
     * This guard closes that window.
     */
    private static final long STATE_TOKEN_TTL_MS = 10 * 60 * 1_000L; // 10 min

    @Inject("oauthConfig")
    private OAuthConfig config;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData accountsConf;

    /** Registered providers keyed by lower-case name, e.g. {@code "google"}. */
    private final Map<String, OAuthProvider> providers = new HashMap<>();

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String queryParam(org.restheart.exchange.ServiceRequest<?> req, String name) {
        if (req == null) return null;
        var values = req.getQueryParameters().get(name);
        return (values != null && !values.isEmpty()) ? values.getFirst() : null;
    }

    private MongoCollection<BsonDocument> oauthCodes(String db) {
        return mclient.getDatabase(db)
                      .getCollection("oauth_codes", BsonDocument.class);
    }

    @OnInit
    public void onInit() {
        LOGGER.info("OAuthService initialized (state tokens persisted in MongoDB oauth_codes)");
    }

    @Override
    public OAuthService get(PluginRecord<?> caller) { return this; }

    // ── Provider registration ─────────────────────────────────────────────────

    @Override
    public void registerProvider(OAuthProvider provider) {
        providers.put(provider.getProviderName(), provider);
        LOGGER.info("OAuth provider registered: {}", provider.getProviderName());
    }

    // ── Authorization URL ─────────────────────────────────────────────────────

    /**
     * Returns the authorization URL for the given provider and stores a CSRF
     * state token in MongoDB. Checks per-tenant overrides in the request.
     *
     * <p>Optional query parameters forwarded into the state document:
     * <ul>
     *   <li>{@code pendingInviteToken} — invite token from the activation email</li>
     *   <li>{@code consentsAccepted=true} — user accepted T&amp;C before the redirect</li>
     * </ul>
     *
     * @param providerName the OAuth provider name (e.g. {@code "google"})
     * @param req          the incoming HTTP request; may be {@code null} for non-HTTP use
     * @return an {@link AuthResult} with the authorization redirect URL and the CSRF state
     * @throws OAuthException if the provider is not configured or not registered
     */
    public AuthResult getAuthorizationUrl(String providerName,
            org.restheart.exchange.ServiceRequest<?> req) throws OAuthException {
        var cfg                = resolveProviderConfig(providerName, req);
        var provider           = resolveProvider(providerName);
        var tenantDb           = RequestOverrides.db(req, accountsConf);
        var state              = generateState(tenantDb);
        var pendingInviteToken = queryParam(req, "pendingInviteToken");
        var consentsAccepted   = "true".equalsIgnoreCase(queryParam(req, "consentsAccepted"));

        storeStateToken(state, providerName, tenantDb, pendingInviteToken, consentsAccepted);

        var url = provider.getAuthorizationUrl(cfg.clientId(), cfg.clientSecret(),
                config.callbackUrl(providerName), cfg.scope(), state);
        return new AuthResult(url, state);
    }

    /**
     * @deprecated Use {@link #getAuthorizationUrl(String, org.restheart.exchange.ServiceRequest)} instead.
     * @param providerName the OAuth provider name
     * @throws OAuthException if the provider is not configured or not registered
     */
    @Deprecated
    public AuthResult getAuthorizationUrl(String providerName) throws OAuthException {
        return getAuthorizationUrl(providerName, null);
    }

    // ── Callback / code exchange ──────────────────────────────────────────────

    /**
     * Handles the OAuth callback: atomically verifies and consumes the state
     * token from MongoDB ({@code verifyAndConsumeState}), then delegates token
     * exchange and profile fetch to the provider.
     *
     * @param providerName the OAuth provider name extracted from the callback path
     * @param code         the authorization code received from the OAuth provider
     * @param state        the CSRF state token returned by the provider (must match a stored token)
     * @return a {@link CallbackResult} carrying the user profile and the invite
     *         context that was stored in the state token at authorization time
     * @throws OAuthException if the state token is invalid/expired, the provider is
     *                        not registered, or the profile fetch fails
     */
    public CallbackResult handleCallback(String providerName, String code, String state)
            throws OAuthException {

        var token = verifyAndConsumeState(state, providerName);
        if (token == null) {
            throw new OAuthException("Invalid or expired state token (possible CSRF)");
        }

        var cfg      = resolveProviderConfig(providerName);
        var provider = resolveProvider(providerName);

        try {
            var profile = provider.fetchUserProfile(cfg.clientId(), cfg.clientSecret(),
                    config.callbackUrl(providerName), cfg.scope(), code);
            return new CallbackResult(profile, token.pendingInviteToken(), token.consentsAccepted());
        } catch (OAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new OAuthException("OAuth callback failed: " + e.getMessage(), e);
        }
    }

    // ── State token persistence ───────────────────────────────────────────────

    private void storeStateToken(String state, String providerName, String tenantDb,
                                 String pendingInviteToken, boolean consentsAccepted) {
        var doc = new BsonDocument()
                .append("code",             new BsonString(state))
                .append("providerName",     new BsonString(providerName))
                .append("created_at",       new BsonDateTime(System.currentTimeMillis()))
                .append("consentsAccepted", new BsonBoolean(consentsAccepted));
        if (pendingInviteToken != null) {
            doc.append("pendingInviteToken", new BsonString(pendingInviteToken));
        }
        try {
            oauthCodes(tenantDb).insertOne(doc);
        } catch (Exception e) {
            LOGGER.error("OAuthService: failed to store state token in {}.oauth_codes", tenantDb, e);
            throw new RuntimeException("OAuth state storage failed", e);
        }
    }

    /**
     * Atomically removes the state document from the correct tenant's MongoDB
     * collection and returns a {@link StateToken} if the token is valid, or
     * {@code null} otherwise.
     *
     * <p>The tenant database is decoded from the state string (see class Javadoc).
     * The token is always consumed when found by {@code code}, regardless of
     * provider-name match or TTL, to prevent replay probing.
     */
    private StateToken verifyAndConsumeState(String state, String expectedProvider) {
        if (state == null || state.isBlank()) return null;

        // Decode the tenant db from the state prefix
        var tenantDb = decodeDbFromState(state);
        if (tenantDb == null) {
            LOGGER.warn("OAuthService: state token has invalid format");
            return null;
        }

        BsonDocument doc;
        try {
            doc = oauthCodes(tenantDb).findOneAndDelete(Filters.eq("code", state));
        } catch (Exception e) {
            LOGGER.error("OAuthService: failed to consume state token from {}.oauth_codes", tenantDb, e);
            return null;
        }

        if (doc == null) return null;

        // Application-level TTL guard (closes the ~60 s MongoDB TTL task window)
        var createdAt = doc.containsKey("created_at") && doc.get("created_at").isDateTime()
                ? doc.getDateTime("created_at").getValue() : 0L;
        if (System.currentTimeMillis() - createdAt > STATE_TOKEN_TTL_MS) {
            LOGGER.debug("OAuthService: state token expired (found past TTL in DB)");
            return null;
        }

        var storedProvider = doc.containsKey("providerName") && doc.get("providerName").isString()
                ? doc.getString("providerName").getValue() : "";
        if (!expectedProvider.equalsIgnoreCase(storedProvider)) {
            LOGGER.warn("OAuthService: state token providerName mismatch (expected={}, found={})",
                    expectedProvider, storedProvider);
            return null;
        }

        var pendingInviteToken = doc.containsKey("pendingInviteToken")
                && doc.get("pendingInviteToken").isString()
                ? doc.getString("pendingInviteToken").getValue() : null;
        var consentsAccepted = doc.containsKey("consentsAccepted")
                && doc.get("consentsAccepted").isBoolean()
                && doc.getBoolean("consentsAccepted").getValue();

        return new StateToken(storedProvider, pendingInviteToken, consentsAccepted);
    }

    // ── Config helpers ────────────────────────────────────────────────────────

    private OAuthConfig.ProviderConfig resolveProviderConfig(String name) throws OAuthException {
        if (!config.isEnabled()) throw new OAuthException("OAuth is disabled");
        var cfg = config.provider(name);
        if (cfg == null || !cfg.isValid())
            throw new OAuthException("Provider '" + name + "' is not configured or not enabled");
        return cfg;
    }

    /**
     * Resolves provider config, checking per-tenant overrides before falling
     * back to static config (currently only Google supports overrides).
     *
     * @param name provider name (case-insensitive), e.g. {@code "google"}
     * @param req  the incoming request used to read per-tenant overrides; may be {@code null}
     * @return the effective {@link OAuthConfig.ProviderConfig}
     * @throws OAuthException if OAuth is disabled or the provider is not configured
     */
    public OAuthConfig.ProviderConfig resolveProviderConfig(String name,
            org.restheart.exchange.ServiceRequest<?> req) throws OAuthException {
        if ("google".equalsIgnoreCase(name) && req != null) {
            var tenantCfg = org.restheart.accounts.util.RequestOverrides.oauthGoogle(req);
            if (tenantCfg != null && tenantCfg.isValid()) {
                return tenantCfg;
            }
        }
        return resolveProviderConfig(name);
    }

    private OAuthProvider resolveProvider(String name) throws OAuthException {
        var p = providers.get(name.toLowerCase());
        if (p == null) throw new OAuthException("Provider '" + name + "' is not registered");
        return p;
    }

    /**
     * Generates a state string encoding the tenant database name and 32 random bytes.
     *
     * <p>Format: {@code base64url(dbName) + "." + base64url(32-random-bytes)}
     * <br>The {@code .} separator is safe because it is not part of the base64url alphabet.
     */
    private String generateState(String tenantDb) {
        var dbB64  = Base64.getUrlEncoder().withoutPadding()
                          .encodeToString(tenantDb.getBytes(StandardCharsets.UTF_8));
        var rndB64 = Base64.getUrlEncoder().withoutPadding()
                          .encodeToString(randomBytes(32));
        return dbB64 + "." + rndB64;
    }

    /**
     * Decodes the tenant database name from the state string prefix.
     * Returns {@code null} if the format is invalid.
     */
    private String decodeDbFromState(String state) {
        var dot = state.indexOf('.');
        if (dot <= 0) return null;
        try {
            return new String(Base64.getUrlDecoder().decode(state.substring(0, dot)),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private byte[] randomBytes(int n) {
        var bytes = new byte[n];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    // ── Records ───────────────────────────────────────────────────────────────

    /**
     * Carries the authorization redirect URL and the CSRF state token.
     *
     * @param url   the full authorization URL to redirect the user to
     * @param state the CSRF state token (also embedded in the URL)
     */
    public record AuthResult(String url, String state) {}

    /**
     * Carries the provider profile and the invite context stored in the state
     * token at authorization time.
     *
     * @param profile             user profile from the OAuth provider
     * @param pendingInviteToken  value of {@code ?pendingInviteToken=} on authorize, or {@code null}
     * @param consentsAccepted    {@code true} if {@code ?consentsAccepted=true} was passed
     */
    public record CallbackResult(org.bson.BsonDocument profile,
                                 String pendingInviteToken,
                                 boolean consentsAccepted) {}

    private record StateToken(String providerName,
                               String pendingInviteToken,
                               boolean consentsAccepted) {}

    public static class OAuthException extends Exception {
        public OAuthException(String msg)                   { super(msg); }
        public OAuthException(String msg, Throwable cause) { super(msg, cause); }
    }
}
