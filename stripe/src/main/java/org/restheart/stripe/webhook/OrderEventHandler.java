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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
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
        var products = ctx.conf().products();
        if (products == null || !products.enabled()) {
            return;
        }

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
        var session = deserialize(event, Session.class);
        if (session != null) {
            if (!"paid".equals(session.getPaymentStatus())) {
                LOGGER.info("[stripe] checkout.session.completed with status '{}' — leaving as pending_payment",
                        session.getPaymentStatus());
                return;
            }
            markAsPaid(session.getId(), session.getAmountTotal(), session.getCurrency(),
                    session.getPaymentIntent(), session.getCustomerDetails(), event, ctx, products);
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
                data.paymentIntent, null, event, ctx, products);
    }

    private void handleAsyncPaymentSucceeded(Event event, StripeEventContext ctx, ProductsConfig products) {
        var session = deserialize(event, Session.class);
        if (session == null) {
            // Fallback: extract directly from JSON when SDK deserialization fails (API version mismatch)
            var data = extractSessionData(event);
            if (data == null) {
                return;
            }
            markAsPaid(data.sessionId, data.amountTotal, data.currency,
                    data.paymentIntent, null, event, ctx, products);
            return;
        }

        markAsPaid(session.getId(), session.getAmountTotal(), session.getCurrency(),
                session.getPaymentIntent(), session.getCustomerDetails(), event, ctx, products);
    }

    private void handleAsyncPaymentFailed(Event event, StripeEventContext ctx, ProductsConfig products) {
        var session = deserialize(event, Session.class);
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
        var session = deserialize(event, Session.class);
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
        var charge = deserialize(event, Charge.class);
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
        sendOrderNotification(ctx, products, "order-refunded", orderId, refundAmount, cur, buyerEmail);
    }

    private void handleDisputeCreated(Event event, StripeEventContext ctx, ProductsConfig products) {
        var dispute = deserialize(event, Dispute.class);
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
                            Event event, StripeEventContext ctx, ProductsConfig products) {
        var ordersCol = ordersCollection(ctx, products);
        var transactionsCol = transactionsCollection(ctx, products);

        // Monotonic transition: only from pending_payment
        var filter = Filters.and(
                Filters.eq("stripe_session_id", sessionId),
                Filters.eq("status", "pending_payment"));

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

        var result = ordersCol.findOneAndUpdate(filter, new BsonDocument("$set", updateDoc));
        if (result == null) {
            LOGGER.debug("[stripe] markAsPaid skipped — session={} not in pending_payment", sessionId);
            return;
        }

        // Append payment transaction to ledger
        var orderId = result.getObjectId("_id").getValue();
        var amount = amountTotal != null ? amountTotal : 0L;
        var cur = currency != null ? currency : "eur";

        appendTransaction(transactionsCol, orderId, "payment", amount, cur,
                paymentIntent, event.getId(), ctx.appliedAt());

        LOGGER.info("[stripe] order marked as paid — id={}, session={}, amount={}", orderId, sessionId, amount);

        // Send order confirmation notification
        sendOrderNotification(ctx, products, "order-confirmed", orderId, amount, cur,
                customerDetails != null ? customerDetails.getEmail() : null);
    }

    /** Fallback extraction when Stripe SDK deserialization fails (API version mismatch). */
    private static SessionData extractSessionData(Event event) {
        try {
            var raw = event.getRawJsonObject();
            if (raw == null || !raw.has("data")) {
                return null;
            }
            var data = raw.getAsJsonObject("data");
            if (data == null || !data.has("object")) {
                return null;
            }
            var obj = data.getAsJsonObject("object");

            var sessionId = obj.has("id") && !obj.get("id").isJsonNull()
                    ? obj.get("id").getAsString() : null;
            var amountTotal = obj.has("amount_total") && !obj.get("amount_total").isJsonNull()
                    ? obj.get("amount_total").getAsLong() : null;
            var currency = obj.has("currency") && !obj.get("currency").isJsonNull()
                    ? obj.get("currency").getAsString() : null;
            var paymentIntent = obj.has("payment_intent") && !obj.get("payment_intent").isJsonNull()
                    ? obj.get("payment_intent").getAsString() : null;

            return new SessionData(sessionId, amountTotal, currency, paymentIntent);
        } catch (Exception e) {
            LOGGER.warn("[stripe] failed to extract session data from event: {}", e.getMessage());
            return null;
        }
    }

    /** Extracts payment_status from raw JSON for fallback handling. */
    private static String extractPaymentStatus(Event event) {
        try {
            var raw = event.getRawJsonObject();
            if (raw == null || !raw.has("data")) {
                return null;
            }
            var data = raw.getAsJsonObject("data");
            if (data == null || !data.has("object")) {
                return null;
            }
            var obj = data.getAsJsonObject("object");
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
            var raw = event.getRawJsonObject();
            if (raw == null || !raw.has("data")) {
                return null;
            }
            var data = raw.getAsJsonObject("data");
            if (data == null || !data.has("object")) {
                return null;
            }
            var obj = data.getAsJsonObject("object");

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
            var raw = event.getRawJsonObject();
            if (raw == null || !raw.has("data")) {
                return null;
            }
            var data = raw.getAsJsonObject("data");
            if (data == null || !data.has("object")) {
                return null;
            }
            var obj = data.getAsJsonObject("object");

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
        return ctx.mclient()
                .getDatabase(ctx.conf().db())
                .getCollection(products.ordersCollection(), BsonDocument.class);
    }

    private MongoCollection<BsonDocument> transactionsCollection(StripeEventContext ctx, ProductsConfig products) {
        return ctx.mclient()
                .getDatabase(ctx.conf().db())
                .getCollection(products.transactionsCollection(), BsonDocument.class);
    }

    @SuppressWarnings("unchecked")
    private static <T extends StripeObject> T deserialize(Event event, Class<T> type) {
        var obj = event.getDataObjectDeserializer().getObject();
        if (obj.isEmpty() || !type.isInstance(obj.get())) {
            LOGGER.warn("[stripe] could not deserialize {} payload as {} (API version mismatch?)",
                    event.getType(), type.getSimpleName());
            return null;
        }
        return (T) obj.get();
    }

    // ── Notifications ────────────────────────────────────────────────────────

    private void sendOrderNotification(StripeEventContext ctx, ProductsConfig products, String name,
                                       ObjectId orderId, long amount, String currency, String email) {
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
            vars.put("order-id", orderId.toHexString());
            vars.put("amount", String.valueOf(amount));
            vars.put("currency", currency);

            var raw = EmailTemplateLoader.loadWithFallback(null, null, name + ".html");
            var rendered = EmailRenderer.render(raw, vars, "en");

            emailSender.sendEmailAsync(ctx.req(), email, email, rendered.subject(), rendered.htmlBody());
            LOGGER.info("[stripe] sent '{}' notification for order {}", name, orderId);
        } catch (Exception e) {
            LOGGER.error("[stripe] failed to send '{}' notification for order {}: {}", name, orderId, e.getMessage());
        }
    }
}
