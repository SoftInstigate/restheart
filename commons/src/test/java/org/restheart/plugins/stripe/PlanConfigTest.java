package org.restheart.plugins.stripe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * {@code fromMap} is the single parser for a {@code stripeConfig.plans.<id>} entry, shared
 * between this module's own YAML loader ({@code StripeConfig.parseSubscriptions}) and a
 * multi-tenant deployment's own per-tenant config interceptor, which parses the equivalent
 * structure out of a MongoDB document instead of YAML — see {@link PlanConfig#fromMap} javadoc.
 * These tests cover the parsing rules the YAML loader used to duplicate.
 */
class PlanConfigTest {

    @Test
    void fromMap_fullPlan_parsesEveryField() {
        var plan = PlanConfig.fromMap(Map.of(
                "price-id-monthly", "price_monthly",
                "price-id-annual", "price_annual",
                "trial-period-days", 30,
                "seats", Map.of("mode", "per_seat", "max", 100),
                "limits", Map.of("max-projects", 50)));

        assertEquals("price_monthly", plan.priceIdMonthly());
        assertEquals("price_annual", plan.priceIdAnnual());
        assertEquals(30, plan.trialPeriodDays());
        assertEquals(SeatsMode.PER_SEAT, plan.seats().mode());
        assertEquals(100, plan.seats().max());
        assertEquals(50, plan.limits().get("max-projects"));
    }

    @Test
    void fromMap_missingSeats_defaultsToUnlimitedWithNoMax() {
        var plan = PlanConfig.fromMap(Map.of("price-id-monthly", "price_x"));

        assertEquals(SeatsMode.UNLIMITED, plan.seats().mode());
        assertNull(plan.seats().max());
    }

    @Test
    void fromMap_unknownSeatsMode_fallsBackToUnlimited() {
        var plan = PlanConfig.fromMap(Map.of("seats", Map.of("mode", "not-a-real-mode")));

        assertEquals(SeatsMode.UNLIMITED, plan.seats().mode());
    }

    @Test
    void fromMap_cappedSeats_parsesModeAndMax() {
        var plan = PlanConfig.fromMap(Map.of("seats", Map.of("mode", "capped", "max", 1)));

        assertEquals(SeatsMode.CAPPED, plan.seats().mode());
        assertEquals(1, plan.seats().max());
    }

    @Test
    void fromMap_noLimits_returnsEmptyMap() {
        var plan = PlanConfig.fromMap(Map.of("price-id-monthly", "price_x"));

        assertTrue(plan.limits().isEmpty());
    }

    @Test
    void fromMap_noPriceIds_bothNull() {
        // The shape of the "free" / default plan: no price, purchasable for neither interval.
        var plan = PlanConfig.fromMap(Map.of());

        assertNull(plan.priceIdMonthly());
        assertNull(plan.priceIdAnnual());
        assertEquals(false, plan.purchasableFor("month"));
        assertEquals(false, plan.purchasableFor("year"));
    }

    @Test
    void fromMap_sameResultRegardlessOfMapOrigin() {
        // A YAML-parsed map and a BSON-document-converted map both arrive as plain
        // Map<String,Object> with the same keys — fromMap must treat them identically.
        Map<String, Object> yamlLike = new java.util.LinkedHashMap<>();
        yamlLike.put("price-id-monthly", "price_x");
        yamlLike.put("seats", Map.of("mode", "capped", "max", 5));

        Map<String, Object> bsonConvertedLike = new java.util.HashMap<>();
        bsonConvertedLike.put("price-id-monthly", "price_x");
        bsonConvertedLike.put("seats", Map.of("mode", "capped", "max", 5));

        assertEquals(PlanConfig.fromMap(yamlLike), PlanConfig.fromMap(bsonConvertedLike));
    }
}
