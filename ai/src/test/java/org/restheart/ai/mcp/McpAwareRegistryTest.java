/*-
 * ========================LICENSE_START=================================
 * restheart-ai
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
package org.restheart.ai.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.restheart.plugins.mcp.McpAware;
import org.restheart.plugins.mcp.McpContext;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.Service;

public class McpAwareRegistryTest {

    /** Mode A plugin, no defaultMcpConfig() override. */
    private static final class PlainMcpService implements JsonService, McpAware {
        @Override
        public void handle(JsonRequest request, JsonResponse response) {
        }
    }

    /** Mode A plugin with a code-baked default. */
    private static final class PingLikeService implements JsonService, McpAware {
        @Override
        public Map<String, Object> defaultMcpConfig() {
            return Map.of("description", "Liveness probe.");
        }

        @Override
        public void handle(JsonRequest request, JsonResponse response) {
        }
    }

    /** Mode B plugin: overrides describeMcp() directly, no defaultMcpConfig() needed. */
    private static final class DynamicService implements JsonService, McpAware {
        @Override
        public List<McpResource> describeMcp(McpContext ctx) {
            return List.of(McpResource.builder().uri(ctx.baseUrl() + ctx.pluginUri()).build());
        }

        @Override
        public void handle(JsonRequest request, JsonResponse response) {
        }
    }

    private static final class NotMcpAwareService implements JsonService {
        @Override
        public void handle(JsonRequest request, JsonResponse response) {
        }
    }

    @SuppressWarnings("unchecked")
    private static PluginRecord<Service<?, ?>> recordFor(String name, Service<?, ?> instance, boolean enabled, Map<String, Object> confArgs) {
        PluginRecord<Service<?, ?>> record = mock(PluginRecord.class);
        when(record.getName()).thenReturn(name);
        // getInstance() returns Service<?, ?>; each occurrence captures a distinct
        // wildcard type variable, so when(...).thenReturn(instance) doesn't unify.
        // doReturn(...).when(...) sidesteps compile-time generic capture entirely.
        doReturn(instance).when(record).getInstance();
        when(record.isEnabled()).thenReturn(enabled);
        when(record.getConfArgs()).thenReturn(confArgs);
        return record;
    }

    private static PluginsRegistry registryWith(PluginRecord<Service<?, ?>>... records) {
        var registry = mock(PluginsRegistry.class);
        when(registry.getServices()).thenReturn(Set.of(records));
        return registry;
    }

    @Test
    public void notMcpAware_neverConsidered_configIgnored() {
        var record = recordFor("plain", new NotMcpAwareService(), true, Map.of("mcp", true, "uri", "/plain"));
        var registered = McpAwareRegistry.discover(registryWith(record)).registered();
        assertTrue(registered.isEmpty());
    }

    @Test
    public void mcpAware_noConfig_registeredByDefault() {
        var record = recordFor("plain", new PlainMcpService(), true, Map.of("uri", "/plain"));
        var registered = McpAwareRegistry.discover(registryWith(record)).registered();

        assertEquals(1, registered.size());
        assertEquals("plain", registered.get(0).pluginName());
        assertEquals("/plain", registered.get(0).pluginUri());
    }

    @Test
    public void mcpAware_mcpFalseInConfig_notRegistered() {
        var record = recordFor("plain", new PlainMcpService(), true, Map.of("uri", "/plain", "mcp", false));
        var registered = McpAwareRegistry.discover(registryWith(record)).registered();
        assertTrue(registered.isEmpty());
    }

    @Test
    public void mcpAware_pluginItselfDisabled_notRegistered() {
        var record = recordFor("plain", new PlainMcpService(), false, Map.of("uri", "/plain"));
        var registered = McpAwareRegistry.discover(registryWith(record)).registered();
        assertTrue(registered.isEmpty());
    }

    @Test
    public void modeA_withCodeBakedDefault_registeredWithoutWarningPath() {
        var record = recordFor("ping", new PingLikeService(), true, Map.of("uri", "/ping"));
        var registered = McpAwareRegistry.discover(registryWith(record)).registered();
        assertEquals(1, registered.size());
    }

    @Test
    public void modeB_customDescribeMcp_registeredEvenWithNoDefaultConfig() {
        var record = recordFor("dynamic", new DynamicService(), true, Map.of("uri", "/dynamic"));
        var registered = McpAwareRegistry.discover(registryWith(record)).registered();

        assertEquals(1, registered.size());
        // the Mode B override is honored: describeMcp() works even though neither
        // defaultMcpConfig() nor "mcp-config" was ever supplied
        var resources = registered.get(0).instance().describeMcp(
                new McpContext(null, "https://host", "dynamic", "/dynamic", Map.of()));
        assertEquals("https://host/dynamic", resources.get(0).uri());
    }

    @Test
    public void pluginConfigurationIsPassedThrough() {
        var confArgs = Map.<String, Object>of("uri", "/plain", "mcp-config", Map.of("description", "x"));
        var record = recordFor("plain", new PlainMcpService(), true, confArgs);
        var registered = McpAwareRegistry.discover(registryWith(record)).registered();
        assertEquals(confArgs, registered.get(0).pluginConfiguration());
    }
}
