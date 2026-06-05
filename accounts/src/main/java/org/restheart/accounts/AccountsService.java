package org.restheart.accounts;

import com.mongodb.client.MongoClient;
import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.spi.DefaultMembershipProvider;
import org.restheart.plugins.accounts.MembershipProvider;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RESTHeart {@link Provider} that manages the active {@link MembershipProvider}
 * and exposes it to other plugins via dependency injection.
 *
 * <p>By default the built-in {@link DefaultMembershipProvider} is used, which
 * preserves the {@code tenant}/{@code tenants} schema from restheart-accounts 9.4.
 *
 * <p>Custom providers can replace the default at startup:
 * <pre>{@code
 * @RegisterPlugin(name = "myMembershipProvider", description = "...")
 * public class MyMembershipProvider implements MembershipProvider, Initializer {
 *
 *     @Inject("accountsService")
 *     private AccountsService accountsService;
 *
 *     @Override
 *     public void init() {
 *         accountsService.registerMembershipProvider(this);
 *     }
 *     // ... implement MembershipProvider methods ...
 * }
 * }</pre>
 *
 * <p>Services inject this provider as:
 * <pre>{@code
 * @Inject("accountsService")
 * private AccountsService accountsService;
 * }</pre>
 * and access the active provider via {@link #getMembershipProvider()}.
 */
@RegisterPlugin(
    name             = "accountsService",
    description      = "Manages the active MembershipProvider for restheart-accounts",
    enabledByDefault = false,
    priority         = 25   // after accountsConfig (priority 20) so conf is ready at @OnInit
)
public class AccountsService implements Provider<AccountsService> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountsService.class);

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    private volatile MembershipProvider membershipProvider;

    @OnInit
    public void onInit() {
        // Install the default provider; custom providers can replace it later via
        // registerMembershipProvider() during their Initializer.init() call.
        this.membershipProvider = new DefaultMembershipProvider(mclient, conf.db());
        LOGGER.info("AccountsService initialized with DefaultMembershipProvider");
    }

    /**
     * Replaces the active {@link MembershipProvider} with a custom implementation.
     *
     * <p>Must be called from an {@code Initializer.init()} method so that it runs
     * before the server starts accepting requests.
     *
     * @param provider the custom provider to use; must not be {@code null}
     */
    public void registerMembershipProvider(MembershipProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider must not be null");
        this.membershipProvider = provider;
        LOGGER.info("Custom MembershipProvider registered: {}", provider.getClass().getName());
    }

    /**
     * Returns the currently active {@link MembershipProvider}.
     * This is the custom provider if one has been registered, or the
     * {@link DefaultMembershipProvider} otherwise.
     */
    public MembershipProvider getMembershipProvider() {
        return membershipProvider;
    }

    @Override
    public AccountsService get(PluginRecord<?> caller) {
        return this;
    }
}
