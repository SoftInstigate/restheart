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
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.Request;

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

    private Request<?> requestWith(String origin, List<String> overrideWhitelist) {
        var req = mock(Request.class);
        when(req.getPath()).thenReturn("/some/path");
        when(req.getHeader("Origin")).thenReturn(origin);
        when(req.attachedParam("override-origin-whitelist")).thenReturn(overrideWhitelist);
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
}
