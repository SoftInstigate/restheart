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
package org.restheart.security.authorizers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.Request;
import org.restheart.plugins.security.Authorizer;

public class OriginVetoerTest {

    private OriginVetoer newVetoer(List<String> whitelist) throws Exception {
        var vetoer = new OriginVetoer();
        Map<String, Object> config = new HashMap<>();
        if (whitelist != null) {
            config.put("whitelist", whitelist);
        }
        var configField = OriginVetoer.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(vetoer, config);
        vetoer.init();
        return vetoer;
    }

    private OriginVetoer newVetoer(List<String> whitelist, boolean allowMissingOrigin) throws Exception {
        var vetoer = new OriginVetoer();
        Map<String, Object> config = new HashMap<>();
        if (whitelist != null) {
            config.put("whitelist", whitelist);
        }
        config.put("allow-missing-origin", allowMissingOrigin);
        var configField = OriginVetoer.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(vetoer, config);
        vetoer.init();
        return vetoer;
    }

    private Request<?> requestWith(String origin, List<String> overrideWhitelist) {
        return requestWith(origin, overrideWhitelist, null);
    }

    private Request<?> requestWith(String origin, List<String> overrideWhitelist, Boolean overrideAllowMissing) {
        var req = mock(Request.class);
        when(req.getPath()).thenReturn("/some/path");
        when(req.getHeader("Origin")).thenReturn(origin);
        when(req.attachedParam("override-origin-whitelist")).thenReturn(overrideWhitelist);
        when(req.attachedParam("override-origin-allow-missing")).thenReturn(overrideAllowMissing);
        return req;
    }

    /** An OPTIONS request; a genuine CORS preflight also carries Access-Control-Request-Method. */
    private Request<?> optionsRequestWith(String origin, String accessControlRequestMethod) {
        var req = mock(Request.class);
        when(req.getPath()).thenReturn("/some/path");
        when(req.isOptions()).thenReturn(true);
        when(req.getHeader("Origin")).thenReturn(origin);
        when(req.getHeader("Access-Control-Request-Method")).thenReturn(accessControlRequestMethod);
        return req;
    }

    @Test
    void noOverride_usesStaticWhitelist() throws Exception {
        var vetoer = newVetoer(List.of("https://allowed.example.com"));

        assertTrue(vetoer.isAllowed(requestWith("https://allowed.example.com", null)));
        assertFalse(vetoer.isAllowed(requestWith("https://not-allowed.example.com", null)));
    }

    @Test
    void overridePresent_takesPrecedenceOverStaticWhitelist() throws Exception {
        var vetoer = newVetoer(List.of("https://static-allowed.example.com"));

        // an origin NOT in the static whitelist is allowed because the
        // override whitelist takes over entirely for this request
        assertTrue(vetoer.isAllowed(
                requestWith("https://tenant-allowed.example.com", List.of("https://tenant-allowed.example.com"))));

        // an origin that WAS in the static whitelist is now rejected, since
        // the override replaces (not merges with) the static list
        assertFalse(vetoer.isAllowed(
                requestWith("https://static-allowed.example.com", List.of("https://tenant-allowed.example.com"))));
    }

    @Test
    void overrideEmptyList_denyAll() throws Exception {
        // no static whitelist at all (would normally mean "allow all")
        var vetoer = newVetoer(null);

        assertFalse(vetoer.isAllowed(requestWith("https://anything.example.com", List.of())));
    }

    @Test
    void noOverrideNoStaticWhitelist_allowsAll() throws Exception {
        var vetoer = newVetoer(null);

        assertTrue(vetoer.isAllowed(requestWith("https://anything.example.com", null)));
        assertTrue(vetoer.isAllowed(requestWith(null, null)));
    }

    @Test
    void allowMissingOrigin_defaultAllows() throws Exception {
        var vetoer = newVetoer(List.of("https://allowed.example.com"));

        // default (true): missing Origin is allowed
        assertTrue(vetoer.isAllowed(requestWith(null, null)));
    }

    @Test
    void allowMissingOrigin_true_allowsMissingOrigin() throws Exception {
        var vetoer = newVetoer(List.of("https://allowed.example.com"), true);

        // missing Origin is allowed
        assertTrue(vetoer.isAllowed(requestWith(null, null)));
        // non-whitelisted Origin is still denied
        assertFalse(vetoer.isAllowed(requestWith("https://not-allowed.example.com", null)));
        // whitelisted Origin is still allowed
        assertTrue(vetoer.isAllowed(requestWith("https://allowed.example.com", null)));
    }

    @Test
    void overrideAllowMissing_true_allowsMissingOrigin() throws Exception {
        var vetoer = newVetoer(List.of("https://allowed.example.com"));

        // per-request override allows missing Origin even when static config denies it
        assertTrue(vetoer.isAllowed(requestWith(null, null, true)));
    }

    @Test
    void overrideAllowMissing_false_deniesMissingOrigin() throws Exception {
        var vetoer = newVetoer(List.of("https://allowed.example.com"), true);

        // per-request override denies missing Origin even when static config allows it
        assertFalse(vetoer.isAllowed(requestWith(null, null, false)));
    }

    @Test
    void allowMissingOrigin_withOverrideWhitelist() throws Exception {
        var vetoer = newVetoer(List.of("https://static.example.com"), true);

        // missing Origin allowed even with override whitelist in play
        assertTrue(vetoer.isAllowed(requestWith(null, List.of("https://tenant.example.com"))));
        // whitelisted origin still works
        assertTrue(vetoer.isAllowed(requestWith("https://tenant.example.com", List.of("https://tenant.example.com"))));
        // non-whitelisted origin still denied
        assertFalse(vetoer.isAllowed(requestWith("https://other.example.com", List.of("https://tenant.example.com"))));
    }

    @Test
    void corsPreflight_allowedEvenWithNonWhitelistedOrigin() throws Exception {
        var vetoer = newVetoer(List.of("https://allowed.example.com"), false);

        // a preflight carries no credentials, so it can't be a CSRF vector: it is
        // let through so the service can answer with the CORS headers, and the
        // actual request is vetoed instead
        assertTrue(vetoer.isAllowed(optionsRequestWith("https://not-allowed.example.com", "POST")));
    }

    @Test
    void deniedOrigin_attachesVetoMessageWithOffendingOrigin() throws Exception {
        var vetoer = newVetoer(List.of("https://allowed.example.com"));
        var request = requestWith("https://not-allowed.example.com", null);

        assertFalse(vetoer.isAllowed(request));
        verify(request).attachParam(Authorizer.VETO_MESSAGE, "Origin https://not-allowed.example.com not allowed");
    }

    @Test
    void missingOrigin_attachesVetoMessage() throws Exception {
        var vetoer = newVetoer(List.of("https://allowed.example.com"), false);
        var request = requestWith(null, null);

        assertFalse(vetoer.isAllowed(request));
        verify(request).attachParam(Authorizer.VETO_MESSAGE, "Origin header is required");
    }

    @Test
    void optionsWithoutPreflightHeaders_isStillChecked() throws Exception {
        var vetoer = newVetoer(List.of("https://allowed.example.com"), false);

        // OPTIONS without Access-Control-Request-Method is not a preflight
        assertFalse(vetoer.isAllowed(optionsRequestWith("https://not-allowed.example.com", null)));

        // OPTIONS without Origin is not a preflight either — it never comes from a
        // browser, so it stays subject to allow-missing-origin
        assertFalse(vetoer.isAllowed(optionsRequestWith(null, null)));
        assertFalse(vetoer.isAllowed(optionsRequestWith(null, "POST")));
    }
}
