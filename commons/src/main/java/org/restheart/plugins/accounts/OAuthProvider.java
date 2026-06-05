package org.restheart.plugins.accounts;

import org.bson.BsonDocument;

/**
 * Service Provider Interface for OAuth 2.0 provider implementations.
 *
 * <p>Each provider handles the provider-specific parts of the OAuth flow:
 * building the authorization URL and exchanging the authorization code for
 * an access token, then fetching the user profile.
 *
 * <p>Implementations must also implement RESTHeart's {@code Initializer} and
 * register themselves with {@code OAuthService} via {@code @Inject("oauthService")}:
 *
 * <pre>{@code
 * @RegisterPlugin(name = "myOAuthProvider", description = "...")
 * public class MyOAuthProvider implements OAuthProvider, Initializer {
 *
 *     @Inject("oauthService")
 *     private OAuthService oauthService;
 *
 *     @Override
 *     public void init() {
 *         oauthService.registerProvider(this);
 *     }
 *
 *     @Override
 *     public String getProviderName() { return "myprovider"; }
 *
 *     @Override
 *     public String getAuthorizationUrl(String clientId, String clientSecret,
 *                                       String callbackUrl, String scope, String state) {
 *         // build and return the provider's authorization URL
 *     }
 *
 *     @Override
 *     public BsonDocument fetchUserProfile(String clientId, String clientSecret,
 *                                          String callbackUrl, String scope, String code)
 *             throws Exception {
 *         // exchange code for token, fetch profile, return BsonDocument
 *         // with at least: email, name, providerId (and optionally avatarUrl)
 *     }
 * }
 * }</pre>
 */
public interface OAuthProvider {

    /** Returns the provider name, e.g. {@code "google"} or {@code "github"}. */
    String getProviderName();

    /**
     * Builds and returns the authorization URL to redirect the user to.
     *
     * @param clientId     OAuth application client ID
     * @param clientSecret OAuth application client secret
     * @param callbackUrl  absolute callback URL registered in the provider console
     * @param scope        space-separated OAuth scopes
     * @param state        CSRF state token that must be included verbatim in the URL
     * @return the full authorization URL
     */
    String getAuthorizationUrl(String clientId, String clientSecret,
                               String callbackUrl, String scope, String state);

    /**
     * Exchanges the authorization code for an access token and fetches the
     * authenticated user's profile from the provider.
     *
     * @param clientId     OAuth application client ID
     * @param clientSecret OAuth application client secret
     * @param callbackUrl  absolute callback URL registered in the provider console
     * @param scope        space-separated OAuth scopes
     * @param code         the authorization code received in the OAuth callback
     * @return a {@link BsonDocument} with at least {@code email}, {@code name},
     *         {@code providerId} and (optionally) {@code avatarUrl}
     * @throws Exception if the token exchange or user info request fails
     */
    BsonDocument fetchUserProfile(String clientId, String clientSecret,
                                  String callbackUrl, String scope, String code) throws Exception;
}
