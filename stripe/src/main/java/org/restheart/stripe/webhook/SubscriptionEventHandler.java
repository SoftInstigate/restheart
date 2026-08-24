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

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.emails.EmailSender;
import org.restheart.plugins.stripe.NotificationConfig;
import org.restheart.plugins.stripe.PriceAttribution;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.plugins.stripe.SubscriptionOwner;
import org.restheart.plugins.stripe.SubscriptionState;
import org.restheart.stripe.util.Seats;
import org.restheart.stripe.util.StripeCatalogCache;
import org.restheart.stripe.util.StripeNotifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;

/**
 * Handles subscription-related Stripe webhook events.
 */
public class SubscriptionEventHandler implements StripeEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionEventHandler.class);

    private final StripeCatalogCache catalogCache;
    private final EmailSender emailSender;

    public SubscriptionEventHandler(StripeCatalogCache catalogCache, EmailSender emailSender) {
        this.catalogCache = catalogCache;
        this.emailSender = emailSender;
    }

    @Override
    public Set<String> handledEventTypes() {
        return Set.of(
                "checkout.session.completed",
                "customer.subscription.created",
                "customer.subscription.updated",
                "customer.subscription.deleted",
                "customer.subscription.trial_will_end",
                "invoice.payment_succeeded",
                "invoice.payment_failed",
                "product.updated",
                "price.updated");
    }

    @Override
    public void handle(Event event, StripeEventContext ctx) throws Exception {
        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutSessionCompleted(event);
            case "customer.subscription.created", "customer.subscription.updated" ->
                handleSubscriptionUpsert(event, ctx);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event, ctx);
            case "customer.subscription.trial_will_end" -> handleTrialWillEnd(event, ctx);
            case "invoice.payment_succeeded" -> handleInvoicePaymentSucceeded(event, ctx);
            case "invoice.payment_failed" -> handleInvoicePaymentFailed(event, ctx);
            case "product.updated", "price.updated" -> catalogCache.invalidateAll();
            default -> LOGGER.debug("[stripe] unhandled event type: {}", event.getType());
        }
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    private void handleCheckoutSessionCompleted(Event event) {
        var session = EventPayloads.deserialize(event, Session.class);
        if (session == null) {
            return;
        }
        LOGGER.info("[stripe] checkout completed — owner={}, customer={}, subscription={}",
                session.getClientReferenceId(), session.getCustomer(), session.getSubscription());
    }

    private void handleSubscriptionUpsert(Event event, StripeEventContext ctx) {
        var subscription = EventPayloads.deserialize(event, Subscription.class);
        if (subscription == null) {
            return;
        }

        var ownerOpt = ctx.provider().byStripeCustomerId(ctx.scope(), subscription.getCustomer());
        if (ownerOpt.isEmpty()) {
            LOGGER.warn("[stripe] {} for unknown customer {} — no entity is linked to it",
                    event.getType(), subscription.getCustomer());
            return;
        }
        var owner = ownerOpt.get();

        var previous = ctx.provider().readSubscription(owner, ctx.defaultPlanId());
        var licensedCount = ctx.provider().licensedCount(owner);
        var newState = buildState(subscription, previous, licensedCount, ctx.conf());

        var applied = ctx.provider().writeSubscription(owner, newState, ctx.appliedAt());
        if (applied && enteredOverLimit(previous, newState)) {
            LOGGER.info("[stripe] entity {} entered over-limit state (plan={})", owner.id(), newState.plan());
            notifyOverLimit(ctx, owner, newState, licensedCount);
        }
    }

    private void notifyOverLimit(StripeEventContext ctx, SubscriptionOwner owner, SubscriptionState state, int licensedCount) {
        var planConf = ctx.conf().plan(state.plan());
        var limit = Seats.limit(planConf, state);

        var vars = new HashMap<String, String>();
        vars.put("plan", state.plan());
        vars.put("seats-limit", limit != null ? String.valueOf(limit) : "∞");
        vars.put("seats-licensed", String.valueOf(licensedCount));

        StripeNotifications.send(emailSender, ctx.req(), ctx.conf(), NotificationConfig.OVER_LIMIT, owner, vars);
    }

    private void handleSubscriptionDeleted(Event event, StripeEventContext ctx) {
        var subscription = EventPayloads.deserialize(event, Subscription.class);
        if (subscription == null) {
            return;
        }

        var ownerOpt = ctx.provider().byStripeCustomerId(ctx.scope(), subscription.getCustomer());
        if (ownerOpt.isEmpty()) {
            LOGGER.warn("[stripe] subscription.deleted for unknown customer {}", subscription.getCustomer());
            return;
        }
        var owner = ownerOpt.get();

        var previous = ctx.provider().readSubscription(owner, ctx.defaultPlanId());
        var licensedCount = ctx.provider().licensedCount(owner);

        var freePreliminary = SubscriptionState.defaultFor(ctx.defaultPlanId());
        var limit = Seats.limit(ctx.conf().plan(ctx.defaultPlanId()), freePreliminary);
        var overLimitSince = computeOverLimitSince(previous.overLimitSince(), limit, licensedCount);

        var newState = new SubscriptionState(ctx.defaultPlanId(), null, "canceled", null, null, null, 1, false, overLimitSince);
        var applied = ctx.provider().writeSubscription(owner, newState, ctx.appliedAt());
        if (applied) {
            if (enteredOverLimit(previous, newState)) {
                LOGGER.info("[stripe] entity {} entered over-limit state (plan={})", owner.id(), newState.plan());
                notifyOverLimit(ctx, owner, newState, licensedCount);
            }
            var vars = Map.of("plan", previous.plan() != null ? previous.plan() : "");
            StripeNotifications.send(emailSender, ctx.req(), ctx.conf(), NotificationConfig.SUBSCRIPTION_CANCELED, owner, vars);
        }
    }

    private void handleTrialWillEnd(Event event, StripeEventContext ctx) {
        var subscription = EventPayloads.deserialize(event, Subscription.class);
        if (subscription == null) {
            return;
        }

        var ownerOpt = ctx.provider().byStripeCustomerId(ctx.scope(), subscription.getCustomer());
        if (ownerOpt.isEmpty()) {
            LOGGER.warn("[stripe] trial_will_end for unknown customer {}", subscription.getCustomer());
            return;
        }
        var owner = ownerOpt.get();

        var changes = new BsonDocument().append("trial_will_end_notified_at",
                new BsonDateTime(ctx.appliedAt().toEpochMilli()));
        var applied = ctx.provider().patchSubscription(owner, changes, ctx.appliedAt());
        if (!applied) {
            return;
        }

        var trialEnd = subscription.getTrialEnd() != null ? Instant.ofEpochSecond(subscription.getTrialEnd()) : null;
        var vars = new HashMap<String, String>();
        vars.put("plan", ctx.conf().byPriceId(firstPriceId(subscription)).map(PriceAttribution::planId).orElse(""));
        vars.put("trial-end-date", trialEnd != null ? trialEnd.toString() : "");
        StripeNotifications.send(emailSender, ctx.req(), ctx.conf(), NotificationConfig.TRIAL_WILL_END, owner, vars);
    }

    private void handleInvoicePaymentSucceeded(Event event, StripeEventContext ctx) {
        var invoice = EventPayloads.deserialize(event, Invoice.class);
        if (invoice == null || !hasSubscription(invoice)) {
            return;
        }

        var ownerOpt = ctx.provider().byStripeCustomerId(ctx.scope(), invoice.getCustomer());
        if (ownerOpt.isEmpty()) {
            LOGGER.warn("[stripe] invoice.payment_succeeded for unknown customer {}", invoice.getCustomer());
            return;
        }

        var changes = new BsonDocument().append("status", new BsonString("active"));
        ctx.provider().patchSubscription(ownerOpt.get(), changes, ctx.appliedAt());
    }

    private void handleInvoicePaymentFailed(Event event, StripeEventContext ctx) {
        var invoice = EventPayloads.deserialize(event, Invoice.class);
        if (invoice == null || !hasSubscription(invoice)) {
            return;
        }

        var ownerOpt = ctx.provider().byStripeCustomerId(ctx.scope(), invoice.getCustomer());
        if (ownerOpt.isEmpty()) {
            LOGGER.warn("[stripe] invoice.payment_failed for unknown customer {}", invoice.getCustomer());
            return;
        }
        var owner = ownerOpt.get();

        var changes = new BsonDocument().append("status", new BsonString("past_due"));
        var applied = ctx.provider().patchSubscription(owner, changes, ctx.appliedAt());
        if (applied) {
            var state = ctx.provider().readSubscription(owner, ctx.defaultPlanId());
            var vars = Map.of("plan", state.plan() != null ? state.plan() : "");
            StripeNotifications.send(emailSender, ctx.req(), ctx.conf(), NotificationConfig.PAYMENT_FAILED, owner, vars);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String firstPriceId(Subscription subscription) {
        var items = subscription.getItems() != null ? subscription.getItems().getData() : List.<SubscriptionItem>of();
        return items.isEmpty() || items.get(0).getPrice() == null ? null : items.get(0).getPrice().getId();
    }

    private SubscriptionState buildState(Subscription subscription, SubscriptionState previous, int licensedCount, StripeConfigData conf) {
        List<SubscriptionItem> items = subscription.getItems() != null
                ? subscription.getItems().getData()
                : List.of();
        var item = items.isEmpty() ? null : items.get(0);

        var priceId = item != null && item.getPrice() != null ? item.getPrice().getId() : null;
        Optional<PriceAttribution> attribution = priceId != null ? conf.byPriceId(priceId) : Optional.empty();

        String planId;
        if (attribution.isPresent()) {
            planId = attribution.get().planId();
        } else {
            planId = previous.plan();
            if (priceId != null) {
                LOGGER.warn("[stripe] subscription {} carries unrecognised price id {} — keeping previous plan {}",
                        subscription.getId(), priceId, planId);
            }
        }

        var seats = item != null && item.getQuantity() != null ? item.getQuantity().intValue() : 1;
        var currentPeriodEnd = item != null && item.getCurrentPeriodEnd() != null
                ? Instant.ofEpochSecond(item.getCurrentPeriodEnd())
                : null;
        var trialEnd = subscription.getTrialEnd() != null ? Instant.ofEpochSecond(subscription.getTrialEnd()) : null;
        var cancelAtPeriodEnd = Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd());

        var limitProbe = new SubscriptionState(planId, priceId, subscription.getStatus(), subscription.getId(),
                trialEnd, currentPeriodEnd, seats, cancelAtPeriodEnd, previous.overLimitSince());
        var limit = Seats.limit(conf.plan(planId), limitProbe);
        var overLimitSince = computeOverLimitSince(previous.overLimitSince(), limit, licensedCount);

        return new SubscriptionState(planId, priceId, subscription.getStatus(), subscription.getId(),
                trialEnd, currentPeriodEnd, seats, cancelAtPeriodEnd, overLimitSince);
    }

    private static Instant computeOverLimitSince(Instant previousOverLimitSince, Integer limit, int licensedCount) {
        if (limit == null || licensedCount <= limit) {
            return null;
        }
        return previousOverLimitSince != null ? previousOverLimitSince : Instant.now();
    }

    private static boolean enteredOverLimit(SubscriptionState previous, SubscriptionState updated) {
        return updated.overLimitSince() != null && !updated.overLimitSince().equals(previous.overLimitSince());
    }

    private static boolean hasSubscription(Invoice invoice) {
        return invoice.getParent() != null
                && invoice.getParent().getSubscriptionDetails() != null
                && invoice.getParent().getSubscriptionDetails().getSubscription() != null;
    }

}
