/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.security.services;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.ByteArrayRequest;

/**
 * Tests for {@code GET /token/redirect}'s redirect-target resolution:
 * per-request override vs. static config vs. neither (see
 * {@code AuthTokenService.resolveRedirectUrl}).
 */
public class AuthTokenServiceRedirectTest {

    private AuthTokenService newService(Map<String, Object> config) throws Exception {
        var service = new AuthTokenService();
        var configField = AuthTokenService.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(service, config);
        return service;
    }

    private ByteArrayRequest requestWithOverride(String overrideRedirectUrl) {
        var request = mock(ByteArrayRequest.class);
        when(request.attachedParam("override-redirect-url")).thenReturn(overrideRedirectUrl);
        return request;
    }

    @Test
    void noOverride_usesStaticConfig() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put("redirect-url", "https://app.example.com/callback");
        var service = newService(config);

        assertEquals("https://app.example.com/callback", service.resolveRedirectUrl(requestWithOverride(null)));
    }

    @Test
    void override_takesPrecedenceOverStaticConfig() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put("redirect-url", "https://static.example.com/callback");
        var service = newService(config);

        assertEquals("https://tenant.example.com/callback",
                service.resolveRedirectUrl(requestWithOverride("https://tenant.example.com/callback")));
    }

    @Test
    void neitherOverrideNorStaticConfig_returnsNull() throws Exception {
        var service = newService(new HashMap<>());

        assertNull(service.resolveRedirectUrl(requestWithOverride(null)));
    }

    @Test
    void blankOverride_fallsBackToStaticConfig() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put("redirect-url", "https://static.example.com/callback");
        var service = newService(config);

        assertEquals("https://static.example.com/callback", service.resolveRedirectUrl(requestWithOverride("  ")));
    }

    // ── TokenRedirectHelper: fragment construction ──────────────────────────

    @Test
    void fragment_includesTokenTypeAndExpiresIn() {
        var url = TokenRedirectHelper.appendTokenFragment(
                "https://app.example.com/callback", "the-token", "Bearer", 1800);

        assertEquals("https://app.example.com/callback#access_token=the-token&token_type=Bearer&expires_in=1800", url);
    }

    @Test
    void fragment_omitsTokenTypeAndExpiresInWhenNull() {
        var url = TokenRedirectHelper.appendTokenFragment(
                "https://app.example.com/callback", "the-token", null, null);

        assertEquals("https://app.example.com/callback#access_token=the-token", url);
    }

    @Test
    void fragment_neverAppearsInQueryString() {
        var url = TokenRedirectHelper.appendTokenFragment(
                "https://app.example.com/callback?flow=signin", "secret-token-value", "Bearer", null);

        var queryPart = url.substring(0, url.indexOf('#'));
        var fragmentPart = url.substring(url.indexOf('#'));

        org.junit.jupiter.api.Assertions.assertFalse(queryPart.contains("secret-token-value"));
        org.junit.jupiter.api.Assertions.assertTrue(fragmentPart.contains("secret-token-value"));
    }
}
