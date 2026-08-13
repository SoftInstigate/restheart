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
package org.restheart.stripe.util;

import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.restheart.exchange.ServiceRequest;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.plugins.stripe.SubscriptionOwner;
import org.restheart.plugins.stripe.SubscriptionOwnerProvider;
import org.restheart.plugins.stripe.SubscriptionState;

/**
 * Builds the client-facing view of a subscription — the single computation shared by
 * {@code GET /stripe/subscription} and the {@code @subscription} ACL variable, so the
 * billing page a client renders and what the ACL plan gates enforce can never disagree.
 *
 * <p>Deliberately excludes {@link SubscriptionState#stripeSubscriptionId()} and
 * {@link SubscriptionState#priceId()} — internal Stripe identifiers with no client use —
 * and {@code SubscriptionOwner#stripeCustomerId()}. Serialises named fields rather than
 * mapping the record wholesale, so an added internal field does not leak by default.
 *
 * <p>⚠️ {@code licensed} is a pure fact — whether the caller holds a seat licence — not
 * conditioned on the entity's over-limit state. The module states {@code seats.over_limit},
 * {@code seats.over_limit_since} and {@code seats.over_limit_days}; whether and when that
 * should affect access is a policy decision left to the deployment's own ACL predicate
 * (composing {@code over_limit_days} with the {@code gte}/{@code lte} predicates), not
 * something this module decides on the deployment's behalf.
 */
public final class SubscriptionView {

    private SubscriptionView() {
    }

    /**
     * @param provider the active {@link SubscriptionOwnerProvider}
     * @param req      the current request, for {@link SubscriptionOwnerProvider#isLicensed}
     *                 (the calling user's own licence) and for resolving overrides
     * @param conf     the effective (possibly per-tenant) configuration
     * @param owner    the resolved entity
     * @param state    the entity's current subscription state
     * @return the view document — see the class javadoc for its shape
     */
    public static BsonDocument build(SubscriptionOwnerProvider provider, ServiceRequest<?> req,
            StripeConfigData conf, SubscriptionOwner owner, SubscriptionState state) {

        var plans = RequestOverrides.plans(req, conf);
        var planConf = plans.get(state.plan());
        var limit = Seats.limit(planConf, state);
        var licensedCount = provider.licensedCount(owner);
        var available = Seats.available(limit, licensedCount);
        var overLimitDays = Seats.overLimitDays(state);

        var account = req.getAuthenticatedAccount();
        var callerId = account != null && account.getPrincipal() != null ? account.getPrincipal().getName() : null;
        var licensed = callerId != null && provider.isLicensed(owner, callerId);

        var seatsDoc = new BsonDocument()
                .append("limit", limit != null ? new BsonInt32(limit) : BsonNull.VALUE)
                .append("licensed", new BsonInt32(licensedCount))
                .append("available", available != null ? new BsonInt32(available) : BsonNull.VALUE)
                .append("over_limit", BsonBoolean.valueOf(state.isOverLimit()));
        if (state.overLimitSince() != null) {
            seatsDoc.append("over_limit_since", new BsonDateTime(state.overLimitSince().toEpochMilli()));
        }
        if (overLimitDays != null) {
            seatsDoc.append("over_limit_days", new BsonInt32(overLimitDays));
        }

        var doc = new BsonDocument()
                .append("plan", new BsonString(state.plan() != null ? state.plan() : ""))
                .append("active", BsonBoolean.valueOf(state.isActive()))
                .append("licensed", BsonBoolean.valueOf(licensed))
                .append("cancel_at_period_end", BsonBoolean.valueOf(state.cancelAtPeriodEnd()))
                .append("seats", seatsDoc);

        if (state.status() != null) {
            doc.append("status", new BsonString(state.status()));
        }
        if (state.trialEnd() != null) {
            doc.append("trial_end", new BsonDateTime(state.trialEnd().toEpochMilli()));
        }
        if (state.currentPeriodEnd() != null) {
            doc.append("current_period_end", new BsonDateTime(state.currentPeriodEnd().toEpochMilli()));
        }

        return doc;
    }
}
