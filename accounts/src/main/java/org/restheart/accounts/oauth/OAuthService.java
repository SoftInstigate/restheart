package org.restheart.accounts.oauth;

import org.bson.BsonDocument;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.accounts.OAuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core OAuth service: manages provider registration, state tokens (CSRF) and
 * authorization-code exchange.
 *
 * <p>State tokens are kept in a {@link ConcurrentHashMap} with a 10-minute TTL.
 * Each token is one-time-use — it is removed when verified.
 */
@RegisterPlugin(
        name             = "oauthService",
        description      = "Core OAuth 2.0 service for restheart-accounts",
        enabledByDefault = false)
public class OAuthService implements Provider<OAuthService> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long STATE_TOKEN_TTL_MS = 10 * 60 * 1_000L; // 10 min

    @Inject("oauthConfig")
    private OAuthConfig config;

    /** Registered providers keyed by name, e.g. {@code "google"}. */
    private final Map<String, OAuthProvider> providers   = new HashMap<>();
    private final Map<String, StateToken>    stateTokens = new ConcurrentHashMap<>();

    @OnInit
    public void onInit() {
        LOGGER.info("OAuthService initialized");
    }

    @Override
    public OAuthService get(PluginRecord<?> caller) { return this; }

    // ── Provider registration (called by provider Initializers) ──────────────

    public void registerProvider(OAuthProvider provider) {
        providers.put(provider.getProviderName(), provider);
        LOGGER.info("OAuth provider registered: {}", provider.getProviderName());
    }

    // ── Authorization URL ─────────────────────────────────────────────────────

    /**
     * Returns the authorization URL for the given provider and generates a
     * CSRF state token. Checks per-tenant overrides in the request.
     */
    public AuthResult getAuthorizationUrl(String providerName,
            org.restheart.exchange.ServiceRequest<?> req) throws OAuthException {
        var cfg      = resolveProviderConfig(providerName, req);
        var provider = resolveProvider(providerName);
        var state    = generateState();
        stateTokens.put(state, new StateToken(providerName, System.currentTimeMillis()));
        var url = provider.getAuthorizationUrl(cfg.clientId(), cfg.clientSecret(),
                config.callbackUrl(providerName), cfg.scope(), state);
        return new AuthResult(url, state);
    }

    /** @deprecated Use {@link #getAuthorizationUrl(String, org.restheart.exchange.ServiceRequest)} instead. */
    @Deprecated
    public AuthResult getAuthorizationUrl(String providerName) throws OAuthException {
        return getAuthorizationUrl(providerName, null);
    }

    // ── Callback / code exchange ──────────────────────────────────────────────

    /**
     * Handles the OAuth callback: verifies the state token, then delegates
     * token exchange and profile fetch to the provider.
     *
     * @return the user profile as a {@code BsonDocument} (fields: email, name,
     *         providerId, avatarUrl)
     */
    public BsonDocument handleCallback(String providerName, String code, String state)
            throws OAuthException {

        if (!verifyAndConsumeState(state, providerName)) {
            throw new OAuthException("Invalid or expired state token (possible CSRF)");
        }

        var cfg      = resolveProviderConfig(providerName);
        var provider = resolveProvider(providerName);

        try {
            return provider.fetchUserProfile(cfg.clientId(), cfg.clientSecret(),
                    config.callbackUrl(providerName), cfg.scope(), code);
        } catch (OAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new OAuthException("OAuth callback failed: " + e.getMessage(), e);
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private OAuthConfig.ProviderConfig resolveProviderConfig(String name) throws OAuthException {
        if (!config.isEnabled()) throw new OAuthException("OAuth is disabled");
        var cfg = config.provider(name);
        if (cfg == null || !cfg.isValid())
            throw new OAuthException("Provider '" + name + "' is not configured or not enabled");
        return cfg;
    }

    /**
     * Resolves the provider configuration for the given request, checking per-tenant
     * overrides (from {@link org.restheart.accounts.util.RequestOverrides}) before
     * falling back to the static config.
     *
     * <p>Currently only Google supports per-tenant override via:
     * <ul>
     *   <li>{@code override-accounts-oauth-google-client-id}</li>
     *   <li>{@code override-accounts-oauth-google-client-secret}</li>
     * </ul>
     *
     * @param name provider name (case-insensitive)
     * @param req  the incoming request (used to read per-tenant overrides)
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

    private String generateState() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean verifyAndConsumeState(String state, String expectedProvider) {
        if (state == null || state.isBlank()) return false;
        var token = stateTokens.remove(state);
        if (token == null) return false;
        if (System.currentTimeMillis() - token.timestamp() > STATE_TOKEN_TTL_MS) return false;
        stateTokens.entrySet().removeIf(e ->
                System.currentTimeMillis() - e.getValue().timestamp() > STATE_TOKEN_TTL_MS);
        return token.providerName().equalsIgnoreCase(expectedProvider);
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record AuthResult(String url, String state) {}

    private record StateToken(String providerName, long timestamp) {}

    public static class OAuthException extends Exception {
        public OAuthException(String msg)                   { super(msg); }
        public OAuthException(String msg, Throwable cause) { super(msg, cause); }
    }
}
