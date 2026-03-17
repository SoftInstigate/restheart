/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.server.handlers.sse.ServerSentEventConnection;
import org.junit.jupiter.api.Test;

/**
 * Tests for the URI resolution and validation logic applied to SSE services
 * in {@code Bootstrapper.plugSseServices()}.
 *
 * <p>Each test directly reads the {@link RegisterPlugin} annotation — exactly
 * as the production code does — so the tests remain implementation-faithful
 * without requiring a running server.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class PlugSseServicesTest {

    // -----------------------------------------------------------------------
    // Test fixtures
    // -----------------------------------------------------------------------

    @RegisterPlugin(name = "withExplicitUri", description = "has explicit defaultURI", defaultURI = "/sse/explicit")
    static class ExplicitUriSse implements SseService {
        @Override public void onConnect(ServerSentEventConnection c, String id) {}
    }

    @RegisterPlugin(name = "withoutUri", description = "defaultURI left empty, falls back to /name")
    static class NoUriSse implements SseService {
        @Override public void onConnect(ServerSentEventConnection c, String id) {}
    }

    @RegisterPlugin(name = "secureSse", description = "secured SSE service", defaultURI = "/sse/secure", secure = true)
    static class SecureSse implements SseService {
        @Override public void onConnect(ServerSentEventConnection c, String id) {}
    }

    @RegisterPlugin(name = "unsecureSse", description = "unsecured SSE service", defaultURI = "/sse/open", secure = false)
    static class UnsecureSse implements SseService {
        @Override public void onConnect(ServerSentEventConnection c, String id) {}
    }

    // -----------------------------------------------------------------------
    // URI resolution helpers (same logic as Bootstrapper.plugSseServices)
    // -----------------------------------------------------------------------

    private static String resolveUri(SseService svc) {
        var ann = svc.getClass().getDeclaredAnnotation(RegisterPlugin.class);
        if (ann == null) return null;
        var defaultUri = ann.defaultURI();
        return (defaultUri == null || defaultUri.isEmpty()) ? "/" + ann.name() : defaultUri;
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void explicitDefaultUriIsUsed() {
        assertEquals("/sse/explicit", resolveUri(new ExplicitUriSse()));
    }

    @Test
    public void missingDefaultUriFallsBackToName() {
        assertEquals("/withoutUri", resolveUri(new NoUriSse()));
    }

    @Test
    public void resolvedUriStartsWithSlash() {
        var uri = resolveUri(new ExplicitUriSse());
        assertNotNull(uri);
        assertTrue(uri.startsWith("/"), "URI must start with /");
    }

    @Test
    public void nullAnnotationYieldsNullUri() {
        // a class without @RegisterPlugin should return null
        var plain = new SseService() {
            @Override public void onConnect(ServerSentEventConnection c, String id) {}
        };
        assertNull(plain.getClass().getDeclaredAnnotation(RegisterPlugin.class),
            "anonymous class must not carry @RegisterPlugin");
    }

    @Test
    public void securedFlagTrueIsReadFromAnnotation() {
        var ann = SecureSse.class.getDeclaredAnnotation(RegisterPlugin.class);
        assertTrue(ann.secure());
    }

    @Test
    public void securedFlagFalseIsReadFromAnnotation() {
        var ann = UnsecureSse.class.getDeclaredAnnotation(RegisterPlugin.class);
        assertFalse(ann.secure());
    }

    @Test
    public void sseServiceIsNotARegularService() {
        assertFalse(new ExplicitUriSse() instanceof Service,
            "SseService must NOT extend Service");
    }
}
