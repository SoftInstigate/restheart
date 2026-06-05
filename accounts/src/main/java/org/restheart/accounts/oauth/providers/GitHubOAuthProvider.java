package org.restheart.accounts.oauth.providers;

import com.github.scribejava.apis.GitHubApi;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.JsonParser;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.restheart.accounts.oauth.OAuthProvider;
import org.restheart.accounts.oauth.OAuthService;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GitHub OAuth 2.0 provider.
 *
 * <p>Registers itself with {@link OAuthService} on startup.
 *
 * <p>Fetches user profile from the GitHub REST API:
 * <ul>
 *   <li>{@code GET https://api.github.com/user} — basic profile</li>
 *   <li>{@code GET https://api.github.com/user/emails} — verified email address
 *       (fetched only if the primary profile endpoint returns no email)</li>
 * </ul>
 *
 * <p>Returns a {@link BsonDocument} with fields:
 * <ul>
 *   <li>{@code email}      — primary verified email</li>
 *   <li>{@code name}       — display name (falls back to login if absent)</li>
 *   <li>{@code providerId} — GitHub numeric user ID (as string)</li>
 *   <li>{@code avatarUrl}  — profile picture URL (or {@link BsonNull})</li>
 * </ul>
 *
 * <p>Configuration in {@code oauthConfig.providers.github}:
 * <pre>{@code
 * oauthConfig:
 *   providers:
 *     github:
 *       enabled: true
 *       client-id: "Iv1.…"
 *       client-secret: "…"
 *       scope: "user:email"   # default — grants access to the emails endpoint
 * }</pre>
 */
@RegisterPlugin(
        name             = "githubOAuthProvider",
        description      = "GitHub OAuth 2.0 provider for restheart-accounts",
        enabledByDefault = false)
public class GitHubOAuthProvider implements OAuthProvider, Initializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubOAuthProvider.class);
    private static final String PROVIDER_NAME = "github";
    private static final String USER_URL      = "https://api.github.com/user";
    private static final String EMAILS_URL    = "https://api.github.com/user/emails";

    @Inject("oauthService")
    private OAuthService oauthService;

    @Override
    public void init() {
        if (oauthService != null) {
            oauthService.registerProvider(this);
            LOGGER.info("GitHub OAuth provider registered");
        } else {
            LOGGER.error("Cannot register GitHub OAuth provider: oauthService is null");
        }
    }

    @Override
    public String getProviderName() { return PROVIDER_NAME; }

    @Override
    public OAuth20Service buildService(String clientId, String clientSecret,
                                       String callbackUrl, String scope) {
        return new ServiceBuilder(clientId)
                .apiSecret(clientSecret)
                .defaultScope(scope)
                .callback(callbackUrl)
                .build(GitHubApi.instance());
    }

    @Override
    public BsonDocument fetchUserProfile(OAuth20Service service, String accessToken)
            throws Exception {

        var request = new OAuthRequest(Verb.GET, USER_URL);
        service.signRequest(accessToken, request);

        try (var response = service.execute(request)) {
            if (!response.isSuccessful()) {
                throw new Exception("GitHub user request failed: HTTP " + response.getCode());
            }

            var json = JsonParser.parseString(response.getBody()).getAsJsonObject();

            var providerId = json.has("id")    ? String.valueOf(json.get("id").getAsLong()) : "";
            var login      = json.has("login") ? json.get("login").getAsString()            : "";
            var name       = json.has("name") && !json.get("name").isJsonNull()
                    ? json.get("name").getAsString()
                    : login;
            var avatarUrl  = json.has("avatar_url") && !json.get("avatar_url").isJsonNull()
                    ? json.get("avatar_url").getAsString()
                    : null;

            // GitHub may not include the email in the user endpoint —
            // fetch it from the emails endpoint if needed.
            String email = json.has("email") && !json.get("email").isJsonNull()
                    ? json.get("email").getAsString()
                    : fetchPrimaryEmail(service, accessToken);

            if (email == null || email.isBlank()) {
                throw new OAuthService.OAuthException(
                        "GitHub account has no verified email address. "
                        + "Ensure the 'user:email' scope is requested.");
            }

            LOGGER.debug("GitHub profile: id={}, email={}, name={}", providerId, email, name);

            return new BsonDocument()
                    .append("email",      new BsonString(email))
                    .append("name",       new BsonString(name))
                    .append("providerId", new BsonString(providerId))
                    .append("avatarUrl",  avatarUrl != null ? new BsonString(avatarUrl) : BsonNull.VALUE);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Fetches the primary verified email from the GitHub emails endpoint.
     * Returns {@code null} if no verified email is found.
     */
    private String fetchPrimaryEmail(OAuth20Service service, String accessToken) {
        try {
            var req = new OAuthRequest(Verb.GET, EMAILS_URL);
            service.signRequest(accessToken, req);

            try (var res = service.execute(req)) {
                if (!res.isSuccessful()) {
                    LOGGER.warn("GitHub emails endpoint returned: {}", res.getCode());
                    return null;
                }

                var arr = JsonParser.parseString(res.getBody()).getAsJsonArray();

                // 1. Prefer primary + verified
                for (var el : arr) {
                    var obj       = el.getAsJsonObject();
                    var primary   = obj.has("primary")  && obj.get("primary").getAsBoolean();
                    var verified  = obj.has("verified") && obj.get("verified").getAsBoolean();
                    if (primary && verified) return obj.get("email").getAsString();
                }

                // 2. Fall back to any verified address
                for (var el : arr) {
                    var obj      = el.getAsJsonObject();
                    var verified = obj.has("verified") && obj.get("verified").getAsBoolean();
                    if (verified) return obj.get("email").getAsString();
                }

                LOGGER.warn("No verified email found in GitHub emails list");
                return null;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to fetch GitHub emails: {}", e.getMessage());
            return null;
        }
    }
}
