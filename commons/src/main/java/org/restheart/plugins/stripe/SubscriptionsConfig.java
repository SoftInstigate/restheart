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
 * Subscriptions mode configuration, nested under {@code stripeConfig.subscriptions}.
 *
 * @param enabled               whether the subscriptions mode is active
 * @param plans                 the configured plan catalog, keyed by plan id
 * @param defaultPlan           plan id assigned to an entity with no subscription
 * @param defaultTrialPeriodDays fallback trial days when a plan does not declare its own
 * @param successUrl            Stripe Checkout success redirect URL
 * @param cancelUrl             Stripe Checkout cancel redirect URL
 * @param portalReturnUrl       Customer Portal return URL
 * @param notifications         notification configuration, keyed by name
 */
public record SubscriptionsConfig(
        boolean enabled,
        Map<String, PlanConfig> plans,
        String defaultPlan,
        int defaultTrialPeriodDays,
        String successUrl,
        String cancelUrl,
        String portalReturnUrl,
        Map<String, NotificationConfig> notifications) {

    public PlanConfig plan(String planId) {
        return planId == null ? null : plans.get(planId);
    }

    public String priceId(String planId, String interval) {
        var plan = plan(planId);
        return plan == null ? null : plan.priceId(interval);
    }

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

    public int effectiveTrialPeriodDays(String planId) {
        var plan = plan(planId);
        return plan != null && plan.trialPeriodDays() != null
                ? plan.trialPeriodDays()
                : defaultTrialPeriodDays;
    }

    public NotificationConfig notification(String name) {
        var n = notifications.get(name);
        return n != null ? n : new NotificationConfig(NotificationConfig.defaultEnabled(name), null);
    }
}
