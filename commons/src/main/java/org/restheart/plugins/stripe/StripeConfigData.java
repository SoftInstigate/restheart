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
 *
 * <p>The config is restructured into {@code subscriptions} and {@code products}
 * sub-sections. For backward compatibility, subscriptions fields can also be specified
 * at the top level (flat format). If the {@code subscriptions} sub-section is present,
 * it takes precedence.
 */
public record StripeConfigData(

        // ── Stripe API credentials (shared) ─────────────────────────────────

        /** Stripe secret key ({@code sk_test_...} or {@code sk_live_...}). */
        String secretKey,

        /** Webhook signing secret ({@code whsec_...}). */
        String webhookSecret,

        // ── MongoDB (shared, from restheart-accounts) ───────────────────────

        /** MongoDB database containing the teams collection, sourced from {@code mongoRealmAuthenticator}. */
        String db,

        /** Name of the teams collection. Default: {@code "teams"}. */
        String teamsCollection,

        // ── Mode sub-configs ────────────────────────────────────────────────

        /** Subscriptions mode configuration. */
        SubscriptionsConfig subscriptions,

        /** Products mode configuration. */
        ProductsConfig products) {

    /** @return {@code true} if {@link #secretKey()} is a live-mode key ({@code sk_live_...}). */
    public boolean isLiveMode() {
        return secretKey != null && secretKey.startsWith("sk_live_");
    }

    // ── Backward-compatible convenience methods (delegate to subscriptions) ──

    /** @return the configured plan, or {@code null} if {@code planId} is not in the catalog. */
    public PlanConfig plan(String planId) {
        return subscriptions == null ? null : subscriptions.plan(planId);
    }

    /**
     * @param planId   a configured plan id
     * @param interval {@code "month"} or {@code "year"}
     * @return the Stripe price id for that plan and interval, or {@code null}
     */
    public String priceId(String planId, String interval) {
        return subscriptions == null ? null : subscriptions.priceId(planId, interval);
    }

    /**
     * Resolves a Stripe price id back to the plan it belongs to.
     *
     * @param priceId a Stripe price id as carried by a subscription item
     * @return the matching plan and interval, or empty
     */
    public Optional<PriceAttribution> byPriceId(String priceId) {
        return subscriptions == null ? Optional.empty() : subscriptions.byPriceId(priceId);
    }

    /**
     * @param planId a configured plan id
     * @return the effective trial period days for that plan
     */
    public int effectiveTrialPeriodDays(String planId) {
        return subscriptions == null ? 0 : subscriptions.effectiveTrialPeriodDays(planId);
    }

    /**
     * @param name a notification name
     * @return the configured notification, falling back to a default-shaped one
     */
    public NotificationConfig notification(String name) {
        return subscriptions == null
                ? new NotificationConfig(NotificationConfig.defaultEnabled(name), null)
                : subscriptions.notification(name);
    }

    // ── Deprecated accessors for flat-format backward compatibility ─────────

    /**
     * @deprecated use {@link #subscriptions()} and access fields from there.
     */
    @Deprecated
    public Map<String, PlanConfig> plans() {
        return subscriptions == null ? Map.of() : subscriptions.plans();
    }

    /**
     * @deprecated use {@link #subscriptions()} and access fields from there.
     */
    @Deprecated
    public String defaultPlan() {
        return subscriptions == null ? null : subscriptions.defaultPlan();
    }

    /**
     * @deprecated use {@link #subscriptions()} and access fields from there.
     */
    @Deprecated
    public int defaultTrialPeriodDays() {
        return subscriptions == null ? 0 : subscriptions.defaultTrialPeriodDays();
    }

    /**
     * @deprecated use {@link #subscriptions()} and access fields from there.
     */
    @Deprecated
    public String successUrl() {
        return subscriptions == null ? "" : subscriptions.successUrl();
    }

    /**
     * @deprecated use {@link #subscriptions()} and access fields from there.
     */
    @Deprecated
    public String cancelUrl() {
        return subscriptions == null ? "" : subscriptions.cancelUrl();
    }

    /**
     * @deprecated use {@link #subscriptions()} and access fields from there.
     */
    @Deprecated
    public String portalReturnUrl() {
        return subscriptions == null ? "" : subscriptions.portalReturnUrl();
    }

    /**
     * @deprecated use {@link #subscriptions()} and access fields from there.
     */
    @Deprecated
    public Map<String, NotificationConfig> notifications() {
        return subscriptions == null ? Map.of() : subscriptions.notifications();
    }
}
