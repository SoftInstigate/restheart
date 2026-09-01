package org.restheart.stripe;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.Document;
import org.restheart.plugins.Inject;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.stripe.util.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

/**
 * Tracks which databases have been initialized for Stripe subscriptions and products mode.
 *
 * <p>Always enabled — services inject this provider to check whether a database is ready
 * before serving requests, regardless of whether initialization happened at boot
 * ({@code stripeInitializer}) or on-demand ({@code stripeInitService}).
 *
 * <h2>The cache is in-memory, not persisted</h2>
 * <p>It resets on every process restart. On a single-tenant deployment this is harmless —
 * {@code stripeInitializer} unconditionally re-initializes the one static database at every
 * boot, so the cache is always warm again before the server accepts a request. On a
 * multi-tenant deployment (many databases, unknown at boot — see
 * {@code restheart-website/docs/stripe/multi-tenancy.adoc}) nothing re-runs {@code initSubscriptions()}
 * for every tenant after a restart; a naive cache-miss-means-"not initialized" would then lock
 * every tenant on that node out of {@code /stripe/checkout} etc. until the deployment layer
 * happened to call it again.
 *
 * <p>So a cache miss does not mean "never initialized" — it means "this process doesn't
 * remember." {@link #isSubscriptionsInitialized} treats a cached {@code true} as authoritative
 * (no repeated work for the common case), but on a miss it checks the actual source of truth —
 * does the index {@code initSubscriptions()} creates already exist in MongoDB — before
 * concluding "not initialized". A positive result is then cached, so the check runs at most
 * once per database per process, not once per request.
 */
@RegisterPlugin(
        name = "stripeInitTracker",
        description = "Tracks Stripe database initialization state",
        enabledByDefault = true,
        // Must run after MongoClientProvider ("mclient", priority 11) has had its own @OnInit
        // called — RESTHeart instantiates/injects providers in ascending priority order, and
        // MongoClientProvider.get() throws IllegalStateException("not initialized") if called
        // before its @OnInit runs. Default priority (10) sorts before 11 and hits exactly that
        // — same reason StripeService uses priority = 20 for the same @Inject("mclient").
        priority = 20)
public class StripeInitTracker implements Provider<StripeInitTracker> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeInitTracker.class);

    @Inject("mclient")
    private MongoClient mclient;

    // Always enabled by default (see class javadoc), unlike every other stripe plugin — so on
    // a node that never enables stripeConfig/stripeService at all, this provider must still
    // load cleanly rather than crash boot on a hard-required dependency that was never turned on.
    @Inject(value = "stripeConfig", required = false)
    private StripeConfigData conf;

    @Inject(value = "stripeService", required = false)
    private StripeService stripeService;

    private final Set<String> subscriptionsDbs = ConcurrentHashMap.newKeySet();
    private final Set<String> productsDbs = ConcurrentHashMap.newKeySet();

    public void markSubscriptionsInitialized(String dbName) {
        subscriptionsDbs.add(dbName);
    }

    public void markProductsInitialized(String dbName) {
        productsDbs.add(dbName);
    }

    /**
     * @return {@code true} if this process has marked {@code dbName} initialized, or — on a
     *         cache miss — if the {@code stripe_customer_id} index already exists there (see
     *         class javadoc). A custom {@code SubscriptionOwnerProvider} has no such index to
     *         check; for that case a miss is treated as "nothing to wait on" and cached
     *         {@code true} immediately, matching what {@code StripeInitService.initSubscriptions()}
     *         does for the same case.
     */
    public boolean isSubscriptionsInitialized(String dbName) {
        if (subscriptionsDbs.contains(dbName)) {
            return true;
        }

        if (conf == null || stripeService == null) {
            // stripeConfig/stripeService aren't enabled on this node at all — no stripe
            // service could be enabled either (they all hard-require stripeConfig), so
            // nothing will ever actually gate on this. Nothing to wait on.
            subscriptionsDbs.add(dbName);
            return true;
        }

        if (stripeService.defaultProviderOrNull() == null) {
            subscriptionsDbs.add(dbName);
            return true;
        }

        if (indexExists(dbName, conf.teamsCollection(), TeamRepository.STRIPE_CUSTOMER_ID_INDEX)) {
            subscriptionsDbs.add(dbName);
            return true;
        }

        return false;
    }

    /**
     * @return {@code true} if this process has marked {@code dbName} initialized, or — on a
     *         cache miss — if one of the indexes {@code StripeInitService.initProducts()}
     *         creates already exists there. {@code false} if products mode isn't configured at
     *         all — there is nothing to check.
     */
    public boolean isProductsInitialized(String dbName) {
        if (productsDbs.contains(dbName)) {
            return true;
        }

        if (conf == null) {
            // stripeConfig isn't enabled on this node at all — see isSubscriptionsInitialized.
            productsDbs.add(dbName);
            return true;
        }

        if (conf.products() == null) {
            return false;
        }

        if (indexExists(dbName, conf.products().ordersCollection(), StripeInitService.ORDERS_SESSION_ID_INDEX)) {
            productsDbs.add(dbName);
            return true;
        }

        return false;
    }

    /**
     * A failure here (unreachable Mongo, etc.) must not be mistaken for "initialized" — it
     * returns {@code false}, same as a genuine cache miss, so the caller's own error handling
     * (not this class's) decides what to do about it.
     */
    private boolean indexExists(String dbName, String collectionName, String indexName) {
        try {
            for (var index : mclient.getDatabase(dbName).getCollection(collectionName, Document.class).listIndexes()) {
                if (indexName.equals(index.getString("name"))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            LOGGER.warn("[stripe] failed to check index '{}' on '{}.{}': {}",
                    indexName, dbName, collectionName, e.getMessage());
            return false;
        }
    }

    @Override
    public StripeInitTracker get(PluginRecord<?> caller) {
        return this;
    }
}
