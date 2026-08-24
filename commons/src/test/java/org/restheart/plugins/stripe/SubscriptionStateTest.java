package org.restheart.plugins.stripe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SubscriptionStateTest {

    @Test
    void defaultFor_returnsFreeStateWithNoStripeLinkage() {
        var state = SubscriptionState.defaultFor("free");

        assertEquals("free", state.plan());
        assertNull(state.priceId());
        assertNull(state.status());
        assertNull(state.stripeSubscriptionId());
        assertNull(state.trialEnd());
        assertNull(state.currentPeriodEnd());
        assertEquals(1, state.seats());
        assertFalse(state.cancelAtPeriodEnd());
        assertNull(state.overLimitSince());
        assertFalse(state.isActive());
        assertFalse(state.isOverLimit());
    }

    @Test
    void isActive_trueForActiveAndTrialing() {
        assertTrue(stateWithStatus("active").isActive());
        assertTrue(stateWithStatus("trialing").isActive());
        assertFalse(stateWithStatus("past_due").isActive());
        assertFalse(stateWithStatus("canceled").isActive());
        assertFalse(stateWithStatus(null).isActive());
    }

    @Test
    void isTrialing_onlyTrueForTrialingStatus() {
        assertTrue(stateWithStatus("trialing").isTrialing());
        assertFalse(stateWithStatus("active").isTrialing());
    }

    @Test
    void isOverLimit_reflectsPresenceOfOverLimitSince() {
        var over = new SubscriptionState("gold", "price_x", "active", "sub_x", null, null, 5, false, Instant.now());
        var within = new SubscriptionState("gold", "price_x", "active", "sub_x", null, null, 5, false, null);

        assertTrue(over.isOverLimit());
        assertFalse(within.isOverLimit());
    }

    private static SubscriptionState stateWithStatus(String status) {
        return new SubscriptionState("gold", "price_x", status, "sub_x", null, null, 1, false, null);
    }
}
