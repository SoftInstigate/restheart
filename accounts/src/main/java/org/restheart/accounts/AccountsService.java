package org.restheart.accounts;

import com.mongodb.client.MongoClient;
import org.restheart.plugins.accounts.AccountsConfigData;
import org.restheart.accounts.spi.DefaultMembershipProvider;
import org.restheart.accounts.util.RequestOverrides;
import org.restheart.exchange.ServiceRequest;
import org.restheart.plugins.accounts.MembershipProvider;
import org.restheart.plugins.accounts.MembershipProviderRegistry;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.schema.JsonSchemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RESTHeart {@link Provider} that manages the active {@link MembershipProvider}
 * and exposes it to other plugins via dependency injection.
 *
 * <p>By default the built-in {@link DefaultMembershipProvider} is used, which
 * preserves the {@code team}/{@code teams} schema from restheart-accounts 9.4.
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
public class AccountsService implements Provider<AccountsService>, MembershipProviderRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountsService.class);

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("json-schemas")
    private JsonSchemas jsonSchemas;

    private volatile MembershipProvider membershipProvider;

    @OnInit
    public void onInit() {
        // Install the default provider; custom providers can replace it later via
        // registerMembershipProvider() during their Initializer.init() call.
        this.membershipProvider = new DefaultMembershipProvider(mclient, conf.db(), conf.usersCollection(), conf.ownershipRole(), conf.defaultRole(), jsonSchemas);
        LOGGER.info("AccountsService initialized with DefaultMembershipProvider");
    }

    /**
     * Replaces the active {@link MembershipProvider} with a custom implementation.
     *
     * <p>Must be called from an {@code Initializer.init()} method so that it runs
     * before the server starts accepting requests.
     *
     * @param provider the custom provider to use; must not be {@code null}
     * @throws IllegalArgumentException if {@code provider} is {@code null}
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
     *
     * @return the active {@link MembershipProvider}; never {@code null}
     */
    public MembershipProvider getMembershipProvider() {
        return membershipProvider;
    }

    /**
     * Returns the active {@link MembershipProvider} configured for the current request.
     *
     * <p>When the active provider is the built-in {@link DefaultMembershipProvider},
     * a new instance is created using the per-request database resolved by
     * {@link RequestOverrides#db(ServiceRequest, AccountsConfigData)} — which reads
     * the {@code override-users-db} attached parameter (set by {@code AuthDbResolver})
     * and falls back to {@code mongoRealmAuthenticator/users-db}. {@code MongoRealmAuthenticator}
     * honours the same attached parameter, so authentication follows the same database.
     * This ensures that on shared deployments the correct per-team MongoDB database is used.
     *
     * <p>Custom providers registered via {@link #registerMembershipProvider(MembershipProvider)}
     * are returned as-is; they are responsible for their own database resolution.
     *
     * @param req the current service request
     * @return the active {@link MembershipProvider} for this request; never {@code null}
     */
    public MembershipProvider getMembershipProvider(ServiceRequest<?> req) {
        if (membershipProvider instanceof DefaultMembershipProvider) {
            return new DefaultMembershipProvider(
                mclient,
                RequestOverrides.db(req, conf),
                RequestOverrides.usersCollection(req, conf),
                RequestOverrides.ownershipRole(req, conf),
                RequestOverrides.defaultRole(req, conf),
                jsonSchemas
            );
        }
        return membershipProvider;
    }

    @Override
    public AccountsService get(PluginRecord<?> caller) {
        return this;
    }
}
