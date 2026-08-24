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

import org.bson.BsonDocument;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.plugins.BsonService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.stripe.SeatsMode;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.stripe.util.CustomerProvisioning;
import org.restheart.stripe.util.RequestOverrides;
import org.restheart.stripe.util.StripeIds;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;

/**
 * {@code POST /stripe/checkout} — creates a Stripe Checkout session for upgrading the
 * caller's entity to the requested plan.
 *
 * <p>Request body: {@code {"plan": "gold", "interval": "month"|"year"}}.
 * Response {@code 201}: {@code {"url": "https://checkout.stripe.com/..."}}
 *
 * <p>This is the only path in the module that creates a Stripe Customer — see
 * {@link CustomerProvisioning}. There is no {@code 402}: an entity with no Customer gets
 * one here, on demand.
 *
 * <p>Requires the caller to be authenticated and to be allowed to manage the resolved
 * entity's billing ({@link org.restheart.plugins.stripe.SubscriptionOwnerProvider#canManageBilling}) —
 * starting a checkout commits the entity to a recurring charge.
 *
 * <p>{@code 404} when {@code rh-stripe-subscriptions-disabled} is attached — see
 * {@link RequestOverrides}.
 */
@RegisterPlugin(
        name = "stripeCheckoutService",
        description = "POST /stripe/checkout — create a Stripe Checkout session",
        defaultURI = "/stripe/checkout",
        enabledByDefault = false)
public class StripeCheckoutService implements BsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeCheckoutService.class);

    @Inject("stripeConfig")
    private StripeConfigData conf;

    @Inject("stripeService")
    private StripeService stripeService;

    @Inject("stripeInitTracker")
    private StripeInitTracker initTracker;

    @Override
    public void handle(BsonRequest req, BsonResponse res) throws Exception {
        if (req.isOptions()) {
            handleOptions(req);
            return;
        }
        if (RequestOverrides.subscriptionsDisabled(req)) {
            res.setStatusCode(HttpStatus.SC_NOT_FOUND);
            return;
        }
        if (!req.isPost()) {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }
        if (!req.isAuthenticated()) {
            res.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            return;
        }
        if (!initTracker.isSubscriptionsInitialized(RequestOverrides.db(req, conf))) {
            res.setInError(HttpStatus.SC_SERVICE_UNAVAILABLE, "Stripe subscriptions module is not initialized");
            return;
        }

        if (!(req.getContent() instanceof BsonDocument body)) {
            res.setInError(HttpStatus.SC_BAD_REQUEST, "request body must be a JSON object");
            return;
        }

        var planId = stringField(body, "plan");
        var interval = stringField(body, "interval");
        if (planId == null || interval == null || !("month".equals(interval) || "year".equals(interval))) {
            res.setInError(HttpStatus.SC_BAD_REQUEST, "'plan' and 'interval' ('month' or 'year') are required");
            return;
        }

        var provider = stripeService.getSubscriptionOwnerProvider();
        var scope = RequestOverrides.scope(req, conf);

        var ownerOpt = provider.fromRequest(req, scope);
        if (ownerOpt.isEmpty()) {
            res.setStatusCode(HttpStatus.SC_FORBIDDEN);
            return;
        }
        var owner = ownerOpt.get();

        if (!provider.canManageBilling(req, owner)) {
            res.setStatusCode(HttpStatus.SC_FORBIDDEN);
            return;
        }

        var plans = RequestOverrides.plans(req, conf);
        var planConf = plans.get(planId);
        if (planConf == null || !planConf.purchasableFor(interval)) {
            res.setInError(HttpStatus.SC_BAD_REQUEST, "plan '" + planId + "' is not purchasable for interval '" + interval + "'");
            return;
        }

        var defaultPlanId = RequestOverrides.defaultPlan(req, conf);
        var currentState = provider.readSubscription(owner, defaultPlanId);
        if (currentState.isActive()) {
            res.setStatusCode(HttpStatus.SC_CONFLICT);
            return;
        }

        var apiKey = RequestOverrides.secretKey(req, conf);
        var ownerId = StripeIds.toIdString(owner.id());

        String customerId;
        try {
            customerId = CustomerProvisioning.ensureCustomer(provider, owner, apiKey);
        } catch (StripeException e) {
            LOGGER.error("[stripe] failed to provision Customer for owner {}: {}", ownerId, e.getMessage());
            res.setInError(HttpStatus.SC_BAD_GATEWAY, "unable to reach Stripe");
            return;
        }

        var priceId = planConf.priceId(interval);
        var seatsMode = planConf.seats() != null ? planConf.seats().mode() : SeatsMode.UNLIMITED;
        var isPerSeat = seatsMode == SeatsMode.PER_SEAT;

        // Pre-fill with the current licensed count for per-seat plans (at least 1) — the
        // buyer can still adjust it in the hosted page, since adjustable_quantity is enabled
        // only for per-seat. For capped plans the price already includes the cap: quantity
        // must stay 1, and adjustable_quantity must stay disabled, or the buyer could
        // multiply the cap by raising it.
        var quantity = isPerSeat ? Math.max(1, provider.licensedCount(owner)) : 1;

        var lineItemBuilder = SessionCreateParams.LineItem.builder()
                .setPrice(priceId)
                .setQuantity((long) quantity)
                .setAdjustableQuantity(SessionCreateParams.LineItem.AdjustableQuantity.builder()
                        .setEnabled(isPerSeat)
                        .build());

        var sessionBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .setClientReferenceId(ownerId)
                .setSuccessUrl(RequestOverrides.successUrl(req, conf))
                .setCancelUrl(RequestOverrides.cancelUrl(req, conf))
                .addLineItem(lineItemBuilder.build())
                .putMetadata("owner_id", ownerId)
                .putMetadata("plan", planId);

        var trialDays = conf.effectiveTrialPeriodDays(planId);
        if (trialDays > 0) {
            sessionBuilder.setSubscriptionData(
                    SessionCreateParams.SubscriptionData.builder()
                            .setTrialPeriodDays((long) trialDays)
                            .putMetadata("owner_id", ownerId)
                            .putMetadata("plan", planId)
                            .build());
        }

        var opts = RequestOptions.builder()
                .setApiKey(apiKey)
                .setIdempotencyKey("stripe-checkout-" + ownerId + "-" + planId + "-" + interval)
                .build();

        Session session;
        try {
            session = Session.create(sessionBuilder.build(), opts);
        } catch (StripeException e) {
            LOGGER.error("[stripe] failed to create Checkout session for owner {}: {}", ownerId, e.getMessage());
            res.setInError(HttpStatus.SC_BAD_GATEWAY, "unable to reach Stripe");
            return;
        }

        res.setStatusCode(HttpStatus.SC_CREATED);
        res.setContent(BsonUtils.document().put("url", session.getUrl()));
    }

    private static String stringField(BsonDocument body, String key) {
        return body.containsKey(key) && body.get(key).isString() ? body.getString(key).getValue() : null;
    }
}
