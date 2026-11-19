package org.restheart.stripe.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.plugins.stripe.StripeConfigData;

class RequestOverridesDefaultLocaleTest {

    private static StripeConfigData conf(String defaultLocale) {
        return new StripeConfigData("sk_test_x", "whsec_x", "restheart", "teams", null, null, defaultLocale);
    }

    private static ByteArrayRequest requestWithOverride(Object value) {
        var req = mock(ByteArrayRequest.class);
        when(req.attachedParam(RequestOverrides.DEFAULT_LOCALE)).thenReturn(value);
        return req;
    }

    @Test
    void theConfigurationsDefaultWhenTheTenantHasNone() {
        assertEquals("de", RequestOverrides.defaultLocale(requestWithOverride(null), conf("de")));
    }

    @Test
    void theTenantsOverrideWins_inLowerCase() {
        assertEquals("it", RequestOverrides.defaultLocale(requestWithOverride("IT"), conf("de")));
    }

    @Test
    void anOverrideThatIsNotALanguageCodeIsIgnored() {
        assertEquals("de", RequestOverrides.defaultLocale(requestWithOverride("it-IT"), conf("de")));
        assertEquals("de", RequestOverrides.defaultLocale(requestWithOverride(1), conf("de")));
    }

    @Test
    void englishWhenNothingNamesALanguage() {
        assertEquals("en", RequestOverrides.defaultLocale(requestWithOverride(null), conf(null)));
        assertEquals("en", RequestOverrides.defaultLocale(null, conf(null)));
    }
}
