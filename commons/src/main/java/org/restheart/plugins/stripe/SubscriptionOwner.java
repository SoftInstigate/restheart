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

import org.bson.BsonValue;

/**
 * The entity a subscription belongs to, resolved by a {@link SubscriptionOwnerProvider}.
 *
 * <p>Deliberately not called "account" — in RESTHeart that word already means the
 * authenticated principal. A {@code SubscriptionOwner} is whatever a deployment bills:
 * by default a {@code restheart-accounts} team, but a custom provider may resolve it to
 * a user, an organisation, a project, or any other entity.
 *
 * @param id               the entity's identifier
 * @param scope            where it was resolved — see {@link BillingScope}; travels with
 *                         the entity so persistence calls need not be told again
 * @param displayName      for logs and Stripe Customer metadata
 * @param ownerEmail       the email used on the Stripe Customer and for billing notifications
 * @param stripeCustomerId the linked Stripe Customer id, or {@code null} until the first
 *                         checkout — Customers are provisioned lazily, never at entity creation
 */
public record SubscriptionOwner(
        BsonValue id,
        BillingScope scope,
        String displayName,
        String ownerEmail,
        String stripeCustomerId) {

    /** @return a copy of this owner with {@link #stripeCustomerId()} replaced. */
    public SubscriptionOwner withStripeCustomerId(String newStripeCustomerId) {
        return new SubscriptionOwner(id, scope, displayName, ownerEmail, newStripeCustomerId);
    }
}
