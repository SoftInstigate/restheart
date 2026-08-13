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

import java.util.LinkedHashMap;
import java.util.Map;

import org.restheart.cache.Cache;
import org.restheart.cache.Cache.EXPIRE_POLICY;
import org.restheart.cache.CacheFactory;
import org.restheart.plugins.stripe.PlanConfig;
import org.restheart.plugins.stripe.StripeConfigData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stripe.exception.StripeException;
import com.stripe.model.Price;
import com.stripe.net.RequestOptions;
import com.stripe.param.PriceRetrieveParams;

/**
 * Caches the plan catalog's Stripe display data (Product name/description, Price
 * amount/currency), read from {@code GET /stripe/plans} ({@code StripePlansService}).
 *
 * <p>A public pricing page turns every anonymous visitor into a call to
 * {@code api.stripe.com} otherwise — a network round trip and a rate-limit surface for
 * data that changes a few times a year. Keyed by the effective Stripe API key, since a
 * multi-tenant node's tenants are different Stripe accounts with different catalogs.
 *
 * <h2>Two tiers</h2>
 * <p>{@code fresh} expires after {@link #TTL_MILLIS}; {@code lastKnownGood} never expires
 * and is updated only on a successful fetch. If Stripe is unreachable when the fresh entry
 * has expired, {@link #get} serves the stale-but-known catalog rather than an empty one —
 * a price a few hours out of date is a far smaller problem than a pricing page that renders
 * as "no plans available" only because Stripe's API happened to be down for a request.
 */
public class StripeCatalogCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeCatalogCache.class);

    private static final long TTL_MILLIS = 60 * 60 * 1000; // 1 hour

    private final Cache<String, Map<String, PlanDisplay>> fresh =
            CacheFactory.createLocalCache(1000, EXPIRE_POLICY.AFTER_WRITE, TTL_MILLIS);
    private final Cache<String, Map<String, PlanDisplay>> lastKnownGood =
            CacheFactory.createLocalCache(1000, EXPIRE_POLICY.NEVER, 0);

    /** Display data for one plan: name/description plus per-interval price info. */
    public record PlanDisplay(String name, String description, Map<String, PriceDisplay> prices) {
    }

    /** @param amount the unit amount in the smallest currency unit (e.g. cents) */
    public record PriceDisplay(String priceId, Long amount, String currency) {
    }

    /**
     * Returns the catalog for the given API key, fetching from Stripe on a cache miss or
     * expiry. On a Stripe failure with no cached value at all, returns an empty map — the
     * caller (StripePlansService) still has the configured plan ids and limits, just no
     * display data for them.
     *
     * @param apiKey the effective (possibly per-tenant) Stripe secret key — also the cache key
     * @param plans  the effective plan catalog configuration
     */
    public Map<String, PlanDisplay> get(String apiKey, Map<String, PlanConfig> plans) {
        var cached = fresh.get(apiKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        var built = build(apiKey, plans);
        fresh.put(apiKey, built);
        lastKnownGood.put(apiKey, built);
        return built;
    }

    /** Forces the next {@link #get} for every tenant to refetch — called on {@code product.updated} / {@code price.updated}. */
    public void invalidateAll() {
        fresh.invalidateAll();
    }

    /**
     * Fetches display data for every configured plan. A single plan whose price id is
     * misconfigured or was deleted on Stripe's side must not take the whole catalog down
     * with it — that plan is fetched from {@code lastKnownGood} (or omitted, on a first
     * fetch with no prior value) and every other plan still refreshes normally.
     */
    private Map<String, PlanDisplay> build(String apiKey, Map<String, PlanConfig> plans) {
        var opts = RequestOptions.builder().setApiKey(apiKey).build();
        var expandProduct = PriceRetrieveParams.builder().addExpand("product").build();
        var previousGood = lastKnownGood.get(apiKey).orElseGet(Map::of);

        var result = new LinkedHashMap<String, PlanDisplay>();
        for (var entry : plans.entrySet()) {
            var planId = entry.getKey();
            var planConf = entry.getValue();

            try {
                result.put(planId, fetchPlanDisplay(planId, planConf, expandProduct, opts));
            } catch (StripeException e) {
                LOGGER.warn("[stripe] failed to fetch display data for plan '{}': {}", planId, e.getMessage());
                var stale = previousGood.get(planId);
                if (stale != null) {
                    result.put(planId, stale);
                }
            }
        }
        return result;
    }

    private PlanDisplay fetchPlanDisplay(String planId, PlanConfig planConf, PriceRetrieveParams expandProduct,
            RequestOptions opts) throws StripeException {
        var monthly = fetchPrice(planConf.priceIdMonthly(), expandProduct, opts);
        var annual = fetchPrice(planConf.priceIdAnnual(), expandProduct, opts);

        var reference = monthly != null ? monthly : annual;
        var name = reference != null && reference.getProductObject() != null
                ? reference.getProductObject().getName()
                : planId;
        var description = reference != null && reference.getProductObject() != null
                ? reference.getProductObject().getDescription()
                : null;

        var prices = new LinkedHashMap<String, PriceDisplay>();
        if (monthly != null) {
            prices.put("month", new PriceDisplay(monthly.getId(), monthly.getUnitAmount(), monthly.getCurrency()));
        }
        if (annual != null) {
            prices.put("year", new PriceDisplay(annual.getId(), annual.getUnitAmount(), annual.getCurrency()));
        }

        return new PlanDisplay(name, description, prices);
    }

    private Price fetchPrice(String priceId, PriceRetrieveParams params, RequestOptions opts) throws StripeException {
        return priceId == null || priceId.isBlank() ? null : Price.retrieve(priceId, params, opts);
    }
}
