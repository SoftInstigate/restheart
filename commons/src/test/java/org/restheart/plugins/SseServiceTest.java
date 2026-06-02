/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.server.handlers.sse.ServerSentEventConnection;
import org.junit.jupiter.api.Test;

/**
 * Verifies that SseService is a proper Plugin sub-type and that
 * @RegisterPlugin metadata is accessible without a MongoDB connection.
 */
public class SseServiceTest {

    @RegisterPlugin(
        name        = "testSseService",
        description = "SSE service used in unit tests",
        defaultURI  = "/sse/test",
        secure      = false
    )
    static class TestSseService implements SseService {
        @Override
        public void onConnect(ServerSentEventConnection connection, String lastEventId) {
            // no-op for test
        }
    }

    @Test
    public void sseServiceIsAPlugin() {
        var svc = new TestSseService();
        assertTrue(svc instanceof Plugin,
            "SseService must extend Plugin");
    }

    @Test
    public void registerPluginAnnotationIsPresent() {
        var ann = TestSseService.class.getAnnotation(RegisterPlugin.class);
        assertNotNull(ann, "@RegisterPlugin must be present");
        assertEquals("testSseService", ann.name());
        assertEquals("/sse/test",      ann.defaultURI());
        assertEquals(false,            ann.secure());
    }

}
