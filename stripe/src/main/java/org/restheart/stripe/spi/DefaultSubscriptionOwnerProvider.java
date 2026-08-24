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
package org.restheart.stripe.spi;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.BsonDocument;
import org.restheart.exchange.ServiceRequest;
import org.restheart.plugins.stripe.BillingScope;
import org.restheart.plugins.stripe.LicenseGrantResult;
import org.restheart.plugins.stripe.SubscriptionOwner;
import org.restheart.plugins.stripe.SubscriptionOwnerProvider;
import org.restheart.plugins.stripe.SubscriptionState;
import org.restheart.security.WithProperties;
import org.restheart.stripe.util.RequestOverrides;
import org.restheart.stripe.util.TeamRepository;

import com.mongodb.client.MongoClient;

/**
 * Default {@link SubscriptionOwnerProvider} implementation, backed by the
 * {@code restheart-accounts} team.
 *
 * <p>The paying entity is the team resolved from the configured team claim on the
 * authenticated principal; ownership follows {@code accountsConfig.ownership-role};
 * billing state is stored on the team document, via {@link TeamRepository}.
 *
 * <p>This class is <em>not</em> a RESTHeart plugin. It is instantiated directly by
 * {@code StripeService}, which passes a {@link MongoClient} and the effective accounts
 * settings (team claim name, ownership role) to the constructor — mirroring
 * {@code restheart-accounts}' own {@code DefaultMembershipProvider} / {@code AccountsService}.
 *
 * <p>Unlike {@code DefaultMembershipProvider}, this class does not bake the MongoDB database
 * into its constructor: every method takes an explicit {@link BillingScope}, resolved
 * per-request by the caller (see {@link RequestOverrides#scope}). So {@code StripeService}
 * never needs to re-instantiate this class per request — a single instance, built once at
 * startup, is reused for every tenant.
 *
 * <h2>Team claim shape</h2>
 * <p>Tolerates both the current {@code team: {_id, role}} object shape (from which the
 * caller's role in the team is read directly, for {@link #canManageBilling}) and a legacy
 * scalar {@code team: <id>} shape, in which case {@link #canManageBilling} always denies —
 * there is no role to compare against the ownership role, and a broken resolution must not
 * widen access.
 */
public class DefaultSubscriptionOwnerProvider implements SubscriptionOwnerProvider {

    /** Used when {@code accountsConfig} could not be resolved — see {@code StripeService}. */
    public static final String DEFAULT_TEAM_CLAIM_NAME = "team";
    public static final String DEFAULT_OWNERSHIP_ROLE = "owner";

    private final TeamRepository repo;
    private final String teamClaimName;
    private final String ownershipRole;

    /**
     * @param mclient       RESTHeart-managed MongoDB client
     * @param teamClaimName the JWT/session claim carrying the active team; falls back to
     *                      {@value #DEFAULT_TEAM_CLAIM_NAME} if {@code null}
     * @param ownershipRole the team role allowed to manage billing; falls back to
     *                      {@value #DEFAULT_OWNERSHIP_ROLE} if {@code null}
     */
    public DefaultSubscriptionOwnerProvider(MongoClient mclient, String teamClaimName, String ownershipRole) {
        this.repo = new TeamRepository(mclient);
        this.teamClaimName = teamClaimName != null && !teamClaimName.isBlank() ? teamClaimName : DEFAULT_TEAM_CLAIM_NAME;
        this.ownershipRole = ownershipRole != null && !ownershipRole.isBlank() ? ownershipRole : DEFAULT_OWNERSHIP_ROLE;
    }

    // ── resolution ───────────────────────────────────────────────────────────

    @Override
    public Optional<SubscriptionOwner> fromRequest(ServiceRequest<?> req, BillingScope scope) {
        var claim = readTeamClaim(req);
        return claim == null ? Optional.empty() : byId(scope, claim.teamId());
    }

    @Override
    public Optional<SubscriptionOwner> byStripeCustomerId(BillingScope scope, String stripeCustomerId) {
        return repo.findTeamByCustomerId(scope, stripeCustomerId).map(doc -> toOwner(doc, scope));
    }

    @Override
    public Optional<SubscriptionOwner> byId(BillingScope scope, String ownerId) {
        return repo.findTeamById(scope, ownerId).map(doc -> toOwner(doc, scope));
    }

    @Override
    public boolean canManageBilling(ServiceRequest<?> req, SubscriptionOwner owner) {
        var claim = readTeamClaim(req);
        if (claim == null || claim.role() == null) {
            // legacy scalar claim shape, or no claim at all — nothing to compare, deny
            return false;
        }
        var effectiveOwnershipRole = RequestOverrides.effectiveOwnershipRole(req, ownershipRole);
        return effectiveOwnershipRole.equals(claim.role());
    }

    // ── persistence ──────────────────────────────────────────────────────────

    @Override
    public String linkStripeCustomer(SubscriptionOwner owner, String stripeCustomerId) {
        return repo.setCustomerId(owner.scope(), owner.id(), stripeCustomerId);
    }

    @Override
    public SubscriptionState readSubscription(SubscriptionOwner owner, String defaultPlanId) {
        return repo.findTeamByObjectId(owner.scope(), owner.id())
                .map(doc -> repo.readSubscriptionState(doc, defaultPlanId))
                .orElseGet(() -> SubscriptionState.defaultFor(defaultPlanId));
    }

    @Override
    public boolean writeSubscription(SubscriptionOwner owner, SubscriptionState state, Instant appliedAt) {
        return repo.writeSubscriptionState(owner.scope(), owner.id(), state, appliedAt);
    }

    @Override
    public boolean patchSubscription(SubscriptionOwner owner, BsonDocument changes, Instant appliedAt) {
        return repo.patchSubscriptionState(owner.scope(), owner.id(), changes, appliedAt);
    }

    // ── seat licensing ───────────────────────────────────────────────────────

    @Override
    public LicenseGrantResult grantLicense(SubscriptionOwner owner, String userId, Integer limit) {
        return repo.grantLicense(owner.scope(), owner.id(), userId, limit);
    }

    @Override
    public void revokeLicense(SubscriptionOwner owner, String userId) {
        repo.revokeLicense(owner.scope(), owner.id(), userId);
    }

    @Override
    public int licensedCount(SubscriptionOwner owner) {
        return repo.licensedCount(owner.scope(), owner.id());
    }

    @Override
    public List<String> licensedUserIds(SubscriptionOwner owner) {
        return repo.licensedUserIds(owner.scope(), owner.id());
    }

    @Override
    public boolean isLicensed(SubscriptionOwner owner, String userId) {
        return repo.isLicensed(owner.scope(), owner.id(), userId);
    }

    /** Exposed so {@code stripeInitializer} can create the {@code stripe_customer_id} index. */
    public TeamRepository repository() {
        return repo;
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private SubscriptionOwner toOwner(BsonDocument teamDoc, BillingScope scope) {
        var id = teamDoc.get("_id");
        var displayName = teamDoc.containsKey("name") && teamDoc.get("name").isString()
                ? teamDoc.getString("name").getValue()
                : null;
        // The team's creator is its original owner; restheart-accounts already stamps this
        // on every team it creates, so no schema addition is needed to have a billing email.
        var ownerEmail = teamDoc.containsKey("createdBy") && teamDoc.get("createdBy").isString()
                ? teamDoc.getString("createdBy").getValue()
                : null;
        var stripeCustomerId = teamDoc.containsKey("stripe_customer_id") && teamDoc.get("stripe_customer_id").isString()
                ? teamDoc.getString("stripe_customer_id").getValue()
                : null;

        return new SubscriptionOwner(id, scope, displayName, ownerEmail, stripeCustomerId);
    }

    private record TeamClaim(String teamId, String role) {
    }

    /**
     * Reads the team claim off the authenticated principal, tolerating both realm-based
     * accounts ({@code WithProperties<BsonDocument>} / {@code WithProperties<Map>}) and
     * JWT-based accounts ({@code WithProperties<String>}) uniformly via
     * {@link WithProperties#propertiesAsMap()} — the same access path the built-in
     * {@code @user.<property>} ACL variable uses.
     */
    private TeamClaim readTeamClaim(ServiceRequest<?> req) {
        if (req == null || req.getAuthenticatedAccount() == null) {
            return null;
        }
        if (!(req.getAuthenticatedAccount() instanceof WithProperties<?> withProperties)) {
            return null;
        }

        var props = withProperties.propertiesAsMap();
        if (props == null) {
            return null;
        }

        var claimValue = props.get(teamClaimName);
        if (claimValue == null) {
            return null;
        }

        if (claimValue instanceof Map<?, ?> claimMap) {
            var idValue = claimMap.get("_id") != null ? claimMap.get("_id") : claimMap.get("id");
            var teamId = extractId(idValue);
            if (teamId == null) {
                return null;
            }
            var roleValue = claimMap.get("role");
            return new TeamClaim(teamId, roleValue != null ? roleValue.toString() : null);
        }

        // legacy scalar shape: just an id, no role — canManageBilling always denies for this shape
        return new TeamClaim(extractId(claimValue), null);
    }

    /**
     * Extracts a plain id string from a claim id value, which is either already a plain
     * string/ObjectId, or — as every JWT {@code team} claim is, see
     * {@code org.restheart.plugins.accounts.TeamClaim} — MongoDB Extended JSON's
     * {@code {"$oid": "<hex>"}} shape for an ObjectId. A naive {@code toString()} on the
     * latter would yield {@code "{$oid=<hex>}"}, not the hex string {@link #byId} needs.
     */
    private static String extractId(Object idValue) {
        if (idValue == null) {
            return null;
        }
        if (idValue instanceof Map<?, ?> extendedJson && extendedJson.get("$oid") != null) {
            return extendedJson.get("$oid").toString();
        }
        return idValue.toString();
    }
}
