package org.restheart.stripe.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.BsonDocument;
import org.bson.BsonString;
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

    // ── metadata ─────────────────────────────────────────────────────────────

    @Test
    void metadata_absentIsEmptyRatherThanNull() {
        // Every order line carries the field, so a line without metadata and a line whose
        // metadata were dropped look the same in the database — which is the point.
        assertEquals(new BsonDocument(),
                OrdersCheckoutInterceptor.metadataOf(BsonDocument.parse("{}"), "item 'x'"));
    }

    @Test
    void metadata_stringsPassThroughUntouched() {
        var doc = BsonDocument.parse("""
                { "metadata": { "colour": "yellow", "size": "L" } }
                """);

        var metadata = OrdersCheckoutInterceptor.metadataOf(doc, "item 'x'");

        assertEquals("yellow", metadata.getString("colour").getValue());
        assertEquals("L", metadata.getString("size").getValue());
    }

    @Test
    void metadata_mustBeAnObject() {
        var doc = BsonDocument.parse("""
                { "metadata": [ { "key": "colour", "value": "yellow" } ] }
                """);

        var e = assertThrows(IllegalArgumentException.class,
                () -> OrdersCheckoutInterceptor.metadataOf(doc, "item 'tee'"));
        assertTrue(e.getMessage().contains("item 'tee'"), e.getMessage());
    }

    @Test
    void metadata_valuesMustBeStrings() {
        // Stripe's metadata are strings. Accepting a number here would mean deciding how to
        // render it at the boundary, which is a decision nobody asked us to make.
        var doc = BsonDocument.parse("""
                { "metadata": { "size": 42 } }
                """);

        var e = assertThrows(IllegalArgumentException.class,
                () -> OrdersCheckoutInterceptor.metadataOf(doc, "item 'tee'"));
        assertTrue(e.getMessage().contains("size"), e.getMessage());
    }

    @Test
    void metadata_refusesMoreKeysThanStripeAccepts() {
        var metadata = new BsonDocument();
        for (var i = 0;i < 51;i++) {
            metadata.append("k" + i, new BsonString("v"));
        }

        var e = assertThrows(IllegalArgumentException.class,
                () -> OrdersCheckoutInterceptor.metadataOf(
                        new BsonDocument("metadata", metadata), "order"));
        assertTrue(e.getMessage().contains("50"), e.getMessage());
    }

    @Test
    void metadata_refusesAValueLongerThanStripeAccepts() {
        var doc = new BsonDocument("metadata",
                new BsonDocument("note", new BsonString("x".repeat(501))));

        var e = assertThrows(IllegalArgumentException.class,
                () -> OrdersCheckoutInterceptor.metadataOf(doc, "order"));
        assertTrue(e.getMessage().contains("note"), e.getMessage());
    }

    @Test
    void metadata_theMessageSaysWhichItem() {
        // A cart of five items and one bad value: "metadata too long" would send the developer
        // looking through all five.
        var doc = new BsonDocument("metadata",
                new BsonDocument("k".repeat(41), new BsonString("v")));

        var e = assertThrows(IllegalArgumentException.class,
                () -> OrdersCheckoutInterceptor.metadataOf(doc, "item 'tee-classic/yellow-l'"));
        assertTrue(e.getMessage().contains("tee-classic/yellow-l"), e.getMessage());
    }
}
