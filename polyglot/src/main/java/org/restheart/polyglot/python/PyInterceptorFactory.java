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
package org.restheart.polyglot.python;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Optional;

import org.graalvm.polyglot.PolyglotException;
import org.restheart.configuration.Configuration;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.PluginRecord;
import org.restheart.polyglot.ContextQueue;
import org.restheart.polyglot.JSPlugin;
import org.restheart.polyglot.PolyglotThreadUtils;
import org.restheart.polyglot.interceptors.ByteArrayJSInterceptor;
import org.restheart.polyglot.interceptors.ByteArrayProxyJSInterceptor;
import org.restheart.polyglot.interceptors.CsvJSInterceptor;
import org.restheart.polyglot.interceptors.JsonJSInterceptor;
import org.restheart.polyglot.interceptors.MongoJSInterceptor;
import org.restheart.polyglot.interceptors.StringJSInterceptor;
import org.restheart.polyglot.interceptors.WildCardJSInterceptor;

import com.mongodb.client.MongoClient;

/**
 * Python counterpart of {@link org.restheart.polyglot.interceptors.JSInterceptorFactory}.
 * Python interceptors reuse the JSInterceptor classes: only the Sources differ.
 */
public class PyInterceptorFactory {
    private final Optional<MongoClient> mclient;
    private final Configuration config;

    public PyInterceptorFactory(Optional<MongoClient> mclient, Configuration config) {
        this.mclient = mclient;
        this.config = config;
    }

    public PluginRecord<Interceptor<?, ?>> create(Path pluginPath) throws IOException, InterruptedException {
        var contextOptions = PyPlugins.contextOptions(PyPlugins.pluginRoot(pluginPath));

        return PolyglotThreadUtils.onPlatformThreadIO(() -> {
            var ctx = ContextQueue.newContext(JSPlugin.engine(), "foo", config, null, mclient, "", contextOptions);
            ctx.enter();
            try {
                var options = PyPlugins.options(ctx.eval(PyPlugins.memberSource(pluginPath, "options")), pluginPath);

                var name = PyPlugins.requireString(options, "name", pluginPath);
                var description = PyPlugins.requireString(options, "description", pluginPath);
                var interceptPoint = options.containsKey("interceptPoint")
                        ? InterceptPoint.valueOf(PyPlugins.requireString(options, "interceptPoint", pluginPath))
                        : InterceptPoint.REQUEST_AFTER_AUTH;
                var pluginClass = options.containsKey("pluginClass")
                        ? PyPlugins.requireString(options, "pluginClass", pluginPath)
                        : "StringInterceptor";

                var handleSource = PyPlugins.memberSource(pluginPath, "handle");
                PyPlugins.requireCallable(ctx.eval(handleSource), "handle", pluginPath);
                var resolveSource = PyPlugins.memberSource(pluginPath, "resolve");
                PyPlugins.requireCallable(ctx.eval(resolveSource), "resolve", pluginPath);

                var interceptor = switch (pluginClass.replace("org.restheart.plugins.", "")) {
                    case "StringInterceptor", "BsonInterceptor" -> new StringJSInterceptor(name, pluginClass, description, interceptPoint, null, handleSource, resolveSource, mclient, config, contextOptions);
                    case "ByteArrayInterceptor" -> new ByteArrayJSInterceptor(name, pluginClass, description, interceptPoint, null, handleSource, resolveSource, mclient, config, contextOptions);
                    case "ByteArrayProxyInterceptor" -> new ByteArrayProxyJSInterceptor(name, pluginClass, description, interceptPoint, null, handleSource, resolveSource, mclient, config, contextOptions);
                    case "CsvInterceptor" -> new CsvJSInterceptor(name, pluginClass, description, interceptPoint, null, handleSource, resolveSource, mclient, config, contextOptions);
                    case "JsonInterceptor" -> new JsonJSInterceptor(name, pluginClass, description, interceptPoint, null, handleSource, resolveSource, mclient, config, contextOptions);
                    case "MongoInterceptor" -> new MongoJSInterceptor(name, pluginClass, description, interceptPoint, null, handleSource, resolveSource, mclient, config, contextOptions);
                    case "WildCardJSInterceptor" -> new WildCardJSInterceptor(name, pluginClass, description, interceptPoint, null, handleSource, resolveSource, mclient, config, contextOptions);
                    default -> throw new IllegalArgumentException("wrong python interceptor " + pluginPath.toAbsolutePath() + ", wrong options['pluginClass']");
                };

                return new PluginRecord<Interceptor<?, ?>>(interceptor.name(), interceptor.getDescription(), false, true,
                        interceptor.getClass().getName(), interceptor, new HashMap<>());
            } catch (PolyglotException pe) {
                // e.g. a SyntaxError or an ImportError of a missing package
                throw new IllegalArgumentException("wrong python plugin " + pluginPath.toAbsolutePath() + ": " + pe.getMessage(), pe);
            } finally {
                ctx.leave();
                ctx.close();
            }
        });
    }
}
