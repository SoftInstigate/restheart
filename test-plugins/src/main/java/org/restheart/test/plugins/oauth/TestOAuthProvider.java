package org.restheart.test.plugins.oauth;

import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.accounts.OAuthProvider;
import org.restheart.plugins.accounts.OAuthProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mock OAuth 2.0 provider for integration tests.
 *
 * <p>Bypasses all external HTTP calls. The authorization URL is a local fake URL
 * that carries the CSRF state as a query parameter so Karate tests can extract it.
 *
 * <p>The authorization code encodes the target email:
 * <pre>
 *   code = "test-" + email
 *   e.g.  "test-invited-user@example.com"
 * </pre>
 *
 * <p>Configuration ({@code restheart.yml}):
 * <pre>{@code
 * oauthConfig:
 *   enabled: true
 *   api-base-url: http://localhost:8080
 *   frontend-success-url: http://localhost:4200/app
 *   frontend-error-url:   http://localhost:4200/login?error=oauth_error
 *   providers:
 *     test:
 *       enabled: true
 *       client-id:     test-client
 *       client-secret: test-secret
 *       scope:         email
 * }</pre>
 */
@RegisterPlugin(
        name             = "testOAuthProvider",
        description      = "Mock OAuth provider for integration tests",
        enabledByDefault = false)
public class TestOAuthProvider implements OAuthProvider, Initializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestOAuthProvider.class);
    private static final String PROVIDER_NAME = "test";
    /** Code prefix used by Karate tests: {@code "test-" + email}. */
    public  static final String CODE_PREFIX   = "test-";

    @Inject("oauthService")
    private OAuthProviderRegistry oauthService;

    @Override
    public void init() {
        if (oauthService != null) {
            oauthService.registerProvider(this);
            LOGGER.info("TestOAuthProvider registered");
        }
    }

    @Override
    public String getProviderName() { return PROVIDER_NAME; }

    /**
     * Returns a local fake authorization URL. The CSRF {@code state} is embedded
     * as a query parameter so tests can extract it from the {@code Location} header.
     *
     * <p>Example: {@code http://localhost/test-oauth/authorize?state=XXXX&client_id=test-client}
     */
    @Override
    public String getAuthorizationUrl(String clientId, String clientSecret,
                                      String callbackUrl, String scope, String state) {
        return "http://localhost/test-oauth/authorize?state=" + state + "&client_id=" + clientId;
    }

    /**
     * Returns a hardcoded profile derived from the authorization code.
     *
     * <p>The code must start with {@value CODE_PREFIX} followed by the user email.
     * Example: {@code "test-alice@example.com"} → profile with {@code email: alice@example.com}.
     *
     * @throws Exception if the code does not match the expected format
     */
    @Override
    public BsonDocument fetchUserProfile(String clientId, String clientSecret,
                                         String callbackUrl, String scope, String code)
            throws Exception {
        if (code == null || !code.startsWith(CODE_PREFIX)) {
            throw new Exception("TestOAuthProvider: invalid code format '" + code
                    + "' — expected '" + CODE_PREFIX + "<email>'");
        }
        var email = code.substring(CODE_PREFIX.length());
        LOGGER.debug("TestOAuthProvider: returning profile for <{}>", email);
        return new BsonDocument()
                .append("email",      new BsonString(email))
                .append("name",       new BsonString("Test User"))
                .append("providerId", new BsonString("test-" + Math.abs(email.hashCode())))
                .append("avatarUrl",  BsonNull.VALUE);
    }
}
