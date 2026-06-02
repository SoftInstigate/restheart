package org.restheart.accounts.oauth;

import org.bson.BsonDocument;

/**
 * Marker interface for OAuth 2.0 provider implementations.
 *
 * <p>Each provider handles the provider-specific parts of the OAuth flow:
 * building the {@code OAuth20Service} and fetching the user profile after
 * the authorization code has been exchanged for an access token.
 *
 * <p>Implementations must also implement RESTHeart's {@code Initializer} and
 * register themselves with {@link OAuthService} via {@code @Inject("oauthService")}.
 */
public interface OAuthProvider {

    /** Returns the provider name, e.g. {@code "google"}. */
    String getProviderName();

    /**
     * Builds the ScribeJava {@code OAuth20Service} for this provider.
     *
     * @param clientId     OAuth application client ID
     * @param clientSecret OAuth application client secret
     * @param callbackUrl  absolute callback URL registered in the provider console
     * @param scope        space-separated OAuth scopes
     */
    com.github.scribejava.core.oauth.OAuth20Service buildService(
            String clientId,
            String clientSecret,
            String callbackUrl,
            String scope);

    /**
     * Fetches the authenticated user's profile from the provider.
     *
     * @param service     the OAuth20Service (used to sign requests)
     * @param accessToken the access token obtained during the callback
     * @return a {@link BsonDocument} with at least {@code email}, {@code name},
     *         {@code providerId} and (optionally) {@code avatarUrl}
     * @throws Exception if the user info request fails or the response is invalid
     */
    BsonDocument fetchUserProfile(
            com.github.scribejava.core.oauth.OAuth20Service service,
            String accessToken) throws Exception;
}
