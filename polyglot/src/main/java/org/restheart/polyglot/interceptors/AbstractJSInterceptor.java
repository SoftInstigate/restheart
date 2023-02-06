/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2023 SoftInstigate
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

import com.google.common.collect.Maps;
import com.mongodb.client.MongoClient;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.restheart.configuration.Configuration;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.Interceptor;
import org.restheart.polyglot.AbstractJSPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractJSInterceptor<R extends Request<?>, S extends Response<?>> extends AbstractJSPlugin implements Interceptor<R, S> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJSInterceptor.class);

    private final Source resolveSource;

    /**
     * @param name
     * @param pluginClass
     * @param description
     * @param interceptPoint
     * @param mclient
     * @param modulesReplacements
     */
    public AbstractJSInterceptor(String name,
        String pluginClass,
        String description,
        InterceptPoint interceptPoint,
        Source handleSource,
        Source resolveSource,
        Optional<MongoClient> mclient,
        Configuration config,
        Map<String, String> contextOptions) {
            super(name, pluginClass, description, null, false, null, interceptPoint, config, false, true);
            this.contextOptions = contextOptions;
            this.mclient = mclient;
            this.conf = config;
            this.handleSource = handleSource;
            this.resolveSource = resolveSource;
    }

    /**
     *
     */
    public void handle(R request, S response) {
        _handle().executeVoid(request, response);
    }

    @Override
    public boolean resolve(R request, S response) {
        var ret = resolve().execute(request);

        if (ret.isBoolean()) {
            return ret.asBoolean();
        } else {
            LOGGER.error("resolve() of interceptor did not returned a boolean", name);
            return false;
        }
    }

    // each working thread is associates with one resolve
    // because js Context does not allow multithreaded access
    protected Map<String, Value> resolves = Maps.newHashMap();

    /**
     *
     * @return the resolve Value associated with this thread. If not existing, it instanitates it.
     */
    private Value resolve() {
        var workingThreadName = Thread.currentThread().getName();

        if (this.resolves.containsKey(workingThreadName)) {
            return this.resolves.get(workingThreadName);
        } else {
            var resolve = ctx().eval(this.resolveSource);
            this.resolves.put(workingThreadName, resolve);
            return resolve;
        }
    }
}
