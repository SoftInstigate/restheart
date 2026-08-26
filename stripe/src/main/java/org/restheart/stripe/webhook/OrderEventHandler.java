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
package org.restheart.stripe.webhook;

import java.util.Set;

import org.bson.BsonArray;
import org.bson.BsonValue;
import java.util.Map;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.restheart.emails.EmailRenderer;
import org.restheart.emails.EmailSender;
import org.restheart.emails.EmailTemplateLoader;
import org.restheart.plugins.stripe.ProductsConfig;
import org.restheart.stripe.util.RequestOverrides;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.stripe.model.Event;
import com.stripe.model.Charge;
import com.stripe.model.Dispute;
import com.stripe.model.checkout.Session;

/**
 * Handles order-related Stripe webhook events for the products mode.
 *
 * <p>Events handled:
 * <ul>
 *   <li>{@code checkout.session.completed} — mark as paid (if payment_status == "paid")</li>
 *   <li>{@code checkout.session.async_payment_succeeded} — mark as paid</li>
 *   <li>{@code checkout.session.async_payment_failed} — mark as failed</li>
 *   <li>{@code checkout.session.expired} — mark as expired</li>
 *   <li>{@code charge.refunded} — append refund transaction, update amount_refunded</li>
 *   <li>{@code charge.dispute.created} — append dispute transaction</li>
 * </ul>
 */
public class OrderEventHandler implements StripeEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderEventHandler.class);

    private final EmailSender emailSender;

    public OrderEventHandler(EmailSender emailSender) {
        this.emailSender = emailSender;
        LOGGER.info("[stripe] OrderEventHandler created");
    }

    @Override
    public Set<String> handledEventTypes() {
        return Set.of(
                "checkout.session.completed",
                "checkout.session.async_payment_succeeded",
                "checkout.session.async_payment_failed",
                "checkout.session.expired",
                "charge.refunded",
                "charge.dispute.created");
    }

    @Override
    public void handle(Event event, StripeEventContext ctx) throws Exception {
        LOGGER.info("[stripe] OrderEventHandler.handle: called with event type={}", event.getType());
        // The effective (possibly per-tenant) products config, not ctx.conf().products() — the
        // latter is the static one and disagrees with it for a tenant with an override.
        var products = ctx.products();
        LOGGER.info("[stripe] OrderEventHandler.handle: products={}, products.enabled={}", products, products != null ? products.enabled() : "N/A");
        if (products == null || !products.enabled()) {
            LOGGER.warn("[stripe] OrderEventHandler: products mode not enabled, skipping");
            return;
        }

        LOGGER.info("[stripe] OrderEventHandler: processing event type={}", event.getType());

        switch (event.getType()) {
            case "checkout.session.completed" -> handleSessionCompleted(event, ctx, products);
            case "checkout.session.async_payment_succeeded" -> handleAsyncPaymentSucceeded(event, ctx, products);
            case "checkout.session.async_payment_failed" -> handleAsyncPaymentFailed(event, ctx, products);
            case "checkout.session.expired" -> handleSessionExpired(event, ctx, products);
            case "charge.refunded" -> handleChargeRefunded(event, ctx, products);
            case "charge.dispute.created" -> handleDisputeCreated(event, ctx, products);
        }
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    private void handleSessionCompleted(Event event, StripeEventContext ctx, ProductsConfig products) {
        var session = EventPayloads.deserialize(event, Session.class);
        if (session != null) {
            if (!"paid".equals(session.getPaymentStatus())) {
                LOGGER.info("[stripe] checkout.session.completed with status '{}' — leaving as pending_payment",
                        session.getPaymentStatus());
                return;
            }
            markAsPaid(session.getId(), session.getAmountTotal(), session.getCurrency(),
                    session.getPaymentIntent(), session.getCustomerDetails(),
                    session.getCollectedInformation(), event, ctx, products);
            return;
        }

        // Fallback: extract directly from JSON when SDK deserialization fails
        var data = extractSessionData(event);
        if (data == null) {
            return;
        }
        // Check payment_status from raw JSON
        var paymentStatus = extractPaymentStatus(event);
        if (!"paid".equals(paymentStatus)) {
            LOGGER.info("[stripe] checkout.session.completed with status '{}' — leaving as pending_payment",
                    paymentStatus);
            return;
        }
        markAsPaid(data.sessionId, data.amountTotal, data.currency,
                data.paymentIntent, null, null, event, ctx, products);
    }

    private void handleAsyncPaymentSucceeded(Event event, StripeEventContext ctx, ProductsConfig products) {
        LOGGER.info("[stripe] handleAsyncPaymentSucceeded: starting");
        var session = EventPayloads.deserialize(event, Session.class);
        if (session == null) {
            LOGGER.info("[stripe] handleAsyncPaymentSucceeded: session is null, trying fallback extraction");
            // Fallback: extract directly from JSON when SDK deserialization fails (API version mismatch)
            var data = extractSessionData(event);
            if (data == null) {
                LOGGER.warn("[stripe] handleAsyncPaymentSucceeded: fallback extraction failed");
                return;
            }
            LOGGER.info("[stripe] handleAsyncPaymentSucceeded: extracted sessionId={}, amountTotal={}", data.sessionId, data.amountTotal);
            markAsPaid(data.sessionId, data.amountTotal, data.currency,
                    data.paymentIntent, null, null, event, ctx, products);
            return;
        }

        markAsPaid(session.getId(), session.getAmountTotal(), session.getCurrency(),
                session.getPaymentIntent(), session.getCustomerDetails(),
                session.getCollectedInformation(), event, ctx, products);
    }

    private void handleAsyncPaymentFailed(Event event, StripeEventContext ctx, ProductsConfig products) {
        var session = EventPayloads.deserialize(event, Session.class);
        String sessionId;
        if (session != null) {
            sessionId = session.getId();
        } else {
            var data = extractSessionData(event);
            sessionId = data != null ? data.sessionId : null;
        }

        if (sessionId == null) {
            return;
        }

        var ordersCol = ordersCollection(ctx, products);

        var filter = Filters.and(
                Filters.eq("stripe_session_id", sessionId),
                Filters.eq("status", "pending_payment"));

        var result = ordersCol.findOneAndUpdate(filter, Updates.set("status", "failed"));
        if (result != null) {
            LOGGER.info("[stripe] order marked as failed — session={}", sessionId);
        } else {
            LOGGER.debug("[stripe] async_payment_failed skipped — session={} not in pending_payment", sessionId);
        }
    }

    private void handleSessionExpired(Event event, StripeEventContext ctx, ProductsConfig products) {
        var session = EventPayloads.deserialize(event, Session.class);
        String sessionId;
        if (session != null) {
            sessionId = session.getId();
        } else {
            var data = extractSessionData(event);
            sessionId = data != null ? data.sessionId : null;
        }

        if (sessionId == null) {
            return;
        }

        var ordersCol = ordersCollection(ctx, products);

        var filter = Filters.and(
                Filters.eq("stripe_session_id", sessionId),
                Filters.eq("status", "pending_payment"));

        var result = ordersCol.findOneAndUpdate(filter, Updates.set("status", "expired"));
        if (result != null) {
            LOGGER.info("[stripe] order marked as expired — session={}", sessionId);
        } else {
            LOGGER.debug("[stripe] session.expired skipped — session={} not in pending_payment", sessionId);
        }
    }

    private void handleChargeRefunded(Event event, StripeEventContext ctx, ProductsConfig products) {
        var charge = EventPayloads.deserialize(event, Charge.class);
        String paymentIntentId;
        Long refundAmount;
        String currency;

        if (charge != null) {
            paymentIntentId = charge.getPaymentIntent();
            refundAmount = charge.getAmountRefunded() != null ? charge.getAmountRefunded() : 0L;
            currency = charge.getCurrency() != null ? charge.getCurrency() : "eur";
        } else {
            // Fallback: extract from raw JSON
            var data = extractChargeData(event);
            if (data == null) {
                return;
            }
            paymentIntentId = data.paymentIntent;
            refundAmount = data.amountRefunded;
            currency = data.currency;
        }

        if (paymentIntentId == null) {
            LOGGER.warn("[stripe] charge.refunded without payment_intent — skipping");
            return;
        }

        var ordersCol = ordersCollection(ctx, products);
        var transactionsCol = transactionsCollection(ctx, products);

        // Find order by payment_intent
        var order = ordersCol.find(Filters.eq("stripe_payment_intent", paymentIntentId)).first();
        if (order == null) {
            LOGGER.warn("[stripe] charge.refunded for unknown payment_intent={}", paymentIntentId);
            return;
        }

        var orderId = order.getObjectId("_id").getValue();
        var cur = currency != null ? currency : "eur";

        // Update amount_refunded on order
        ordersCol.updateOne(
                Filters.eq("_id", order.getObjectId("_id")),
                Updates.set("amount_refunded", refundAmount));

        // Append refund transaction to ledger
        appendTransaction(transactionsCol, orderId, "refund", -refundAmount, cur,
                charge != null ? charge.getId() : null, event.getId(), ctx.appliedAt());

        LOGGER.info("[stripe] refund recorded — order={}, amount={}", orderId, refundAmount);

        // Send refund notification
        var buyerEmail = order.containsKey("buyer_email") && order.get("buyer_email").isString()
                ? order.getString("buyer_email").getValue() : null;
        sendOrderNotification(ctx, products, "order-refunded", orderId, refundAmount, cur, buyerEmail, order);
    }

    private void handleDisputeCreated(Event event, StripeEventContext ctx, ProductsConfig products) {
        var dispute = EventPayloads.deserialize(event, Dispute.class);
        String paymentIntentId;
        Long disputeAmount;
        String currency;
        String disputeId;

        if (dispute != null) {
            paymentIntentId = dispute.getPaymentIntent();
            disputeAmount = dispute.getAmount() != null ? dispute.getAmount() : 0L;
            currency = dispute.getCurrency() != null ? dispute.getCurrency() : "eur";
            disputeId = dispute.getId();
        } else {
            // Fallback: extract from raw JSON
            var data = extractDisputeData(event);
            if (data == null) {
                return;
            }
            paymentIntentId = data.paymentIntent;
            disputeAmount = data.amount;
            currency = data.currency;
            disputeId = data.disputeId;
        }

        if (paymentIntentId == null) {
            LOGGER.warn("[stripe] charge.dispute.created without payment_intent — skipping");
            return;
        }

        var ordersCol = ordersCollection(ctx, products);
        var transactionsCol = transactionsCollection(ctx, products);

        // Find order by payment_intent
        var order = ordersCol.find(Filters.eq("stripe_payment_intent", paymentIntentId)).first();
        if (order == null) {
            LOGGER.warn("[stripe] charge.dispute.created for unknown payment_intent={}", paymentIntentId);
            return;
        }

        var orderId = order.getObjectId("_id").getValue();
        var cur = currency != null ? currency : "eur";

        // Append dispute transaction to ledger
        appendTransaction(transactionsCol, orderId, "dispute", disputeAmount, cur,
                disputeId, event.getId(), ctx.appliedAt());

        LOGGER.info("[stripe] dispute recorded — order={}, amount={}", orderId, disputeAmount);
    }

    // ── Shared logic ─────────────────────────────────────────────────────────

    private void markAsPaid(String sessionId, Long amountTotal, String currency,
                            String paymentIntent,
                            com.stripe.model.checkout.Session.CustomerDetails customerDetails,
                            com.stripe.model.checkout.Session.CollectedInformation collected,
                            Event event, StripeEventContext ctx, ProductsConfig products) {
        LOGGER.info("[stripe] markAsPaid: sessionId={}, amountTotal={}, currency={}", sessionId, amountTotal, currency);

        var ordersCol = ordersCollection(ctx, products);
        var transactionsCol = transactionsCollection(ctx, products);

        // Monotonic transition: only from pending_payment
        var filter = Filters.and(
                Filters.eq("stripe_session_id", sessionId),
                Filters.eq("status", "pending_payment"));

        LOGGER.info("[stripe] markAsPaid: looking for order with filter={}", filter);

        // Build update
        var now = System.currentTimeMillis();
        var updateDoc = new BsonDocument();
        updateDoc.append("status", new BsonString("paid"));
        updateDoc.append("paid_at", new org.bson.BsonDateTime(now));

        // Fill amount_total
        if (amountTotal != null) {
            updateDoc.append("amount_total", new BsonInt64(amountTotal));
        }

        // Fill buyer_email from customer details
        if (customerDetails != null && customerDetails.getEmail() != null) {
            updateDoc.append("buyer_email", new BsonString(customerDetails.getEmail()));
        }

        // Fill payment_intent
        if (paymentIntent != null) {
            updateDoc.append("stripe_payment_intent", new BsonString(paymentIntent));
        }

        // Where it goes.
        //
        // The order document has carried a `shipping_address` field since it was
        // first written — always null, because nothing ever filled it in. Stripe
        // collects the address on its own page (when the service names the
        // countries it ships to), and this is where it comes back: a shop that
        // never reads it has an order it cannot post.
        //
        // Absent for a digital-only cart, and absent on the JSON fallback path,
        // where the SDK failed to deserialise and there is no structure to read.
        var shipping = shippingAddress(collected);
        if (shipping != null) {
            updateDoc.append("shipping_address", shipping);
        }

        var result = ordersCol.findOneAndUpdate(filter, new BsonDocument("$set", updateDoc));
        if (result == null) {
            LOGGER.warn("[stripe] markAsPaid skipped — session={} not in pending_payment", sessionId);
            return;
        }

        LOGGER.info("[stripe] markAsPaid: found and updating order");

        // Append payment transaction to ledger
        var orderId = result.getObjectId("_id").getValue();
        var amount = amountTotal != null ? amountTotal : 0L;
        var cur = currency != null ? currency : "eur";

        appendTransaction(transactionsCol, orderId, "payment", amount, cur,
                paymentIntent, event.getId(), ctx.appliedAt());

        LOGGER.info("[stripe] order marked as paid — id={}, session={}, amount={}", orderId, sessionId, amount);

        // Send order confirmation notification
        sendOrderNotification(ctx, products, "order-confirmed", orderId, amount, cur,
                customerDetails != null ? customerDetails.getEmail() : null, result);
    }

    /** Fallback extraction when Stripe SDK deserialization fails (API version mismatch). */
    private static SessionData extractSessionData(Event event) {
        try {
            var rawJson = event.getDataObjectDeserializer().getRawJson();
            LOGGER.info("[stripe] extractSessionData: rawJson={}", rawJson);
            if (rawJson == null || rawJson.isBlank()) {
                LOGGER.warn("[stripe] extractSessionData: rawJson is null or blank");
                return null;
            }

            var parser = com.google.gson.JsonParser.parseString(rawJson);
            if (!parser.isJsonObject()) {
                LOGGER.warn("[stripe] extractSessionData: parser is not a JSON object");
                return null;
            }
            var obj = parser.getAsJsonObject();

            var sessionId = obj.has("id") && !obj.get("id").isJsonNull()
                    ? obj.get("id").getAsString() : null;
            var amountTotal = obj.has("amount_total") && !obj.get("amount_total").isJsonNull()
                    ? obj.get("amount_total").getAsLong() : null;
            var currency = obj.has("currency") && !obj.get("currency").isJsonNull()
                    ? obj.get("currency").getAsString() : null;
            var paymentIntent = obj.has("payment_intent") && !obj.get("payment_intent").isJsonNull()
                    ? obj.get("payment_intent").getAsString() : null;

            LOGGER.info("[stripe] extractSessionData: sessionId={}, amountTotal={}, currency={}, paymentIntent={}",
                    sessionId, amountTotal, currency, paymentIntent);

            return new SessionData(sessionId, amountTotal, currency, paymentIntent);
        } catch (Exception e) {
            LOGGER.error("[stripe] failed to extract session data from event: {}", e.getMessage(), e);
            return null;
        }
    }

    /** Extracts payment_status from raw JSON for fallback handling. */
    private static String extractPaymentStatus(Event event) {
        try {
            var rawJson = event.getDataObjectDeserializer().getRawJson();
            if (rawJson == null || rawJson.isBlank()) {
                return null;
            }

            var parser = com.google.gson.JsonParser.parseString(rawJson);
            if (!parser.isJsonObject()) {
                return null;
            }
            var obj = parser.getAsJsonObject();

            return obj.has("payment_status") && !obj.get("payment_status").isJsonNull()
                    ? obj.get("payment_status").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private record SessionData(String sessionId, Long amountTotal, String currency, String paymentIntent) {}

    /** Extracts charge data from raw JSON for fallback handling. */
    private static ChargeData extractChargeData(Event event) {
        try {
            var rawJson = event.getDataObjectDeserializer().getRawJson();
            if (rawJson == null || rawJson.isBlank()) {
                return null;
            }

            var parser = com.google.gson.JsonParser.parseString(rawJson);
            if (!parser.isJsonObject()) {
                return null;
            }
            var obj = parser.getAsJsonObject();

            var paymentIntent = obj.has("payment_intent") && !obj.get("payment_intent").isJsonNull()
                    ? obj.get("payment_intent").getAsString() : null;
            var amountRefunded = obj.has("amount_refunded") && !obj.get("amount_refunded").isJsonNull()
                    ? obj.get("amount_refunded").getAsLong() : 0L;
            var currency = obj.has("currency") && !obj.get("currency").isJsonNull()
                    ? obj.get("currency").getAsString() : null;
            var chargeId = obj.has("id") && !obj.get("id").isJsonNull()
                    ? obj.get("id").getAsString() : null;

            return new ChargeData(paymentIntent, amountRefunded, currency, chargeId);
        } catch (Exception e) {
            LOGGER.warn("[stripe] failed to extract charge data from event: {}", e.getMessage());
            return null;
        }
    }

    private record ChargeData(String paymentIntent, Long amountRefunded, String currency, String chargeId) {}

    /** Extracts dispute data from raw JSON for fallback handling. */
    private static DisputeData extractDisputeData(Event event) {
        try {
            var rawJson = event.getDataObjectDeserializer().getRawJson();
            if (rawJson == null || rawJson.isBlank()) {
                return null;
            }

            var parser = com.google.gson.JsonParser.parseString(rawJson);
            if (!parser.isJsonObject()) {
                return null;
            }
            var obj = parser.getAsJsonObject();

            var paymentIntent = obj.has("payment_intent") && !obj.get("payment_intent").isJsonNull()
                    ? obj.get("payment_intent").getAsString() : null;
            var amount = obj.has("amount") && !obj.get("amount").isJsonNull()
                    ? obj.get("amount").getAsLong() : 0L;
            var currency = obj.has("currency") && !obj.get("currency").isJsonNull()
                    ? obj.get("currency").getAsString() : null;
            var disputeId = obj.has("id") && !obj.get("id").isJsonNull()
                    ? obj.get("id").getAsString() : null;

            return new DisputeData(paymentIntent, amount, currency, disputeId);
        } catch (Exception e) {
            LOGGER.warn("[stripe] failed to extract dispute data from event: {}", e.getMessage());
            return null;
        }
    }

    private record DisputeData(String paymentIntent, Long amount, String currency, String disputeId) {}

    private void appendTransaction(MongoCollection<BsonDocument> transactionsCol,
                                   ObjectId orderId, String type, long amount, String currency,
                                   String stripeObjectId, String stripeEventId,
                                   java.time.Instant occurredAt) {
        var tx = new BsonDocument()
                .append("_id", new BsonObjectId())
                .append("order_id", new BsonObjectId(orderId))
                .append("type", new BsonString(type))
                .append("amount", new BsonInt64(amount))
                .append("currency", new BsonString(currency))
                .append("stripe_object_id", stripeObjectId != null ? new BsonString(stripeObjectId) : BsonNull.VALUE)
                .append("stripe_event_id", new BsonString(stripeEventId))
                .append("occurred_at", new org.bson.BsonDateTime(occurredAt.toEpochMilli()))
                .append("recorded_at", new org.bson.BsonDateTime(System.currentTimeMillis()));

        try {
            transactionsCol.insertOne(tx);
        } catch (MongoException e) {
            // Duplicate key on stripe_event_id = idempotency (redelivered event)
            if (e.getCode() == 11000) {
                LOGGER.debug("[stripe] transaction already recorded — event={}", stripeEventId);
            } else {
                throw e;
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private MongoCollection<BsonDocument> ordersCollection(StripeEventContext ctx, ProductsConfig products) {
        // ctx.scope().db(), not ctx.conf().db(): the former is the effective (possibly
        // per-tenant) database, the latter the static one — see StripeEventContext javadoc.
        return ctx.mclient()
                .getDatabase(ctx.scope().db())
                .getCollection(products.ordersCollection(), BsonDocument.class);
    }

    private MongoCollection<BsonDocument> transactionsCollection(StripeEventContext ctx, ProductsConfig products) {
        return ctx.mclient()
                .getDatabase(ctx.scope().db())
                .getCollection(products.transactionsCollection(), BsonDocument.class);
    }


    /**
     * Pours an order's metadata into the template variables.
     *
     * <p>The order's own always. A line's only when the order has exactly one line: with several,
     * a flat {@code {{key}}} cannot come from all of them, so it comes from none — an empty
     * variable is better than the description of one item out of three. Templates that need the
     * detail use {@code {{items}}}.
     */
    private static void putMetadata(Map<String, String> vars, BsonDocument order) {
        if (order == null) {
            return;
        }

        putStrings(vars, order.get("metadata"));

        if (order.get("line_items") instanceof BsonArray lines && lines.size() == 1
                && lines.get(0) instanceof BsonDocument line) {
            putStrings(vars, line.get("metadata"));
        }
    }

    private static void putStrings(Map<String, String> vars, BsonValue metadata) {
        if (metadata instanceof BsonDocument doc) {
            doc.forEach((k, v) -> {
                if (v.isString()) {
                    vars.put(k, v.asString().getValue());
                }
            });
        }
    }

    /**
     * The order's lines as one line of text: {@code "2 × Classic T-shirt, 1 × Enamel mug"}.
     *
     * <p>Rendered here because {@link org.restheart.emails.EmailRenderer} substitutes
     * {@code {{key}}} and has no loops. A template language would let the shop lay this out
     * itself, and is a decision about every email RESTHeart sends rather than about orders.
     */
    private static String renderItems(BsonDocument order) {
        if (order == null || !(order.get("line_items") instanceof BsonArray lines)) {
            return "";
        }

        var out = new StringBuilder();
        for (var value : lines) {
            if (!(value instanceof BsonDocument line)) {
                continue;
            }
            var quantity = line.get("quantity") != null && line.get("quantity").isNumber()
                    ? line.getNumber("quantity").intValue() : 1;
            var itemName = line.get("name") != null && line.get("name").isString()
                    ? line.getString("name").getValue() : "";
            if (itemName.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(quantity).append(" \u00d7 ").append(itemName);
        }
        return out.toString();
    }

    /**
     * Stripe's shipping details as the order schema declares them.
     *
     * Field by field rather than by serialising Stripe's own object: the schema
     * on the collection is closed over these names, and letting an SDK upgrade
     * decide the shape of a stored document is how a validator starts rejecting
     * writes nobody changed.
     */
    private static BsonDocument shippingAddress(
            com.stripe.model.checkout.Session.CollectedInformation collected) {
        if (collected == null || collected.getShippingDetails() == null) {
            return null;
        }
        var details = collected.getShippingDetails();
        var address = details.getAddress();
        if (address == null) {
            return null;
        }

        var doc = new BsonDocument();
        putIfPresent(doc, "name", details.getName());
        putIfPresent(doc, "line1", address.getLine1());
        putIfPresent(doc, "line2", address.getLine2());
        putIfPresent(doc, "city", address.getCity());
        putIfPresent(doc, "state", address.getState());
        putIfPresent(doc, "postal_code", address.getPostalCode());
        putIfPresent(doc, "country", address.getCountry());
        return doc.isEmpty() ? null : doc;
    }

    private static void putIfPresent(BsonDocument doc, String key, String value) {
        if (value != null && !value.isBlank()) {
            doc.append(key, new BsonString(value));
        }
    }

    // ── Notifications ────────────────────────────────────────────────────────

    private void sendOrderNotification(StripeEventContext ctx, ProductsConfig products, String name,
                                       ObjectId orderId, long amount, String currency, String email,
                                       BsonDocument order) {
        if (emailSender == null || !emailSender.isEnabled()) {
            return;
        }

        var notification = products.orderNotifications() != null
                ? products.orderNotifications().get(name)
                : null;

        if (notification == null || !notification.enabled()) {
            return;
        }

        if (email == null || email.isBlank()) {
            LOGGER.debug("[stripe] cannot send '{}' notification: no email for order {}", name, orderId);
            return;
        }

        try {
            var vars = new java.util.HashMap<String, String>();

            // The metadata become template variables, and go in first.
            //
            // Whoever builds the shop names the keys — call one `pippo` and the template writes
            // {{pippo}}. The plugin reserves no name and knows none. Written before the plugin's
            // own variables on purpose: a metadata key called `amount` must not be able to change
            // the amount the email prints.
            putMetadata(vars, order);

            vars.put("order-id", orderId.toHexString());
            vars.put("amount", String.valueOf(amount));
            vars.put("amount-formatted", formatAmount(amount, currency));
            vars.put("currency", currency);
            // Same convention StripeNotifications.send() uses for subscriptions — kept in step
            // with it deliberately, not reinvented here. "App" is the fallback because, unlike
            // subscriptions, nothing upstream of this handler resolves a tenant's real app name.
            vars.putIfAbsent("year", String.valueOf(java.time.Year.now().getValue()));
            vars.putIfAbsent("app-name", "App");

            // What the buyer bought, as a line of text, for templates that do not care about the
            // detail. The renderer substitutes {{key}} and cannot iterate, so a list has to arrive
            // already rendered.
            vars.put("items", renderItems(order));

            // Same inline > path > built-in precedence as subscription notifications, and the
            // same override key convention: override-stripe-tmpl-{name} is generic on the
            // notification name, so it already covers order-confirmed/order-refunded without any
            // dedicated wiring — see RequestOverrides.templateInline().
            var inline = RequestOverrides.templateInline(ctx.req(), name);
            var raw = EmailTemplateLoader.loadWithFallback(inline, notification.templatePath(), name + ".html");
            var rendered = EmailRenderer.render(raw, vars, "en");

            emailSender.sendEmailAsync(ctx.req(), email, email, rendered.subject(), rendered.htmlBody());
            LOGGER.info("[stripe] sent '{}' notification for order {}", name, orderId);
        } catch (Exception e) {
            LOGGER.error("[stripe] failed to send '{}' notification for order {}: {}", name, orderId, e.getMessage());
        }
    }

    /**
     * {@code amountMinorUnits} converted to the currency's major unit, formatted with that
     * currency's own number of fraction digits — 2 for EUR/USD, 0 for JPY, 3 for BHD, etc. Stripe
     * amounts are always in the minor unit (docs.stripe.com/currencies#zero-decimal), so a naive
     * "divide by 100" would misrender any zero- or three-decimal currency.
     *
     * <p>Falls back to 2 fraction digits for a currency code {@link java.util.Currency} does not
     * recognize (Stripe supports a few it does not, e.g. some historical or crypto-adjacent
     * codes) — the common case, better than failing the whole notification over formatting.
     */
    static String formatAmount(long amountMinorUnits, String currencyCode) {
        int fractionDigits;
        try {
            fractionDigits = java.util.Currency.getInstance(currencyCode.toUpperCase()).getDefaultFractionDigits();
            if (fractionDigits < 0) {
                fractionDigits = 2;
            }
        } catch (IllegalArgumentException e) {
            fractionDigits = 2;
        }

        // BigDecimal.valueOf(unscaled, scale) is amountMinorUnits * 10^-fractionDigits by
        // definition — exact, no division and no rounding mode to get wrong.
        return java.math.BigDecimal.valueOf(amountMinorUnits, fractionDigits).toPlainString();
    }
}
