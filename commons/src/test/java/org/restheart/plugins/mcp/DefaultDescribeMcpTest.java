/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.plugins.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.SseService;

import io.undertow.server.handlers.sse.ServerSentEventConnection;

public class DefaultDescribeMcpTest {

    /** Mode A, code-baked default — no operator config needed to "just work". */
    private static final class PingLikePlugin implements McpAware {
        @Override
        public Map<String, Object> defaultMcpConfig() {
            return Map.of(
                    "description", "Liveness probe.",
                    "actions", Map.of("ping", Map.of("method", "GET")));
        }
    }

    /** Mode A, no code-baked default — plugin author leaves it entirely to the operator. */
    private static final class OperatorOnlyPlugin implements McpAware {
    }

    /** Mode A plugin that is also a JsonService — transport should auto-derive to http. */
    private static final class JsonMcpPlugin implements JsonService, McpAware {
        @Override
        public Map<String, Object> defaultMcpConfig() {
            return Map.of("description", "x", "actions", Map.of("get", Map.of("method", "GET")));
        }

        @Override
        public void handle(JsonRequest request, JsonResponse response) {
        }
    }

    /** Mode A plugin that is an SseService — transport should auto-derive to sse, not silently fall back to http. */
    private static final class SseMcpPlugin implements SseService, McpAware {
        @Override
        public Map<String, Object> defaultMcpConfig() {
            return Map.of("description", "x", "actions", Map.of("subscribe", Map.of("method", "GET")));
        }

        @Override
        public void onConnect(ServerSentEventConnection connection, String lastEventId) {
        }
    }

    private static McpContext ctx(Map<String, Object> pluginConfiguration) {
        return new McpContext(null, "https://cloud.restheart.com", "p", "/p", pluginConfiguration);
    }

    @Test
    public void codeBakedDefault_noOperatorConfig_producesExpectedSingleResource() {
        var resources = new PingLikePlugin().describeMcp(ctx(Map.of()));

        assertEquals(1, resources.size());
        var resource = resources.get(0);
        assertEquals("Liveness probe.", resource.description());
        assertEquals("GET", resource.actions().get("ping").method());
    }

    @Test
    public void codeBakedDefault_operatorOverridesDescription_deepMerged() {
        var operatorConfig = Map.<String, Object>of("mcp-config", Map.of("description", "Sonda di liveness."));
        var resources = new PingLikePlugin().describeMcp(ctx(operatorConfig));

        var resource = resources.get(0);
        assertEquals("Sonda di liveness.", resource.description());
        // the code-baked action survives the merge, since the operator didn't override "actions"
        assertEquals("GET", resource.actions().get("ping").method());
    }

    @Test
    public void operatorOnlyPlugin_withMcpConfig_producesResource() {
        var operatorConfig = Map.<String, Object>of("mcp-config", Map.of("description", "Echoes the request body."));
        var resources = new OperatorOnlyPlugin().describeMcp(ctx(operatorConfig));

        assertEquals(1, resources.size());
        assertEquals("Echoes the request body.", resources.get(0).description());
    }

    @Test
    public void operatorOnlyPlugin_withNoConfigAtAll_producesEmptyDescriptionResource_notAnError() {
        var resources = new OperatorOnlyPlugin().describeMcp(ctx(Map.of()));

        assertEquals(1, resources.size());
        assertNull(resources.get(0).description());
        assertTrue(resources.get(0).actions().isEmpty());
    }

    @Test
    public void jsonServicePlugin_transportAutoDerivedAsHttp() {
        var resource = new JsonMcpPlugin().describeMcp(ctx(Map.of())).get(0);

        @SuppressWarnings("unchecked")
        var transports = (List<Map<String, Object>>) resource.toMap().get("transports");
        assertEquals(1, transports.size());
        assertEquals("http", transports.get(0).get("name"));
    }

    @Test
    public void sseServicePlugin_transportAutoDerivedAsSse_notHttpFallback() {
        var resource = new SseMcpPlugin().describeMcp(ctx(Map.of())).get(0);

        @SuppressWarnings("unchecked")
        var transports = (List<Map<String, Object>>) resource.toMap().get("transports");
        assertEquals(1, transports.size());
        assertEquals("sse", transports.get(0).get("name"));
    }
}
