package org.restheart.stripe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.restheart.plugins.stripe.ProductsConfig;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.plugins.stripe.SubscriptionsConfig;
import org.restheart.stripe.spi.DefaultSubscriptionOwnerProvider;
import org.restheart.stripe.util.TeamRepository;

import com.mongodb.ServerAddress;
import com.mongodb.ServerCursor;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

/**
 * The cache-miss-recheck behavior is the whole point of this class — see its javadoc: after a
 * process restart the in-memory cache is empty for every tenant, and a naive "miss means never
 * initialized" would lock every tenant on a multi-tenant node out of {@code /stripe/checkout}
 * etc. until something happened to re-run {@code initSubscriptions()}. These tests exercise that
 * recheck path directly, without a real MongoDB.
 */
@DisplayName("StripeInitTracker Tests")
class StripeInitTrackerTest {

    private MongoClient mclient;
    private MongoDatabase db;
    @SuppressWarnings("unchecked")
    private MongoCollection<Document> collection;
    @SuppressWarnings("unchecked")
    private ListIndexesIterable<Document> indexesIterable;
    private StripeService stripeService;
    private StripeInitTracker tracker;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        mclient = mock(MongoClient.class);
        db = mock(MongoDatabase.class);
        collection = mock(MongoCollection.class);
        indexesIterable = mock(ListIndexesIterable.class);
        stripeService = mock(StripeService.class);

        when(mclient.getDatabase(any())).thenReturn(db);
        when(db.getCollection(any(), eq(Document.class))).thenReturn(collection);
        when(collection.listIndexes()).thenReturn(indexesIterable);

        tracker = new StripeInitTracker();
        setField(tracker, "mclient", mclient);
        setField(tracker, "conf", subscriptionsOnlyConf());
        setField(tracker, "stripeService", stripeService);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = StripeInitTracker.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static StripeConfigData subscriptionsOnlyConf() {
        var subscriptions = new SubscriptionsConfig(true, Map.of(), "free", 0, "", "", "", Map.of());
        return new StripeConfigData("sk_test_x", "whsec_x", "restheart", "teams", subscriptions, null);
    }

    private static StripeConfigData confWithProducts(ProductsConfig products) {
        var subscriptions = new SubscriptionsConfig(true, Map.of(), "free", 0, "", "", "", Map.of());
        return new StripeConfigData("sk_test_x", "whsec_x", "restheart", "teams", subscriptions, products);
    }

    private static ProductsConfig productsConf(String ordersCollection) {
        return new ProductsConfig(true, true, "catalog", ordersCollection, "transactions", null,
                "eur", "_id", true, true, "", "", 60, 50, 100, true, List.of(), Map.of());
    }

    /** {@code ListIndexesIterable.iterator()} returns a {@code MongoCursor}, not a plain {@code Iterator} — wrap a list for stubbing. */
    private static MongoCursor<Document> cursorOf(List<Document> docs) {
        var it = docs.iterator();
        return new MongoCursor<>() {
            @Override public void close() { }
            @Override public boolean hasNext() { return it.hasNext(); }
            @Override public Document next() { return it.next(); }
            @Override public int available() { return 0; }
            @Override public Document tryNext() { return it.hasNext() ? it.next() : null; }
            @Override public ServerCursor getServerCursor() { return null; }
            @Override public ServerAddress getServerAddress() { return new ServerAddress(); }
        };
    }

    // ── Subscriptions ────────────────────────────────────────────────────────

    @Test
    @DisplayName("cached true short-circuits — no Mongo call at all")
    void cachedTrueShortCircuits() {
        tracker.markSubscriptionsInitialized("acme");

        assertTrue(tracker.isSubscriptionsInitialized("acme"));
        // No stubbing was needed for defaultProviderOrNull()/listIndexes() to reach this
        // assertion — if the fast path fell through to the Mongo check, the mocks' default
        // (null-returning) behavior would have thrown a NullPointerException before we got here.
    }

    @Test
    @DisplayName("cache miss, custom SubscriptionOwnerProvider active -> true, no Mongo call, and cached")
    void cacheMissCustomProvider() {
        when(stripeService.defaultProviderOrNull()).thenReturn(null);

        assertTrue(tracker.isSubscriptionsInitialized("acme"));
        assertTrue(tracker.isSubscriptionsInitialized("acme"), "second call must hit the now-warm cache");
    }

    @Test
    @DisplayName("cache miss, default provider, index already exists in Mongo -> true, and cached")
    void cacheMissIndexExists() {
        when(stripeService.defaultProviderOrNull()).thenReturn(mock(DefaultSubscriptionOwnerProvider.class));
        when(indexesIterable.iterator()).thenReturn(
                cursorOf(List.of(new Document("name", TeamRepository.STRIPE_CUSTOMER_ID_INDEX))));

        assertTrue(tracker.isSubscriptionsInitialized("acme"));
    }

    @Test
    @DisplayName("cache miss, default provider, no matching index -> false, not cached")
    void cacheMissIndexMissing() {
        when(stripeService.defaultProviderOrNull()).thenReturn(mock(DefaultSubscriptionOwnerProvider.class));
        when(indexesIterable.iterator()).thenReturn(cursorOf(List.of()));

        assertFalse(tracker.isSubscriptionsInitialized("acme"));

        // A negative result must not be cached — the next request (once the tenant is
        // genuinely initialized) has to re-check, not stay wrongly locked out forever.
        when(indexesIterable.iterator()).thenReturn(
                cursorOf(List.of(new Document("name", TeamRepository.STRIPE_CUSTOMER_ID_INDEX))));
        assertTrue(tracker.isSubscriptionsInitialized("acme"));
    }

    @Test
    @DisplayName("Mongo failure during recheck -> false, fail-safe, not thrown")
    void mongoFailureIsSwallowed() {
        when(stripeService.defaultProviderOrNull()).thenReturn(mock(DefaultSubscriptionOwnerProvider.class));
        when(collection.listIndexes()).thenThrow(new RuntimeException("connection refused"));

        assertFalse(tracker.isSubscriptionsInitialized("acme"));
    }

    @Test
    @DisplayName("stripeConfig not injected (not enabled on this node) -> true, no Mongo call, and cached")
    void stripeConfigNotEnabled() throws Exception {
        setField(tracker, "conf", null);

        assertTrue(tracker.isSubscriptionsInitialized("acme"));
        assertTrue(tracker.isProductsInitialized("acme"));
    }

    @Test
    @DisplayName("stripeService not injected (not enabled on this node) -> true, no Mongo call, and cached")
    void stripeServiceNotEnabled() throws Exception {
        setField(tracker, "stripeService", null);

        assertTrue(tracker.isSubscriptionsInitialized("acme"));
    }

    @Test
    @DisplayName("two different databases are tracked independently")
    void independentPerDatabase() {
        tracker.markSubscriptionsInitialized("acme");

        assertTrue(tracker.isSubscriptionsInitialized("acme"));

        when(stripeService.defaultProviderOrNull()).thenReturn(mock(DefaultSubscriptionOwnerProvider.class));
        when(indexesIterable.iterator()).thenReturn(cursorOf(List.of()));
        assertFalse(tracker.isSubscriptionsInitialized("globex"));
    }

    // ── Products ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("products: not configured at all -> false, no Mongo call")
    void productsNotConfigured() throws Exception {
        setField(tracker, "conf", subscriptionsOnlyConf()); // products == null

        assertFalse(tracker.isProductsInitialized("acme"));
    }

    @Test
    @DisplayName("products: configured, marker index exists -> true, cached")
    void productsIndexExists() throws Exception {
        setField(tracker, "conf", confWithProducts(productsConf("orders")));
        when(indexesIterable.iterator()).thenReturn(
                cursorOf(List.of(new Document("name", StripeInitService.ORDERS_SESSION_ID_INDEX))));

        assertTrue(tracker.isProductsInitialized("acme"));
    }

    @Test
    @DisplayName("products: configured, no marker index -> false")
    void productsIndexMissing() throws Exception {
        setField(tracker, "conf", confWithProducts(productsConf("orders")));
        when(indexesIterable.iterator()).thenReturn(cursorOf(List.of()));

        assertFalse(tracker.isProductsInitialized("acme"));
    }
}
