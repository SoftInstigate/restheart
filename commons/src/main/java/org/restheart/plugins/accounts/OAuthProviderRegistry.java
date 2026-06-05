package org.restheart.plugins.accounts;

/**
 * Thin registry interface that allows {@link OAuthProvider} implementations
 * to register themselves without depending on the {@code restheart-accounts} module.
 *
 * <p>{@code OAuthService} (in {@code restheart-accounts}) implements this interface
 * and is exposed as a provider named {@code "oauthService"}. Custom OAuth providers
 * can inject it using only {@code restheart-commons}:
 *
 * <pre>{@code
 * @RegisterPlugin(name = "myOAuthProvider", description = "...")
 * public class MyOAuthProvider implements OAuthProvider, Initializer {
 *
 *     @Inject("oauthService")
 *     private OAuthProviderRegistry oauthService;
 *
 *     @Override
 *     public void init() {
 *         oauthService.registerProvider(this);
 *     }
 *
 *     // ... implement OAuthProvider methods ...
 * }
 * }</pre>
 */
public interface OAuthProviderRegistry {
    /**
     * Registers an {@link OAuthProvider} implementation.
     * Must be called from an {@code Initializer.init()} method.
     *
     * @param provider the provider to register; must not be {@code null}
     */
    void registerProvider(OAuthProvider provider);
}
