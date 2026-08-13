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

import java.util.Map;
import java.util.Optional;

/**
 * Immutable record holding all configuration parameters for {@code restheart-stripe}.
 *
 * <p>Produced by the {@code stripeConfig} provider and injected via
 * {@code @Inject("stripeConfig")}.
 *
 * <p>This record lives in {@code restheart-commons} so that downstream modules can
 * depend only on {@code restheart-commons} at compile time, without pulling in the
 * {@code restheart-stripe} module — mirroring {@code AccountsConfigData}.
 *
 * <p>{@code db} is sourced from {@code mongoRealmAuthenticator.users-db}, the same way
 * {@code accountsConfig} sources it, so that {@code restheart-stripe} always reads and
 * writes team documents where {@code restheart-accounts} writes them. There is no
 * {@code stripeConfig.db} key: two independent settings could drift apart, and the
 * failure is silent — checkout succeeds, the webhook updates a document, and the
 * running application never sees the plan change.
 */
public record StripeConfigData(

        // ── Stripe API credentials ──────────────────────────────────────────────

        /** Stripe secret key ({@code sk_test_...} or {@code sk_live_...}). */
        String secretKey,

        /** Webhook signing secret ({@code whsec_...}). */
        String webhookSecret,

        // ── Plan catalog ─────────────────────────────────────────────────────────

        /** The configured plan catalog, keyed by plan id. Never {@code null}. */
        Map<String, PlanConfig> plans,

        /** The plan id assigned to an entity with no subscription. Must name a key in {@link #plans}. */
        String defaultPlan,

        /** Trial days used when a plan does not declare its own {@link PlanConfig#trialPeriodDays()}. */
        int defaultTrialPeriodDays,

        // ── MongoDB ───────────────────────────────────────────────────────────────

        /** MongoDB database containing the teams collection, sourced from {@code mongoRealmAuthenticator}. */
        String db,

        /** Name of the teams collection. Default: {@code "teams"}. */
        String teamsCollection,

        // ── URLs ──────────────────────────────────────────────────────────────────

        /** Stripe Checkout success redirect URL. */
        String successUrl,

        /** Stripe Checkout cancel redirect URL. */
        String cancelUrl,

        /** Customer Portal return URL. */
        String portalReturnUrl,

        // ── Seat over-limit grace ────────────────────────────────────────────────

        /**
         * Days an entity may remain over its seat limit before every user of that
         * entity is blocked ({@code @subscription.licensed} returns {@code false}
         * for all of them). {@code 0} blocks immediately on the downgrade;
         * {@code null} means the over-limit state never expires — nothing is ever
         * blocked automatically. Overridable per plan via
         * {@link PlanConfig#overLimitGraceDays()}.
         */
        Integer overLimitGraceDays,

        // ── Billing notifications ────────────────────────────────────────────────

        /** Notification configuration, keyed by name — see {@link NotificationConfig}. Never {@code null}. */
        Map<String, NotificationConfig> notifications) {

    /** @return {@code true} if {@link #secretKey()} is a live-mode key ({@code sk_live_...}). */
    public boolean isLiveMode() {
        return secretKey != null && secretKey.startsWith("sk_live_");
    }

    /** @return the configured plan, or {@code null} if {@code planId} is not in the catalog. */
    public PlanConfig plan(String planId) {
        return planId == null ? null : plans.get(planId);
    }

    /**
     * @param planId   a configured plan id
     * @param interval {@code "month"} or {@code "year"}
     * @return the Stripe price id for that plan and interval, or {@code null} if the
     *         plan is unknown or has no price for that interval
     */
    public String priceId(String planId, String interval) {
        var plan = plan(planId);
        return plan == null ? null : plan.priceId(interval);
    }

    /**
     * Resolves a Stripe price id back to the plan it belongs to. Iterates the
     * (typically small) configured catalog — this runs on the webhook path, never
     * on the request hot path, so no reverse index is precomputed or cached.
     *
     * @param priceId a Stripe price id as carried by a subscription item
     * @return the matching plan and interval, or empty if the price id is not in
     *         the configured catalog — see {@code TeamRepository} on why an unknown
     *         price id must not resolve to {@link #defaultPlan()}
     */
    public Optional<PriceAttribution> byPriceId(String priceId) {
        if (priceId == null || priceId.isBlank()) {
            return Optional.empty();
        }

        for (var entry : plans.entrySet()) {
            var plan = entry.getValue();
            if (priceId.equals(plan.priceIdMonthly())) {
                return Optional.of(new PriceAttribution(entry.getKey(), "month"));
            }
            if (priceId.equals(plan.priceIdAnnual())) {
                return Optional.of(new PriceAttribution(entry.getKey(), "year"));
            }
        }

        return Optional.empty();
    }

    /**
     * @param planId a configured plan id
     * @return the effective trial period days for that plan: {@link PlanConfig#trialPeriodDays()}
     *         if declared, otherwise {@link #defaultTrialPeriodDays()}
     */
    public int effectiveTrialPeriodDays(String planId) {
        var plan = plan(planId);
        return plan != null && plan.trialPeriodDays() != null
                ? plan.trialPeriodDays()
                : defaultTrialPeriodDays;
    }

    /**
     * @param planId a configured plan id
     * @return the effective over-limit grace days for that plan: {@link PlanConfig#overLimitGraceDays()}
     *         if declared, otherwise {@link #overLimitGraceDays()}. {@code null} means never expires.
     */
    public Integer effectiveOverLimitGraceDays(String planId) {
        var plan = plan(planId);
        return plan != null && plan.overLimitGraceDays() != null
                ? plan.overLimitGraceDays()
                : overLimitGraceDays;
    }

    /**
     * @param name a notification name, see {@link NotificationConfig}
     * @return the configured notification, falling back to a default-shaped one with
     *         the name's default {@code enabled} and no template override, if not
     *         explicitly configured
     */
    public NotificationConfig notification(String name) {
        var n = notifications.get(name);
        return n != null ? n : new NotificationConfig(NotificationConfig.defaultEnabled(name), null);
    }
}
