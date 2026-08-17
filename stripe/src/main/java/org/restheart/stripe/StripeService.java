/*-
 * ========================LICENSE_START=================================
 * restheart-stripe
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.stripe;

import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.accounts.AccountsConfigData;
import org.restheart.plugins.stripe.SubscriptionOwnerProvider;
import org.restheart.plugins.stripe.SubscriptionOwnerProviderRegistry;
import org.restheart.stripe.spi.DefaultSubscriptionOwnerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

/**
 * RESTHeart {@link Provider} that manages the active {@link SubscriptionOwnerProvider}
 * and exposes it to other plugins via dependency injection.
 *
 * <p>By default the built-in {@link DefaultSubscriptionOwnerProvider} is used, backed by
 * the {@code restheart-accounts} team.
 *
 * <p>Custom providers can replace the default at startup:
 * <pre>{@code
 * @RegisterPlugin(name = "myBillingOwnerProvider", description = "...")
 * public class MyBillingOwnerProvider implements SubscriptionOwnerProvider, Initializer {
 *
 *     @Inject("stripeService")
 *     private StripeService stripeService;
 *
 *     @Override
 *     public void init() {
 *         stripeService.registerSubscriptionOwnerProvider(this);
 *     }
 *     // ... implement SubscriptionOwnerProvider methods ...
 * }
 * }</pre>
 *
 * <h2>⚠️ {@code accountsConfig} is an optional dependency</h2>
 * <p>This class declares {@code @Inject(value = "accountsConfig", required = false)}.
 * If {@code accountsConfig} is absent or disabled, the field is {@code null} and
 * {@link DefaultSubscriptionOwnerProvider} falls back to its own defaults
 * ({@value DefaultSubscriptionOwnerProvider#DEFAULT_TEAM_CLAIM_NAME} /
 * {@value DefaultSubscriptionOwnerProvider#DEFAULT_OWNERSHIP_ROLE}).
 */
@RegisterPlugin(
        name = "stripeService",
        description = "Manages the active SubscriptionOwnerProvider for restheart-stripe",
        enabledByDefault = false,
        priority = 20)
public class StripeService implements Provider<StripeService>, SubscriptionOwnerProviderRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeService.class);

    @Inject("mclient")
    private MongoClient mclient;

    @Inject(value = "accountsConfig", required = false)
    private AccountsConfigData accountsConf;

    private volatile SubscriptionOwnerProvider subscriptionOwnerProvider;

    @OnInit
    public void onInit() {
        this.subscriptionOwnerProvider = accountsConf != null
                ? new DefaultSubscriptionOwnerProvider(mclient, accountsConf.teamClaimName(), accountsConf.ownershipRole())
                : new DefaultSubscriptionOwnerProvider(mclient, null, null);

        LOGGER.info("StripeService initialized with DefaultSubscriptionOwnerProvider (accountsConfig {})",
                accountsConf != null ? "found" : "not found — using built-in defaults");
    }

    /**
     * Replaces the active {@link SubscriptionOwnerProvider} with a custom implementation.
     *
     * <p>Must be called from an {@code Initializer.init()} method so that it runs before
     * the server starts accepting requests.
     *
     * @param provider the custom provider to use; must not be {@code null}
     * @throws IllegalArgumentException if {@code provider} is {@code null}
     */
    @Override
    public void registerSubscriptionOwnerProvider(SubscriptionOwnerProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        this.subscriptionOwnerProvider = provider;
        LOGGER.info("Custom SubscriptionOwnerProvider registered: {}", provider.getClass().getName());
    }

    /**
     * @return the currently active {@link SubscriptionOwnerProvider} — the custom provider
     *         if one has been registered, or {@link DefaultSubscriptionOwnerProvider} otherwise.
     *         Never {@code null}.
     */
    public SubscriptionOwnerProvider getSubscriptionOwnerProvider() {
        return subscriptionOwnerProvider;
    }

    /**
     * Also exposed for {@code stripeInitializer}, to create the {@code stripe_customer_id}
     * index when the active provider is the default one — a custom provider owns its own
     * indexing.
     */
    public DefaultSubscriptionOwnerProvider defaultProviderOrNull() {
        return subscriptionOwnerProvider instanceof DefaultSubscriptionOwnerProvider dsop ? dsop : null;
    }

    @Override
    public StripeService get(PluginRecord<?> caller) {
        return this;
    }
}
