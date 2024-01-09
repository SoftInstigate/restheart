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
package org.restheart.polyglot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class NodeService extends AbstractJSPlugin implements StringService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeService.class);

    private String source;

    private int codeHash = 0;

    private static final String errorHint = """
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

    private NodeService(Path scriptPath, Optional<MongoClient> mclient, Configuration conf) throws IOException {
        this.mclient = mclient;
        this.conf = conf;

        this.source = Files.readString(scriptPath);
        this.codeHash = this.source.hashCode();

        // check plugin definition

        var out = new LinkedBlockingDeque<String>();
        Object[] message = { "parse", this.source, out };
        NodeQueue.instance().queue().offer(message);
        try {
            var result = out.take();

            Thread.sleep(300);

            var parsed = JsonParser.parseString(result);

            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("wrong node plugin, " + errorHint);
            }

            var parsedObj = parsed.getAsJsonObject();

            if (!parsedObj.has("options")) {
                throw new IllegalArgumentException("wrong node plugin, missing member 'options', " + errorHint);
            }

            if (!parsedObj.get("options").isJsonObject()) {
                throw new IllegalArgumentException("wrong node plugin, wrong member 'options', " + errorHint);
            }

            var optionsObj = parsedObj.getAsJsonObject("options");

            if (!optionsObj.has("name")) {
                throw new IllegalArgumentException("wrong node plugin, missing member 'options.name', " + errorHint);
            }

            if (!optionsObj.get("name").isJsonPrimitive() || !optionsObj.get("name").getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("wrong node plugin, wrong member 'options.name', " + errorHint);
            }

            this.name = optionsObj.get("name").getAsString();

            if (!optionsObj.has("description")) {
                throw new IllegalArgumentException(
                        "wrong node plugin, missing member 'options.description', " + errorHint);
            }

            if (!optionsObj.get("description").isJsonPrimitive()
                    || !optionsObj.get("description").getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(
                        "wrong node plugin, wrong member 'options.description', " + errorHint);
            }

            this.description = optionsObj.get("description").getAsString();

            if (!optionsObj.has("uri")) {
                throw new IllegalArgumentException("wrong node plugin, missing member 'options.uri', " + errorHint);
            }

            if (!optionsObj.get("uri").isJsonPrimitive() || !optionsObj.get("uri").getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("wrong node plugin, wrong member 'options.uri', " + errorHint);
            }

            if (!optionsObj.get("uri").getAsString().startsWith("/")) {
                throw new IllegalArgumentException("wrong node plugin, wrong member 'options.uri', " + errorHint);
            }

            this.uri = optionsObj.get("uri").getAsString();

            if (!optionsObj.has("secured")) {
                this.secured = false;
            } else {
                if (!optionsObj.get("secured").isJsonPrimitive()
                        || !optionsObj.get("secured").getAsJsonPrimitive().isBoolean()) {
                    throw new IllegalArgumentException(
                            "wrong node plugin, wrong member 'options.secured', " + errorHint);
                } else {
                    this.secured = optionsObj.get("secured").getAsBoolean();
                }
            }

            if (!optionsObj.has("matchPolicy")) {
                this.matchPolicy = MATCH_POLICY.PREFIX;
            } else {
                if (!optionsObj.get("matchPolicy").isJsonPrimitive()
                        || !optionsObj.get("matchPolicy").getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException(
                            "wrong node plugin, wrong member 'options.secured', " + errorHint);
                } else {
                    var _matchPolicy = optionsObj.get("matchPolicy").getAsString();
                    try {
                        this.matchPolicy = MATCH_POLICY.valueOf(_matchPolicy);
                    } catch (Throwable t) {
                        throw new IllegalArgumentException(
                                "wrong node plugin, wrong member 'options.matchPolicy', " + errorHint);
                    }
                }
            }

            if (!parsedObj.has("handle")) {
                throw new IllegalArgumentException("wrong js plugin, missing member 'handle', " + errorHint);
            }

            if (!parsedObj.get("handle").isJsonPrimitive() || !parsedObj.get("handle").getAsJsonPrimitive().isString()
                    || !"function".equals(parsedObj.get("handle").getAsString())) {
                throw new IllegalArgumentException("wrong js plugin, member 'handle' is not a function, " + errorHint);
            }
        } catch (InterruptedException ie) {
            LOGGER.debug("Error initializing node plugin", ie);
            Thread.currentThread().interrupt();
        }
    }

    /**
     *
     */
    public void handle(StringRequest request, StringResponse response) {
        var out = new LinkedBlockingDeque<Object>();
        Object[] message = { "handle",
            this.codeHash, this.source,
            request, response,
            out,
            LOGGER,                  // pass LOGGER to node runtime
            this.mclient,            // pass mclient to node runtime
            this.conf == null        // pass pluginArgs to node runtime
                ? Maps.newHashMap() : this.conf.getOrDefault(this.name, Maps.newHashMap())
        };

        try {
            NodeQueue.instance().queue().offer(message);
            var result = out.take();
            if (result instanceof RuntimeException) {
                throw ((RuntimeException) result);
            } else {
                LOGGER.debug("handle result: {}", result);
            }
        } catch (InterruptedException ie) {
            throw new RuntimeException("error", ie);
        }
    }
}
