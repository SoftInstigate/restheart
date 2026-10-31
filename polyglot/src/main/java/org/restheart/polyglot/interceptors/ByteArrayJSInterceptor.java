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
package org.restheart.polyglot.interceptors;

import java.util.Optional;

import org.restheart.configuration.Configuration;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.polyglot.spi.PluginDefinition;
import org.restheart.polyglot.spi.RhRuntime;

import com.mongodb.client.MongoClient;

/**
 * The JavaScript interceptor of content kind bytes.
 */
public class ByteArrayJSInterceptor extends JSInterceptor<ByteArrayRequest, ByteArrayResponse> {
    public ByteArrayJSInterceptor(PluginDefinition definition, RhRuntime runtime, Configuration config, Optional<MongoClient> mclient) {
        super(definition, runtime, config, mclient);
    }
}
