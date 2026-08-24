package org.restheart.stripe.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class OrdersCheckoutInterceptorTest {

    private static final ObjectId ORDER_ID = new ObjectId("507f1f77bcf86cd799439011");
    private static final String SECRET = "a3f1c0de";

    @Test
    void interpolateOrderRef_substitutesBothPlaceholders() {
        var url = OrdersCheckoutInterceptor.interpolateOrderRef(
                "https://shop.example.com/order?order={ORDER_ID}&secret={ORDER_SECRET}", ORDER_ID, SECRET);

        assertEquals("https://shop.example.com/order?order=507f1f77bcf86cd799439011&secret=a3f1c0de", url);
    }

    @Test
    void interpolateOrderRef_worksInTheFragment_whereTheSecretBelongs() {
        // The documented placement: a fragment never reaches the server, so the
        // secret stays out of access logs and Referer headers.
        var url = OrdersCheckoutInterceptor.interpolateOrderRef(
                "https://shop.example.com/order#order={ORDER_ID}&secret={ORDER_SECRET}", ORDER_ID, SECRET);

        assertEquals("https://shop.example.com/order#order=507f1f77bcf86cd799439011&secret=a3f1c0de", url);
    }

    @Test
    void interpolateOrderRef_leavesAUrlWithoutPlaceholdersAlone() {
        // Substitution is opt-in: every deployment configured before this existed
        // must keep behaving exactly as it did.
        var configured = "https://shop.example.com/order?session={CHECKOUT_SESSION_ID}";

        assertEquals(configured, OrdersCheckoutInterceptor.interpolateOrderRef(configured, ORDER_ID, SECRET));
    }

    @Test
    void interpolateOrderRef_doesNotDisturbStripesOwnPlaceholder() {
        // Stripe substitutes {CHECKOUT_SESSION_ID} itself, after we hand the URL
        // over — ours must pass through untouched alongside it.
        var url = OrdersCheckoutInterceptor.interpolateOrderRef(
                "https://shop.example.com/order?session={CHECKOUT_SESSION_ID}#order={ORDER_ID}", ORDER_ID, SECRET);

        assertTrue(url.contains("{CHECKOUT_SESSION_ID}"), "Stripe's placeholder must survive");
        assertTrue(url.contains("order=507f1f77bcf86cd799439011"));
    }

    @Test
    void interpolateOrderRef_substitutesEachPlaceholderEverywhereItAppears() {
        var url = OrdersCheckoutInterceptor.interpolateOrderRef(
                "https://x/{ORDER_ID}/receipt#order={ORDER_ID}", ORDER_ID, SECRET);

        assertEquals("https://x/507f1f77bcf86cd799439011/receipt#order=507f1f77bcf86cd799439011", url);
    }

    @Test
    void interpolateOrderRef_urlEncodesTheSecret() {
        // Secrets are hex today, so this changes nothing in practice — it is here
        // so the URL stays well-formed if that representation ever changes.
        var url = OrdersCheckoutInterceptor.interpolateOrderRef(
                "https://x/order#secret={ORDER_SECRET}", ORDER_ID, "a b&c=d");

        assertEquals("https://x/order#secret=a+b%26c%3Dd", url);
    }

    @Test
    void interpolateOrderRef_toleratesAnUnsetSuccessUrl() {
        // `success-url` defaults to "" in StripeConfig, and Stripe rejects the
        // session later — that failure should stay Stripe's, not become an NPE here.
        assertEquals("", OrdersCheckoutInterceptor.interpolateOrderRef("", ORDER_ID, SECRET));
        assertNull(OrdersCheckoutInterceptor.interpolateOrderRef(null, ORDER_ID, SECRET));
    }
}
