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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.restheart.configuration.Configuration;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.PluginRecord;
import org.restheart.polyglot.ContextQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.mongodb.client.MongoClient;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JSInterceptorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(JSInterceptorFactory.class);

    Map<String, String> contextOptions = new HashMap<>();

    private final Engine engine = Engine.create();

    private final Optional<MongoClient> mclient;

    private final Configuration config;

    public JSInterceptorFactory(Optional<MongoClient> mclient, Configuration config) {
        this.mclient = mclient;
        this.config = config;
    }

    public PluginRecord<Interceptor<?, ?>> create(Path pluginPath) throws IOException, InterruptedException {
        // find plugin root, i.e the parent dir that contains package.json
        var pluginRoot = pluginPath.getParent();
        while (true) {
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
        var sindexPath = pluginPath.toUri().toString();
        LOGGER.debug("Resolved interceptor path: {}", sindexPath);

        try (Context ctx = ContextQueue.newContext(engine, "foo", config, LOGGER, mclient, "", contextOptions)) {

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
                throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", " + PACKAGE_HINT);
            }

            if (!options.getMemberKeys().contains("name")) {
                throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", missing member 'options.name', " + PACKAGE_HINT);
            }

            if (!options.getMember("name").isString()) {
                throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", wrong member 'options.name', " + PACKAGE_HINT);
            }

            var name = options.getMember("name").asString();

            if (!options.getMemberKeys().contains("description")) {
                throw new IllegalArgumentException(
                        "wrong js interceptor " + pluginPath.toAbsolutePath() + ", missing member 'options.description', " + PACKAGE_HINT);
            }

            if (!options.getMember("description").isString()) {
                throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", wrong member 'options.description', " + PACKAGE_HINT);
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
                            "wrong js interceptor " + pluginPath.toAbsolutePath() + ", wrong member 'options.interceptPoint', " + HANDLE_RESOLVE_HINT);
                } else {
                    var _interceptPoint = options.getMember("interceptPoint").asString();
                    try {
                        interceptPoint = InterceptPoint.valueOf(_interceptPoint);
                    } catch (Throwable t) {
                        throw new IllegalArgumentException(
                                "wrong js interceptor " + pluginPath.toAbsolutePath() + ", wrong member 'options.interceptPoint', " + HANDLE_RESOLVE_HINT);
                    }
                }
            }

            String pluginClass;

            if (!options.getMemberKeys().contains("pluginClass")) {
                pluginClass = "StringInterceptor";
            } else if (!options.getMember("pluginClass").isString()) {
                throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", wrong member 'options.pluginClass', " + HANDLE_RESOLVE_HINT);
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
                throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", " + HANDLE_RESOLVE_HINT);
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
                throw new IllegalArgumentException("wrong js interceptor " + pluginPath.toAbsolutePath() + ", " + HANDLE_RESOLVE_HINT);
            }

            JSInterceptor<? extends Request<?>, ? extends Response<?>> interceptor;

            Map<String, String> contextOpts = Maps.newHashMap();
            contextOpts.putAll(contextOptions);

            if (modulesReplacements != null) {
                LOGGER.debug("modules-replacements: {} ", modulesReplacements);
                contextOpts.put("js.commonjs-core-modules-replacements", modulesReplacements);
            } else {
                contextOpts.remove("js.commonjs-core-modules-replacements");
            }

            switch (pluginClass) {
                case "StringInterceptor", "org.restheart.plugins.StringInterceptor" ->
                    interceptor = new StringJSInterceptor(name,
                            pluginClass,
                            description,
                            interceptPoint,
                            modulesReplacements,
                            handleSource,
                            resolveSource,
                            mclient,
                            config,
                            contextOpts);
                case "BsonInterceptor", "org.restheart.plugins.BsonInterceptor" ->
                    interceptor = new StringJSInterceptor(name,
                            pluginClass,
                            description,
                            interceptPoint,
                            modulesReplacements,
                            handleSource,
                            resolveSource,
                            mclient,
                            config,
                            contextOpts);
                case "ByteArrayInterceptor", "org.restheart.plugins.ByteArrayInterceptor" ->
                    interceptor = new ByteArrayJSInterceptor(name,
                            pluginClass,
                            description,
                            interceptPoint,
                            modulesReplacements,
                            handleSource,
                            resolveSource,
                            mclient,
                            config,
                            contextOpts);
                case "ByteArrayProxyInterceptor", "org.restheart.plugins.ByteArrayProxyInterceptor" ->
                    interceptor = new ByteArrayProxyJSInterceptor(name,
                            pluginClass,
                            description,
                            interceptPoint,
                            modulesReplacements,
                            handleSource,
                            resolveSource,
                            mclient,
                            config,
                            contextOpts);
                case "CsvInterceptor", "org.restheart.plugins.CsvInterceptor" ->
                    interceptor = new CsvJSInterceptor(name,
                            pluginClass,
                            description,
                            interceptPoint,
                            modulesReplacements,
                            handleSource,
                            resolveSource,
                            mclient,
                            config,
                            contextOpts);
                case "JsonInterceptor", "org.restheart.plugins.JsonInterceptor" ->
                    interceptor = new JsonJSInterceptor(name,
                            pluginClass,
                            description,
                            interceptPoint,
                            modulesReplacements,
                            handleSource,
                            resolveSource,
                            mclient,
                            config,
                            contextOpts);
                case "MongoInterceptor", "org.restheart.plugins.MongoInterceptor" ->
                    interceptor = new MongoJSInterceptor(name,
                            pluginClass,
                            description,
                            interceptPoint,
                            modulesReplacements,
                            handleSource,
                            resolveSource,
                            mclient,
                            config,
                            contextOpts);
                case "WildCardJSInterceptor", "org.restheart.plugins.WildCardJSInterceptor" ->
                    interceptor = new WildCardJSInterceptor(name,
                            pluginClass,
                            description,
                            interceptPoint,
                            modulesReplacements,
                            handleSource,
                            resolveSource,
                            mclient,
                            config,
                            contextOpts);
                default ->
                    throw new IllegalArgumentException("wrong js interceptor, wrong member 'options.pluginClass', " + PACKAGE_HINT);
            }

            return new PluginRecord<>(interceptor.name(),
                    interceptor.getDescription(),
                    false,
                    true,
                    interceptor.getClass().getName(),
                    interceptor,
                    new HashMap<>());
        }
    }

    private static final String HANDLE_RESOLVE_HINT = """
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

    private static final String PACKAGE_HINT = """
            the plugin module must export the object 'options', example:
            export const options = {
                name: "mongoCollInterceptor",
                description: "modifies the response of GET /coll/<docid>",
                interceptPoint: "RESPONSE",
                pluginClass: "MongoInterceptor"
            }
            """;
}