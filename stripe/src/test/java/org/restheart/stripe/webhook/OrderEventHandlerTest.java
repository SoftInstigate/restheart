package org.restheart.stripe.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OrderEventHandler.formatAmount Tests")
class OrderEventHandlerTest {

    @Test
    @DisplayName("a two-decimal currency divides the minor unit by 100")
    void twoDecimalCurrency() {
        assertEquals("19.90", OrderEventHandler.formatAmount(1990, "eur"));
        assertEquals("19.90", OrderEventHandler.formatAmount(1990, "EUR"));
        assertEquals("0.05", OrderEventHandler.formatAmount(5, "usd"));
        assertEquals("100.00", OrderEventHandler.formatAmount(10000, "usd"));
    }

    @Test
    @DisplayName("a zero-decimal currency is not divided at all")
    void zeroDecimalCurrency() {
        // JPY has no minor unit — Stripe amounts for it are already whole yen.
        assertEquals("500", OrderEventHandler.formatAmount(500, "jpy"));
    }

    @Test
    @DisplayName("a three-decimal currency divides by 1000")
    void threeDecimalCurrency() {
        // BHD (Bahraini dinar) — the case a naive amount/100 would silently misrender.
        assertEquals("19.900", OrderEventHandler.formatAmount(19900, "bhd"));
    }

    @Test
    @DisplayName("an unrecognized currency code falls back to two decimals rather than failing")
    void unknownCurrencyCode_fallsBackToTwoDecimals() {
        assertEquals("19.90", OrderEventHandler.formatAmount(1990, "xyz"));
    }
}
