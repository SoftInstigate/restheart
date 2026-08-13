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

import java.time.Instant;
import java.util.Optional;

import org.bson.BsonDocument;
import org.restheart.exchange.ServiceRequest;

/**
 * Service Provider Interface for resolving <em>who owns a subscription</em> and where its
 * billing state is stored.
 *
 * <p>Implementations control how the paying entity is resolved from a request or from a
 * Stripe Customer id, whether a caller may manage its billing, and how the Stripe Customer
 * linkage, the subscription state, and seat licences are persisted. {@code restheart-stripe}
 * consults this interface for every billing operation.
 *
 * <p>A default implementation ({@code DefaultSubscriptionOwnerProvider}, in
 * {@code restheart-stripe}) is backed by the {@code restheart-accounts} team: the entity is
 * the team resolved from the configured team claim, ownership follows
 * {@code accountsConfig.ownership-role}, and state is stored on the team document. Custom
 * implementations can be registered at startup via:
 *
 * <pre>{@code
 * @RegisterPlugin(name = "myBillingOwnerProvider", description = "...")
 * public class MyBillingOwnerProvider implements SubscriptionOwnerProvider, Initializer {
 *     @Inject("stripeService") private SubscriptionOwnerProviderRegistry stripeService;
 *
 *     @Override
 *     public void init() {
 *         stripeService.registerSubscriptionOwnerProvider(this);
 *     }
 *     // ... implement methods ...
 * }
 * }</pre>
 *
 * <p>If no custom provider is registered, {@code restheart-stripe} falls back to the
 * built-in {@code DefaultSubscriptionOwnerProvider}.
 *
 * @see SubscriptionOwnerProviderRegistry
 * @see SubscriptionOwner
 * @see BillingScope
 */
public interface SubscriptionOwnerProvider {

    // ── resolution: scope in, entity out ────────────────────────────────────

    /**
     * Resolves the paying entity for the given request.
     *
     * <p>Takes the request rather than a user id because implementations differ in what
     * they read: the default one reads a JWT team claim, another might read a header,
     * a path segment, or the authenticated principal's own properties.
     *
     * @param req   the incoming request
     * @param scope where to resolve the entity, from the effective {@code stripeConfig}
     * @return the resolved entity, or empty if the caller has none
     */
    Optional<SubscriptionOwner> fromRequest(ServiceRequest<?> req, BillingScope scope);

    /**
     * Resolves the paying entity holding the given Stripe Customer id. Used on the
     * webhook path, which has no request and no authenticated principal.
     *
     * @param scope            where to resolve the entity
     * @param stripeCustomerId the {@code cus_xxx} value
     * @return the resolved entity, or empty if not found
     */
    Optional<SubscriptionOwner> byStripeCustomerId(BillingScope scope, String stripeCustomerId);

    /**
     * Resolves the paying entity with the given id — used to resolve a Checkout
     * session's {@code client_reference_id} on the webhook path.
     *
     * @param scope   where to resolve the entity
     * @param ownerId the entity id, as a string (e.g. an ObjectId hex string)
     * @return the resolved entity, or empty if not found
     */
    Optional<SubscriptionOwner> byId(BillingScope scope, String ownerId);

    /**
     * Whether the caller may manage this entity's billing: open the Customer Portal,
     * start a checkout, or grant/revoke a seat licence. Spending money, or consuming a
     * scarce resource, is not something every member of an entity should be able to do.
     *
     * @param req   the incoming request
     * @param owner the resolved entity
     * @return {@code true} if the caller may manage {@code owner}'s billing
     */
    boolean canManageBilling(ServiceRequest<?> req, SubscriptionOwner owner);

    // ── persistence: the provider decides where the Stripe linkage and state live ──

    /**
     * Links a newly created Stripe Customer to this entity.
     *
     * <p>Must be atomic and idempotent: if a concurrent call already linked one, keep it
     * and return that value rather than overwriting — this is what makes it safe for two
     * simultaneous first checkouts to both call this method, since only one Customer must
     * win. Callers use the return value, not the argument, so a losing racer transparently
     * proceeds with the winner's Customer.
     *
     * @param owner            the entity to link
     * @param stripeCustomerId a candidate Stripe Customer id
     * @return the effective, persisted Stripe Customer id for this entity
     */
    String linkStripeCustomer(SubscriptionOwner owner, String stripeCustomerId);

    /**
     * @param owner        the entity
     * @param defaultPlanId the effective default plan id (the module resolves per-tenant
     *                      overrides before calling this — the provider does not read
     *                      {@code StripeConfigData} itself)
     * @return the current subscription state, never {@code null} — the {@code defaultPlanId}
     *         state when the entity has no subscription
     */
    SubscriptionState readSubscription(SubscriptionOwner owner, String defaultPlanId);

    /**
     * Replaces the subscription state, unless a newer update was already applied.
     *
     * @param owner     the entity
     * @param state     the new state
     * @param appliedAt the source Stripe event's own timestamp — Stripe delivers events
     *                  out of order, so this must be compared against the last-applied
     *                  timestamp, kept by the provider next to the state, never inside it
     * @return {@code false} if the write was skipped as stale — a newer update already
     *         landed, the state is correct and nothing is pending. Not an error: the
     *         webhook must answer {@code 200} either way.
     */
    boolean writeSubscription(SubscriptionOwner owner, SubscriptionState state, Instant appliedAt);

    /**
     * Applies a partial update to the subscription state, with the same staleness rule
     * as {@link #writeSubscription(SubscriptionOwner, SubscriptionState, Instant)}.
     * Fields absent from {@code changes} are left untouched.
     *
     * @param owner     the entity
     * @param changes   a document whose top-level keys are the state fields to set
     * @param appliedAt the source event's timestamp
     * @return {@code false} if skipped as stale — not an error
     */
    boolean patchSubscription(SubscriptionOwner owner, BsonDocument changes, Instant appliedAt);

    // ── seat licensing ───────────────────────────────────────────────────────

    /**
     * Grants a seat licence to a membership of this entity.
     *
     * <p>Must test availability and write in a single atomic operation — a check
     * followed by a separate write is a race between two concurrent grants on the
     * last available seat.
     *
     * @param owner  the entity
     * @param userId the member to licence
     * @param limit  the plan's seat limit ({@code null} for unlimited); comes from module
     *               configuration, which the provider does not read itself
     * @return {@code false} if no seat was available — not an error
     */
    boolean grantLicense(SubscriptionOwner owner, String userId, Integer limit);

    /**
     * Revokes a member's seat licence. A no-op if the member was not licensed.
     *
     * @param owner  the entity
     * @param userId the member to revoke
     */
    void revokeLicense(SubscriptionOwner owner, String userId);

    /**
     * @param owner the entity
     * @return the number of currently licensed members
     */
    int licensedCount(SubscriptionOwner owner);

    /**
     * @param owner  the entity
     * @param userId a member id
     * @return {@code true} if that member currently holds a seat licence
     */
    boolean isLicensed(SubscriptionOwner owner, String userId);
}
