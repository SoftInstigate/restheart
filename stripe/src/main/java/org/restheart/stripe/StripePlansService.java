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
package org.restheart.stripe;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.plugins.BsonService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.stripe.PlanConfig;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.stripe.util.RequestOverrides;
import org.restheart.stripe.util.StripeCatalogCache;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;

/**
 * {@code GET /stripe/plans} — the plan catalog, with display data (name, description,
 * amount, currency) read from the Stripe Products/Prices the configured price ids point at.
 *
 * <p>⚠️ The module registers no ACL rule for this endpoint — no allow, no veto. It follows
 * whatever the deployment's own ACL says: open to {@code $unauthenticated} for a public
 * pricing page, or kept authenticated. {@code /stripe/webhook} remains the only endpoint
 * public by construction.
 *
 * <p>Configuration declares what the module enforces (plan ids, price ids, seat limits);
 * Stripe holds what the customer reads (name, description, price). This endpoint is where
 * the two are joined for a client.
 */
@RegisterPlugin(
        name = "stripePlansService",
        description = "GET /stripe/plans — the plan catalog with display data from Stripe",
        defaultURI = "/stripe/plans",
        enabledByDefault = false)
public class StripePlansService implements BsonService {

    @Inject("stripeConfig")
    private StripeConfigData conf;

    @Inject("stripeCatalogCache")
    private StripeCatalogCache catalogCache;

    @Override
    public void handle(BsonRequest req, BsonResponse res) throws Exception {
        if (req.isOptions()) {
            handleOptions(req);
            return;
        }
        if (!req.isGet()) {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        var plans = RequestOverrides.plans(req, conf);
        var apiKey = RequestOverrides.secretKey(req, conf);
        var display = catalogCache.get(apiKey, plans);

        var plansArray = new BsonArray();
        for (var entry : plans.entrySet()) {
            plansArray.add(planDoc(entry.getKey(), entry.getValue(), display.get(entry.getKey())));
        }

        res.setStatusCode(HttpStatus.SC_OK);
        res.setContent(BsonUtils.document()
                .put("default_plan", RequestOverrides.defaultPlan(req, conf))
                .put("plans", plansArray)
                .get());
    }

    private static BsonDocument planDoc(String planId, PlanConfig planConf, StripeCatalogCache.PlanDisplay display) {
        var doc = BsonUtils.document()
                .put("id", planId)
                .put("name", display != null && display.name() != null ? display.name() : planId);

        if (display != null && display.description() != null) {
            doc.put("description", display.description());
        }

        if (planConf.seats() != null) {
            var seatsDoc = BsonUtils.document().put("mode", planConf.seats().mode().name().toLowerCase());
            if (planConf.seats().max() != null) {
                seatsDoc.put("max", planConf.seats().max());
            }
            doc.put("seats", seatsDoc);
        }

        if (planConf.limits() != null && !planConf.limits().isEmpty()) {
            var limitsDoc = BsonUtils.document();
            planConf.limits().forEach((k, v) -> {
                if (v instanceof Number n) {
                    limitsDoc.put(k, n.longValue());
                } else if (v instanceof Boolean b) {
                    limitsDoc.put(k, b);
                } else if (v != null) {
                    limitsDoc.put(k, v.toString());
                }
            });
            doc.put("limits", limitsDoc);
        }

        if (display != null && display.prices() != null && !display.prices().isEmpty()) {
            var pricesDoc = BsonUtils.document();
            display.prices().forEach((interval, price) -> {
                var priceDoc = BsonUtils.document().put("price_id", price.priceId());
                priceDoc.put("amount", price.amount() != null ? new BsonInt64(price.amount()) : BsonNull.VALUE);
                priceDoc.put("currency", price.currency() != null ? new BsonString(price.currency()) : BsonNull.VALUE);
                pricesDoc.put(interval, priceDoc);
            });
            doc.put("prices", pricesDoc);
        }

        return doc.get();
    }
}
