package org.restheart.emails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class EmailRendererTest {

    private static final String TEMPLATE = """
            <html><head><title>Welcome to {{app-name}}, {{first-name}}</title></head>
            <body><p>Hi {{first-name}},</p><a href="{{verification-url}}">Verify</a></body></html>
            """;

    @Test
    void bodyValuesAreHtmlEscaped() {
        var r = EmailRenderer.render(TEMPLATE,
                Map.of("first-name", "<a href=\"https://evil.example\">Click</a>",
                        "app-name", "App", "verification-url", "https://x"), "en");

        assertFalse(r.htmlBody().contains("<a href=\"https://evil.example\">"));
        assertTrue(r.htmlBody().contains("&lt;a href=&quot;https://evil.example&quot;&gt;Click&lt;/a&gt;"));
    }

    @Test
    void urlsKeepWorkingWhenEscaped() {
        var r = EmailRenderer.render(TEMPLATE,
                Map.of("first-name", "Ann", "app-name", "App",
                        "verification-url", "https://app.example/verify?email=a%40b.c&token=123"), "en");

        assertTrue(r.htmlBody().contains("href=\"https://app.example/verify?email=a%40b.c&amp;token=123\""));
    }

    @Test
    void subjectValuesLoseLineBreaksButAreNotEscaped() {
        var r = EmailRenderer.render(TEMPLATE,
                Map.of("first-name", "Ann\r\nBcc: victim@example.com", "app-name", "R&D", "verification-url", "x"),
                "en");

        assertFalse(r.subject().contains("\n"));
        assertFalse(r.subject().contains("\r"));
        assertEquals("Welcome to R&D, Ann Bcc: victim@example.com", r.subject());
    }

    @Test
    void aValueIsNotExpandedAgain() {
        var r = EmailRenderer.render(TEMPLATE,
                Map.of("first-name", "{{verification-url}}", "app-name", "App", "verification-url", "https://x"),
                "en");

        assertTrue(r.htmlBody().contains("Hi {{verification-url}},"));
    }

    @Test
    void unknownPlaceholdersAreLeftAsIs() {
        assertEquals("Hi {{nobody}}", EmailRenderer.substituteVars("Hi {{nobody}}", Map.of("x", "y")));
    }

    @Test
    void dollarsAndBackslashesInValuesAreKept() {
        assertEquals("Pay $5 \\ now", EmailRenderer.substituteVars("Pay {{amount}} now", Map.of("amount", "$5 \\")));
    }
}
