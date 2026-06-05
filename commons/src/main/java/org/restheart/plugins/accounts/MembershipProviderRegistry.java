package org.restheart.plugins.accounts;

/**
 * Thin registry interface that allows {@link MembershipProvider} implementations
 * to register themselves without depending on the {@code restheart-accounts} module.
 *
 * <p>{@code AccountsService} (in {@code restheart-accounts}) implements this interface
 * and is exposed as a provider named {@code "accountsService"}. Custom membership
 * providers can inject it using only {@code restheart-commons}:
 *
 * <pre>{@code
 * @RegisterPlugin(name = "myMembershipProvider", description = "...")
 * public class MyMembershipProvider implements MembershipProvider, Initializer {
 *
 *     @Inject("accountsService")
 *     private MembershipProviderRegistry accountsService;
 *
 *     @Override
 *     public void init() {
 *         accountsService.registerMembershipProvider(this);
 *     }
 *
 *     // ... implement MembershipProvider methods ...
 * }
 * }</pre>
 */
public interface MembershipProviderRegistry {
    /**
     * Replaces the active {@link MembershipProvider} with a custom implementation.
     * Must be called from an {@code Initializer.init()} method.
     *
     * @param provider the provider to use; must not be {@code null}
     */
    void registerMembershipProvider(MembershipProvider provider);
}
