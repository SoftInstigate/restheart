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
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.plugins.StringService;
import org.restheart.polyglot.spi.PluginDefinition;
import org.restheart.polyglot.spi.RhRuntime;

import com.mongodb.client.MongoClient;

/**
 * The JavaScript service of content kind string: a {@link StringService} that delegates to the
 * plugin's {@code handle(req, res)}.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JSStringService extends JSService implements StringService {
    public JSStringService(PluginDefinition definition, RhRuntime runtime, Configuration config, Optional<MongoClient> mclient) {
        super(definition, runtime, config, mclient);
    }

    @Override
    public void handle(StringRequest request, StringResponse response) throws Exception {
        invokeHandle(request, response);
    }
}
