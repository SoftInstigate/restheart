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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.restheart.plugins.stripe.BillingScope;
import org.restheart.plugins.stripe.LicenseGrantResult;
import org.restheart.plugins.stripe.SubscriptionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

/**
 * MongoDB read/write helper backing {@code DefaultSubscriptionOwnerProvider}.
 *
 * <p>Not a RESTHeart plugin — instantiated directly by the provider, which receives a
 * {@link MongoClient} via injection. Reached only through {@code DefaultSubscriptionOwnerProvider};
 * no service or interceptor in this module talks to MongoDB directly for billing state.
 *
 * <p>Team document shape:
 * <pre>{@code
 * {
 *   "_id": ObjectId,
 *   "stripe_customer_id": "cus_xxx",
 *   "subscription": {
 *     "plan":                    "gold",
 *     "price_id":                "price_xxx",
 *     "status":                  "trialing | active | past_due | canceled | unpaid",
 *     "stripe_subscription_id":  "sub_xxx",
 *     "trial_end":               ISODate,
 *     "current_period_end":      ISODate,
 *     "seats":                   5,
 *     "cancel_at_period_end":    false,
 *     "over_limit_since":        ISODate,
 *     "last_applied_event_at":   ISODate   // staleness bookkeeping — never in SubscriptionState
 *   },
 *   "members": [
 *     { "userId": "...", "role": "...", "joinedAt": ISODate, "licensed": true }
 *   ]
 * }
 * }</pre>
 */
public class TeamRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(TeamRepository.class);

    private static final String SUBSCRIPTION = "subscription";
    private static final String STRIPE_CUSTOMER_ID = "stripe_customer_id";

    /** Name of the index {@link #ensureIndexes} creates — public so {@code StripeInitTracker} can check for its existence without duplicating the literal. */
    public static final String STRIPE_CUSTOMER_ID_INDEX = "stripe_customer_id_unique_sparse";
    private static final String MEMBERS = "members";
    private static final String LAST_APPLIED_EVENT_AT = SUBSCRIPTION + ".last_applied_event_at";

    private final MongoClient mclient;

    /**
     * @param mclient RESTHeart-managed MongoDB client (injected via {@code @Inject("mclient")})
     */
    public TeamRepository(MongoClient mclient) {
        this.mclient = mclient;
    }

    private MongoCollection<BsonDocument> collection(BillingScope scope) {
        return mclient.getDatabase(scope.db()).getCollection(scope.collection(), BsonDocument.class);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * Finds a team document by its hex-string ObjectId.
     *
     * @param scope    the storage scope
     * @param teamIdHex the team's ObjectId hex string
     * @return the raw BSON document, or empty if not found or {@code teamIdHex} is malformed
     */
    public Optional<BsonDocument> findTeamById(BillingScope scope, String teamIdHex) {
        try {
            return Optional.ofNullable(
                    collection(scope).find(Filters.eq("_id", new BsonObjectId(new ObjectId(teamIdHex)))).first());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[stripe] invalid team id '{}': {}", teamIdHex, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Finds a team document by its Stripe Customer id.
     *
     * @param scope            the storage scope
     * @param stripeCustomerId the {@code cus_xxx} value
     * @return the raw BSON document, or empty if not found
     */
    public Optional<BsonDocument> findTeamByCustomerId(BillingScope scope, String stripeCustomerId) {
        return Optional.ofNullable(
                collection(scope).find(Filters.eq(STRIPE_CUSTOMER_ID, stripeCustomerId)).first());
    }

    /**
     * Finds a team document by its {@code _id}, given as an already-resolved {@link BsonValue}
     * (e.g. from {@code SubscriptionOwner#id()}) — avoids a hex-string round trip through
     * {@link #findTeamById} when the caller already has the typed id.
     *
     * @param scope  the storage scope
     * @param teamId the team's {@code _id}
     * @return the raw BSON document, or empty if not found
     */
    public Optional<BsonDocument> findTeamByObjectId(BillingScope scope, BsonValue teamId) {
        return Optional.ofNullable(collection(scope).find(Filters.eq("_id", teamId)).first());
    }

    /**
     * Deserializes the {@code subscription} sub-document into a {@link SubscriptionState}.
     * Never throws: a malformed field falls back to its default, since this runs on the
     * ACL read path, where a throw would deny a request over a untidy document.
     *
     * @param teamDoc      the raw team document
     * @param defaultPlanId the configured default plan id, used when there is no sub-document
     * @return the current subscription state, never {@code null}
     */
    public SubscriptionState readSubscriptionState(BsonDocument teamDoc, String defaultPlanId) {
        if (teamDoc == null || !teamDoc.containsKey(SUBSCRIPTION) || !teamDoc.get(SUBSCRIPTION).isDocument()) {
            return SubscriptionState.defaultFor(defaultPlanId);
        }

        var sub = teamDoc.getDocument(SUBSCRIPTION);

        return new SubscriptionState(
                str(sub, "plan", defaultPlanId),
                str(sub, "price_id", null),
                str(sub, "status", null),
                str(sub, "stripe_subscription_id", null),
                instant(sub, "trial_end"),
                instant(sub, "current_period_end"),
                Math.max(1, int32(sub, "seats", 1)),
                bool(sub, "cancel_at_period_end", false),
                instant(sub, "over_limit_since"));
    }

    // ── Write ────────────────────────────────────────────────────────────────

    /**
     * Links a Stripe Customer id to a team, atomically and only if the team has none yet.
     *
     * @param scope              the storage scope
     * @param teamId             the team's {@code _id}
     * @param candidateCustomerId a candidate Stripe Customer id
     * @return the effective, persisted Stripe Customer id — {@code candidateCustomerId} if
     *         this call won the race, or whatever was already linked otherwise
     */
    public String setCustomerId(BillingScope scope, BsonValue teamId, String candidateCustomerId) {
        var filter = Filters.and(
                Filters.eq("_id", teamId),
                Filters.or(Filters.exists(STRIPE_CUSTOMER_ID, false), Filters.eq(STRIPE_CUSTOMER_ID, null)));

        var updated = collection(scope).findOneAndUpdate(
                filter,
                Updates.set(STRIPE_CUSTOMER_ID, candidateCustomerId),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));

        if (updated != null) {
            return candidateCustomerId;
        }

        // Someone else already linked one — read it back.
        var existing = collection(scope).find(Filters.eq("_id", teamId)).first();
        var effective = existing != null && existing.containsKey(STRIPE_CUSTOMER_ID) && existing.get(STRIPE_CUSTOMER_ID).isString()
                ? existing.getString(STRIPE_CUSTOMER_ID).getValue()
                : candidateCustomerId;

        LOGGER.debug("[stripe] setCustomerId lost the race for team {}, using existing customer {}", teamId, effective);
        return effective;
    }

    /**
     * Replaces the {@code subscription} sub-document, unless a newer update was already applied.
     *
     * @param scope     the storage scope
     * @param teamId    the team's {@code _id}
     * @param state     the new subscription state
     * @param appliedAt the source event's timestamp
     * @return {@code false} if the write was skipped as stale
     */
    public boolean writeSubscriptionState(BillingScope scope, BsonValue teamId, SubscriptionState state, Instant appliedAt) {
        var filter = staleGuard(teamId, appliedAt);
        var update = Updates.set(SUBSCRIPTION, toBson(state, appliedAt));

        var updated = collection(scope).findOneAndUpdate(filter, update);
        if (updated == null) {
            LOGGER.debug("[stripe] writeSubscriptionState skipped as stale for team {} (appliedAt={})", teamId, appliedAt);
        }
        return updated != null;
    }

    /**
     * Applies a partial update to the {@code subscription} sub-document, with the same
     * staleness rule as {@link #writeSubscriptionState}.
     *
     * @param scope     the storage scope
     * @param teamId    the team's {@code _id}
     * @param changes   top-level keys are the {@code subscription.*} fields to set
     * @param appliedAt the source event's timestamp
     * @return {@code false} if skipped as stale
     */
    public boolean patchSubscriptionState(BillingScope scope, BsonValue teamId, BsonDocument changes, Instant appliedAt) {
        var filter = staleGuard(teamId, appliedAt);

        var updates = new ArrayList<org.bson.conversions.Bson>();
        for (var key : changes.keySet()) {
            updates.add(Updates.set(SUBSCRIPTION + "." + key, changes.get(key)));
        }
        updates.add(Updates.set(LAST_APPLIED_EVENT_AT, new BsonDateTime(appliedAt.toEpochMilli())));

        var updated = collection(scope).findOneAndUpdate(filter, Updates.combine(updates));
        if (updated == null) {
            LOGGER.debug("[stripe] patchSubscriptionState skipped as stale for team {} (appliedAt={})", teamId, appliedAt);
        }
        return updated != null;
    }

    private org.bson.conversions.Bson staleGuard(BsonValue teamId, Instant appliedAt) {
        return Filters.and(
                Filters.eq("_id", teamId),
                Filters.or(
                        Filters.exists(LAST_APPLIED_EVENT_AT, false),
                        Filters.lt(LAST_APPLIED_EVENT_AT, new BsonDateTime(appliedAt.toEpochMilli()))));
    }

    private static BsonDocument toBson(SubscriptionState state, Instant appliedAt) {
        var doc = new BsonDocument()
                .append("plan", str(state.plan()))
                .append("price_id", str(state.priceId()))
                .append("status", str(state.status()))
                .append("stripe_subscription_id", str(state.stripeSubscriptionId()))
                .append("seats", new BsonInt32(state.seats()))
                .append("cancel_at_period_end", BsonBoolean.valueOf(state.cancelAtPeriodEnd()))
                .append("last_applied_event_at", new BsonDateTime(appliedAt.toEpochMilli()));

        if (state.trialEnd() != null) {
            doc.append("trial_end", new BsonDateTime(state.trialEnd().toEpochMilli()));
        }
        if (state.currentPeriodEnd() != null) {
            doc.append("current_period_end", new BsonDateTime(state.currentPeriodEnd().toEpochMilli()));
        }
        if (state.overLimitSince() != null) {
            doc.append("over_limit_since", new BsonDateTime(state.overLimitSince().toEpochMilli()));
        }

        return doc;
    }

    private static BsonValue str(String s) {
        return s == null ? org.bson.BsonNull.VALUE : new BsonString(s);
    }

    // ── Seat licensing ──────────────────────────────────────────────────────

    /**
     * Grants a seat licence to a member, atomically testing availability and writing in
     * one operation — the count is recomputed from the {@code members} array itself, so
     * it cannot drift, and it needs no denormalised counter.
     *
     * <p>On failure, a follow-up read distinguishes why (member not found, already
     * licensed, or no seat available) so the caller can answer with the right HTTP status.
     * This extra query only happens on the failure path.
     *
     * @param scope  the storage scope
     * @param teamId the team's {@code _id}
     * @param userId the member to licence
     * @param limit  the seat limit, or {@code null} for unlimited
     * @return the outcome — see {@link LicenseGrantResult}
     */
    public LicenseGrantResult grantLicense(BillingScope scope, BsonValue teamId, String userId, Integer limit) {
        var filters = new ArrayList<org.bson.conversions.Bson>();
        filters.add(Filters.eq("_id", teamId));
        filters.add(Filters.elemMatch(MEMBERS,
                Filters.and(Filters.eq("userId", userId), Filters.ne("licensed", true))));

        if (limit != null) {
            var licensedCountExpr = new Document("$size",
                    new Document("$filter", new Document("input", "$" + MEMBERS)
                            .append("cond", new Document("$eq", java.util.List.of("$$this.licensed", true)))));
            filters.add(Filters.expr(new Document("$lt", java.util.List.of(licensedCountExpr, limit))));
        }

        var updated = collection(scope).findOneAndUpdate(
                Filters.and(filters),
                Updates.set(MEMBERS + ".$.licensed", true));

        if (updated != null) {
            return LicenseGrantResult.GRANTED;
        }

        return diagnoseGrantFailure(scope, teamId, userId);
    }

    private LicenseGrantResult diagnoseGrantFailure(BillingScope scope, BsonValue teamId, String userId) {
        var teamDoc = collection(scope).find(Filters.eq("_id", teamId)).first();
        if (teamDoc == null || !teamDoc.containsKey(MEMBERS) || !teamDoc.get(MEMBERS).isArray()) {
            return LicenseGrantResult.MEMBER_NOT_FOUND;
        }

        for (var m : teamDoc.getArray(MEMBERS)) {
            if (m.isDocument() && userId.equals(str(m.asDocument(), "userId", null))) {
                var entry = m.asDocument();
                var alreadyLicensed = entry.containsKey("licensed") && entry.get("licensed").isBoolean()
                        && entry.getBoolean("licensed").getValue();
                return alreadyLicensed ? LicenseGrantResult.ALREADY_LICENSED : LicenseGrantResult.NO_SEAT_AVAILABLE;
            }
        }

        return LicenseGrantResult.MEMBER_NOT_FOUND;
    }

    /**
     * Revokes a member's seat licence. A no-op if the member is not found or not licensed.
     */
    public void revokeLicense(BillingScope scope, BsonValue teamId, String userId) {
        collection(scope).updateOne(
                Filters.and(Filters.eq("_id", teamId), Filters.elemMatch(MEMBERS, Filters.eq("userId", userId))),
                Updates.set(MEMBERS + ".$.licensed", false));
    }

    /**
     * @return the number of members currently licensed, or {@code 0} if the team is not found
     */
    public int licensedCount(BillingScope scope, BsonValue teamId) {
        var teamDoc = collection(scope).find(Filters.eq("_id", teamId)).first();
        if (teamDoc == null || !teamDoc.containsKey(MEMBERS) || !teamDoc.get(MEMBERS).isArray()) {
            return 0;
        }

        var count = 0;
        for (var m : teamDoc.getArray(MEMBERS)) {
            if (m.isDocument() && m.asDocument().containsKey("licensed") && m.asDocument().get("licensed").isBoolean()
                    && m.asDocument().getBoolean("licensed").getValue()) {
                count++;
            }
        }
        return count;
    }

    /**
     * @return the ids of currently licensed members, or an empty list if the team is not found
     */
    public java.util.List<String> licensedUserIds(BillingScope scope, BsonValue teamId) {
        var teamDoc = collection(scope).find(Filters.eq("_id", teamId)).first();
        if (teamDoc == null || !teamDoc.containsKey(MEMBERS) || !teamDoc.get(MEMBERS).isArray()) {
            return java.util.List.of();
        }

        var ids = new ArrayList<String>();
        for (var m : teamDoc.getArray(MEMBERS)) {
            if (!m.isDocument()) {
                continue;
            }
            var entry = m.asDocument();
            var licensed = entry.containsKey("licensed") && entry.get("licensed").isBoolean()
                    && entry.getBoolean("licensed").getValue();
            if (licensed) {
                var userId = str(entry, "userId", null);
                if (userId != null) {
                    ids.add(userId);
                }
            }
        }
        return ids;
    }

    /**
     * @return {@code true} if the given member currently holds a seat licence
     */
    public boolean isLicensed(BillingScope scope, BsonValue teamId, String userId) {
        var teamDoc = collection(scope).find(Filters.eq("_id", teamId)).first();
        if (teamDoc == null || !teamDoc.containsKey(MEMBERS) || !teamDoc.get(MEMBERS).isArray()) {
            return false;
        }

        for (var m : teamDoc.getArray(MEMBERS)) {
            if (m.isDocument() && userId.equals(str(m.asDocument(), "userId", null))) {
                return m.asDocument().containsKey("licensed") && m.asDocument().get("licensed").isBoolean()
                        && m.asDocument().getBoolean("licensed").getValue();
            }
        }
        return false;
    }

    // ── Indexes ─────────────────────────────────────────────────────────────

    /**
     * Creates the unique, sparse index on {@code stripe_customer_id} that every webhook
     * delivery relies on for {@link #findTeamByCustomerId}. Sparse because with lazy
     * Customer provisioning most team documents never get the field at all — a plain
     * unique index would collide on the missing value.
     */
    public void ensureIndexes(BillingScope scope) {
        collection(scope).createIndex(
                Indexes.ascending(STRIPE_CUSTOMER_ID),
                new IndexOptions().unique(true).sparse(true).name(STRIPE_CUSTOMER_ID_INDEX));
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private static String str(BsonDocument d, String key, String defaultValue) {
        return d.containsKey(key) && d.get(key).isString() ? d.getString(key).getValue() : defaultValue;
    }

    private static int int32(BsonDocument d, String key, int defaultValue) {
        if (!d.containsKey(key)) {
            return defaultValue;
        }
        var v = d.get(key);
        if (v.isInt32()) {
            return v.asInt32().getValue();
        }
        if (v.isInt64()) {
            return (int) v.asInt64().getValue();
        }
        if (v.isDouble()) {
            return (int) v.asDouble().getValue();
        }
        return defaultValue;
    }

    private static boolean bool(BsonDocument d, String key, boolean defaultValue) {
        return d.containsKey(key) && d.get(key).isBoolean() ? d.getBoolean(key).getValue() : defaultValue;
    }

    private static Instant instant(BsonDocument d, String key) {
        return d.containsKey(key) && d.get(key).isDateTime()
                ? Instant.ofEpochMilli(d.getDateTime(key).getValue())
                : null;
    }
}
