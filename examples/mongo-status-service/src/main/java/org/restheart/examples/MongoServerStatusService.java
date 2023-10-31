/*-
 * ========================LICENSE_START=================================
 * mongo-status-service
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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

import com.mongodb.client.MongoClient;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.plugins.BsonService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(name = "serverstatus", description = "returns MongoDB serverStatus", enabledByDefault = true, defaultURI = "/status")
public class MongoServerStatusService implements BsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoServerStatusService.class);

    @Inject("mclient")
    private MongoClient mclient;

    private static final BsonDocument DEFAULT_COMMAND = new BsonDocument("serverStatus", new BsonInt32(1));

    @Override
    public void handle(BsonRequest request, BsonResponse response) {
        if (request.isGet()) {
            var commandQP = request.getQueryParameters().get("command");

            final var command = commandQP != null ? BsonDocument.parse(commandQP.getFirst()) : DEFAULT_COMMAND;

            LOGGER.debug("### command=" + command);

            var serverStatus = mclient.getDatabase("admin").runCommand(command, BsonDocument.class);

            response.setContent(serverStatus);
            response.setStatusCode(HttpStatus.SC_OK);
            response.setContentTypeAsJson();
        } else {
            // Any other HTTP verb is not allowed
            response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }
}
