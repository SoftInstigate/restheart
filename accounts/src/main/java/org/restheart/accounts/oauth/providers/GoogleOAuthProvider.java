package org.restheart.accounts.oauth.providers;

import com.github.scribejava.apis.GoogleApi20;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.JsonParser;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.restheart.accounts.oauth.OAuthService;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.accounts.OAuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Google OAuth 2.0 provider.
 *
 * <p>Registers itself with {@link OAuthService} on startup.
 *
 * <p>Fetches user profile from {@code https://www.googleapis.com/oauth2/v2/userinfo}
 * and returns a {@link BsonDocument} with fields:
 * <ul>
 *   <li>{@code email}      — verified email address</li>
 *   <li>{@code name}       — full display name</li>
 *   <li>{@code providerId} — Google user ID</li>
 *   <li>{@code avatarUrl}  — profile picture URL (or {@link BsonNull})</li>
 * </ul>
 */
@RegisterPlugin(
        name             = "googleOAuthProvider",
        description      = "Google OAuth 2.0 provider for restheart-accounts",
        enabledByDefault = false)
public class GoogleOAuthProvider implements OAuthProvider, Initializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleOAuthProvider.class);
    private static final String PROVIDER_NAME = "google";
    private static final String USERINFO_URL  = "https://www.googleapis.com/oauth2/v2/userinfo";

    @Inject("oauthService")
    private OAuthService oauthService;

    @Override
    public void init() {
        if (oauthService != null) {
            oauthService.registerProvider(this);
            LOGGER.info("Google OAuth provider registered");
        } else {
            LOGGER.error("Cannot register Google OAuth provider: oauthService is null");
        }
    }

    @Override
    public String getProviderName() { return PROVIDER_NAME; }

    @Override
    public String getAuthorizationUrl(String clientId, String clientSecret,
                                      String callbackUrl, String scope, String state) {
        return buildService(clientId, clientSecret, callbackUrl, scope).getAuthorizationUrl(state);
    }

    @Override
    public BsonDocument fetchUserProfile(String clientId, String clientSecret,
                                         String callbackUrl, String scope, String code)
            throws Exception {
        var service     = buildService(clientId, clientSecret, callbackUrl, scope);
        var accessToken = service.getAccessToken(code).getAccessToken();

        var request = new OAuthRequest(Verb.GET, USERINFO_URL);
        service.signRequest(accessToken, request);

        try (var response = service.execute(request)) {
            if (!response.isSuccessful()) {
                throw new Exception("Google userinfo request failed: HTTP " + response.getCode());
            }

            var json = JsonParser.parseString(response.getBody()).getAsJsonObject();

            if (json.has("verified_email") && !json.get("verified_email").getAsBoolean()) {
                throw new OAuthService.OAuthException("Google email is not verified");
            }

            var email = json.has("email") ? json.get("email").getAsString() : null;
            if (email == null || email.isBlank()) {
                throw new OAuthService.OAuthException("Google account has no email address");
            }

            var providerId = json.has("id")   ? json.get("id").getAsString()   : "";
            var name       = json.has("name") ? json.get("name").getAsString() : email.split("@")[0];
            var avatarUrl  = json.has("picture") && !json.get("picture").isJsonNull()
                    ? json.get("picture").getAsString() : null;

            LOGGER.debug("Google profile: id={}, email={}, name={}", providerId, email, name);

            return new BsonDocument()
                    .append("email",      new BsonString(email))
                    .append("name",       new BsonString(name))
                    .append("providerId", new BsonString(providerId))
                    .append("avatarUrl",  avatarUrl != null ? new BsonString(avatarUrl) : BsonNull.VALUE);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OAuth20Service buildService(String clientId, String clientSecret,
                                        String callbackUrl, String scope) {
        return new ServiceBuilder(clientId)
                .apiSecret(clientSecret)
                .defaultScope(scope)
                .callback(callbackUrl)
                .build(GoogleApi20.instance());
    }
}
