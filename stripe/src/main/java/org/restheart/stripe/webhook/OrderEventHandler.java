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
 * </ul>
 */
public class OrderEventHandler implements StripeEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderEventHandler.class);

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
        if (session == null) {
            return;
        }

        if (!"paid".equals(session.getPaymentStatus())) {
            LOGGER.info("[stripe] checkout.session.completed with status '{}' — leaving as pending_payment",
                    session.getPaymentStatus());
            return;
        }

        markAsPaid(session, event, ctx, products);
    }

    private void handleAsyncPaymentSucceeded(Event event, StripeEventContext ctx, ProductsConfig products) {
        var session = deserialize(event, Session.class);
        if (session == null) {
            return;
        }

        markAsPaid(session, event, ctx, products);
    }

    private void handleAsyncPaymentFailed(Event event, StripeEventContext ctx, ProductsConfig products) {
        var session = deserialize(event, Session.class);
        if (session == null) {
            return;
        }

        var sessionId = session.getId();
        var ordersCol = ordersCollection(ctx, products);

        // Monotonic transition: only from pending_payment
        var filter = Filters.and(
                Filters.eq("stripe_session_id", sessionId),
                Filters.eq("status", "pending_payment"));

        var update = Updates.set("status", "failed");

        var result = ordersCol.findOneAndUpdate(filter, update);
        if (result != null) {
            LOGGER.info("[stripe] order marked as failed — session={}", sessionId);
        } else {
            LOGGER.debug("[stripe] async_payment_failed skipped — session={} not in pending_payment", sessionId);
        }
    }

    private void handleSessionExpired(Event event, StripeEventContext ctx, ProductsConfig products) {
        var session = deserialize(event, Session.class);
        if (session == null) {
            return;
        }

        var sessionId = session.getId();
        var ordersCol = ordersCollection(ctx, products);

        // Monotonic transition: only from pending_payment
        var filter = Filters.and(
                Filters.eq("stripe_session_id", sessionId),
                Filters.eq("status", "pending_payment"));

        var update = Updates.set("status", "expired");

        var result = ordersCol.findOneAndUpdate(filter, update);
        if (result != null) {
            LOGGER.info("[stripe] order marked as expired — session={}", sessionId);
        } else {
            LOGGER.debug("[stripe] session.expired skipped — session={} not in pending_payment", sessionId);
        }
    }

    private void handleChargeRefunded(Event event, StripeEventContext ctx, ProductsConfig products) {
        var charge = deserialize(event, Charge.class);
        if (charge == null) {
            return;
        }

        var paymentIntentId = charge.getPaymentIntent();
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

        var orderId = order.getObjectId("_id");
        var refundAmount = charge.getAmountRefunded() != null ? charge.getAmountRefunded() : 0L;
        var currency = charge.getCurrency() != null ? charge.getCurrency() : "eur";

        // Update amount_refunded on order
        ordersCol.updateOne(
                Filters.eq("_id", orderId),
                Updates.set("amount_refunded", refundAmount));

        // Append refund transaction to ledger
        appendTransaction(transactionsCol, orderId, "refund", -refundAmount, currency,
                charge.getId(), event.getId(), ctx.appliedAt());

        LOGGER.info("[stripe] refund recorded — order={}, amount={}", orderId, refundAmount);
    }

    private void handleDisputeCreated(Event event, StripeEventContext ctx, ProductsConfig products) {
        var dispute = deserialize(event, Dispute.class);
        if (dispute == null) {
            return;
        }

        var paymentIntentId = dispute.getPaymentIntent();
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

        var orderId = order.getObjectId("_id");
        var disputeAmount = dispute.getAmount() != null ? dispute.getAmount() : 0L;
        var currency = dispute.getCurrency() != null ? dispute.getCurrency() : "eur";

        // Append dispute transaction to ledger
        appendTransaction(transactionsCol, orderId, "dispute", disputeAmount, currency,
                dispute.getId(), event.getId(), ctx.appliedAt());

        LOGGER.info("[stripe] dispute recorded — order={}, amount={}", orderId, disputeAmount);
    }

    // ── Shared logic ─────────────────────────────────────────────────────────

    private void markAsPaid(Session session, Event event, StripeEventContext ctx, ProductsConfig products) {
        var sessionId = session.getId();
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
        updateDoc.append("paid_at", new BsonDocument().append("$date", new BsonInt64(now)));

        // Fill tax/shipping from session
        if (session.getAmountTotal() != null) {
            updateDoc.append("amount_total", new BsonInt64(session.getAmountTotal()));
        }
        if (session.getTotalDetails() != null) {
            if (session.getTotalDetails().getAmountTax() != null) {
                updateDoc.append("amount_tax", new BsonInt64(session.getTotalDetails().getAmountTax()));
            }
            if (session.getTotalDetails().getAmountShipping() != null) {
                updateDoc.append("amount_shipping", new BsonInt64(session.getTotalDetails().getAmountShipping()));
            }
        }

        // Fill buyer_email from session
        if (session.getCustomerDetails() != null && session.getCustomerDetails().getEmail() != null) {
            updateDoc.append("buyer_email", new BsonString(session.getCustomerDetails().getEmail()));
        }

        // Fill shipping_address
        if (session.getShippingDetails() != null && session.getShippingDetails().getAddress() != null) {
            var addr = session.getShippingDetails().getAddress();
            var addrDoc = new BsonDocument();
            if (addr.getLine1() != null) addrDoc.append("line1", new BsonString(addr.getLine1()));
            if (addr.getLine2() != null) addrDoc.append("line2", new BsonString(addr.getLine2()));
            if (addr.getCity() != null) addrDoc.append("city", new BsonString(addr.getCity()));
            if (addr.getState() != null) addrDoc.append("state", new BsonString(addr.getState()));
            if (addr.getPostalCode() != null) addrDoc.append("postal_code", new BsonString(addr.getPostalCode()));
            if (addr.getCountry() != null) addrDoc.append("country", new BsonString(addr.getCountry()));
            updateDoc.append("shipping_address", addrDoc);
        }

        // Fill payment_intent
        if (session.getPaymentIntent() != null) {
            updateDoc.append("stripe_payment_intent", new BsonString(session.getPaymentIntent()));
        }

        var result = ordersCol.findOneAndUpdate(filter, new BsonDocument("$set", updateDoc));
        if (result == null) {
            LOGGER.debug("[stripe] checkout.session.completed skipped — session={} not in pending_payment", sessionId);
            return;
        }

        // Append payment transaction to ledger
        var orderId = result.getObjectId("_id");
        var amount = session.getAmountTotal() != null ? session.getAmountTotal() : 0L;
        var currency = session.getCurrency() != null ? session.getCurrency() : "eur";

        appendTransaction(transactionsCol, orderId, "payment", amount, currency,
                session.getPaymentIntent(), event.getId(), ctx.appliedAt());

        // Compare amount_total to detect catalog edits mid-checkout
        if (result.containsKey("amount_total")) {
            var storedAmount = result.getInt64("amount_total").getValue();
            if (storedAmount != amount) {
                LOGGER.error("[stripe] amount mismatch on order {} — stored={}, session={} — possible catalog edit mid-checkout",
                        orderId, storedAmount, amount);
            }
        }

        LOGGER.info("[stripe] order marked as paid — id={}, session={}, amount={}", orderId, sessionId, amount);
    }

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
                .append("occurred_at", new BsonDocument().append("$date", new BsonInt64(occurredAt.toEpochMilli())))
                .append("recorded_at", new BsonDocument().append("$date", new BsonInt64(System.currentTimeMillis())));

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
}
