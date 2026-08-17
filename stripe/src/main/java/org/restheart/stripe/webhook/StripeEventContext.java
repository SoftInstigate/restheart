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
package org.restheart.stripe.webhook;

import java.time.Instant;

import org.restheart.exchange.ByteArrayRequest;
import org.restheart.plugins.stripe.BillingScope;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.plugins.stripe.SubscriptionOwnerProvider;

import com.mongodb.client.MongoClient;

/**
 * Per-delivery context for webhook event handlers.
 *
 * <p>Resolved once by the webhook service and threaded through all handlers.
 *
 * @param req              the raw request (for notifications)
 * @param conf             the full Stripe config
 * @param provider         the subscription owner provider
 * @param scope            the billing scope (db + collection)
 * @param defaultPlanId    the default plan id
 * @param appliedAt        the event's creation timestamp (for staleness guard)
 * @param mclient          the MongoDB client (for order/transaction operations)
 */
public record StripeEventContext(
        ByteArrayRequest req,
        StripeConfigData conf,
        SubscriptionOwnerProvider provider,
        BillingScope scope,
        String defaultPlanId,
        Instant appliedAt,
        MongoClient mclient) {
}
