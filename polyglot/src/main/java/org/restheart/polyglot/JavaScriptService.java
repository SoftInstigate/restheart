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
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.plugins.StringService;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JavaScriptService extends AbstractJSPlugin implements StringService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaScriptService.class);

    Map<String, String> OPTS = new HashMap<>();

    private Engine engine = Engine.create();
    private Source source;

    private final String modulesReplacements;

    private MongoClient mclient;

    // TODO pass this to node runtime
    private Map<String, Object> configuration;

    private static final String errorHint = "hint: the last statement in the script should be:\n({\n\toptions: {..},\n\thandle: (request, response) => {}\n})";

    JavaScriptService(Path scriptPath, Path requireCdw, MongoClient mclient) throws IOException {
        this.mclient = mclient;

        OPTS.put("js.commonjs-require", "true");
        OPTS.put("js.commonjs-require-cwd", requireCdw.toAbsolutePath().toString());

        var language = Source.findLanguage(scriptPath.toFile());

        if ("js".equals(language)) {
            if (scriptPath.getFileName().toString().endsWith(".mjs")) {
                source = Source.newBuilder("js", scriptPath.toFile()).mimeType("application/javascript+module").build();
            } else {
                source = Source.newBuilder("js", scriptPath.toFile()).build();
            }
        }

        // check plugin definition

        try (Context ctx = Context.newBuilder().engine(engine).allowAllAccess(true)
                .allowHostClassLookup(className -> true).allowIO(true).allowExperimentalOptions(true).options(OPTS)
                .build()) {
            Value parsed;

            try {
                ctx.getBindings("js").putMember("LOGGER", LOGGER);

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

            this.name = parsed.getMember("options").getMember("name").asString();

            if (!parsed.getMember("options").getMemberKeys().contains("description")) {
                throw new IllegalArgumentException(
                        "wrong js plugin, missing member 'options.description', " + errorHint);
            }

            if (!parsed.getMember("options").getMember("description").isString()) {
                throw new IllegalArgumentException("wrong js plugin, wrong member 'options.description', " + errorHint);
            }

            this.description = parsed.getMember("options").getMember("description").asString();

            if (!parsed.getMember("options").getMemberKeys().contains("uri")) {
                throw new IllegalArgumentException("wrong js plugin, missing member 'options.uri', " + errorHint);
            }

            if (!parsed.getMember("options").getMember("uri").isString()) {
                throw new IllegalArgumentException("wrong js plugin, wrong member 'options.uri', " + errorHint);
            }

            if (!parsed.getMember("options").getMember("uri").asString().startsWith("/")) {
                throw new IllegalArgumentException("wrong js plugin, wrong member 'options.uri', " + errorHint);
            }

            this.uri = parsed.getMember("options").getMember("uri").asString();

            if (!parsed.getMember("options").getMemberKeys().contains("secured")) {
                this.secured = false;
            } else {
                if (!parsed.getMember("options").getMember("secured").isBoolean()) {
                    throw new IllegalArgumentException("wrong js plugin, wrong member 'options.secured', " + errorHint);
                } else {
                    this.secured = parsed.getMember("options").getMember("secured").asBoolean();
                }
            }

            if (!parsed.getMember("options").getMemberKeys().contains("matchPolicy")) {
                this.matchPolicy = MATCH_POLICY.PREFIX;
            } else {
                if (!parsed.getMember("options").getMember("matchPolicy").isString()) {
                    throw new IllegalArgumentException("wrong js plugin, wrong member 'options.secured', " + errorHint);
                } else {
                    var _matchPolicy = parsed.getMember("options").getMember("matchPolicy").asString();
                    try {
                        this.matchPolicy = MATCH_POLICY.valueOf(_matchPolicy);
                    } catch (Throwable t) {
                        throw new IllegalArgumentException(
                                "wrong js plugin, wrong member 'options.matchPolicy', " + errorHint);
                    }
                }
            }

            if (!parsed.getMember("options").getMemberKeys().contains("modulesReplacements")) {
                this.modulesReplacements = null;
            } else {
                var sb = new StringBuilder();

                parsed.getMember("options").getMember("modulesReplacements").getMemberKeys().stream()
                        .forEach(k -> sb.append(k).append(":")
                                .append(parsed.getMember("options").getMember("modulesReplacements").getMember(k))
                                .append(","));

                this.modulesReplacements = sb.toString();
            }

            if (!parsed.getMemberKeys().contains("handle")) {
                throw new IllegalArgumentException("wrong js plugin, missing member 'handle', " + errorHint);
            }

            if (!parsed.getMember("handle").canExecute()) {
                throw new IllegalArgumentException("wrong js plugin, member 'handle' is not a function, " + errorHint);
            }
        }
    }

    public String getModulesReplacements() {
        return this.modulesReplacements;
    }

    /**
     *
     */
    public void handle(StringRequest request, StringResponse response) {
        if (getModulesReplacements() != null) {
            LOGGER.debug("modules-replacements: {} ", getModulesReplacements());
            OPTS.put("js.commonjs-core-modules-replacements", getModulesReplacements());
        } else {
            OPTS.remove("js.commonjs-core-modules-replacements");
        }

        try (var ctx = Context.newBuilder().engine(engine).allowAllAccess(true).allowHostClassLookup(className -> true)
                .allowIO(true).allowExperimentalOptions(true).options(OPTS).build()) {

            ctx.getBindings("js").putMember("LOGGER", LOGGER);

            if (this.mclient != null) {
                ctx.getBindings("js").putMember("mclient", this.mclient);
            }

            ctx.eval(source).getMember("handle").executeVoid(request, response);
        }
    }
}
