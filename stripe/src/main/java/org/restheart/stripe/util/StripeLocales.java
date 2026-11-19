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

import java.util.Optional;

import com.stripe.param.checkout.SessionCreateParams;

/**
 * A language of ours as Stripe takes it.
 *
 * <p>The plugin chooses the language of its own emails (see
 * {@link org.restheart.emails.EmailLocale}). The same language goes to Stripe, so the Checkout
 * page, the receipt Stripe sends and the email the plugin sends agree. Stripe accepts a closed
 * list of languages for Checkout and for the Customer Portal: a language outside it is used
 * for the plugin's email only, and Stripe's page keeps following the browser.
 */
public final class StripeLocales {

    private StripeLocales() {
        // utility class — no instances
    }

    /**
     * @param locale a language code as {@link org.restheart.emails.EmailLocale} returns it, or
     *               {@code null}
     * @return the Checkout locale with that code, or empty when there is none or Stripe has
     *         no Checkout page in that language
     */
    public static Optional<SessionCreateParams.Locale> checkout(String locale) {
        if (locale == null) {
            return Optional.empty();
        }
        for (var candidate : SessionCreateParams.Locale.values()) {
            // AUTO is Stripe's own "follow the browser", not a language
            if (candidate != SessionCreateParams.Locale.AUTO && candidate.getValue().equalsIgnoreCase(locale)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * @param locale a language code as {@link org.restheart.emails.EmailLocale} returns it, or
     *               {@code null}
     * @return the Customer Portal locale with that code, or empty when there is none or Stripe
     *         has no portal in that language
     */
    public static Optional<com.stripe.param.billingportal.SessionCreateParams.Locale> portal(String locale) {
        if (locale == null) {
            return Optional.empty();
        }
        for (var candidate : com.stripe.param.billingportal.SessionCreateParams.Locale.values()) {
            if (candidate != com.stripe.param.billingportal.SessionCreateParams.Locale.AUTO
                    && candidate.getValue().equalsIgnoreCase(locale)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
