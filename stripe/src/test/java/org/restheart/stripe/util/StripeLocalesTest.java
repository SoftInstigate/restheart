package org.restheart.stripe.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.stripe.param.checkout.SessionCreateParams;

class StripeLocalesTest {

    @Test
    void theLanguagesOfTheDefaultTemplatesAreAllCheckoutLocales() {
        for (var lang : List.of("en", "it", "es", "fr", "de", "nl", "pt", "pl")) {
            assertEquals(lang, StripeLocales.checkout(lang).orElseThrow().getValue());
        }
    }

    @Test
    void theLanguagesOfTheDefaultTemplatesAreAllPortalLocales() {
        for (var lang : List.of("en", "it", "es", "fr", "de", "nl", "pt", "pl")) {
            assertEquals(lang, StripeLocales.portal(lang).orElseThrow().getValue());
        }
        assertTrue(StripeLocales.portal("xx").isEmpty());
        assertTrue(StripeLocales.portal("auto").isEmpty());
        assertTrue(StripeLocales.portal(null).isEmpty());
    }

    @Test
    void theCodeIsMatchedWhateverItsCase() {
        assertEquals(SessionCreateParams.Locale.IT, StripeLocales.checkout("IT").orElseThrow());
    }

    @Test
    void aLanguageStripeHasNoCheckoutPageForIsNotSent() {
        assertTrue(StripeLocales.checkout("xx").isEmpty());
        assertTrue(StripeLocales.checkout(null).isEmpty());
    }

    @Test
    void autoIsNotALanguage() {
        assertTrue(StripeLocales.checkout("auto").isEmpty());
    }
}
