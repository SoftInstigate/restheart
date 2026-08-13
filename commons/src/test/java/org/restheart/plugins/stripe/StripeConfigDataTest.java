package org.restheart.plugins.stripe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Covers the reverse price-id-to-plan attribution (#681) and the per-plan config
 * fallbacks (trial days) that {@code StripeWebhookService} and {@code StripeCheckoutService}
 * depend on.
 */
class StripeConfigDataTest {

    private static StripeConfigData conf() {
        var plans = Map.of(
                "free", new PlanConfig(null, null, null, new SeatsConfig(SeatsMode.CAPPED, 1), Map.of()),
                "gold", new PlanConfig("price_monthly", "price_annual", 30,
                        new SeatsConfig(SeatsMode.CAPPED, 10), Map.of("max-projects", 50)));

        return new StripeConfigData(
                "sk_test_x", "whsec_x", plans, "free", 0,
                "restheart", "teams", "https://x/success", "https://x/cancel", "https://x/portal",
                Map.of());
    }

    @Test
    void byPriceId_resolvesMonthlyAndAnnualPrices() {
        var conf = conf();

        var monthly = conf.byPriceId("price_monthly");
        assertTrue(monthly.isPresent());
        assertEquals("gold", monthly.get().planId());
        assertEquals("month", monthly.get().interval());

        var annual = conf.byPriceId("price_annual");
        assertTrue(annual.isPresent());
        assertEquals("gold", annual.get().planId());
        assertEquals("year", annual.get().interval());
    }

    @Test
    void byPriceId_unknownPriceResolvesToEmpty_neverToDefaultPlan() {
        // This is the failure mode that must never happen: an unrecognised price id
        // must not silently resolve to any plan, default or otherwise — the caller
        // (StripeWebhookService) is responsible for keeping the previous plan.
        assertTrue(conf().byPriceId("price_unknown").isEmpty());
    }

    @Test
    void byPriceId_nullOrBlankResolvesToEmpty() {
        assertTrue(conf().byPriceId(null).isEmpty());
        assertTrue(conf().byPriceId("").isEmpty());
    }

    @Test
    void priceId_returnsNullForUnconfiguredInterval() {
        // 'free' has no price ids at all — must not throw, must not fabricate a value.
        assertNull(conf().priceId("free", "month"));
    }

    @Test
    void plan_returnsNullForUnknownPlanId_ratherThanThrowing() {
        assertNull(conf().plan("does-not-exist"));
    }

    @Test
    void effectiveTrialPeriodDays_perPlanOverridesModuleDefault() {
        var conf = conf();
        assertEquals(30, conf.effectiveTrialPeriodDays("gold"));
        assertEquals(0, conf.effectiveTrialPeriodDays("free")); // no plan-level override -> module default
    }

    @Test
    void notification_fallsBackToNameDefault_whenNotExplicitlyConfigured() {
        var conf = conf(); // notifications map is empty
        assertFalse(conf.notification(NotificationConfig.PAYMENT_FAILED).enabled());
        assertTrue(conf.notification(NotificationConfig.SUBSCRIPTION_CANCELED).enabled());
    }

    @Test
    void isLiveMode_trueOnlyForLiveSecretKey() {
        assertFalse(conf().isLiveMode()); // sk_test_x

        var liveConf = new StripeConfigData(
                "sk_live_x", "whsec_x", Map.of(), "free", 0,
                "restheart", "teams", "", "", "", Map.of());
        assertTrue(liveConf.isLiveMode());
    }
}
