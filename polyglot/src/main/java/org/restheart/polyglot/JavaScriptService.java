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
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JavaScriptService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaScriptService.class);

    Map<String, String> OPTS = new HashMap<>();

    private Engine engine = Engine.create();
    private Source source;

    private String name;
    private String uri;

    private static final String errorHint = "hint: the last statement in the script should be:\n({\n\toptions: {..},\n\thandle: (request, response) => {}\n})";

    JavaScriptService(Path scriptPath, Path requireCdw) throws IOException {
        OPTS.put("js.commonjs-require", "true");
        OPTS.put("js.commonjs-require-cwd", requireCdw.toAbsolutePath().toString());

        var language = Source.findLanguage(scriptPath.toFile());

        if ("js".equals(language)) {
            if (scriptPath.endsWith(".mjs")) {
                source = Source.newBuilder("js", scriptPath.toFile()).mimeType("application/javascript+module").build();
            } else {
                source = Source.newBuilder("js", scriptPath.toFile()).build();
            }
        }

        // check plugin definition

        Context ctx = Context.newBuilder().engine(engine).allowAllAccess(true).allowHostClassLookup(className -> true)
                .allowIO(true).options(OPTS).build();

        Value parsed;

        try {
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

        if (!parsed.getMemberKeys().contains("handle")) {
            throw new IllegalArgumentException("wrong js plugin, missing member 'handle', " + errorHint);
        }

        if (!parsed.getMember("handle").canExecute()) {
            throw new IllegalArgumentException("wrong js plugin, member 'handle' is not a function, " + errorHint);
        }
    }

    public String getName() {
        return name;
    }

    public String getUri() {
        return uri;
    }

    /**
     *
     * @throws Exception
     */
    @SuppressWarnings("rawtypes")
    public void handle(Request request, Response response) throws Exception {
        var ctx = Context.newBuilder().engine(engine).allowAllAccess(true).allowHostClassLookup(className -> true)
                .allowIO(true).options(OPTS).build();

        ctx.getBindings("js").putMember("LOGGER", LOGGER);

        ctx.eval(source).getMember("handle").executeVoid(request, response);
    }
}
