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

/**
 * Configuration of a single plan in the {@code stripeConfig.plans} catalog.
 *
 * <p>Display data — name, description, amount, currency — is deliberately not here.
 * It is fetched from the Stripe Product/Price the price ids point at, so that the
 * pricing shown to the customer is always the one actually charged. This record
 * carries only what the module must enforce.
 *
 * @param priceIdMonthly    Stripe Price id for the monthly interval, or {@code null}
 *                          if this plan is not purchasable monthly (e.g. {@code free})
 * @param priceIdAnnual     Stripe Price id for the annual interval, or {@code null}
 * @param trialPeriodDays   trial days for a Checkout session on this plan, or
 *                          {@code null} to use {@link StripeConfigData#defaultTrialPeriodDays()}
 * @param seats             seat mode and limit for this plan; {@code null} is treated as
 *                           {@link SeatsMode#UNLIMITED} with no maximum
 * @param limits             arbitrary additional plan limits (e.g. {@code max-projects}),
 *                            opaque to the module — surfaced via {@code GET /stripe/plans}
 *                            and {@code @subscription} for the deployment's own ACL rules
 * @param overLimitGraceDays per-plan override of {@link StripeConfigData#overLimitGraceDays()};
 *                           {@code null} inherits the module default
 */
public record PlanConfig(
        String priceIdMonthly,
        String priceIdAnnual,
        Integer trialPeriodDays,
        SeatsConfig seats,
        Map<String, Object> limits,
        Integer overLimitGraceDays) {

    /**
     * @return {@code true} if this plan has a price id for the given interval
     *         ({@code "month"} or {@code "year"}) and can therefore be purchased
     *         through Checkout
     */
    public boolean purchasableFor(String interval) {
        return priceId(interval) != null;
    }

    /**
     * @param interval {@code "month"} or {@code "year"}
     * @return the price id for that interval, or {@code null} if this plan has none
     */
    public String priceId(String interval) {
        return "year".equals(interval) ? priceIdAnnual : priceIdMonthly;
    }
}
