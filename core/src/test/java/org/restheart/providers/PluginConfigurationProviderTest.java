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
package org.restheart.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.plugins.Plugin;
import org.restheart.plugins.PluginRecord;

/**
 * {@code @Inject("config")} must not receive null when a plugin has no configuration block
 * (restheart#732).
 */
public class PluginConfigurationProviderTest {

    @Test
    public void missingConfigurationBlockReturnsEmptyMap() {
        var record = new PluginRecord<>(
                "my-service",
                "test plugin",
                false,
                true,
                "org.example.MyService",
                mock(Plugin.class),
                null);

        var config = new PluginConfigurationProvider().get(record);

        assertNotNull(config);
        assertTrue(config.isEmpty());
        assertEquals("sensors/#", config.getOrDefault("topic", "sensors/#"));
    }

    @Test
    public void existingConfigurationIsPassedThrough() {
        var confArgs = Map.<String, Object>of("topic", "custom/topic");
        var record = new PluginRecord<>(
                "my-service",
                "test plugin",
                false,
                true,
                "org.example.MyService",
                mock(Plugin.class),
                confArgs);

        var config = new PluginConfigurationProvider().get(record);

        assertSame(confArgs, config);
        assertEquals("custom/topic", config.get("topic"));
    }
}
