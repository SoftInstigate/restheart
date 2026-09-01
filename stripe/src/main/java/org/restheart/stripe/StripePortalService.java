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

import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.plugins.BsonService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.stripe.util.RequestOverrides;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stripe.exception.StripeException;
import com.stripe.model.billingportal.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.billingportal.SessionCreateParams;

/**
 * {@code POST /stripe/portal} — creates a Stripe Customer Portal session so the caller's
 * entity can manage its own billing: payment method, invoices, plan change, cancellation.
 *
 * <p>Response {@code 201}: {@code {"url": "https://billing.stripe.com/..."}}
 *
 * <p>⚠️ Does not provision a Stripe Customer — see {@link org.restheart.stripe.util.CustomerProvisioning}.
 * An entity with no Customer has no subscription, no payment method and no invoices, so the
 * Portal would open on an empty page: a {@code 402} instead.
 *
 * <p>Gated on {@code canManageBilling}: the Portal gives full control over the subscription
 * (cancel it, change plan, see every invoice and the billing address on them) — not
 * something every member of an entity should be able to do.
 *
 * <p>{@code 404} when {@code rh-stripe-subscriptions-disabled} is attached — see
 * {@link RequestOverrides}.
 */
@RegisterPlugin(
        name = "stripePortalService",
        description = "POST /stripe/portal — create a Stripe Customer Portal session",
        defaultURI = "/stripe/portal",
        enabledByDefault = false)
public class StripePortalService implements BsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripePortalService.class);

    @Inject("stripeConfig")
    private StripeConfigData conf;

    @Inject("stripeService")
    private StripeService stripeService;

    @Override
    public void handle(BsonRequest req, BsonResponse res) throws Exception {
        if (req.isOptions()) {
            handleOptions(req);
            return;
        }
        // The portal belongs to whoever has a Stripe Customer, and since products
        // mode started provisioning one, that is no longer only subscribers. A
        // shop's buyer has cards, past payments and invoices to look at exactly
        // as a subscriber does — it is Stripe's page either way, and it renders
        // what the Customer holds.
        //
        // Gone with it: the subscriptions-initialised check below, which asked
        // whether a collection this endpoint never reads exists. It answered 503
        // "Stripe subscriptions module is not initialized" to a shop that has no
        // subscriptions and never will, for a page that would have worked.
        if (RequestOverrides.subscriptionsDisabled(req) && RequestOverrides.productsDisabled(req)) {
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

        if (owner.stripeCustomerId() == null || owner.stripeCustomerId().isBlank()) {
            res.setStatusCode(HttpStatus.SC_PAYMENT_REQUIRED);
            return;
        }

        var apiKey = RequestOverrides.secretKey(req, conf);

        // Where Stripe sends them back.
        //
        // `portal-return-url` is a subscriptions setting, and a shop that sells
        // products has no reason to have set it — so fall back to where an
        // abandoned checkout goes, which is a page of that same shop and the
        // nearest thing to "back where you were". Stripe rejects a session with
        // no return url at all, so an empty one is a 400 the buyer cannot read.
        var returnUrl = RequestOverrides.portalReturnUrl(req, conf);
        if (returnUrl == null || returnUrl.isBlank()) {
            var products = RequestOverrides.products(req, conf);
            returnUrl = products != null ? products.cancelUrl() : null;
        }
        if (returnUrl == null || returnUrl.isBlank()) {
            res.setInError(HttpStatus.SC_SERVICE_UNAVAILABLE,
                    "No return url configured: set subscriptions.portal-return-url "
                            + "or products.cancel-url");
            return;
        }

        var params = SessionCreateParams.builder()
                .setCustomer(owner.stripeCustomerId())
                .setReturnUrl(returnUrl)
                .build();

        var opts = RequestOptions.builder().setApiKey(apiKey).build();

        Session session;
        try {
            session = Session.create(params, opts);
        } catch (StripeException e) {
            LOGGER.error("[stripe] failed to create Portal session for customer {}: {}",
                    owner.stripeCustomerId(), e.getMessage());
            res.setInError(HttpStatus.SC_BAD_GATEWAY, "unable to reach Stripe");
            return;
        }

        res.setStatusCode(HttpStatus.SC_CREATED);
        res.setContent(BsonUtils.document().put("url", session.getUrl()));
    }
}
