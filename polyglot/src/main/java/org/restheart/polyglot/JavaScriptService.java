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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.mongodb.client.MongoClient;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.restheart.Configuration;
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.plugins.StringService;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;
import org.restheart.utils.CleanerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JavaScriptService extends AbstractJSPlugin implements StringService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaScriptService.class);

    private static final String handleHint = """
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

    private static final String packageHint = """
    the plugin module must export the object 'options', example:
    export const options = {
        name: "hello"
        description: "a fancy description"
        uri: "/hello"
        secured: false
        matchPolicy: "PREFIX"
    }
    """;

    JavaScriptService(Path pluginPath, MongoClient mclient, Configuration conf) throws IOException {
        // register cleaner
        CleanerUtils.get().cleaner().register(this, new State(this.ctxs));

        this.mclient = mclient;
        this.conf = conf;
        this.isService = true;
        this.isInterceptor = false;

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
            LOGGER.trace("Enabling require for service {} with require-cwd {} ", pluginPath, requireCwdPath);
        }

        // check that the plugin script is js
        var language = Source.findLanguage(pluginPath.toFile());

        if (!"js".equals(language)) {
            throw new IllegalArgumentException("wrong js plugin, not javascript");
        }

        var sindexPath = pluginPath.toAbsolutePath().toString();
        try (var ctx = context(engine, contextOptions)) {

            // add bindings to contenxt
            addBindings(ctx, this.name, conf, LOGGER, this.mclient);

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
                    throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ": " + t.getMessage() + ", " + packageHint);
                }
            }

            checkOptions(options, pluginPath);

            this.name = options.getMember("name").asString();
            this.description = options.getMember("description").asString();
            this.uri = options.getMember("uri").asString();

            if (!options.getMemberKeys().contains("secured")) {
                this.secured = false;
            } else {
                this.secured = options.getMember("secured").asBoolean();
            }

            if (!options.getMemberKeys().contains("matchPolicy")) {
                this.matchPolicy = MATCH_POLICY.PREFIX;
            } else {
                var _matchPolicy = options.getMember("matchPolicy").asString();
                this.matchPolicy = MATCH_POLICY.valueOf(_matchPolicy);
            }

            if (!options.getMemberKeys().contains("modulesReplacements")) {
                this.modulesReplacements = null;
            } else {
                var sb = new StringBuilder();

                options.getMember("modulesReplacements").getMemberKeys().stream()
                        .forEach(k -> sb.append(k).append(":")
                                .append(options.getMember("modulesReplacements").getMember(k))
                                .append(","));

                this.modulesReplacements = sb.toString();
            }

            // ******** evaluate and check handle

            var handleScript = "import { handle } from '" + sindexPath + "'; handle;";
            this.handleSource = Source.newBuilder(language, handleScript, "handleScript").mimeType("application/javascript+module").build();

            Value handle;

            try {
                handle = ctx.eval(this.handleSource);
            } catch (Throwable t) {
                throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", " + t.getMessage());
            }

            checkHandle(handle, pluginPath);
        }
    }

    /**
     *
     */
    public void handle(StringRequest request, StringResponse response) {
        _handle().executeVoid(request, response);
    }

    /**
     *
     * @return the Context associated with this thread. If not existing, it instanitates it.
     */
    @Override
    protected Context ctx() {
        if (getModulesReplacements() != null) {
            LOGGER.debug("modules-replacements: {} ", getModulesReplacements());
            contextOptions.put("js.commonjs-core-modules-replacements", getModulesReplacements());
        } else {
            contextOptions.remove("js.commonjs-core-modules-replacements");
        }

        var workingThreadName = Thread.currentThread().getName();

        if (this.ctxs.get(workingThreadName) == null) {
            var ctx = context(engine, contextOptions);
            this.ctxs.put(workingThreadName, ctx);

            addBindings(ctx, this.name, conf, LOGGER, this.mclient);
        }

        return this.ctxs.get(workingThreadName);
    }

    public String getModulesReplacements() {
        return this.modulesReplacements;
    }

    static void checkOptions(Value options, Path pluginPath) {
         // ******** evaluate and check options

         if (options.getMemberKeys().isEmpty()) {
             throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + " , " + packageHint);
         }

         if (!options.getMemberKeys().contains("name")) {
             throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", missing member 'options.name', " + packageHint);
         }

         if (!options.getMember("name").isString()) {
             throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.name', " + packageHint);
         }

         if (!options.getMemberKeys().contains("description")) {
             throw new IllegalArgumentException(
                 "wrong js service " + pluginPath.toAbsolutePath() + ", missing member 'options.description', " + packageHint);
         }

         if (!options.getMember("description").isString()) {
             throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.description', " + packageHint);
         }

         if (!options.getMemberKeys().contains("uri")) {
             throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", missing member 'options.uri', " + packageHint);
         }

         if (!options.getMember("uri").isString()) {
             throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.uri', " + packageHint);
         }

         if (!options.getMember("uri").asString().startsWith("/")) {
             throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.uri', " + packageHint);
         }

         if (options.getMemberKeys().contains("secured") && !options.getMember("secured").isBoolean()) {
            throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.secured', " + packageHint);
         }

         if (options.getMemberKeys().contains("matchPolicy")) {
             if (!options.getMember("matchPolicy").isString()) {
                 throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.matchPolicy', " + packageHint);
             } else {
                 var _matchPolicy = options.getMember("matchPolicy").asString();
                 try {
                     MATCH_POLICY.valueOf(_matchPolicy);
                 } catch (Throwable t) {
                     throw new IllegalArgumentException(
                         "wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.matchPolicy', " + packageHint);
                 }
             }
         }

         if (options.getMemberKeys().contains("modulesReplacements") && !options.getMember("modulesReplacements").isString()) {
            throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", wrong member 'options.modulesReplacements', " + packageHint);
         }
    }

    static void checkHandle(Value handle, Path pluginPath) {
        if (!handle.canExecute()) {
            throw new IllegalArgumentException("wrong js service " + pluginPath.toAbsolutePath() + ", " + handleHint);
        }
    }
}
