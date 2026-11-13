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
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.Interceptor;
import org.restheart.polyglot.JSPlugin;
import org.restheart.polyglot.spi.PluginDefinition;
import org.restheart.polyglot.spi.RhRuntime;

import com.mongodb.client.MongoClient;

/**
 * A JavaScript interceptor: {@code handle} and {@code resolve} delegate to the plugin module.
 * The subclasses fix the request and response types, which is what the registry matches an
 * interceptor to a service with.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JSInterceptor<R extends Request<?>, S extends Response<?>> extends JSPlugin implements Interceptor<R, S> {
    // read by reflection in PluginUtils.interceptPoint(): a JS interceptor has no @RegisterPlugin
    // annotation, and the registry runs an interceptor at the point this field names
    private final InterceptPoint interceptPoint;
    private final String pluginClass;

    public JSInterceptor(PluginDefinition definition, RhRuntime runtime, Configuration config, Optional<MongoClient> mclient) {
        super(definition, runtime, config, mclient);
        this.interceptPoint = definition.interceptPoint();
        this.pluginClass = definition.pluginClass() != null ? definition.pluginClass() : definition.contentKind().key();
    }

    public InterceptPoint getInterceptPoint() {
        return interceptPoint;
    }

    @Override
    public void handle(R request, S response) throws Exception {
        invokeHandle(request, response);
    }

    @Override
    public boolean resolve(R request, S response) {
        return invokeResolve(request, response);
    }

    /**
     * @return the legacy {@code pluginClass}, or the content kind when the plugin declares none
     */
    public String getPluginClass() {
        return pluginClass;
    }
}
