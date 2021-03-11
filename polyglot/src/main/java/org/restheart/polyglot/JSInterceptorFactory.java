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
package org.restheart.polyglot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import com.mongodb.MongoClient;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.PluginRecord;
import org.restheart.polyglot.interceptors.AbstractJSInterceptor;
import org.restheart.polyglot.interceptors.ByteArrayJSInterceptor;
import org.restheart.polyglot.interceptors.ByteArrayProxyJSInterceptor;
import org.restheart.polyglot.interceptors.CsvJSInterceptor;
import org.restheart.polyglot.interceptors.JsonJSInterceptor;
import org.restheart.polyglot.interceptors.MongoJSInterceptor;
import org.restheart.polyglot.interceptors.StringJSInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JSInterceptorFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(JSInterceptorFactory.class);

    Map<String, String> OPTS = new HashMap<>();

    private final MongoClient mclient;

    private final Map<String, Object> pluginsArgs;

    private static final String errorHint = "hint: the last statement in the script should be:\n({\n\toptions: {..},\n\thandle: (request, response) => {},\n\tresolve: (request) => {}\n})";

    public JSInterceptorFactory(Path requireCdw, MongoClient mclient, Map<String, Object> pluginsArgs) {
        this.mclient = mclient;
        this.pluginsArgs = pluginsArgs;
        OPTS.put("js.commonjs-require", "true");
        OPTS.put("js.commonjs-require-cwd", requireCdw.toAbsolutePath().toString());
    }

    @SuppressWarnings("rawtypes")
    public PluginRecord<Interceptor> create(Path scriptPath) throws IOException {
        var language = Source.findLanguage(scriptPath.toFile());

        Source source;

        if ("js".equals(language)) {
            if (scriptPath.getFileName().toString().endsWith(".mjs")) {
                source = Source.newBuilder("js", scriptPath.toFile()).mimeType("application/javascript+module").build();
            } else {
                source = Source.newBuilder("js", scriptPath.toFile()).build();
            }
        } else {
            throw new IllegalArgumentException("Interceptor is not javascript " + scriptPath);
        }

        // check plugin definition

        var engine = Engine.create();

        try (Context ctx = Context.newBuilder().engine(engine).allowAllAccess(true)
                .allowHostClassLookup(className -> true)
                .allowIO(true)
                .allowExperimentalOptions(true)
                .options(OPTS)
                .build()) {
            Value parsed;

            try {
                ctx.getBindings("js").putMember("LOGGER", LOGGER);
                ctx.getBindings("js").putMember("mclient", this.mclient);
                ctx.getBindings("js").putMember("args", null);

                parsed = ctx.eval(source);
            } catch (Throwable t) {
                throw new IllegalArgumentException("wrong js plugin, " + t.getMessage());
            }

            if (parsed.getMemberKeys().isEmpty()) {
                throw new IllegalArgumentException("wrong js plugin, " + errorHint);
            }

            if (!parsed.getMemberKeys().contains("options")) {
                throw new IllegalArgumentException("wrong js plugin, missing member 'options', " + errorHint);
            }

            if (!parsed.getMember("options").getMemberKeys().contains("name")) {
                throw new IllegalArgumentException("wrong js plugin, missing member 'options.name', " + errorHint);
            }

            if (!parsed.getMember("options").getMember("name").isString()) {
                throw new IllegalArgumentException("wrong js plugin, wrong member 'options.name', " + errorHint);
            }

            var name = parsed.getMember("options").getMember("name").asString();

            if (!parsed.getMember("options").getMemberKeys().contains("description")) {
                throw new IllegalArgumentException(
                        "wrong js plugin, missing member 'options.description', " + errorHint);
            }

            if (!parsed.getMember("options").getMember("description").isString()) {
                throw new IllegalArgumentException("wrong js plugin, wrong member 'options.description', " + errorHint);
            }

            var description = parsed.getMember("options").getMember("description").asString();

            InterceptPoint interceptPoint;
            String pluginClass;
            String modulesReplacements;

            if (!parsed.getMember("options").getMemberKeys().contains("interceptPoint")) {
                interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH;
            } else {
                if (!parsed.getMember("options").getMember("interceptPoint").isString()) {
                    throw new IllegalArgumentException(
                            "wrong js plugin, wrong member 'options.interceptPoint', " + errorHint);
                } else {
                    var _interceptPoint = parsed.getMember("options").getMember("interceptPoint").asString();
                    try {
                        interceptPoint = InterceptPoint.valueOf(_interceptPoint);
                    } catch (Throwable t) {
                        throw new IllegalArgumentException(
                                "wrong js plugin, wrong member 'options.interceptPoint', " + errorHint);
                    }
                }
            }

            if (!parsed.getMember("options").getMemberKeys().contains("modulesReplacements")) {
                modulesReplacements = null;
            } else {
                var sb = new StringBuilder();

                parsed.getMember("options").getMember("modulesReplacements").getMemberKeys().stream()
                        .forEach(k -> sb.append(k).append(":")
                                .append(parsed.getMember("options").getMember("modulesReplacements").getMember(k))
                                .append(","));

                modulesReplacements = sb.toString();
            }

            if (!parsed.getMemberKeys().contains("handle")) {
                throw new IllegalArgumentException("wrong js plugin, missing member 'handle', " + errorHint);
            }

            if (!parsed.getMember("handle").canExecute()) {
                throw new IllegalArgumentException("wrong js plugin, member 'handle' is not a function, " + errorHint);
            }

            if (!parsed.getMemberKeys().contains("resolve")) {
                throw new IllegalArgumentException("wrong js plugin, missing member 'resolve', " + errorHint);
            }

            if (!parsed.getMember("resolve").canExecute()) {
                throw new IllegalArgumentException("wrong js plugin, member 'resolve' is not a function, " + errorHint);
            }

            if (!parsed.getMember("options").getMemberKeys().contains("pluginClass")) {
                pluginClass = "StringInterceptor";
            } else if (!parsed.getMember("options").getMember("pluginClass").isString()) {
                throw new IllegalArgumentException(
                        "wrong js plugin, wrong member 'options.pluginClass', " + errorHint);
            } else {
                pluginClass = parsed.getMember("options").getMember("pluginClass").asString();
            }

            AbstractJSInterceptor<?,?> interceptor;

            @SuppressWarnings("unchecked")
            var args = this.pluginsArgs != null
                ? (Map<String, Object>) this.pluginsArgs.getOrDefault(name, new HashMap<String, Object>())
                : new HashMap<String, Object>();

            switch (pluginClass) {
                case "StringInterceptor":
                case "org.restheart.plugins.StringInterceptor":
                    interceptor = new StringJSInterceptor(name,
                        pluginClass,
                        description,
                        interceptPoint,
                        source,
                        mclient,
                        args,
                        modulesReplacements);
                        break;
                case "BsonInterceptor":
                case "org.restheart.plugins.BsonInterceptor":
                    interceptor = new StringJSInterceptor(name,
                        pluginClass,
                        description,
                        interceptPoint,
                        source,
                        mclient,
                        args,
                        modulesReplacements);
                        break;
                case "ByteArrayInterceptor":
                case "org.restheart.plugins.ByteArrayInterceptor":
                    interceptor = new ByteArrayJSInterceptor(name,
                        pluginClass,
                        description,
                        interceptPoint,
                        source,
                        mclient,
                        args,
                        modulesReplacements);
                        break;
                case "ByteArrayProxyInterceptor":
                case "org.restheart.plugins.ByteArrayProxyInterceptor":
                    interceptor = new ByteArrayProxyJSInterceptor(name,
                        pluginClass,
                        description,
                        interceptPoint,
                        source,
                        mclient,
                        args,
                        modulesReplacements);
                        break;
                case "CsvInterceptor":
                case "org.restheart.plugins.CsvInterceptor":
                    interceptor = new CsvJSInterceptor(name,
                        pluginClass,
                        description,
                        interceptPoint,
                        source,
                        mclient,
                        args,
                        modulesReplacements);
                        break;
                case "JsonInterceptor":
                case "org.restheart.plugins.JsonInterceptor":
                    interceptor = new JsonJSInterceptor(name,
                        pluginClass,
                        description,
                        interceptPoint,
                        source,
                        mclient,
                        args,
                        modulesReplacements);
                        break;
                case "MongoInterceptor":
                case "org.restheart.plugins.MongoInterceptor":
                    interceptor = new MongoJSInterceptor(name,
                        pluginClass,
                        description,
                        interceptPoint,
                        source,
                        mclient,
                        args,
                        modulesReplacements);
                        break;
                default:
                    throw new IllegalArgumentException(
                            "wrong js plugin, wrong member 'options.pluginClass', " + errorHint);
            }

            return new PluginRecord<Interceptor>(interceptor.getName(),
                interceptor.getDescription(),
                false,
                true,
                interceptor.getClass().getName(),
                interceptor,
                new HashMap<>());
        }
    }
}
