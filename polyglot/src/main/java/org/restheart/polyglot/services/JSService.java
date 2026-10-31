/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.polyglot.services;

import java.util.Optional;

import org.restheart.configuration.Configuration;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;
import org.restheart.polyglot.JSPlugin;
import org.restheart.polyglot.spi.PluginDefinition;
import org.restheart.polyglot.spi.RhRuntime;

import com.mongodb.client.MongoClient;

/**
 * A JavaScript service: uri, match policy and whether it is secured come from the module's
 * declaration; the subclasses fix the content kind.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class JSService extends JSPlugin {
    protected JSService(PluginDefinition definition, RhRuntime runtime, Configuration config, Optional<MongoClient> mclient) {
        super(definition, runtime, config, mclient);
    }

    public String uri() {
        return definition().uri();
    }

    public boolean secured() {
        return definition().secured();
    }

    public MATCH_POLICY matchPolicy() {
        return definition().matchPolicy();
    }
}
