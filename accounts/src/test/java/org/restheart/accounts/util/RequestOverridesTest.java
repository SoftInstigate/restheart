package org.restheart.accounts.util;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.JsonRequest;
import org.restheart.plugins.accounts.AccountsConfigData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the per-request overrides added for issues #659 (terms/privacy version)
 * and #661 (account-properties-claims): fall back to {@link AccountsConfigData} when
 * no attached-param is present, and prefer the override when it is.
 */
class RequestOverridesTest {

    private static final AccountsConfigData CONF = new AccountsConfigData(
            "restheart",            // db
            "users",                // usersCollection
            "My App",               // appName
            "jwt-key",              // jwtKey
            "jwt-issuer",           // jwtIssuer
            15,                     // jwtTtl
            "example.com",          // cookieDomain
            "rh_auth",              // cookieName
            true,                   // cookieSecure
            "https://example.com",  // frontendUrl
            "https://example.com/app", // frontendAppUrl
            "1.0",                  // termsVersion
            "1.0",                  // privacyVersion
            "en",                   // defaultLocale
            null,                   // verificationTemplatePath
            null,                   // passwordResetTemplatePath
            null,                   // inviteTemplatePath
            "team",                 // teamClaimName
            "member",               // memberRoleName
            true,                   // membershipEndpointsEnabled
            "owner",                // ownershipRole
            "user",                 // defaultRole
            List.of("srvNode"),     // accountPropertiesClaims
            null);                  // usersUnrestrictedRoles

    private JsonRequest requestWithParam(String key, Object value) {
        var req = mock(JsonRequest.class);
        when(req.attachedParam(key)).thenReturn(value);
        return req;
    }

    @Test
    void termsVersion_fallsBackToStaticConfigWhenNotOverridden() {
        assertEquals("1.0", RequestOverrides.termsVersion(requestWithParam(RequestOverrides.TERMS_VERSION, null), CONF));
    }

    @Test
    void termsVersion_prefersOverrideWhenPresent() {
        var req = requestWithParam(RequestOverrides.TERMS_VERSION, "2.0");
        assertEquals("2.0", RequestOverrides.termsVersion(req, CONF));
    }

    @Test
    void privacyVersion_fallsBackToStaticConfigWhenNotOverridden() {
        assertEquals("1.0", RequestOverrides.privacyVersion(requestWithParam(RequestOverrides.PRIVACY_VERSION, null), CONF));
    }

    @Test
    void privacyVersion_prefersOverrideWhenPresent() {
        var req = requestWithParam(RequestOverrides.PRIVACY_VERSION, "2.0");
        assertEquals("2.0", RequestOverrides.privacyVersion(req, CONF));
    }

    @Test
    void accountPropertiesClaims_fallsBackToStaticConfigWhenNotOverridden() {
        var req = requestWithParam(RequestOverrides.ACCOUNT_PROPERTIES_CLAIMS, null);
        assertEquals(List.of("srvNode"), RequestOverrides.accountPropertiesClaims(req, CONF));
    }

    @Test
    void accountPropertiesClaims_prefersOverrideWhenPresent() {
        var req = requestWithParam(RequestOverrides.ACCOUNT_PROPERTIES_CLAIMS, List.of("consents", "plan"));
        assertEquals(List.of("consents", "plan"), RequestOverrides.accountPropertiesClaims(req, CONF));
    }

    @Test
    void accountPropertiesClaims_ignoresEmptyOverrideList() {
        var req = requestWithParam(RequestOverrides.ACCOUNT_PROPERTIES_CLAIMS, List.of());
        assertEquals(List.of("srvNode"), RequestOverrides.accountPropertiesClaims(req, CONF));
    }
}
