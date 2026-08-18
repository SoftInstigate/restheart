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

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.plugins.BsonService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.plugins.stripe.SubscriptionOwner;
import org.restheart.plugins.stripe.SubscriptionOwnerProvider;
import org.restheart.stripe.util.RequestOverrides;
import org.restheart.stripe.util.Seats;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;

/**
 * {@code /stripe/licenses} — grants, revokes, and lists seat licences on the caller's
 * entity. Follows the same convention as {@code restheart-accounts}' own
 * {@code DELETE /auth/remove-member}: the target member is named in the request body,
 * not in a path segment — RESTHeart services match a fixed URI, not a path template.
 *
 * <pre>{@code
 * POST   /stripe/licenses   {"userId": "alice@example.com"}   -> grant
 * DELETE /stripe/licenses   {"userId": "alice@example.com"}   -> revoke
 * GET    /stripe/licenses                                     -> list
 * }</pre>
 *
 * <p>Every operation is gated on {@code canManageBilling}: granting a licence either
 * spends money (per-seat) or consumes a scarce resource (capped) — the same gate as the
 * Portal and checkout, not a separate policy.
 *
 * <p>A licence is never revoked automatically. If the entity is over its seat limit, the
 * owner sees it (via {@code GET /stripe/subscription} / {@code @subscription.seats}) and
 * revokes down to the limit themselves — see #683 on why blocking is total and reversible
 * rather than the module choosing who to cut.
 *
 * <p>{@code 404} when {@code rh-stripe-subscriptions-disabled} is attached — see
 * {@link RequestOverrides}.
 */
@RegisterPlugin(
        name = "stripeLicensesService",
        description = "/stripe/licenses — grant, revoke, and list seat licences",
        defaultURI = "/stripe/licenses",
        enabledByDefault = false)
public class StripeLicensesService implements BsonService {

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
        if (!req.isAuthenticated()) {
            res.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            return;
        }
        if (!initTracker.isSubscriptionsInitialized(RequestOverrides.db(req, conf))) {
            res.setInError(HttpStatus.SC_SERVICE_UNAVAILABLE, "Stripe subscriptions module is not initialized");
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

        if (req.isGet()) {
            list(req, res, provider, owner);
            return;
        }

        if (!(req.getContent() instanceof BsonDocument body) || !body.containsKey("userId")
                || !body.get("userId").isString()) {
            res.setInError(HttpStatus.SC_BAD_REQUEST, "'userId' is required");
            return;
        }
        var userId = body.getString("userId").getValue();

        if (req.isPost()) {
            grant(req, res, provider, owner, userId);
        } else if (req.isDelete()) {
            provider.revokeLicense(owner, userId);
            res.setStatusCode(HttpStatus.SC_OK);
        } else {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    private void list(BsonRequest req, BsonResponse res, SubscriptionOwnerProvider provider, SubscriptionOwner owner) {
        var limit = currentLimit(req, provider, owner);
        var licensedIds = provider.licensedUserIds(owner);
        var available = Seats.available(limit, licensedIds.size());

        var idsArray = new BsonArray();
        licensedIds.forEach(id -> idsArray.add(new BsonString(id)));

        res.setStatusCode(HttpStatus.SC_OK);
        res.setContent(BsonUtils.document()
                .put("licensed", idsArray)
                .put("seats", BsonUtils.document()
                        .put("limit", limit != null ? new BsonInt32(limit) : BsonNull.VALUE)
                        .put("licensed", new BsonInt32(licensedIds.size()))
                        .put("available", available != null ? new BsonInt32(available) : BsonNull.VALUE)));
    }

    private void grant(BsonRequest req, BsonResponse res, SubscriptionOwnerProvider provider,
                       SubscriptionOwner owner, String userId) {
        var limit = currentLimit(req, provider, owner);
        var result = provider.grantLicense(owner, userId, limit);

        switch (result) {
            case GRANTED -> res.setStatusCode(HttpStatus.SC_CREATED);
            case ALREADY_LICENSED -> res.setStatusCode(HttpStatus.SC_OK);
            case MEMBER_NOT_FOUND -> res.setStatusCode(HttpStatus.SC_NOT_FOUND);
            case NO_SEAT_AVAILABLE -> res.setStatusCode(HttpStatus.SC_CONFLICT);
        }
    }

    /** The entity's current seat limit — {@code null} for unlimited. */
    private Integer currentLimit(BsonRequest req, SubscriptionOwnerProvider provider, SubscriptionOwner owner) {
        var defaultPlanId = RequestOverrides.defaultPlan(req, conf);
        var state = provider.readSubscription(owner, defaultPlanId);
        var plans = RequestOverrides.plans(req, conf);
        var planConf = plans.get(state.plan());
        return Seats.limit(planConf, state);
    }
}
