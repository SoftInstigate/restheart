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

        var params = SessionCreateParams.builder()
                .setCustomer(owner.stripeCustomerId())
                .setReturnUrl(RequestOverrides.portalReturnUrl(req, conf))
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
