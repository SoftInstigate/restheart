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
package org.restheart.stripe;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.emails.EmailSender;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.stripe.BillingScope;
import org.restheart.plugins.stripe.NotificationConfig;
import org.restheart.plugins.stripe.PriceAttribution;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.plugins.stripe.SubscriptionOwner;
import org.restheart.plugins.stripe.SubscriptionOwnerProvider;
import org.restheart.plugins.stripe.SubscriptionState;
import org.restheart.security.ACLRegistry;
import org.restheart.stripe.util.RequestOverrides;
import org.restheart.stripe.util.Seats;
import org.restheart.stripe.util.StripeCatalogCache;
import org.restheart.stripe.util.StripeNotifications;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;

/**
 * {@code POST /stripe/webhook} — verifies the {@code Stripe-Signature} header against the
 * raw request body and dispatches billing events to handlers that update the resolved
 * entity's subscription state.
 *
 * <p>This is the module's only public endpoint. Security is provided exclusively by
 * signature verification against {@code stripeConfig.webhook-secret} (or its per-tenant
 * override) — there is no authenticated caller on this path.
 *
 * <p>⚠️ Implements {@link ByteArrayService}, not a JSON/BSON service: the signature is an
 * HMAC-SHA256 over the exact bytes Stripe sent, and any re-serialisation (key reordering,
 * whitespace, number formatting) invalidates it. Nothing is parsed until the signature is
 * verified.
 *
 * <h2>Status codes</h2>
 * <ul>
 *   <li>{@code 400} — signature verification failed, or no webhook secret is configured</li>
 *   <li>{@code 200} — verified event, whether handled, unhandled, or skipped as stale: Stripe
 *       must stop retrying an event that succeeded or that a newer update has superseded</li>
 *   <li>{@code 500} — an unexpected failure (e.g. MongoDB) applying an otherwise-valid event
 *       — Stripe should retry</li>
 * </ul>
 *
 * <h2>Plan attribution</h2>
 * <p>Subscription events carry a Stripe <em>price id</em>, not a plan id. Resolution goes
 * through {@link StripeConfigData#byPriceId}. An unrecognised price id keeps the
 * <em>previous</em> plan rather than falling back to the default plan — silently downgrading
 * a paying customer is the one failure this module must never produce.
 */
@RegisterPlugin(
        name = "stripeWebhookService",
        description = "POST /stripe/webhook — verify Stripe signature and process billing events",
        defaultURI = "/stripe/webhook",
        enabledByDefault = false)
public class StripeWebhookService implements ByteArrayService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeWebhookService.class);

    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    @Inject("stripeConfig")
    private StripeConfigData conf;

    @Inject("stripeService")
    private StripeService stripeService;

    @Inject("stripeCatalogCache")
    private StripeCatalogCache catalogCache;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    /**
     * Not {@code @Inject("emails")} deliberately: every deployment that enables the webhook
     * (i.e. every deployment) would then be forced to also enable {@code restheart-emails},
     * even one that sets all four notifications to {@code enabled: false} — RESTHeart
     * validates every declared {@code @Inject} target at startup and refuses to load a
     * plugin whose target is missing or disabled. Resolved softly instead, the same way
     * {@code StripeService} resolves {@code accountsConfig} — see that class's javadoc.
     */
    @Inject("registry")
    private PluginsRegistry registry;

    private EmailSender emailSender;

    @OnInit
    public void onInit() {
        // The only public endpoint in the module. Exact-path match — must never widen to
        // the other /stripe/* endpoints, which all require authentication.
        aclRegistry.registerAllow(r -> "/stripe/webhook".equals(r.getPath()) && (r.isPost() || r.isOptions()));

        for (var providerRecord : registry.getProviders()) {
            if ("emails".equals(providerRecord.getName()) && providerRecord.isEnabled()) {
                Object value = providerRecord.getInstance().get(null);
                if (value instanceof EmailSender sender) {
                    this.emailSender = sender;
                }
                break;
            }
        }
        if (emailSender == null) {
            LOGGER.info("[stripe] 'emails' plugin not found or not enabled — billing notifications will not be sent");
        }
    }

    @Override
    public void handle(ByteArrayRequest req, ByteArrayResponse res) throws Exception {
        if (req.isOptions()) {
            handleOptions(req);
            return;
        }
        if (!req.isPost()) {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        var rawBody = req.getContentString();
        var sigHeader = req.getHeader(STRIPE_SIGNATURE_HEADER);
        var webhookSecret = RequestOverrides.webhookSecret(req, conf);

        if (webhookSecret == null || webhookSecret.isBlank()) {
            LOGGER.error("[stripe] webhook received but no webhook-secret is configured — rejecting");
            res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }

        Event event;
        try {
            event = Webhook.constructEvent(rawBody, sigHeader, webhookSecret);
        } catch (SignatureVerificationException | IllegalArgumentException e) {
            LOGGER.warn("[stripe] webhook signature verification failed: {}", e.getMessage());
            res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }

        var ctx = new Context(
                req,
                stripeService.getSubscriptionOwnerProvider(),
                RequestOverrides.scope(req, conf),
                RequestOverrides.defaultPlan(req, conf),
                Instant.ofEpochSecond(event.getCreated() != null ? event.getCreated() : Instant.now().getEpochSecond()));

        try {
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
        } catch (RuntimeException e) {
            // None of the current handlers call the Stripe API — they only read the event's
            // own payload and write to MongoDB — so this is a MongoDB or programming error,
            // not a Stripe-communication failure. A future handler that does call back into
            // Stripe (e.g. Subscription.retrieve for extra verification) should catch
            // StripeException separately and answer 502, so Stripe retries a transient
            // outage without conflating it with an internal error.
            LOGGER.error("[stripe] webhook handler for {} failed unexpectedly", event.getType(), e);
            res.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        res.setStatusCode(HttpStatus.SC_OK);
    }

    /** Per-delivery context, resolved once and threaded through the handlers. */
    private record Context(ByteArrayRequest req, SubscriptionOwnerProvider provider, BillingScope scope,
            String defaultPlanId, Instant appliedAt) {
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    /**
     * {@code checkout.session.completed} is intentionally a no-op beyond logging: Stripe
     * always follows it with {@code customer.subscription.created} for the same
     * subscription, which carries the full state (status, price, quantity, period end) —
     * duplicating that write here from the Session object would race the two events
     * against each other for no benefit.
     */
    private void handleCheckoutSessionCompleted(Event event) {
        var session = deserialize(event, Session.class);
        if (session == null) {
            return;
        }
        LOGGER.info("[stripe] checkout completed — owner={}, customer={}, subscription={}",
                session.getClientReferenceId(), session.getCustomer(), session.getSubscription());
    }

    private void handleSubscriptionUpsert(Event event, Context ctx) {
        var subscription = deserialize(event, Subscription.class);
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
        var newState = buildState(subscription, previous, licensedCount);

        var applied = ctx.provider().writeSubscription(owner, newState, ctx.appliedAt());
        if (applied && enteredOverLimit(previous, newState)) {
            LOGGER.info("[stripe] entity {} entered over-limit state (plan={})", owner.id(), newState.plan());
            notifyOverLimit(ctx, owner, newState, licensedCount);
        }
    }

    private void notifyOverLimit(Context ctx, SubscriptionOwner owner, SubscriptionState state, int licensedCount) {
        var planConf = conf.plan(state.plan());
        var limit = Seats.limit(planConf, state);
        var graceDays = RequestOverrides.overLimitGraceDays(ctx.req(), conf, state.plan());
        var graceExpiresAt = Seats.graceExpiresAt(state, graceDays);

        var vars = new HashMap<String, String>();
        vars.put("plan", state.plan());
        vars.put("seats-limit", limit != null ? String.valueOf(limit) : "∞");
        vars.put("seats-licensed", String.valueOf(licensedCount));
        vars.put("grace-expires-date", graceExpiresAt != null ? graceExpiresAt.toString() : "");

        StripeNotifications.send(emailSender, ctx.req(), conf, NotificationConfig.OVER_LIMIT, owner, vars);
    }

    private void handleSubscriptionDeleted(Event event, Context ctx) {
        var subscription = deserialize(event, Subscription.class);
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
        var limit = Seats.limit(conf.plan(ctx.defaultPlanId()), freePreliminary);
        var overLimitSince = computeOverLimitSince(previous.overLimitSince(), limit, licensedCount);

        var newState = new SubscriptionState(ctx.defaultPlanId(), null, "canceled", null, null, null, 1, false, overLimitSince);
        var applied = ctx.provider().writeSubscription(owner, newState, ctx.appliedAt());
        if (applied) {
            if (enteredOverLimit(previous, newState)) {
                LOGGER.info("[stripe] entity {} entered over-limit state (plan={})", owner.id(), newState.plan());
                notifyOverLimit(ctx, owner, newState, licensedCount);
            }
            var vars = Map.of("plan", previous.plan() != null ? previous.plan() : "");
            StripeNotifications.send(emailSender, ctx.req(), conf, NotificationConfig.SUBSCRIPTION_CANCELED, owner, vars);
        }
    }

    private void handleTrialWillEnd(Event event, Context ctx) {
        var subscription = deserialize(event, Subscription.class);
        if (subscription == null) {
            return;
        }

        var ownerOpt = ctx.provider().byStripeCustomerId(ctx.scope(), subscription.getCustomer());
        if (ownerOpt.isEmpty()) {
            LOGGER.warn("[stripe] trial_will_end for unknown customer {}", subscription.getCustomer());
            return;
        }
        var owner = ownerOpt.get();

        // This event changes no real subscription field — the marker is written purely to
        // reuse the staleness guard as an idempotency check, so a redelivered event does
        // not send the notification twice.
        var changes = new BsonDocument().append("trial_will_end_notified_at",
                new BsonDateTime(ctx.appliedAt().toEpochMilli()));
        var applied = ctx.provider().patchSubscription(owner, changes, ctx.appliedAt());
        if (!applied) {
            return;
        }

        var trialEnd = subscription.getTrialEnd() != null ? Instant.ofEpochSecond(subscription.getTrialEnd()) : null;
        var vars = new HashMap<String, String>();
        vars.put("plan", conf.byPriceId(firstPriceId(subscription)).map(PriceAttribution::planId).orElse(""));
        vars.put("trial-end-date", trialEnd != null ? trialEnd.toString() : "");
        StripeNotifications.send(emailSender, ctx.req(), conf, NotificationConfig.TRIAL_WILL_END, owner, vars);
    }

    private void handleInvoicePaymentSucceeded(Event event, Context ctx) {
        var invoice = deserialize(event, Invoice.class);
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

    private void handleInvoicePaymentFailed(Event event, Context ctx) {
        var invoice = deserialize(event, Invoice.class);
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
            StripeNotifications.send(emailSender, ctx.req(), conf, NotificationConfig.PAYMENT_FAILED, owner, vars);
        }
    }

    private static String firstPriceId(Subscription subscription) {
        var items = subscription.getItems() != null ? subscription.getItems().getData() : List.<SubscriptionItem>of();
        return items.isEmpty() || items.get(0).getPrice() == null ? null : items.get(0).getPrice().getId();
    }

    // ── Shared state-building ───────────────────────────────────────────────

    /**
     * Builds the full {@link SubscriptionState} for a Stripe {@link Subscription}, resolving
     * plan attribution and the over-limit transition against the entity's previous state and
     * current licensed count.
     *
     * <p>Assumes one line item per subscription — the shape this module always creates
     * (see {@code StripeCheckoutService}). {@code current_period_end} lives on
     * {@link SubscriptionItem}, not on {@code Subscription} itself, in the pinned API version.
     */
    private SubscriptionState buildState(Subscription subscription, SubscriptionState previous, int licensedCount) {
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

    /**
     * @param previousOverLimitSince the entity's prior {@code over_limit_since} — carried
     *                               forward unchanged if still over limit, so a partial
     *                               revocation never restarts the grace period
     * @param limit                  the new seat limit, {@code null} for unlimited
     * @param licensedCount          the entity's current licensed member count
     * @return the timestamp to store: {@code null} if within limit (or unlimited), the
     *         existing timestamp if already over limit, or now if this is the transition
     */
    private static Instant computeOverLimitSince(Instant previousOverLimitSince, Integer limit, int licensedCount) {
        if (limit == null || licensedCount <= limit) {
            return null;
        }
        return previousOverLimitSince != null ? previousOverLimitSince : Instant.now();
    }

    /** @return {@code true} only on the actual transition into the over-limit state — not while already over. */
    private static boolean enteredOverLimit(SubscriptionState previous, SubscriptionState updated) {
        return updated.overLimitSince() != null && !updated.overLimitSince().equals(previous.overLimitSince());
    }

    private static boolean hasSubscription(Invoice invoice) {
        return invoice.getParent() != null
                && invoice.getParent().getSubscriptionDetails() != null
                && invoice.getParent().getSubscriptionDetails().getSubscription() != null;
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
