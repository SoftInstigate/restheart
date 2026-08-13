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
import org.restheart.plugins.PluginsRegistry;
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
 * <h2>⚠️ {@code accountsConfig} is looked up softly, not injected</h2>
 * <p>This class deliberately does <strong>not</strong> declare {@code @Inject("accountsConfig")}.
 * RESTHeart validates every declared {@code @Inject} target at startup and refuses to start
 * the plugin if the target provider does not exist or is disabled ({@code ProvidersChecker}) —
 * so a hard-declared injection would make {@code restheart-stripe} require
 * {@code restheart-accounts} to be present and enabled, defeating the point of the SPI (a
 * deployment without {@code restheart-accounts} is exactly the one that needs a custom
 * {@link SubscriptionOwnerProvider}).
 *
 * <p>Instead, {@code accountsConfig} is looked up at {@code @OnInit} time through the injected
 * {@link PluginsRegistry} — a lookup RESTHeart's dependency validation does not see, since it
 * is not a declared {@code @Inject} field. If {@code accountsConfig} is absent or disabled,
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

    private static final String ACCOUNTS_CONFIG_PLUGIN_NAME = "accountsConfig";

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("registry")
    private PluginsRegistry registry;

    private volatile SubscriptionOwnerProvider subscriptionOwnerProvider;

    @OnInit
    public void onInit() {
        var accountsConf = resolveAccountsConfigData();

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

    // ── Internal helpers ────────────────────────────────────────────────────

    /**
     * Looks up the {@code accountsConfig} provider through the plugin registry, without
     * declaring a hard {@code @Inject} dependency on it — see the class javadoc.
     *
     * @return the resolved {@link AccountsConfigData}, or {@code null} if {@code accountsConfig}
     *         is not registered, is disabled, or does not provide the expected type
     */
    private AccountsConfigData resolveAccountsConfigData() {
        if (registry == null) {
            return null;
        }

        for (var providerRecord : registry.getProviders()) {
            if (!ACCOUNTS_CONFIG_PLUGIN_NAME.equals(providerRecord.getName())) {
                continue;
            }
            if (!providerRecord.isEnabled()) {
                LOGGER.info("[stripe] accountsConfig plugin found but not enabled");
                return null;
            }

            // Provider.get(caller) is used by every provider we've inspected only for
            // caller-specific logging or customisation; AccountsConfig.get() ignores it
            // entirely and returns a fixed instance, so passing null here is safe.
            Object value = providerRecord.getInstance().get(null);
            if (value instanceof AccountsConfigData accountsConfigData) {
                return accountsConfigData;
            }

            LOGGER.warn("[stripe] accountsConfig plugin found but did not provide AccountsConfigData");
            return null;
        }

        return null;
    }
}
