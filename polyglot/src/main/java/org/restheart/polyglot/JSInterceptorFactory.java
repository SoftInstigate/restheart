/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2022 SoftInstigate
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

import static org.restheart.polyglot.AbstractJSPlugin.addBindings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.Maps;
import com.mongodb.client.MongoClient;
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
import org.restheart.configuration.Configuration;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JSInterceptorFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(JSInterceptorFactory.class);

    Map<String, String> contextOptions = new HashMap<>();

    private Engine engine = Engine.create();

    private final MongoClient mclient;

    private final Configuration config;

    public JSInterceptorFactory(MongoClient mclient, Configuration config) {
        this.mclient = mclient;
        this.config = config;
    }

    public PluginRecord<Interceptor<? , ?>> create(Path pluginPath) throws IOException {
        // find plugin root, i.e the parent dir that contains package.json
        var pluginRoot = pluginPath.getParent();
        while(true) {
            var p = pluginRoot.resolve("package.json");
            if (Files.exists(p)) {
                break;
            } else {
                pluginRoot = pluginRoot.getParent();
            }
        }

        // set js.commonjs-require-cwd (if the pluginRoot contains the directory 'node_modules')
        var requireCwdPath = pluginRoot.resolve("node_modules");
        if (Files.isDirectory(requireCwdPath)) {
            contextOptions.put("js.commonjs-require", "true");
            contextOptions.put("js.commonjs-require-cwd", requireCwdPath.toAbsolutePath().toString());
            LOGGER.debug("Enabling require for interceptor {} with require-cwd {} ", pluginPath, requireCwdPath);
        }

        // check that the plugin script is js
        var language = Source.findLanguage(pluginPath.toFile());

        if (!"js".equals(language)) {
            throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", not javascript");
        }

        // check plugin definition

        var sindexPath = pluginPath.toAbsolutePath().toString();
        try (var ctx = AbstractJSPlugin.context(engine, contextOptions)) {
            // add bindings to contenxt
            addBindings(ctx, "foo", null, LOGGER, this.mclient);

            // ******** evaluate and check options

            var optionsScript = "import { options } from '" + sindexPath + "'; options;";
            var optionsSource = Source.newBuilder(language, optionsScript, "optionsScript").mimeType("application/javascript+module").build();

            Value options;

            try {
                options = ctx.eval(optionsSource);
            } catch (Throwable t) {
                throw new IllegalArgumentException("wrong js interceptor, " + t.getMessage());
            }

            if (options.getMemberKeys().isEmpty()) {
                throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", " + packageHint);
            }

            if (!options.getMemberKeys().contains("name")) {
                throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", missing member 'options.name', " + packageHint);
            }

            if (!options.getMember("name").isString()) {
                throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", wrong member 'options.name', " + packageHint);
            }

            var name = options.getMember("name").asString();

            if (!options.getMemberKeys().contains("description")) {
                throw new IllegalArgumentException(
                        "wrong js interceptor " + pluginPath.toAbsolutePath() + ", missing member 'options.description', " + packageHint);
            }

            if (!options.getMember("description").isString()) {
                throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", wrong member 'options.description', " + packageHint);
            }

            var description = options.getMember("description").asString();

            String modulesReplacements;

            if (!options.getMemberKeys().contains("modulesReplacements")) {
                modulesReplacements = null;
            } else {
                var sb = new StringBuilder();

                options.getMember("modulesReplacements").getMemberKeys().stream()
                        .forEach(k -> sb.append(k).append(":")
                                .append(options.getMember("modulesReplacements").getMember(k))
                                .append(","));

                modulesReplacements = sb.toString();
            }

            InterceptPoint interceptPoint;

            if (!options.getMemberKeys().contains("interceptPoint")) {
                interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH;
            } else {
                if (!options.getMember("interceptPoint").isString()) {
                    throw new IllegalArgumentException(
                        "wrong js interceptor " + pluginPath.toAbsolutePath() + ", wrong member 'options.interceptPoint', " + handleResolveHint);
                } else {
                    var _interceptPoint = options.getMember("interceptPoint").asString();
                    try {
                        interceptPoint = InterceptPoint.valueOf(_interceptPoint);
                    } catch (Throwable t) {
                        throw new IllegalArgumentException(
                            "wrong js interceptor " + pluginPath.toAbsolutePath() + ", wrong member 'options.interceptPoint', " + handleResolveHint);
                    }
                }
            }

            String pluginClass;

            if (!options.getMemberKeys().contains("pluginClass")) {
                pluginClass = "StringInterceptor";
            } else if (!options.getMember("pluginClass").isString()) {
                throw new IllegalArgumentException(
                    "wrong js interceptor " + pluginPath.toAbsolutePath() + ", wrong member 'options.pluginClass', " + handleResolveHint);
            } else {
                pluginClass = options.getMember("pluginClass").asString();
            }

            // ******** evaluate and check handle

            var handleScript = "import { handle } from '" + sindexPath + "'; handle;";
            var handleSource = Source.newBuilder(language, handleScript, "handleScript").mimeType("application/javascript+module").build();

            Value handle;

            try {
                handle = ctx.eval(handleSource);
            } catch (Throwable t) {
                throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", " + t.getMessage());
            }

            if (!handle.canExecute()) {
                throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", " + handleResolveHint);
            }

            // ******** evaluate and check resolve

            var resolveScript = "import { resolve } from '" + sindexPath + "'; resolve;";
            var resolveSource = Source.newBuilder(language, resolveScript, "resolveScript").mimeType("application/javascript+module").build();

            Value resolve;

            try {
                resolve = ctx.eval(resolveSource);
            } catch (Throwable t) {
                throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", " + t.getMessage());
            }

            if (!resolve.canExecute()) {
                throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", " + handleResolveHint);
            }

            AbstractJSInterceptor<? extends Request<?>, ? extends Response<?>> interceptor;



            Map<String, String> opts = Maps.newHashMap();
            opts.putAll(contextOptions);

            if (modulesReplacements != null) {
                LOGGER.debug("modules-replacements: {} ", modulesReplacements);
                opts.put("js.commonjs-core-modules-replacements", modulesReplacements);
            } else {
                opts.remove("js.commonjs-core-modules-replacements");
            }

            switch (pluginClass) {
                case "StringInterceptor":
                case "org.restheart.plugins.StringInterceptor":
                    interceptor = new StringJSInterceptor(name,
                        pluginClass,
                        description,
                        interceptPoint,
                        handleSource,
                        resolveSource,
                        mclient,
                        config,
                        opts);
                        break;
                case "BsonInterceptor":
                case "org.restheart.plugins.BsonInterceptor":
                    interceptor = new StringJSInterceptor(name,
                        pluginClass,
                        description,
                        interceptPoint,
                        handleSource,
                        resolveSource,
                        mclient,
                        config,
                        opts);
                        break;
                case "ByteArrayInterceptor":
                case "org.restheart.plugins.ByteArrayInterceptor":
                    interceptor = new ByteArrayJSInterceptor(name,
                        pluginClass,
                        description,
                        interceptPoint,
                        handleSource,
                        resolveSource,
                        mclient,
                        config,
                        opts);
                        break;
                case "ByteArrayProxyInterceptor":
                case "org.restheart.plugins.ByteArrayProxyInterceptor":
                    interceptor = new ByteArrayProxyJSInterceptor(name,
                        pluginClass,
                        description,
                        interceptPoint,
                        handleSource,
                        resolveSource,
                        mclient,
                        config,
                        opts);
                        break;
                case "CsvInterceptor":
                case "org.restheart.plugins.CsvInterceptor":
                    interceptor = new CsvJSInterceptor(name,
                        pluginClass,
                        description,
                        interceptPoint,
                        handleSource,
                        resolveSource,
                        mclient,
                        config,
                        opts);
                        break;
                case "JsonInterceptor":
                case "org.restheart.plugins.JsonInterceptor":
                    interceptor = new JsonJSInterceptor(name,
                        pluginClass,
                        description,
                        interceptPoint,
                        handleSource,
                        resolveSource,
                        mclient,
                        config,
                        opts);
                        break;
                case "MongoInterceptor":
                case "org.restheart.plugins.MongoInterceptor":
                    interceptor = new MongoJSInterceptor(name,
                        pluginClass,
                        description,
                        interceptPoint,
                        handleSource,
                        resolveSource,
                        mclient,
                        config,
                        opts);
                        break;
                default:
                    throw new IllegalArgumentException("wrong js interceptor, wrong member 'options.pluginClass', " + packageHint);
            }

            return new PluginRecord<Interceptor<? extends Request<?>, ? extends Response<?>>>(interceptor.getName(),
                interceptor.getDescription(),
                false,
                true,
                interceptor.getClass().getName(),
                interceptor,
                new HashMap<>());
        }
    }

    private static final String handleResolveHint = """
    the interceptor js module must export the functions 'handle' and 'resolve', example:
    export function handle(request, response) {
        const BsonUtils = Java.type("org.restheart.utils.BsonUtils");
        var bson = response.getContent();

        bson.asDocument().put("injectedDoc", BsonUtils.parse("{ 'n': 1, 's': 'foo' }"));
    }

    export function resolve(request) {
        return request.isGet() && request.isDocument() && "coll" === request.getCollectionName();
    }
    """;

    private static final String packageHint = """
    the plugin module must export the object 'options', example:
    export const options = {
        name: "mongoCollInterceptor",
        description: "modifies the response of GET /coll/<docid>",
        interceptPoint: "RESPONSE",
        pluginClass: "MongoInterceptor"
    }
    """;
}
