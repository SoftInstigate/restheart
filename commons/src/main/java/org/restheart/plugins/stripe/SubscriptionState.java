/*-
 * ========================LICENSE_START=================================
 * restheart-commons
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
package org.restheart.plugins.stripe;

import java.time.Instant;

/**
 * Immutable snapshot of an entity's subscription state, as read/written through
 * {@link SubscriptionOwnerProvider#readSubscription(SubscriptionOwner)} /
 * {@link SubscriptionOwnerProvider#writeSubscription(SubscriptionOwner, SubscriptionState, Instant)}.
 *
 * <p>Lives in {@code restheart-commons}, alongside {@link SubscriptionOwnerProvider}, since
 * the SPI's method signatures reference it — a downstream module implementing the SPI must
 * not need to depend on {@code restheart-stripe}.
 *
 * <p>This is what {@code GET /stripe/subscription} serialises to clients and what the
 * {@code @subscription} ACL variable exposes to predicates. It deliberately does not carry
 * the module's own replay-idempotency bookkeeping (the last-applied-event timestamp) — that
 * is the provider's business, kept next to this state, not inside it, so that a replayed or
 * out-of-order webhook event cannot corrupt what a client or an ACL predicate reads.
 *
 * <p>It also does not carry how many seats are <em>licensed</em> — that is counted from the
 * entity's memberships (see {@link SubscriptionOwnerProvider#licensedCount(SubscriptionOwner)}),
 * not stored here. {@link #seats()} is the Stripe-side number: the purchased quantity in
 * {@code per-seat} mode, {@code 1} in {@code capped} mode.
 */
public record SubscriptionState(

        /** The configured plan id (see {@code StripeConfigData.plans}), or the configured default plan. */
        String plan,

        /**
         * The raw Stripe price id backing {@link #plan()}, or {@code null} when there is no
         * active subscription. Kept alongside the resolved plan id so that attribution is
         * recomputable and an anomaly (an unrecognised price) is diagnosable, and so it
         * survives a catalog configuration change that the plan id alone would not.
         */
        String priceId,

        /**
         * Stripe subscription status: {@code trialing}, {@code active}, {@code past_due},
         * {@code canceled}, {@code unpaid}. {@code null} when there is no Stripe subscription.
         */
        String status,

        /** Stripe Subscription id ({@code sub_xxx}), or {@code null}. */
        String stripeSubscriptionId,

        /** When the trial ends, or {@code null} when not in a trial. */
        Instant trialEnd,

        /** When the current billing period ends, or {@code null} with no active subscription. */
        Instant currentPeriodEnd,

        /**
         * The Stripe-side seat count: purchased quantity for {@code per-seat} plans, {@code 1}
         * for {@code capped} plans. Not the number of licences assigned — see
         * {@link SubscriptionOwnerProvider#licensedCount(SubscriptionOwner)} for that.
         */
        int seats,

        /** Whether the subscription is scheduled to cancel at the end of the current period. */
        boolean cancelAtPeriodEnd,

        /**
         * When this entity first went over its seat limit, or {@code null} when it is not
         * over limit. Must survive a full state replacement — recomputing it from scratch on
         * every routine webhook delivery would restart the grace period every time and the
         * expiry would never arrive. Cleared only when the entity returns within its limit;
         * a partial revocation that is still over limit must not clear or restart it.
         */
        Instant overLimitSince) {

    /** @return a default free/unsubscribed state for the given plan id, with no Stripe linkage. */
    public static SubscriptionState defaultFor(String planId) {
        return new SubscriptionState(planId, null, null, null, null, null, 1, false, null);
    }

    /** @return {@code true} if the subscription is currently active or trialing. */
    public boolean isActive() {
        return "active".equals(status) || "trialing".equals(status);
    }

    /** @return {@code true} if the subscription is in a trial period. */
    public boolean isTrialing() {
        return "trialing".equals(status);
    }

    /** @return {@code true} if this entity is currently over its seat limit. */
    public boolean isOverLimit() {
        return overLimitSince != null;
    }
}
