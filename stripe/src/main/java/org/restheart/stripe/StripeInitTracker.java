package org.restheart.stripe;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.restheart.plugins.Provider;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.RegisterPlugin;

/**
 * Tracks which databases have been initialized for Stripe subscriptions and products mode.
 *
 * <p>Always enabled — services inject this provider to check whether a database is ready
 * before serving requests, regardless of whether initialization happened at boot
 * ({@code stripeInitializer}) or on-demand ({@code stripeInitService}).
 */
@RegisterPlugin(
        name = "stripeInitTracker",
        description = "Tracks Stripe database initialization state",
        enabledByDefault = true)
public class StripeInitTracker implements Provider<StripeInitTracker> {

    private final Set<String> subscriptionsDbs = ConcurrentHashMap.newKeySet();
    private final Set<String> productsDbs = ConcurrentHashMap.newKeySet();

    public void markSubscriptionsInitialized(String dbName) {
        subscriptionsDbs.add(dbName);
    }

    public void markProductsInitialized(String dbName) {
        productsDbs.add(dbName);
    }

    public boolean isSubscriptionsInitialized(String dbName) {
        return subscriptionsDbs.contains(dbName);
    }

    public boolean isProductsInitialized(String dbName) {
        return productsDbs.contains(dbName);
    }

    @Override
    public StripeInitTracker get(PluginRecord<?> caller) {
        return this;
    }
}
