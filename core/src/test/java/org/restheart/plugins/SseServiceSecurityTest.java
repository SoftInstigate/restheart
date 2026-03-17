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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.server.handlers.sse.ServerSentEventConnection;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link PluginRecord#isSecure()} correctly reflects the
 * {@code secure} attribute of {@link RegisterPlugin} for SSE services.
 *
 * <p>These tests run without a running server or MongoDB connection.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class SseServiceSecurityTest {

    @RegisterPlugin(name = "secureSseRecord", description = "secured", defaultURI = "/sse/s", secure = true)
    static class SecureSseService implements SseService {
        @Override public void onConnect(ServerSentEventConnection c, String id) {}
    }

    @RegisterPlugin(name = "openSseRecord", description = "open", defaultURI = "/sse/o", secure = false)
    static class OpenSseService implements SseService {
        @Override public void onConnect(ServerSentEventConnection c, String id) {}
    }

    private static PluginRecord<SseService> record(SseService svc, boolean secure) {
        return new PluginRecord<>(
            svc.getClass().getAnnotation(RegisterPlugin.class).name(),
            svc.getClass().getAnnotation(RegisterPlugin.class).description(),
            secure,
            true,
            svc.getClass().getName(),
            svc,
            null
        );
    }

    @Test
    public void secureRecordReportsSecure() {
        var rec = record(new SecureSseService(), true);
        assertTrue(rec.isSecure(), "PluginRecord with secure=true must report isSecure()==true");
    }

    @Test
    public void openRecordReportsNotSecure() {
        var rec = record(new OpenSseService(), false);
        assertFalse(rec.isSecure(), "PluginRecord with secure=false must report isSecure()==false");
    }

    @Test
    public void confArgsOverrideAnnotationSecureFlag() {
        // config can override the annotation; simulate via PluginRecord.isSecure(boolean, Map)
        assertTrue(PluginRecord.isSecure(false, java.util.Map.of("secured", true)),
            "config 'secured:true' must override annotation secure=false");
        assertFalse(PluginRecord.isSecure(true, java.util.Map.of("secured", false)),
            "config 'secured:false' must override annotation secure=true");
    }
}
