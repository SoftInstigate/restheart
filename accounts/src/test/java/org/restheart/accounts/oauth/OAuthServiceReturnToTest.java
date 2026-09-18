package org.restheart.accounts.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * {@code returnTo} decides where a freshly issued token is delivered at the end of the flow, so a
 * value this service can be talked into is the whole of an open redirect. A path on this host, and
 * nothing that a browser would read as another one.
 */
public class OAuthServiceReturnToTest {

    @Test
    public void absent_isNotAReturnTo() throws Exception {
        assertNull(OAuthService.validReturnTo(null));
        assertNull(OAuthService.validReturnTo(""));
        assertNull(OAuthService.validReturnTo("   "));
    }

    @Test
    public void aPathOnThisHost_isKept() throws Exception {
        assertEquals("/static/oauth/login.html", OAuthService.validReturnTo("/static/oauth/login.html"));
        assertEquals("/login?client_id=abc&state=xyz", OAuthService.validReturnTo("/login?client_id=abc&state=xyz"));
    }

    @Test
    public void anAbsoluteUrl_isRefused() {
        assertThrows(OAuthService.OAuthException.class, () -> OAuthService.validReturnTo("https://elsewhere.example/steal"));
        assertThrows(OAuthService.OAuthException.class, () -> OAuthService.validReturnTo("http://elsewhere.example/steal"));
    }

    @Test
    public void aProtocolRelativeUrl_isRefused() {
        // the one that reads like a path and is not: a browser sends //elsewhere.example to that host
        assertThrows(OAuthService.OAuthException.class, () -> OAuthService.validReturnTo("//elsewhere.example/steal"));
    }

    @Test
    public void aBackslashUrl_isRefused() {
        // some browsers normalise backslashes to slashes, so \\elsewhere.example ends up the same
        assertThrows(OAuthService.OAuthException.class, () -> OAuthService.validReturnTo("/\\elsewhere.example"));
        assertThrows(OAuthService.OAuthException.class, () -> OAuthService.validReturnTo("\\\\elsewhere.example"));
    }
}
