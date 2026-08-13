package org.restheart.stripe.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.restheart.plugins.stripe.PlanConfig;
import org.restheart.plugins.stripe.SeatsConfig;
import org.restheart.plugins.stripe.SeatsMode;
import org.restheart.plugins.stripe.SubscriptionState;

class SeatsTest {

    private static SubscriptionState stateWithSeats(int seats) {
        return new SubscriptionState("gold", "price_x", "active", "sub_x", null, null, seats, false, null);
    }

    private static SubscriptionState stateOverLimitSince(Instant since) {
        return new SubscriptionState("gold", "price_x", "active", "sub_x", null, null, 1, false, since);
    }

    @Test
    void limit_capped_returnsThePlanMax() {
        var plan = new PlanConfig(null, null, null, new SeatsConfig(SeatsMode.CAPPED, 10), java.util.Map.of());
        assertEquals(10, Seats.limit(plan, stateWithSeats(1)));
    }

    @Test
    void limit_perSeat_returnsThePurchasedQuantity_notThePlanMax() {
        // per-seat's "max" is an optional upper bound on what can be purchased, not the
        // limit itself — the limit is however many seats were actually bought.
        var plan = new PlanConfig(null, null, null, new SeatsConfig(SeatsMode.PER_SEAT, 100), java.util.Map.of());
        assertEquals(7, Seats.limit(plan, stateWithSeats(7)));
    }

    @Test
    void limit_unlimited_returnsNull() {
        var plan = new PlanConfig(null, null, null, new SeatsConfig(SeatsMode.UNLIMITED, null), java.util.Map.of());
        assertNull(Seats.limit(plan, stateWithSeats(1)));
    }

    @Test
    void limit_unconfiguredPlanOrSeats_returnsNull_ratherThanThrowing() {
        assertNull(Seats.limit(null, stateWithSeats(1)));
        var planWithNoSeatsConfig = new PlanConfig(null, null, null, null, java.util.Map.of());
        assertNull(Seats.limit(planWithNoSeatsConfig, stateWithSeats(1)));
    }

    @Test
    void available_subtractsLicensedFromLimit_flooredAtZero() {
        assertEquals(3, Seats.available(10, 7));
        assertEquals(0, Seats.available(10, 15)); // over limit — never negative
    }

    @Test
    void available_unlimited_returnsNull() {
        assertNull(Seats.available(null, 100));
    }

    @Test
    void overLimitDays_notOverLimit_returnsNull() {
        assertNull(Seats.overLimitDays(stateOverLimitSince(null)));
    }

    @Test
    void overLimitDays_countsWholeDaysSinceTheTransition() {
        var fiveDaysAgo = Instant.now().minus(5, ChronoUnit.DAYS);
        assertEquals(5, Seats.overLimitDays(stateOverLimitSince(fiveDaysAgo)));
    }

    @Test
    void overLimitDays_justNow_isZero() {
        assertEquals(0, Seats.overLimitDays(stateOverLimitSince(Instant.now())));
    }
}
