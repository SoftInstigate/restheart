/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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

import com.mongodb.MongoClient;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.Interceptor;
import org.restheart.polyglot.AbstractJSPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractJSInterceptor<R extends Request<?>, S extends Response<?>> extends AbstractJSPlugin implements Interceptor<R, S> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJSInterceptor.class);

    final Map<String, String> OPTS;

    private final Engine engine = Engine.create();
    private final Source source;

    private final MongoClient mclient;

    private final Map<String, Object> args;

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
        Source source,
        MongoClient mclient,
        Map<String, Object> args,
        Map<String, String> OPTS) {
            super(name, pluginClass, description, null, false, null, interceptPoint);
            this.OPTS = OPTS;
            this.mclient = mclient;
            this.args = args;
            this.source = source;
    }

    /**
     *
     */
    public void handle(R request, S response) {
        try (var ctx = context(engine, OPTS)) {
            ctx.getBindings("js").putMember("LOGGER", LOGGER);

            if (this.mclient != null) {
                ctx.getBindings("js").putMember("mclient", this.mclient);
            }

            if (this.args != null) {
                ctx.getBindings("js").putMember("pluginArgs", this.args);
            }

            ctx.eval(source).getMember("handle").executeVoid(request, response);
        }
    }

    @Override
    public boolean resolve(R request, S response) {
        try (var ctx = context(engine, OPTS)) {
            ctx.getBindings("js").putMember("LOGGER", LOGGER);

            if (this.mclient != null) {
                ctx.getBindings("js").putMember("mclient", this.mclient);
            }

            var ret = ctx.eval(source).getMember("resolve").execute(request);

            if (ret.isBoolean()) {
                return ret.asBoolean();
            } else {
                LOGGER.error("resolve() of plugin did not returned a boolean", name);
                return false;
            }
        }
    }
}
