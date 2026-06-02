package org.restheart.accounts.oauth;

import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration provider for the OAuth 2.0 social-login flow.
 *
 * <p>Supports multiple OAuth providers — both built-in ({@code google}, {@code github})
 * and any custom provider registered as a RESTHeart plugin.
 *
 * <p>YAML block ({@code oauthConfig}):
 * <pre>{@code
 * oauthConfig:
 *   enabled: true
 *   api-base-url: https://api.example.com
 *   frontend-success-url: https://app.example.com/app
 *   frontend-error-url:   https://app.example.com/login?error=oauth_error
 *
 *   providers:
 *     google:
 *       enabled: true
 *       client-id:     "123….apps.googleusercontent.com"
 *       client-secret: "GOCSPX-…"
 *       scope:         "openid email profile"   # optional — this is the default
 *
 *     github:
 *       enabled: true
 *       client-id:     "Iv1.…"
 *       client-secret: "…"
 *       scope:         "user:email"             # optional — this is the default
 *
 *     myapp:                                    # custom provider
 *       enabled: true                           # requires a matching OAuthProvider plugin
 *       client-id:     "…"
 *       client-secret: "…"
 *       scope:         "read:profile"
 * }</pre>
 *
 * <h2>Custom providers</h2>
 * <p>Any provider whose name appears in the {@code providers} map can be backed by a
 * custom {@link OAuthProvider} implementation. The implementation must:
 * <ol>
 *   <li>Implement {@link OAuthProvider} and
 *       {@link org.restheart.plugins.Initializer}.</li>
 *   <li>Inject {@code oauthService} via {@code @Inject("oauthService")}.</li>
 *   <li>Call {@code oauthService.registerProvider(this)} in {@code @OnInit}.</li>
 * </ol>
 * The provider name returned by {@link OAuthProvider#getProviderName()} must match
 * the key used in the {@code providers} map.
 *
 * @see OAuthProvider
 * @see OAuthService
 */
@RegisterPlugin(
        name             = "oauthConfig",
        description      = "OAuth 2.0 configuration provider for restheart-accounts",
        enabledByDefault = true)
public class OAuthConfig implements Provider<OAuthConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthConfig.class);

    // Default scopes per well-known provider
    private static final Map<String, String> DEFAULT_SCOPES = Map.of(
            "google", "openid email profile",
            "github", "user:email");

    @Inject("config")
    private Map<String, Object> config;

    private boolean enabled;
    private String  apiBaseUrl;
    private String  frontendSuccessUrl;
    private String  frontendErrorUrl;

    /** All configured providers, keyed by lower-case provider name. */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    @OnInit
    @SuppressWarnings("unchecked")
    public void onInit() {
        if (config == null) {
            LOGGER.warn("oauthConfig section not found — OAuth social login is disabled");
            return;
        }

        enabled            = configVal(config, "enabled",              false);
        apiBaseUrl         = configVal(config, "api-base-url",         "http://localhost:8080");
        frontendSuccessUrl = configVal(config, "frontend-success-url", "http://localhost:4200/app");
        frontendErrorUrl   = configVal(config, "frontend-error-url",   "http://localhost:4200/login?error=oauth_error");

        // Parse the generic providers map
        var providersMap = config.get("providers") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();

        for (var entry : providersMap.entrySet()) {
            var name = entry.getKey().toString().toLowerCase();
            if (!(entry.getValue() instanceof Map<?, ?> pMap)) continue;

            var pConfig = parseProviderConfig(name, (Map<String, Object>) pMap);
            if (pConfig != null) {
                providers.put(name, pConfig);
                LOGGER.info("OAuth provider '{}' configured: enabled={}", name, pConfig.enabled());
            }
        }

        LOGGER.info("OAuthConfig initialized: enabled={}, providers={}", enabled, providers.keySet());
    }

    @Override
    public OAuthConfig get(PluginRecord<?> caller) { return this; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isEnabled()          { return enabled; }
    public String  apiBaseUrl()         { return apiBaseUrl; }
    public String  frontendSuccessUrl() { return frontendSuccessUrl; }
    public String  frontendErrorUrl()   { return frontendErrorUrl; }

    /** Returns all configured providers (unmodifiable view). */
    public Map<String, ProviderConfig> providers() {
        return Collections.unmodifiableMap(providers);
    }

    /**
     * Returns the configuration for the given provider, or {@code null} if not found.
     *
     * @param name provider name (case-insensitive), e.g. {@code "google"}, {@code "github"}
     */
    public ProviderConfig provider(String name) {
        return name == null ? null : providers.get(name.toLowerCase());
    }

    /** Returns {@code true} if the provider is configured and enabled. */
    public boolean isProviderEnabled(String name) {
        var p = provider(name);
        return p != null && p.isValid();
    }

    /** Returns the absolute callback URL for the given provider name. */
    public String callbackUrl(String providerName) {
        return apiBaseUrl + "/auth/oauth/callback/" + providerName.toLowerCase();
    }

    // ── Nested record ─────────────────────────────────────────────────────────

    /**
     * Configuration for a single OAuth provider.
     *
     * @param name         provider name (lower-case), e.g. {@code "google"}
     * @param enabled      whether the provider is active
     * @param clientId     OAuth application client ID
     * @param clientSecret OAuth application client secret
     * @param scope        space-separated OAuth scopes
     */
    public record ProviderConfig(
            String  name,
            boolean enabled,
            String  clientId,
            String  clientSecret,
            String  scope) {

        /** Returns {@code true} when all required fields are present and enabled. */
        public boolean isValid() {
            return enabled
                    && clientId     != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank();
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ProviderConfig parseProviderConfig(String name, Map<String, Object> map) {
        var defaultScope = DEFAULT_SCOPES.getOrDefault(name, "");
        return new ProviderConfig(
                name,
                configVal(map, "enabled",       false),
                configVal(map, "client-id",     null),
                configVal(map, "client-secret", null),
                configVal(map, "scope",         defaultScope));
    }

    @SuppressWarnings("unchecked")
    private static <T> T configVal(Map<?, ?> map, String key, T def) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) return def;
        try { return (T) map.get(key); } catch (ClassCastException e) { return def; }
    }
}
