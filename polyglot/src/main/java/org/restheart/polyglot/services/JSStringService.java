/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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
package org.restheart.polyglot.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Optional;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.restheart.configuration.Configuration;
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;
import org.restheart.plugins.StringService;
import org.restheart.polyglot.ContextQueue;

import com.mongodb.client.MongoClient;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JSStringService extends JSService implements StringService {

    private static final String HANDLE_HINT = """
    the plugin module must export the function 'handle', example:
    export function handle(request, response) {
        LOGGER.debug('request {}', request.getContent());
        const rc = JSON.parse(request.getContent() || '{}');

        let body = {
            msg: `Hello ${rc.name || 'Cruel World'}`
        }

        response.setContent(JSON.stringify(body));
        response.setContentTypeAsJson();
    }
    """;

    private static final String PACKAGE_HINT = """
    the plugin module must export the object 'options', example:
    export const options = {
        name: "hello"
        description: "a fancy description"
        uri: "/hello"
        secured: false
        matchPolicy: "PREFIX"
    }
    """;

    public JSStringService(Path pluginPath, Optional<MongoClient> mclient, Configuration config) throws IOException, InterruptedException {
        super(args(pluginPath, mclient, config));
    }

    private static JSServiceArgs args(Path pluginPath, Optional<MongoClient> mclient, Configuration config) throws IOException {
        // find plugin root, i.e the parent dir that contains package.json
        var contextOptions = new HashMap<String, String>();
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
            LOGGER.trace("Enabling require for service {} with require-cwd {} ", pluginPath, requireCwdPath);
        }

        try (Context ctx = ContextQueue.newContext(engine(), "foo", config, LOGGER, mclient, "", contextOptions)) {
            // check that the plugin script is js
            var language = Source.findLanguage(pluginPath.toFile());

            if (!"js".equals(language)) {
                throw new IllegalArgumentException("wrong js plugin, not javascript");
            }

            var sindexPath = pluginPath.toUri().toString();
            LOGGER.debug("Resolved plugin path for import: {}", sindexPath);
            var optionsScript = "import { options } from '" + sindexPath + "'; options;";
            var optionsSource = Source.newBuilder(language, optionsScript, "optionsScript").mimeType("application/javascript+module").build();

            Value options;

            try {
                options = ctx.eval(optionsSource);
            } catch (Throwable t) {
                if (t.getMessage() != null && t.getMessage().contains("Cannot load CommonJS module")) {
                    throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ": " + t.getMessage());
                } else if (t.getMessage() != null && t.getMessage().contains("Access to host class")) {
                    throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ": " + t.getMessage());
                } else {
                    throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ": " + t.getMessage() + ", " + PACKAGE_HINT);
                }
            }

            checkOptions(options, pluginPath);

            var name = options.getMember("name").asString();
            var description = options.getMember("description").asString();
            var uri = options.getMember("uri").asString();
            var secured = !options.getMemberKeys().contains("secured") ? false : options.getMember("secured").asBoolean();
            var matchPolicy = !options.getMemberKeys().contains("matchPolicy") ? MATCH_POLICY.PREFIX : MATCH_POLICY.valueOf(options.getMember("matchPolicy").asString());
            String modulesReplacements = null;

            if (options.getMemberKeys().contains("modulesReplacements")) {
                var sb = new StringBuilder();

                options.getMember("modulesReplacements").getMemberKeys().stream()
                        .forEach(k -> sb.append(k).append(":")
                        .append(options.getMember("modulesReplacements").getMember(k))
                        .append(","));

                modulesReplacements = sb.toString();
            }

            // ******** evaluate and check handle
            var _handleScript = "import { handle } from '" + sindexPath + "'; handle;";
            var handleSource = Source.newBuilder(language, _handleScript, "handleScript").mimeType("application/javascript+module").build();

            Value handle;

            try {
                handle = ctx.eval(handleSource);
            } catch (Throwable t) {
                throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", " + t.getMessage());
            }

            checkHandle(handle, pluginPath);

            return new JSServiceArgs(name, description, uri, secured, modulesReplacements, matchPolicy, handleSource, config, mclient, contextOptions);
        }
    }

    /**
     *
     * @throws java.lang.InterruptedException
     */
    @Override
    public void handle(StringRequest request, StringResponse response) throws InterruptedException {
        Context ctx = null;
        try {
            ctx = takeCtx();
            // Use cached function to avoid re-evaluation overhead
            var handleFunction = org.restheart.polyglot.ContextQueue.cacheHandleFunction(ctx, handleSource());
            handleFunction.executeVoid(request, response);
        } finally {
            if (ctx != null) {
                releaseCtx(ctx);
            }
        }
    }

    static void checkOptions(Value options, Path pluginPath) {
        // ******** evaluate and check options

        if (options.getMemberKeys().isEmpty()) {
            throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + " , " + PACKAGE_HINT);
        }

        if (!options.getMemberKeys().contains("name")) {
            throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", missing member 'options.name', " + PACKAGE_HINT);
        }

        if (!options.getMember("name").isString()) {
            throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.name', " + PACKAGE_HINT);
        }

        if (!options.getMemberKeys().contains("description")) {
            throw new IllegalArgumentException(
                    "wrong js service " + pluginPath.toAbsolutePath() + ", missing member 'options.description', " + PACKAGE_HINT);
        }

        if (!options.getMember("description").isString()) {
            throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.description', " + PACKAGE_HINT);
        }

        if (!options.getMemberKeys().contains("uri")) {
            throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", missing member 'options.uri', " + PACKAGE_HINT);
        }

        if (!options.getMember("uri").isString()) {
            throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.uri', " + PACKAGE_HINT);
        }

        if (!options.getMember("uri").asString().startsWith("/")) {
            throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.uri', " + PACKAGE_HINT);
        }

        if (options.getMemberKeys().contains("secured") && !options.getMember("secured").isBoolean()) {
            throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.secured', " + PACKAGE_HINT);
        }

        if (options.getMemberKeys().contains("matchPolicy")) {
            if (!options.getMember("matchPolicy").isString()) {
                throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.matchPolicy', " + PACKAGE_HINT);
            } else {
                var _matchPolicy = options.getMember("matchPolicy").asString();
                try {
                    MATCH_POLICY.valueOf(_matchPolicy);
                } catch (Throwable t) {
                    throw new IllegalArgumentException(
                            "wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.matchPolicy', " + PACKAGE_HINT);
                }
            }
        }

        if (options.getMemberKeys().contains("modulesReplacements") && !options.getMember("modulesReplacements").isString()) {
            throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.modulesReplacements', " + PACKAGE_HINT);
        }
    }

    static void checkHandle(Value handle, Path pluginPath) {
        if (!handle.canExecute()) {
            throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", " + HANDLE_HINT);
        }
    }
}
