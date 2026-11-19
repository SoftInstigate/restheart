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

import org.restheart.plugins.stripe.SubscriptionOwner;
import org.restheart.plugins.stripe.SubscriptionOwnerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;

/**
 * Provisions the Stripe Customer <em>lazily</em>, on the first checkout — never at entity
 * creation. See {@code SubscriptionOwnerProvider}'s class javadoc and issue #676 for the
 * rationale: an eager Customer-on-signup interceptor couples signup to Stripe being
 * reachable and fails in a way nothing recovers from, and it sends a Customer object to a
 * third party for every signup, including the majority who never pay.
 *
 * <p>{@link #ensureCustomer} is the only place in the module that creates a Stripe Customer.
 * The Customer Portal ({@code stripePortalService}) does not provision — an entity with no
 * Customer has nothing to manage there.
 */
public final class CustomerProvisioning {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerProvisioning.class);

    private CustomerProvisioning() {
    }

    /**
     * Returns the entity's Stripe Customer id, creating one if it does not have one yet.
     *
     * <p>Safe to call repeatedly and under concurrency: a Stripe idempotency key derived
     * from the owner id collapses concurrent {@code Customer.create} calls into one Customer
     * server-side, and {@link SubscriptionOwnerProvider#linkStripeCustomer} is contractually
     * atomic — it keeps an already-linked id and returns it rather than overwriting, so a
     * losing racer transparently proceeds with the winner's Customer.
     *
     * @param provider the active provider, for the atomic link
     * @param owner    the resolved entity
     * @param apiKey   the effective (possibly per-tenant) Stripe secret key — passed
     *                 explicitly via {@link RequestOptions}, never through the {@code Stripe.apiKey} global
     * @return the effective Stripe Customer id
     * @throws StripeException if Customer creation fails
     */
    public static String ensureCustomer(SubscriptionOwnerProvider provider, SubscriptionOwner owner, String apiKey)
            throws StripeException {
        if (owner.stripeCustomerId() != null && !owner.stripeCustomerId().isBlank()) {
            return owner.stripeCustomerId();
        }

        var ownerId = StripeIds.toIdString(owner.id());

        var opts = RequestOptions.builder()
                .setApiKey(apiKey)
                .setIdempotencyKey("stripe-customer-" + ownerId)
                .build();

        var paramsBuilder = CustomerCreateParams.builder()
                .putMetadata("owner_id", ownerId);
        if (owner.ownerEmail() != null && !owner.ownerEmail().isBlank()) {
            paramsBuilder.setEmail(owner.ownerEmail());
        }

        var customer = Customer.create(paramsBuilder.build(), opts);
        var effectiveId = provider.linkStripeCustomer(owner, customer.getId());

        if (effectiveId.equals(customer.getId())) {
            tellPreferredLocale(provider, owner, customer, apiKey);
        }

        if (!effectiveId.equals(customer.getId())) {
            LOGGER.debug("[stripe] ensureCustomer lost the link race for owner {}: created {} but {} is now linked",
                    ownerId, customer.getId(), effectiveId);
        }

        return effectiveId;
    }

    /**
     * Tells Stripe the language of the billing contact, so the receipts and invoices Stripe
     * sends are in the language of the emails the plugin sends.
     *
     * <p>A separate update, after the creation: the creation is sent under an idempotency key
     * made of the owner's id alone, and Stripe refuses a key that comes back with other
     * parameters. With the language among them, a retry after the contact changed language
     * would fail for as long as Stripe remembers the key.
     *
     * <p>Never fatal: the Customer exists and the checkout goes ahead without it.
     */
    private static void tellPreferredLocale(SubscriptionOwnerProvider provider, SubscriptionOwner owner,
                                            Customer customer, String apiKey) {
        try {
            var locale = provider.locale(owner).map(org.restheart.emails.EmailLocale::normalize);
            if (locale.isEmpty()) {
                return;
            }
            customer.update(
                    com.stripe.param.CustomerUpdateParams.builder().addPreferredLocale(locale.get()).build(),
                    RequestOptions.builder().setApiKey(apiKey).build());
        } catch (StripeException | RuntimeException e) {
            LOGGER.warn("[stripe] could not set the preferred language of Customer {}: {}", customer.getId(), e.getMessage());
        }
    }
}
