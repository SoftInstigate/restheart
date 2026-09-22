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
import java.util.Optional;

import org.graalvm.polyglot.PolyglotException;
import org.restheart.configuration.Configuration;
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;
import org.restheart.plugins.StringService;
import org.restheart.polyglot.ContextQueue;
import org.restheart.polyglot.PolyglotThreadUtils;
import org.restheart.polyglot.services.JSService;
import org.restheart.polyglot.services.JSServiceArgs;

import com.mongodb.client.MongoClient;

/**
 * Python counterpart of {@link org.restheart.polyglot.services.JSStringService}.
 */
public class PyStringService extends JSService implements StringService {

    public PyStringService(Path pluginPath, Optional<MongoClient> mclient, Configuration config) throws IOException, InterruptedException {
        super(args(pluginPath, mclient, config));
    }

    private static JSServiceArgs args(Path pluginPath, Optional<MongoClient> mclient, Configuration config) throws IOException, InterruptedException {
        var contextOptions = PyPlugins.contextOptions(PyPlugins.pluginRoot(pluginPath));

        return PolyglotThreadUtils.onPlatformThreadIO(() -> {
            var ctx = ContextQueue.newContext(engine(), "foo", config, LOGGER, mclient, "", contextOptions);
            ctx.enter();
            try {
                var options = PyPlugins.options(ctx.eval(PyPlugins.memberSource(pluginPath, "options")), pluginPath);

                var name = PyPlugins.requireString(options, "name", pluginPath);
                var description = PyPlugins.requireString(options, "description", pluginPath);
                var uri = PyPlugins.requireString(options, "uri", pluginPath);
                if (!uri.startsWith("/")) {
                    throw new IllegalArgumentException("wrong python service " + pluginPath.toAbsolutePath() + ", options['uri'] must start with /");
                }
                var secured = options.get("secured") instanceof Boolean b && b;
                var matchPolicy = options.containsKey("matchPolicy")
                        ? MATCH_POLICY.valueOf(PyPlugins.requireString(options, "matchPolicy", pluginPath))
                        : MATCH_POLICY.PREFIX;

                var handleSource = PyPlugins.memberSource(pluginPath, "handle");
                PyPlugins.requireCallable(ctx.eval(handleSource), "handle", pluginPath);

                return new JSServiceArgs(name, description, uri, secured, null, matchPolicy, handleSource, config, mclient, contextOptions);
            } catch (PolyglotException pe) {
                // e.g. a SyntaxError or an ImportError of a missing package
                throw new IllegalArgumentException("wrong python plugin " + pluginPath.toAbsolutePath() + ": " + pe.getMessage(), pe);
            } finally {
                ctx.leave();
                ctx.close();
            }
        });
    }

    @Override
    public void handle(StringRequest request, StringResponse response) throws Exception {
        contextQueue().executeWithContext(ctx -> {
            var handleFunction = ContextQueue.cacheHandleFunction(ctx, handleSource());
            handleFunction.executeVoid(request, response);
        });
    }
}
