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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * A {@code plugins-args:} wrapper is never read (restheart#723): a plugin's arguments are looked
 * up as a top-level key named after the plugin. Everything nested under that wrapper is therefore
 * inert — the plugin starts on its defaults and nothing says a setting was dropped, which is how
 * {@code restheart-mqtt} shipped with an entirely inert configuration.
 */
public class PluginsArgsWrapperTest {

    @Test
    public void theFlatShapeIsNotMistakenForTheWrapper() {
        var conf = new LinkedHashMap<String, Object>();
        conf.put("myPlugin", Map.of("apiKey", "secret"));

        assertTrue(PluginsFactory.unreadPluginsArgsKeys(conf).isEmpty(),
                "the supported, flat shape must not warn");
    }

    @Test
    public void theWrapperNamesEveryPluginItWouldSwallow() {
        var conf = new LinkedHashMap<String, Object>();
        conf.put("plugins-args", Map.of("myPlugin", Map.of("apiKey", "secret")));

        assertEquals(Set.of("myPlugin"), PluginsFactory.unreadPluginsArgsKeys(conf),
                "the warning has to say which settings were ignored, or it doesn't help anyone");
    }

    @Test
    public void anEmptyOrAbsentConfigurationIsNotAWarning() {
        assertTrue(PluginsFactory.unreadPluginsArgsKeys(null).isEmpty());
        assertTrue(PluginsFactory.unreadPluginsArgsKeys(new LinkedHashMap<>()).isEmpty());
        // a scalar under that name is not a wrapper of anything
        assertTrue(PluginsFactory.unreadPluginsArgsKeys(Map.of("plugins-args", "nonsense")).isEmpty());
    }
}
