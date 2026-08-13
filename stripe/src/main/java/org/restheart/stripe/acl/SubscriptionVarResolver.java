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
package org.restheart.stripe.acl;

import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.restheart.exchange.Request;
import org.restheart.exchange.ServiceRequest;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.security.VarResolver;
import org.restheart.stripe.StripeService;
import org.restheart.stripe.util.RequestOverrides;
import org.restheart.stripe.util.SubscriptionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exposes the caller's subscription state to ACL predicates and {@code readFilter} /
 * {@code mergeRequest} documents as {@code @subscription}, via the {@code VarResolver} SPI.
 *
 * <pre>{@code
 * - roles: [user]
 *   predicate: "path-prefix('/api') and @subscription.licensed"
 * }</pre>
 *
 * <h2>Shape</h2>
 * <pre>{@code
 * {
 *   "plan":                 "gold",
 *   "status":               "active",
 *   "active":               true,
 *   "licensed":             true,
 *   "trial_end":            ISODate,
 *   "current_period_end":   ISODate,
 *   "cancel_at_period_end": false,
 *   "seats": {
 *     "limit":            10,
 *     "licensed":         7,
 *     "available":        3,
 *     "over_limit":       false,
 *     "grace_expires_at": ISODate
 *   }
 * }
 * }</pre>
 *
 * <p>{@code licensed} (top level) is the calling user's own licence, already accounting for
 * the over-limit block: once the grace period has expired it is {@code false} for every user
 * of the entity, regardless of their individual licence — see {@link Seats#isBlocked}.
 *
 * <h2>Failure semantics</h2>
 * <p>Any failure resolving the owner, or any exception during resolution, returns
 * {@link BsonNull#VALUE} — treated by {@code AclVarsInterpolator} as unresolved, which denies
 * rather than grants. This resolver never throws.
 *
 * <h2>Cost</h2>
 * <p>The underlying {@code SubscriptionOwner} / state / licence lookups are cached on the
 * exchange for the life of the request (the resolver contract's caching responsibility,
 * since {@code AclVarsInterpolator} only memoizes per exact expression string, and a
 * predicate naming both {@code @subscription.plan} and {@code @subscription.status} would
 * otherwise redo the work).
 */
public class SubscriptionVarResolver implements VarResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionVarResolver.class);

    private static final String PREFIX = "@subscription";
    private static final String CACHE_KEY = "__stripe_subscription_var_cache";

    private final StripeService stripeService;
    private final StripeConfigData conf;

    public SubscriptionVarResolver(StripeService stripeService, StripeConfigData conf) {
        this.stripeService = stripeService;
        this.conf = conf;
    }

    @Override
    public String name() {
        return "subscription";
    }

    @Override
    public BsonValue resolve(Request<?> request, String var) {
        if (!(request instanceof ServiceRequest<?> req)) {
            return BsonNull.VALUE;
        }

        try {
            var doc = cachedDoc(req);
            if (doc == null) {
                return BsonNull.VALUE;
            }

            if (PREFIX.equals(var)) {
                return doc;
            }
            if (var.startsWith(PREFIX + ".")) {
                return navigate(doc, var.substring(PREFIX.length() + 1));
            }
            return BsonNull.VALUE;
        } catch (Exception e) {
            // A broken resolver must deny, never grant — see VarResolver's failure semantics.
            LOGGER.warn("[stripe] @subscription resolution failed for {}: {}", var, e.toString());
            return BsonNull.VALUE;
        }
    }

    @Override
    public boolean cacheable() {
        return true;
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private BsonDocument cachedDoc(ServiceRequest<?> req) {
        Object cached = req.attachedParam(CACHE_KEY);
        if (cached instanceof BsonDocument d) {
            return d;
        }
        if (cached instanceof NotResolved) {
            return null;
        }

        var doc = build(req);
        req.attachParam(CACHE_KEY, doc != null ? doc : NotResolved.INSTANCE);
        return doc;
    }

    private BsonDocument build(ServiceRequest<?> req) {
        var provider = stripeService.getSubscriptionOwnerProvider();
        var scope = RequestOverrides.scope(req, conf);

        var ownerOpt = provider.fromRequest(req, scope);
        if (ownerOpt.isEmpty()) {
            return null;
        }
        var owner = ownerOpt.get();

        var defaultPlanId = RequestOverrides.defaultPlan(req, conf);
        var state = provider.readSubscription(owner, defaultPlanId);

        // Shared with GET /stripe/subscription (StripeSubscriptionService), so the billing
        // page a client renders and what this ACL variable enforces can never disagree.
        return SubscriptionView.build(provider, req, conf, owner, state);
    }

    private static BsonValue navigate(BsonDocument doc, String dottedPath) {
        BsonValue current = doc;
        for (var segment : dottedPath.split("\\.")) {
            if (!(current instanceof BsonDocument d) || !d.containsKey(segment)) {
                return BsonNull.VALUE;
            }
            current = d.get(segment);
        }
        return current;
    }

    /** Sentinel cached when the caller resolves to no owner, so a second lookup is not repeated. */
    private enum NotResolved {
        INSTANCE
    }
}
