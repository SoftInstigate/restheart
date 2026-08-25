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
package org.restheart.stripe.products;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.types.ObjectId;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.stripe.ProductsConfig;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.stripe.util.RequestOverrides;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;

/**
 * Intercepts {@code POST /{orders-collection}} to create an order from a cart.
 *
 * <p>Replaces the request content with a full order document. The MongoDB handler
 * then inserts this document into the collection.
 *
 * <p>The client sends {@code {items: [{productId, quantity}]}} (and optionally
 * {@code email} for guests). The interceptor:
 * <ol>
 *   <li>Validates the request (only {@code items} and {@code email} allowed)</li>
 *   <li>Reads the catalog (one query)</li>
 *   <li>Rejects unpurchasable, unknown, recurring, mixed-currency items</li>
 *   <li>Computes totals</li>
 *   <li>Creates a Stripe Checkout Session</li>
 *   <li>Builds the full order document</li>
 *   <li>Replaces the request content</li>
 * </ol>
 */
@RegisterPlugin(
        name = "ordersCheckoutInterceptor",
        description = "Intercepts POST /orders to create an order from a cart",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
        // Must run *before* `jsonSchemaBeforeWrite`, which is registered at
        // `Integer.MAX_VALUE` precisely so it is the last thing to see the
        // content. This interceptor is what produces that content: the client
        // sends `{items, email}` and the stored order is a different document
        // altogether — `_id`, `stripe_session_id`, `secret`, `status`,
        // `line_items`, the amounts.
        //
        // At MAX_VALUE the two tie, the order between them is whatever the sort
        // happens to do, and when the checker wins it validates the cart against
        // the schema of the order and answers 400 naming twelve fields that were
        // about to be written. The Stripe session is created either way, so the
        // failure arrives *after* the money side already happened.
        //
        // The default, 10, is the right value: this is an ordinary request
        // transform, and nothing about it wants to be last.
        priority = 10,
        requiresContent = true,
        enabledByDefault = false)
public class OrdersCheckoutInterceptor implements MongoInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrdersCheckoutInterceptor.class);
    private static final SecureRandom RNG = new SecureRandom();

    @Inject("stripeConfig")
    private StripeConfigData conf;

    @Inject("mclient")
    private MongoClient mclient;

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        // Effective (possibly per-tenant) config — resolved once, threaded through this method
        // instead of re-reading conf.products()/conf.db()/conf.secretKey() piecemeal, so a
        // multi-tenant deployment cannot end up mixing one tenant's products config with
        // another's db or key.
        var products = RequestOverrides.products(request, conf);
        if (products == null || !products.enabled()) {
            return;
        }
        var db = RequestOverrides.db(request, conf);

        // 1. Parse and validate request body
        if (!(request.getContent() instanceof BsonDocument body)) {
            reject(response, HttpStatus.SC_BAD_REQUEST, "request body must be a JSON object");
            return;
        }

        // Reject any field other than 'items' (and 'email' for guests)
        for (var key : body.keySet()) {
            if (!"items".equals(key) && !"email".equals(key)) {
                reject(response, HttpStatus.SC_BAD_REQUEST,
                        "unexpected field '%s' — only 'items' (and 'email' for guests) are allowed".formatted(key));
                return;
            }
        }

        if (!body.containsKey("items") || !body.get("items").isArray()) {
            reject(response, HttpStatus.SC_BAD_REQUEST, "'items' must be an array");
            return;
        }

        var itemsArray = body.getArray("items");
        if (itemsArray.isEmpty()) {
            reject(response, HttpStatus.SC_BAD_REQUEST, "'items' must not be empty");
            return;
        }

        if (itemsArray.size() > products.maxLineItems()) {
            reject(response, HttpStatus.SC_BAD_REQUEST,
                    "too many items: %d (max %d)".formatted(itemsArray.size(), products.maxLineItems()));
            return;
        }

        // 2. Parse and validate each item
        var requestedItems = new ArrayList<RequestedItem>();
        for (var item : itemsArray) {
            if (!item.isDocument()) {
                reject(response, HttpStatus.SC_BAD_REQUEST, "each item must be an object");
                return;
            }
            var itemDoc = item.asDocument();

            var productId = stringField(itemDoc, "productId");
            if (productId == null || productId.isBlank()) {
                reject(response, HttpStatus.SC_BAD_REQUEST, "each item must have a 'productId'");
                return;
            }

            if (!itemDoc.containsKey("quantity") || !itemDoc.get("quantity").isNumber()) {
                reject(response, HttpStatus.SC_BAD_REQUEST,
                        "item '%s' must have a numeric 'quantity'".formatted(productId));
                return;
            }

            var quantity = itemDoc.getNumber("quantity").intValue();
            if (quantity < 1) {
                reject(response, HttpStatus.SC_BAD_REQUEST,
                        "item '%s' quantity must be at least 1".formatted(productId));
                return;
            }

            if (quantity > products.maxQuantityPerLine()) {
                reject(response, HttpStatus.SC_BAD_REQUEST,
                        "item '%s' quantity %d exceeds max %d".formatted(productId, quantity, products.maxQuantityPerLine()));
                return;
            }

            requestedItems.add(new RequestedItem(productId, quantity));
        }

        // 3. Read catalog (one query)
        var catalogReader = new CatalogReader(mclient, db, products);
        var productIds = requestedItems.stream().map(RequestedItem::productId).collect(java.util.stream.Collectors.toSet());

        java.util.List<CatalogItem> catalogItems;
        try {
            catalogItems = catalogReader.readItems(productIds);
        } catch (CatalogReader.CatalogValidationException e) {
            reject(response, HttpStatus.SC_BAD_REQUEST, e.getMessage());
            return;
        }

        // Check all requested products were found
        var foundIds = catalogItems.stream().map(CatalogItem::id).collect(java.util.stream.Collectors.toSet());
        for (var requested : requestedItems) {
            if (!foundIds.contains(requested.productId())) {
                reject(response, HttpStatus.SC_BAD_REQUEST, "unknown product '%s'".formatted(requested.productId()));
                return;
            }
        }

        // Check purchasable
        try {
            catalogReader.checkPurchasable(catalogItems);
        } catch (CatalogReader.CatalogValidationException e) {
            reject(response, HttpStatus.SC_CONFLICT, e.getMessage());
            return;
        }

        // Check single currency
        try {
            catalogReader.checkSingleCurrency(catalogItems);
        } catch (CatalogReader.CatalogValidationException e) {
            reject(response, HttpStatus.SC_BAD_REQUEST, e.getMessage());
            return;
        }

        // 4. Check inventory (optional)
        if (products.inventoryCollection() != null) {
            try {
                checkInventory(catalogItems, requestedItems, products, db);
            } catch (CatalogReader.CatalogValidationException e) {
                reject(response, HttpStatus.SC_CONFLICT, e.getMessage());
                return;
            }
        }

        // 5. Resolve currency and compute totals
        var catalogMap = new LinkedHashMap<String, CatalogItem>();
        for (var item : catalogItems) {
            catalogMap.put(item.id(), item);
        }

        var currency = catalogItems.get(0).currency() != null
                ? catalogItems.get(0).currency().toLowerCase()
                : products.defaultCurrency().toLowerCase();

        long amountSubtotal = 0;
        boolean requiresShipping = false;
        var lineItemsBson = new BsonArray();

        for (var requested : requestedItems) {
            var catalogItem = catalogMap.get(requested.productId());
            var subtotal = catalogItem.unitAmount() * requested.quantity();
            amountSubtotal += subtotal;

            if (catalogItem.isPhysical()) {
                requiresShipping = true;
            }

            lineItemsBson.add(new BsonDocument()
                    .append("product_id", new BsonString(catalogItem.id()))
                    .append("type", new BsonString(catalogItem.type()))
                    .append("name", new BsonString(catalogItem.name()))
                    .append("unit_amount", new BsonInt64(catalogItem.unitAmount()))
                    .append("quantity", new BsonInt32(requested.quantity()))
                    .append("subtotal", new BsonInt64(subtotal))
                    .append("tax_code", catalogItem.taxCode() != null
                            ? new BsonString(catalogItem.taxCode())
                            : BsonNull.VALUE));
        }

        // 5. Resolve buyer info
        var buyerId = resolveBuyerId(request);
        var buyerEmail = resolveBuyerEmail(request, body, products);

        // 5b. Mint the order identity up front.
        //
        // Both of these are generated locally — an ObjectId needs no round trip
        // and the secret comes from an RNG — so nothing is gained by waiting.
        // Minting them here instead of after Session.create is what lets the
        // success URL carry them: see interpolateOrderRef.
        var orderId = new ObjectId();
        var secret = generateSecret();

        // 6. Build Stripe Checkout Session
        var sessionBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(interpolateOrderRef(products.successUrl(), orderId, secret))
                .setCancelUrl(products.cancelUrl())
                .putMetadata("order_source", "restheart-products");

        if (buyerId != null) {
            sessionBuilder.setClientReferenceId(buyerId);
            sessionBuilder.putMetadata("buyer_id", buyerId);
        }

        if (buyerEmail != null) {
            sessionBuilder.setCustomerEmail(buyerEmail);
        }

        // Add line items
        for (var requested : requestedItems) {
            var catalogItem = catalogMap.get(requested.productId());

            SessionCreateParams.LineItem.Builder lineItemBuilder;

            if (catalogItem.stripePriceId() != null) {
                // Escape hatch: use a real Stripe Price
                lineItemBuilder = SessionCreateParams.LineItem.builder()
                        .setPrice(catalogItem.stripePriceId())
                        .setQuantity((long) requested.quantity());
            } else {
                // Normal case: ad-hoc price_data
                var priceDataBuilder = SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(currency)
                        .setUnitAmount(catalogItem.unitAmount())
                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName(catalogItem.name())
                                .build());

                if (catalogItem.description() != null) {
                    priceDataBuilder.setProductData(
                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(catalogItem.name())
                                    .setDescription(catalogItem.description())
                                    .build());
                }

                if (catalogItem.taxCode() != null && products.automaticTax()) {
                    priceDataBuilder.setProductData(
                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(catalogItem.name())
                                    .setTaxCode(catalogItem.taxCode())
                                    .build());
                }

                lineItemBuilder = SessionCreateParams.LineItem.builder()
                        .setPriceData(priceDataBuilder.build())
                        .setQuantity((long) requested.quantity());
            }

            sessionBuilder.addLineItem(lineItemBuilder.build());
        }

        // Automatic tax
        if (products.automaticTax()) {
            sessionBuilder.setAutomaticTax(
                    SessionCreateParams.AutomaticTax.builder().setEnabled(true).build());
        }

        // Ask for somewhere to send it.
        //
        // Without this Stripe never shows the address form, so `shipping_address`
        // on the order stays null for ever — and the shipping *rates* below were
        // being offered all the same, which is a checkout that charges for
        // delivery and never learns the destination.
        //
        // Stripe has no "anywhere": the allowed countries are an explicit list,
        // so an empty one can only mean "do not ask". A cart that needs shipping
        // and a service that never named a country is a misconfiguration rather
        // than a preference, and says so once per order rather than silently.
        if (requiresShipping) {
            var countries = allowedCountries(products.shippingAddressCountries());
            if (!countries.isEmpty()) {
                sessionBuilder.setShippingAddressCollection(
                        SessionCreateParams.ShippingAddressCollection.builder()
                                .addAllAllowedCountry(countries)
                                .build());
            } else {
                LOGGER.warn("[stripe] order {} needs shipping but products.shipping-address-countries "
                        + "is empty: Stripe will not ask for an address and the order will have none",
                        orderId);
            }
        }

        // Shipping options (for physical products)
        if (requiresShipping && products.shippingOptions() != null) {
            for (var shippingOpt : products.shippingOptions()) {
                sessionBuilder.addShippingOption(
                        SessionCreateParams.ShippingOption.builder()
                                .setShippingRateData(
                                        SessionCreateParams.ShippingOption.ShippingRateData.builder()
                                                .setDisplayName(shippingOpt.displayName())
                                                .setFixedAmount(
                                                        SessionCreateParams.ShippingOption.ShippingRateData.FixedAmount.builder()
                                                                .setAmount(shippingOpt.amount())
                                                                .setCurrency(currency)
                                                                .build())
                                                .build())
                                .build());
            }
        }

        // Session expiry (min 30 min, max 24 h)
        var expiresMinutes = Math.max(30, Math.min(products.sessionExpiresMinutes(), 1440));

        // Create the Stripe session
        var apiKey = RequestOverrides.secretKey(request, conf);
        var opts = RequestOptions.builder()
                .setApiKey(apiKey)
                .setIdempotencyKey("order-" + buyerId + "-" + System.currentTimeMillis())
                .build();

        Session session;
        try {
            session = Session.create(sessionBuilder.build(), opts);
        } catch (StripeException e) {
            LOGGER.error("[stripe] failed to create Checkout session: {}", e.getMessage());
            reject(response, HttpStatus.SC_BAD_GATEWAY, "unable to reach Stripe");
            return;
        }

        // 7. Build order document (orderId and secret were minted at step 5b)
        var now = System.currentTimeMillis();
        var expiresAt = now + (expiresMinutes * 60L * 1000L);

        var order = new BsonDocument()
                .append("_id", new BsonObjectId(orderId))
                .append("stripe_session_id", new BsonString(session.getId()))
                .append("stripe_payment_intent", BsonNull.VALUE)
                .append("secret", new BsonString(secret))
                .append("checkout_url", new BsonString(session.getUrl()))
                .append("buyer_id", buyerId != null ? new BsonString(buyerId) : BsonNull.VALUE)
                .append("buyer_email", buyerEmail != null ? new BsonString(buyerEmail) : BsonNull.VALUE)
                .append("payer", buildPayerDocument(request))
                .append("status", new BsonString("pending_payment"))
                .append("requires_shipping", BsonBoolean.valueOf(requiresShipping))
                .append("line_items", lineItemsBson)
                .append("currency", new BsonString(currency))
                .append("amount_subtotal", new BsonInt64(amountSubtotal))
                .append("amount_tax", new BsonInt64(0))
                .append("amount_shipping", new BsonInt64(0))
                .append("amount_total", new BsonInt64(amountSubtotal))
                .append("amount_refunded", new BsonInt64(0))
                .append("shipping_address", BsonNull.VALUE)
                .append("created_at", new org.bson.BsonDateTime(now))
                .append("paid_at", BsonNull.VALUE)
                .append("expires_at", new org.bson.BsonDateTime(expiresAt));

        // 8. Replace request content with order document
        request.setContent(order);

        LOGGER.info("[stripe] order created — id={}, session={}, buyer={}", orderId, session.getId(), buyerId);
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        // Request-level disable (for RESTHeart Cloud multi-tenancy) — see RequestOverrides.
        // Checked before resolving the effective config: a disabled tenant should never pay
        // for reading a per-tenant products config it isn't entitled to.
        if (RequestOverrides.productsDisabled(request)) {
            return false;
        }

        var products = RequestOverrides.products(request, conf);
        if (products == null || !products.enabled()) {
            return false;
        }

        return request.isPost()
                && request.isWriteDocument()
                && products.ordersCollection().equals(request.getCollectionName());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String resolveBuyerId(MongoRequest request) {
        var account = request.getAuthenticatedAccount();
        if (account == null) {
            return null;
        }
        // The principal name is the one identifier every account type carries. A JWT issued by
        // restheart-accounts has no "_id" claim — the identity is in "sub" — so reading "_id"
        // off the properties would silently make every authenticated buyer look like a guest.
        if (account.getPrincipal() != null && account.getPrincipal().getName() != null) {
            return account.getPrincipal().getName();
        }
        if (account instanceof org.restheart.security.WithProperties<?> withProperties) {
            var props = withProperties.propertiesAsMap();
            if (props != null && props.get("_id") != null) {
                return props.get("_id").toString();
            }
        }
        return null;
    }

    private String resolveBuyerEmail(MongoRequest request, BsonDocument body, ProductsConfig products) {
        // Guest: email from body
        if (!request.isAuthenticated()) {
            return stringField(body, "email");
        }

        // Authenticated: from user document field
        if (products.buyerEmailField() != null) {
            var account = request.getAuthenticatedAccount();
            if (account instanceof org.restheart.security.WithProperties<?> withProperties) {
                var props = withProperties.propertiesAsMap();
                if (props != null) {
                    var emailValue = props.get(products.buyerEmailField());
                    if (emailValue != null) {
                        return emailValue.toString();
                    }
                }
            }
        }

        return null;
    }

    private BsonDocument buildPayerDocument(MongoRequest request) {
        if (!request.isAuthenticated()) {
            return new BsonDocument()
                    .append("type", new BsonString("guest"))
                    .append("id", BsonNull.VALUE)
                    .append("stripe_customer_id", BsonNull.VALUE);
        }

        // Authenticated: resolve team
        var account = request.getAuthenticatedAccount();
        if (account instanceof org.restheart.security.WithProperties<?> withProperties) {
            var props = withProperties.propertiesAsMap();
            if (props != null && props.get("team") instanceof Map<?, ?> teamMap) {
                // The claim carries {"$oid": "<hex>"}, which toString()s to "{$oid=<hex>}" —
                // ObjectId would reject that, failing every authenticated order.
                var teamId = org.restheart.stripe.util.StripeIds.fromClaim(
                        teamMap.get("_id") != null ? teamMap.get("_id") : teamMap.get("id"));
                var stripeCustomerId = teamMap.get("stripe_customer_id");

                return new BsonDocument()
                        .append("type", new BsonString("team"))
                        .append("id", teamId != null ? new BsonObjectId(new ObjectId(teamId)) : BsonNull.VALUE)
                        .append("stripe_customer_id", stripeCustomerId != null
                                ? new BsonString(stripeCustomerId.toString())
                                : BsonNull.VALUE);
            }
        }

        // Fallback: guest (should not happen for authenticated users)
        return new BsonDocument()
                .append("type", new BsonString("guest"))
                .append("id", BsonNull.VALUE)
                .append("stripe_customer_id", BsonNull.VALUE);
    }

    private static String stringField(BsonDocument doc, String key) {
        if (doc == null || !doc.containsKey(key) || !doc.get(key).isString()) {
            return null;
        }
        return doc.getString(key).getValue();
    }

    /**
     * Substitutes {@code {ORDER_ID}} and {@code {ORDER_SECRET}} in the configured
     * success URL.
     *
     * <p>Without this the buyer comes back from Checkout holding nothing that
     * identifies their order. Stripe substitutes only {@code {CHECKOUT_SESSION_ID}},
     * and a guest has no session for the server to recognise them by — so every
     * client had to invent its own way to carry the reference across the redirect
     * (typically stashing it in {@code localStorage}, which does not survive a
     * different browser, a cleared store, or a private window).
     *
     * <p>Substitution is opt-in: a URL without the placeholders is returned
     * unchanged, so existing configurations keep working untouched.
     *
     * <p><b>Put {@code {ORDER_SECRET}} in the URL fragment, not the query
     * string.</b> The secret is a bearer credential — it is the only thing
     * standing between a stranger and a guest's order, with their email and
     * shipping address in it. A fragment is never sent to the server, so it stays
     * out of access logs, proxy logs and {@code Referer} headers, and the client
     * can strip it from the address bar on arrival:
     *
     * <pre>
     * success-url: https://shop.example.com/order#order={ORDER_ID}&amp;secret={ORDER_SECRET}
     * </pre>
     *
     * <p>Both values are URL-encoded. An ObjectId is hex and a secret is hex, so
     * in practice neither changes — the encoding is there so this stays correct
     * if either representation ever does.
     */
    /**
     * Configured country codes as Stripe's enum, dropping what it does not know.
     *
     * `AllowedCountry.valueOf` throws on anything that is not an ISO 3166-1
     * alpha-2 code Stripe recognises, and a typo in a service's configuration
     * must not take a checkout down with it: a customer cannot act on it, and
     * the rest of the list is still good. So an unknown code is a warning and a
     * skip — and if that empties the list, the caller falls through to not
     * collecting an address at all, which it already knows how to report.
     */
    private static List<SessionCreateParams.ShippingAddressCollection.AllowedCountry> allowedCountries(
            List<String> codes) {
        var allowed = new ArrayList<SessionCreateParams.ShippingAddressCollection.AllowedCountry>();
        if (codes == null) {
            return allowed;
        }
        for (var code : codes) {
            try {
                allowed.add(SessionCreateParams.ShippingAddressCollection.AllowedCountry
                        .valueOf(code.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("[stripe] products.shipping-address-countries: '{}' is not a country "
                        + "code Stripe accepts — ignoring it", code);
            }
        }
        return allowed;
    }

    static String interpolateOrderRef(String url, ObjectId orderId, String secret) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        return url
                .replace("{ORDER_ID}", URLEncoder.encode(orderId.toHexString(), StandardCharsets.UTF_8))
                .replace("{ORDER_SECRET}", URLEncoder.encode(secret, StandardCharsets.UTF_8));
    }

    private static String generateSecret() {
        var bytes = new byte[32];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static void reject(MongoResponse response, int status, String message) {
        response.setInError(status, message);
        LOGGER.warn("[stripe] order creation rejected: {}", message);
    }

    /**
     * Checks inventory for physical products. Refuses if available stock is below requested quantity.
     */
    private void checkInventory(java.util.List<CatalogItem> catalogItems,
                                 java.util.List<RequestedItem> requestedItems,
                                 ProductsConfig products,
                                 String db) throws CatalogReader.CatalogValidationException {
        var inventoryCol = mclient.getDatabase(db)
                .getCollection(products.inventoryCollection(), org.bson.BsonDocument.class);

        var requestedMap = new java.util.LinkedHashMap<String, Integer>();
        for (var item : requestedItems) {
            requestedMap.merge(item.productId(), item.quantity(), Integer::sum);
        }

        for (var catalogItem : catalogItems) {
            if (!catalogItem.isPhysical()) {
                continue;
            }

            var requested = requestedMap.getOrDefault(catalogItem.id(), 0);
            if (requested <= 0) {
                continue;
            }

            var inventoryDoc = inventoryCol.find(com.mongodb.client.model.Filters.eq("_id", catalogItem.id())).first();
            if (inventoryDoc == null) {
                // No inventory record = unlimited stock
                continue;
            }

            var available = inventoryDoc.getInt32("available").getValue();
            if (available < requested) {
                throw new CatalogReader.CatalogValidationException(
                        "product %s has %d available but %d requested".formatted(catalogItem.id(), available, requested));
            }
        }
    }

    private record RequestedItem(String productId, int quantity) {}
}
