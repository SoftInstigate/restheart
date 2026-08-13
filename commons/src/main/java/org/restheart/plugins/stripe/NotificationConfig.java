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

/**
 * Configuration of a single billing notification email.
 *
 * <p>{@code payment-failed} and {@code trial-will-end} default to {@code enabled = false}:
 * Stripe can send its own equivalent from the Dashboard ("Subscriptions and emails"),
 * and duplication cannot be detected at runtime — an operator enabling one of these here
 * must turn the matching Stripe dashboard email off. {@code subscription-canceled} and
 * {@code over-limit} default to {@code enabled = true}, since nothing else sends them.
 *
 * @param enabled      whether this notification is sent
 * @param templatePath path to the HTML template, or {@code null} to use the built-in
 */
public record NotificationConfig(boolean enabled, String templatePath) {

    /** Notification names, used as keys in {@code stripeConfig.notifications} and {@code .templates}. */
    public static final String PAYMENT_FAILED = "payment-failed";
    public static final String TRIAL_WILL_END = "trial-will-end";
    public static final String SUBSCRIPTION_CANCELED = "subscription-canceled";
    public static final String OVER_LIMIT = "over-limit";

    /** Default {@code enabled} for a given notification name, per the class javadoc. */
    public static boolean defaultEnabled(String name) {
        return !(PAYMENT_FAILED.equals(name) || TRIAL_WILL_END.equals(name));
    }
}
