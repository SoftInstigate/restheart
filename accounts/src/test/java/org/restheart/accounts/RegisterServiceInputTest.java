package org.restheart.accounts;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RegisterServiceInputTest {

    private static String check(String email, String first, String last, String team) {
        return RegisterService.invalidInput(email, first, last, team);
    }

    @Test
    void ordinaryInputIsAccepted() {
        assertNull(check("anna.rossi+test@example.co.uk", "Anna", "Rossi", "Acme S.r.l."));
        assertNull(check("o'brien@example.com".replace("'", ""), "Seán", "O'Brien", "Team Ü"));
    }

    @Test
    void anythingButOneAddressIsRefused() {
        assertNotNull(check("not-an-email", "A", "B", "T"));
        assertNotNull(check("a@b", "A", "B", "T"));
        assertNotNull(check("a@example.com,b@example.com", "A", "B", "T"));
        assertNotNull(check("a@example.com\r\nBcc: x@example.com", "A", "B", "T"));
        assertNotNull(check("Name <a@example.com>", "A", "B", "T"));
        assertNotNull(check("a b@example.com", "A", "B", "T"));
        assertNotNull(check("x".repeat(250) + "@example.com", "A", "B", "T"));
    }

    @Test
    void namesAreOneLineWithoutMarkup() {
        assertNotNull(check("a@example.com", "<a href=x>Click</a>", "B", "T"));
        assertNotNull(check("a@example.com", "A", "B\nC", "T"));
        assertNotNull(check("a@example.com", "A", "B", "Team Two"));
    }

    @Test
    void namesAreBounded() {
        assertNotNull(check("a@example.com", "A".repeat(RegisterService.NAME_MAX + 1), "B", "T"));
        assertNull(check("a@example.com", "A".repeat(RegisterService.NAME_MAX), "B", "T"));
    }
}
