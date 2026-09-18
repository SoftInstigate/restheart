package org.restheart.plugins.accounts;

import java.util.List;

import org.restheart.exchange.ServiceRequest;

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
     * The providers this request could actually sign in with: registered, and configured for
     * whoever the request belongs to. A deployment configures them per tenant, so the answer is
     * per request and not a property of the instance.
     *
     * <p>Whoever offers a "continue with ..." button asks here first, rather than assuming that a
     * registered provider is a usable one. The default answers with nothing, so an implementation
     * that predates this method offers no buttons instead of broken ones.
     *
     * @param request the request being served, to resolve per-tenant configuration; may be
     *                {@code null}, and then only static configuration is considered
     * @return the provider names, lower-case, in no particular order
     */
    default List<String> availableProviders(ServiceRequest<?> request) {
        return List.of();
    }

    /**
     * Registers an {@link OAuthProvider} implementation.
     * Must be called from an {@code Initializer.init()} method.
     *
     * @param provider the provider to register; must not be {@code null}
     */
    void registerProvider(OAuthProvider provider);
}
