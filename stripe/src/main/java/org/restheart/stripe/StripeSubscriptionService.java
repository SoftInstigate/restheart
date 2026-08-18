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
import org.restheart.stripe.util.SubscriptionView;
import org.restheart.utils.HttpStatus;

/**
 * {@code GET /stripe/subscription} — returns the caller's entity's current subscription
 * state: plan, status, seats, trial/period dates.
 *
 * <p>A {@code 200} with the {@code default-plan} state is returned for an entity with no
 * subscription — a free/unsubscribed entity is a legitimate state, not a missing resource.
 *
 * <p>Reading subscription state is not a billing-management operation — any member of the
 * entity may see the plan they are on — so this endpoint does not gate on
 * {@code canManageBilling}, unlike checkout, the Portal, and licence grants/revokes.
 *
 * <p>Reuses {@link SubscriptionView#build}, the same computation the {@code @subscription}
 * ACL variable uses, so this response and what the ACL plan gates enforce never disagree.
 *
 * <p>{@code 404} when {@code rh-stripe-subscriptions-disabled} is attached — see
 * {@link RequestOverrides}.
 */
@RegisterPlugin(
        name = "stripeSubscriptionService",
        description = "GET /stripe/subscription — the caller's entity's subscription state",
        defaultURI = "/stripe/subscription",
        enabledByDefault = false)
public class StripeSubscriptionService implements BsonService {

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
        if (RequestOverrides.subscriptionsDisabled(req)) {
            res.setStatusCode(HttpStatus.SC_NOT_FOUND);
            return;
        }
        if (!req.isGet()) {
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

        var defaultPlanId = RequestOverrides.defaultPlan(req, conf);
        var state = provider.readSubscription(owner, defaultPlanId);

        res.setStatusCode(HttpStatus.SC_OK);
        res.setContent(SubscriptionView.build(provider, req, conf, owner, state));
    }
}
