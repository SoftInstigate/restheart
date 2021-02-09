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

import java.util.HashMap;
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

    Map<String, String> OPTS = new HashMap<>();

    private final Engine engine = Engine.create();
    private final Source source;

    private final String modulesReplacements;

    private final MongoClient mclient;

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
        String modulesReplacements) {
            super(name, pluginClass, description, null, false, null, interceptPoint);
            this.modulesReplacements = modulesReplacements;
            this.mclient = mclient;
            this.source = source;
    }

    /**
     *
     */
    public void handle(R request, S response) {
        if (modulesReplacements != null) {
            LOGGER.debug("modules-replacements: {} ", modulesReplacements);
            OPTS.put("js.commonjs-core-modules-replacements", modulesReplacements);
        } else {
            OPTS.remove("js.commonjs-core-modules-replacements");
        }

        try (var ctx = context(engine, OPTS)) {
            ctx.getBindings("js").putMember("LOGGER", LOGGER);

            if (this.mclient != null) {
                ctx.getBindings("js").putMember("mclient", this.mclient);
            }

            ctx.eval(source).getMember("handle").executeVoid(request, response);
        }
    }

    @Override
    public boolean resolve(R request, S response) {
        if (modulesReplacements != null) {
            LOGGER.debug("modules-replacements: {} ", modulesReplacements);
            OPTS.put("js.commonjs-core-modules-replacements", modulesReplacements);
        } else {
            OPTS.remove("js.commonjs-core-modules-replacements");
        }

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
