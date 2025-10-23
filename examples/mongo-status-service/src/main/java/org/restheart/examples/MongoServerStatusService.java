/*-
 * ========================LICENSE_START=================================
 * mongo-status-service
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

package org.restheart.examples;

import java.util.Map;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.plugins.BsonService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

@RegisterPlugin(
    name = "mongoServerStatus",
    description = "returns MongoDB serverStatus information",
    secure = true,
    defaultURI = "/status/mongo",
    blocking = true // MongoDB command execution is blocking I/O
)
public class MongoServerStatusService implements BsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoServerStatusService.class);

    private static final BsonDocument DEFAULT_COMMAND = new BsonDocument("serverStatus", new BsonInt32(1));

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("config")
    private Map<String, Object> config;

    // Whitelist of allowed serverStatus options that can be toggled (0/1)
    private static final Set<String> ALLOWED_OPTIONS = Set.of(
            "serverStatus", // Required base command
            "repl", // Replica set status
            "metrics", // Server metrics
            "locks", // Lock information
            "network", // Network statistics
            "opcounters", // Operation counters
            "connections", // Connection information
            "memory", // Memory usage
            "asserts", // Assertion counters
            "extra_info", // Additional info
            "globalLock", // Global lock information
            "wiredTiger", // WiredTiger storage engine stats
            "tcmalloc", // TCMalloc memory allocator stats
            "storageEngine", // Storage engine info
            "indexStats", // Index statistics
            "sharding", // Sharding information
            "security" // Security statistics
    );

    private boolean enableCustomCommands = false;

    @OnInit
    public void init() {
        // Allow enabling custom commands via configuration (disabled by default for security)
        this.enableCustomCommands = argOrDefault(this.config, "enable-custom-commands", false);

        if (this.enableCustomCommands) {
            LOGGER.warn("Custom serverStatus commands are enabled. Ensure only trusted users can access this service.");
        }
    }

    @Override
    public void handle(final BsonRequest request, final BsonResponse response) {
        if (request.isOptions()) {
            handleOptions(request);
            return;
        }

        if (!request.isGet()) {
            // Any other HTTP verb is not allowed
            response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        try {
            final var commandQP = request.getQueryParameters().get("command");

            final BsonDocument command;
            if (commandQP != null && !commandQP.isEmpty()) {
                if (!enableCustomCommands) {
                    response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                    response.setContent(BsonDocument.parse(
                            "{\"error\": \"Custom commands are disabled. Enable 'enable-custom-commands' in configuration.\"}"));
                    response.setContentTypeAsJson();
                    return;
                }

                command = validateAndParseCommand(commandQP.getFirst());
                if (command == null) {
                    response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                    response.setContent(BsonDocument.parse(
                            "{\"error\": \"Invalid command. Only 'serverStatus' with allowed options is permitted.\"}"));
                    response.setContentTypeAsJson();
                    return;
                }
            } else {
                command = DEFAULT_COMMAND;
            }

            LOGGER.debug("Executing serverStatus command: {}", command);

            final var serverStatus = mclient.getDatabase("admin").runCommand(command, BsonDocument.class);

            response.setContent(serverStatus);
            response.setStatusCode(HttpStatus.SC_OK);
            response.setContentTypeAsJson();
        } catch (final Exception e) {
            LOGGER.error("Error executing serverStatus command", e);
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            response.setContent(BsonDocument.parse(
                    String.format("{\"error\": \"Failed to execute serverStatus: %s\"}",
                            e.getMessage().replace("\"", "\\\""))));
            response.setContentTypeAsJson();
        }
    }

    /**
     * Validates and parses a custom command to ensure it only contains serverStatus
     * with allowed options.
     * 
     * @param commandStr the command string to validate
     * @return validated BsonDocument or null if invalid
     */
    private BsonDocument validateAndParseCommand(final String commandStr) {
        try {
            final var command = BsonDocument.parse(commandStr);

            // Must contain serverStatus key
            if (!command.containsKey("serverStatus")) {
                LOGGER.warn("Command rejected: missing 'serverStatus' key");
                return null;
            }

            // Validate all keys are in the whitelist
            for (final String key : command.keySet()) {
                if (!ALLOWED_OPTIONS.contains(key)) {
                    LOGGER.warn("Command rejected: unauthorized option '{}'", key);
                    return null;
                }

                // Validate values are only 0 or 1 (boolean flags)
                final BsonValue value = command.get(key);
                if (!value.isInt32() || (value.asInt32().getValue() != 0 && value.asInt32().getValue() != 1)) {
                    LOGGER.warn("Command rejected: invalid value for option '{}'. Only 0 or 1 allowed.", key);
                    return null;
                }
            }

            return command;
        } catch (final Exception e) {
            LOGGER.warn("Command parsing failed", e);
            return null;
        }
    }
}
