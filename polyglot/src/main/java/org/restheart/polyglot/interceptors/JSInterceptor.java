/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.restheart.configuration.Configuration;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.Interceptor;
import org.restheart.polyglot.JSPlugin;

import com.mongodb.client.MongoClient;

public class JSInterceptor<R extends Request<?>, S extends Response<?>> extends JSPlugin implements Interceptor<R, S> {
    private final String pluginClass;
    private final InterceptPoint interceptPoint;
    private final Source resolveSource;

    /**
     * @param name
     * @param pluginClass
     * @param description
     * @param interceptPoint
     * @param modulesReplacements
     * @param handleSource
     * @param mclient
     * @param resolveSource
     * @param contextOptions
     * @param config
     */
    public JSInterceptor(String name,
        String pluginClass,
        String description,
        InterceptPoint interceptPoint,
        String modulesReplacements,
        Source handleSource,
        Source resolveSource,
        Optional<MongoClient> mclient,
        Configuration config,
        Map<String, String> contextOptions) {
            super(name, description, handleSource, modulesReplacements, config, mclient, contextOptions);
            this.pluginClass = pluginClass;
            this.interceptPoint = interceptPoint;
            this.resolveSource = resolveSource;
    }

    public InterceptPoint getInterceptPoint() {
        return interceptPoint;
    }

    /**
     *
     * @param request
     * @param response
     * @throws java.lang.InterruptedException */
    @Override
    public void handle(R request, S response) throws InterruptedException {
        Context ctx = null;

        try {
            ctx = takeCtx();
            ctx.eval(handleSource()).executeVoid(request, response);
        } finally {
            if (ctx != null) {
                releaseCtx(ctx);
            }
        }
    }

    @Override
    public boolean resolve(R request, S response) {
        Context ctx = null;
        Value ret = null;

        try {
            ctx = takeCtx();
            ret = ctx.eval(this.resolveSource).execute(request);
        } catch(InterruptedException ie) {
            LOGGER.error("error on interceptor {} resolve()", name(), ie);
            request.setInError(true);
            return false;
        } finally {
            if (ctx != null) {
                releaseCtx(ctx);
            }
        }

        if (ret != null && ret.isBoolean()) {
            return ret.asBoolean();
        } else {
            LOGGER.error("resolve() of interceptor {} did not returned a boolean", name());
            request.setInError(true);
            return false;
        }
    }

    public String getPluginClass() {
        return pluginClass;
    }
}
