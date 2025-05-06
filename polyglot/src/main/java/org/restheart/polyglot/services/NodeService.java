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
import java.util.concurrent.LinkedBlockingDeque;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.collect.Maps;
import com.google.gson.JsonParser;
import com.mongodb.client.MongoClient;

import org.restheart.configuration.Configuration;
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.plugins.StringService;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;
import org.restheart.polyglot.NodeQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class NodeService extends JSService implements StringService {
    private String source;
    private int codeHash = 0;

    private static final String ERROR_HINT = """
    hint: the last statement in the script something like:
    ({
        options: {
            name: "hello"
            description: "a fancy description"
            uri: "/hello"
            secured: false
            matchPolicy: "PREFIX"
        }

        handle: (request, response) => {
            LOGGER.debug('request {}', request.getContent());
            const rc = JSON.parse(request.getContent() || '{}');

            let body = {
                msg: `Hello ${rc.name || 'Cruel World'}`
            }

            response.setContent(JSON.stringify(body));
            response.setContentTypeAsJson();
        }
    })
    """;

    public static Future<NodeService> get(Path scriptPath, Optional<MongoClient> mclient, Configuration conf) throws IOException {
        var executor = Executors.newSingleThreadExecutor();
        return executor.submit(() -> new NodeService(scriptPath, mclient, conf));
    }

    private NodeService(Path scriptPath, Optional<MongoClient> mclient, Configuration config) throws IOException {
        super(args(scriptPath, mclient, config));
        this.source = Files.readString(scriptPath);
        this.codeHash = this.source.hashCode();
    }

    private static JSServiceArgs args(Path scriptPath, Optional<MongoClient> mclient, Configuration config) throws IOException {
        // check plugin definition
        var out = new LinkedBlockingDeque<String>();
        Object[] message = { "parse", Files.readString(scriptPath), out };
        NodeQueue.instance().queue().offer(message);

        try {
            var result = out.take();

            Thread.sleep(300);

            var parsed = JsonParser.parseString(result);

            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("wrong node plugin, " + ERROR_HINT);
            }

            var parsedObj = parsed.getAsJsonObject();

            if (!parsedObj.has("options")) {
                throw new IllegalArgumentException("wrong node plugin, missing member 'options', " + ERROR_HINT);
            }

            if (!parsedObj.get("options").isJsonObject()) {
                throw new IllegalArgumentException("wrong node plugin, wrong member 'options', " + ERROR_HINT);
            }

            var optionsObj = parsedObj.getAsJsonObject("options");

            if (!optionsObj.has("name")) {
                throw new IllegalArgumentException("wrong node plugin, missing member 'options.name', " + ERROR_HINT);
            }

            if (!optionsObj.get("name").isJsonPrimitive() || !optionsObj.get("name").getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("wrong node plugin, wrong member 'options.name', " + ERROR_HINT);
            }

            var name = optionsObj.get("name").getAsString();

            if (!optionsObj.has("description")) {
                throw new IllegalArgumentException(
                        "wrong node plugin, missing member 'options.description', " + ERROR_HINT);
            }

            if (!optionsObj.get("description").isJsonPrimitive()
                    || !optionsObj.get("description").getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(
                        "wrong node plugin, wrong member 'options.description', " + ERROR_HINT);
            }

            var description = optionsObj.get("description").getAsString();

            if (!optionsObj.has("uri")) {
                throw new IllegalArgumentException("wrong node plugin, missing member 'options.uri', " + ERROR_HINT);
            }

            if (!optionsObj.get("uri").isJsonPrimitive() || !optionsObj.get("uri").getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("wrong node plugin, wrong member 'options.uri', " + ERROR_HINT);
            }

            if (!optionsObj.get("uri").getAsString().startsWith("/")) {
                throw new IllegalArgumentException("wrong node plugin, wrong member 'options.uri', " + ERROR_HINT);
            }

            var uri = optionsObj.get("uri").getAsString();

            boolean secured;

            if (!optionsObj.has("secured")) {
                secured = false;
            } else {
                if (!optionsObj.get("secured").isJsonPrimitive()
                        || !optionsObj.get("secured").getAsJsonPrimitive().isBoolean()) {
                    throw new IllegalArgumentException(
                            "wrong node plugin, wrong member 'options.secured', " + ERROR_HINT);
                } else {
                    secured = optionsObj.get("secured").getAsBoolean();
                }
            }

            MATCH_POLICY matchPolicy;

            if (!optionsObj.has("matchPolicy")) {
                matchPolicy = MATCH_POLICY.PREFIX;
            } else {
                if (!optionsObj.get("matchPolicy").isJsonPrimitive()
                        || !optionsObj.get("matchPolicy").getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException(
                            "wrong node plugin, wrong member 'options.secured', " + ERROR_HINT);
                } else {
                    var _matchPolicy = optionsObj.get("matchPolicy").getAsString();
                    try {
                        matchPolicy = MATCH_POLICY.valueOf(_matchPolicy);
                    } catch (Throwable t) {
                        throw new IllegalArgumentException(
                                "wrong node plugin, wrong member 'options.matchPolicy', " + ERROR_HINT);
                    }
                }
            }

            if (!parsedObj.has("handle")) {
                throw new IllegalArgumentException("wrong js plugin, missing member 'handle', " + ERROR_HINT);
            }

            if (!parsedObj.get("handle").isJsonPrimitive() || !parsedObj.get("handle").getAsJsonPrimitive().isString() || !"function".equals(parsedObj.get("handle").getAsString())) {
                throw new IllegalArgumentException("wrong js plugin, member 'handle' is not a function, " + ERROR_HINT);
            }

            return new JSServiceArgs(name, description, uri, secured, null, matchPolicy, null, config, mclient, new HashMap<String, String>());
        } catch (InterruptedException ie) {
            LOGGER.debug("Error initializing node plugin", ie);
            Thread.currentThread().interrupt();
        }

        return null;
    }

    /**
     *
     */
    @Override
    public void handle(StringRequest request, StringResponse response) {
        var out = new LinkedBlockingDeque<Object>();
        Object[] message = { "handle",
            this.codeHash, this.source,
            request, response,
            out,
            LOGGER,                  // pass LOGGER to node runtime
            mclient(),               // pass mclient to node runtime
            configuration() == null  // pass pluginArgs to node runtime
                ? Maps.newHashMap() : configuration().getOrDefault(name(), Maps.newHashMap())
        };

        try {
            NodeQueue.instance().queue().offer(message);
            var result = out.take();
            if (result instanceof RuntimeException runtimeException) {
                throw runtimeException;
            } else {
                LOGGER.debug("handle result: {}", result);
            }
        } catch (InterruptedException ie) {
            throw new RuntimeException("error", ie);
        }
    }
}
