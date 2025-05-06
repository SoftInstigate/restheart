/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2025 SoftInstigate
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

import java.util.Map;
import java.util.Optional;

import org.graalvm.polyglot.Source;
import org.restheart.configuration.Configuration;
import org.restheart.exchange.ByteArrayProxyRequest;
import org.restheart.exchange.ByteArrayProxyResponse;
import org.restheart.plugins.InterceptPoint;

import com.mongodb.client.MongoClient;

public class ByteArrayProxyJSInterceptor extends JSInterceptor<ByteArrayProxyRequest, ByteArrayProxyResponse> {
    public ByteArrayProxyJSInterceptor(String name,
        String pluginClass,
        String description,
        InterceptPoint interceptPoint,
        String modulesReplacements,
        Source handleSource,
        Source resolveSource,
        Optional<MongoClient> mclient,
        Configuration config,
        Map<String, String> contextOptions) {
            super(name, pluginClass, description, interceptPoint, modulesReplacements, handleSource, resolveSource, mclient, config, contextOptions);
    }
}