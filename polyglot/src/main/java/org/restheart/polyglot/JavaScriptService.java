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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mongodb.MongoClient;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.plugins.StringService;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;
import static org.restheart.polyglot.PolyglotDeployer.initRequireCdw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JavaScriptService extends AbstractJSPlugin implements StringService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaScriptService.class);

    Map<String, String> contextOptions = new HashMap<>();

    private Engine engine = Engine.create();
    private Source source;

    private final String modulesReplacements;

    private MongoClient mclient;

    private final Map<String, Object> pluginsArgs;

    private static final String errorHint = "the plugin module must export the function handle(request, response)";
    private static final String packageHint = "hint: add to package.json an object like \n\"rh:service\": {\n" +
        "\t\"name\": \"foo\",\n" +
        "\t\"description\": \"a fancy description\",\n" +
        "\t\"uri\": \"/foo\",\n" +
        "\t\"secured\": false,\n" +
        "\t\"matchPolicy\": \"PREFIX\"\n" +
        "}";

    JavaScriptService(Path pluginPath, MongoClient mclient, Map<String, Object> pluginsArgs) throws IOException {
        var jsIndexPath = pluginPath.resolve("index.js");
        var mjsIndexPath = pluginPath.resolve("index.mjs");

        var indexPath = Files.exists(jsIndexPath)
            ? jsIndexPath
            : Files.exists(mjsIndexPath)
            ? mjsIndexPath
            : null;

        this.mclient = mclient;
        this.pluginsArgs = pluginsArgs;

        contextOptions.put("js.commonjs-require", "true");
        contextOptions.put("js.commonjs-require-cwd", initRequireCdw(pluginPath).toAbsolutePath().toString());

        // check rh:service object in package.json

        var packagePath = pluginPath.resolve("package.json");

        try {
            var packageJson = JsonParser.parseReader(Files.newBufferedReader(packagePath));

            if (!packageJson.isJsonObject() || !(packageJson.getAsJsonObject().has("rh:service")) || !packageJson.getAsJsonObject().get("rh:service").isJsonObject()) {
                throw new IllegalArgumentException(packagePath.toAbsolutePath().toString() + " does not contain the object 'rh:service'");
            }

            var specs = packageJson.getAsJsonObject().getAsJsonObject("rh:service");

            // name is mandatory
            if (!specs.has("name")) {
                throw new IllegalArgumentException("wrong js plugin, missing member 'rh:service.name', " + packageHint);
            }

            if (!specs.get("name").isJsonPrimitive() || !specs.getAsJsonPrimitive("name").isString()) {
                throw new IllegalArgumentException("wrong js plugin, wrong member 'rh:service.name', it must be a string, " + packageHint);
            }

            this.name = specs.get("name").getAsString();

            // description is mandatory
            if (!specs.has("description")) {
                throw new IllegalArgumentException("wrong js plugin, missing member 'rh:service.description', " + packageHint);
            }

            if (!specs.get("description").isJsonPrimitive() || !specs.getAsJsonPrimitive("description").isString()) {
                throw new IllegalArgumentException("wrong js plugin, wrong member 'rh:service.description', it must be a string, " + packageHint);
            }

            this.description = specs.get("description").getAsString();

            // uri is optional, default is /<name>
            if (!specs.has("uri")) {
                this.uri = "/".concat(this.name);
            } else {
                if (!specs.get("uri").isJsonPrimitive() || !specs.getAsJsonPrimitive("uri").isString()) {
                    throw new IllegalArgumentException("wrong js plugin, wrong member 'rh:service.uri', it must be a string, " + packageHint);
                }

                if (!specs.get("uri").getAsString().startsWith("/")) {
                    throw new IllegalArgumentException("wrong js plugin, wrong member 'rh:service.uri', it must start with '/', " + packageHint);
                }

                this.uri = specs.get("uri").getAsString();
            }

            // secured is optional, default is false
            if (!specs.has("secured")) {
                this.secured = false;
            } else {
                if (!specs.get("secured").isJsonPrimitive() || !specs.getAsJsonPrimitive("secured").isBoolean()) {
                    throw new IllegalArgumentException("wrong js plugin, wrong member 'rh:service.secured', it must be a boolean, " + packageHint);
                }

                this.secured = specs.get("secured").getAsBoolean();
            }

            // matchPolicy is optional, default is PREFIX
            if (!specs.has("matchPolicy")) {
                this.matchPolicy = MATCH_POLICY.PREFIX;
            } else {
                if (!specs.get("matchPolicy").isJsonPrimitive() || !specs.getAsJsonPrimitive("matchPolicy").isString()) {
                    throw new IllegalArgumentException("wrong js plugin, wrong member 'rh:service.matchPolicy', it must be " + MATCH_POLICY.values() + ", " + packageHint);
                }

                try {
                    this.matchPolicy = MATCH_POLICY.valueOf(specs.get("matchPolicy").getAsString());
                } catch(Throwable t) {
                    throw new IllegalArgumentException("wrong js plugin, wrong member 'rh:service.matchPolicy', it must be " + MATCH_POLICY.values() + ", " + packageHint);
                }
            }

            // modulesReplacements is optional, default is null
            if (!specs.has("modulesReplacements")) {
                this.modulesReplacements = null;
            } else {
                if (!specs.get("modulesReplacements").isJsonPrimitive() || !specs.getAsJsonPrimitive("modulesReplacements").isString()) {
                    throw new IllegalArgumentException("wrong js plugin, wrong member 'rh:service.modulesReplacements', it must be a string, " + packageHint);
                }

                this.modulesReplacements = specs.get("modulesReplacements").getAsString();
            }
        } catch(JsonParseException jpe) {
            throw new IllegalArgumentException("Error parsing " + packagePath.toAbsolutePath().toString(), jpe);
        }

        // check index.js

        var language = Source.findLanguage(indexPath.toFile());

        if (!"js".equals(language)) {
            throw new IllegalArgumentException("wrong js plugin, not javascript");
        }

        var sindexPath = indexPath.toAbsolutePath().toString();

        var wrappingScript = "import { handle } from '" + sindexPath + "'; handle;";

        this.source = Source.newBuilder(language, wrappingScript, "wrappingScript").mimeType("application/javascript+module").build();

        try (var ctx = Context.newBuilder().engine(engine).allowAllAccess(true).allowHostClassLookup(className -> true)
                .allowIO(true).allowExperimentalOptions(true).options(contextOptions).build()) {
            Value handle;

            try {
                addBindings(ctx);
                handle = ctx.eval(source);
            } catch (Throwable t) {
                throw new IllegalArgumentException("wrong js plugin, " + t.getMessage());
            }

            if (!handle.canExecute()) {
                throw new IllegalArgumentException("wrong js plugin, " + errorHint);
            }
        }
    }

    /**
     *
     */
    public void handle(StringRequest request, StringResponse response) {
        if (getModulesReplacements() != null) {
            LOGGER.debug("modules-replacements: {} ", getModulesReplacements());
            contextOptions.put("js.commonjs-core-modules-replacements", getModulesReplacements());
        } else {
            contextOptions.remove("js.commonjs-core-modules-replacements");
        }

        try (var ctx = Context.newBuilder().engine(engine).allowAllAccess(true).allowHostClassLookup(className -> true)
                .allowIO(true).allowExperimentalOptions(true).options(contextOptions).build()) {

            addBindings(ctx);
            ctx.eval(source).executeVoid(request, response);
        }
    }

    public String getModulesReplacements() {
        return this.modulesReplacements;
    }

    private void addBindings(Context ctx) {
        ctx.getBindings("js").putMember("LOGGER", LOGGER);

        if (this.mclient != null) {
            ctx.getBindings("js").putMember("mclient", this.mclient);
        }

        @SuppressWarnings("unchecked")
        var args = this.pluginsArgs != null
            ? (Map<String, Object>) this.pluginsArgs.getOrDefault(this.name, new HashMap<String, Object>())
            : new HashMap<String, Object>();

        ctx.getBindings("js").putMember("pluginArgs", args);
    }
}
