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
 * The tenant-resolved MongoDB storage scope for a {@link SubscriptionOwnerProvider}
 * resolution or lookup, derived from the effective {@code stripeConfig} — the static
 * configuration, or a per-tenant {@code override-stripe-db} / {@code override-stripe-teams-collection}.
 *
 * <p>On a multi-tenant node the database is per request, but the webhook path
 * ({@link SubscriptionOwnerProvider#byStripeCustomerId(BillingScope, String)}) has no
 * request to derive it from — only a tenant resolved from the endpoint URI. So resolution
 * is always told the scope explicitly rather than reading it from ambient state, and the
 * resolved {@link SubscriptionOwner} then carries it, so no persistence call needs to be
 * told again where the entity it was just handed lives.
 *
 * <p>Deliberately thin and MongoDB-shaped, because that is what the module's own
 * configuration is. A provider storing state elsewhere ignores both fields — this is
 * not a contract to honour, it is the module saying what it was configured with.
 *
 * @param db         the MongoDB database name
 * @param collection the MongoDB collection name (the teams collection, by default)
 */
public record BillingScope(String db, String collection) {
}
