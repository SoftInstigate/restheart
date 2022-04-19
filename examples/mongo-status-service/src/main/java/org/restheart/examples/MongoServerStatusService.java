package org.restheart.examples;

import com.mongodb.client.MongoClient;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.plugins.BsonService;
import org.restheart.plugins.InjectMongoClient;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(name = "serverstatus", description = "returns MongoDB serverStatus", enabledByDefault = true, defaultURI = "/status")
public class MongoServerStatusService implements BsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoServerStatusService.class);

    private MongoClient mongoClient;

    private static final BsonDocument DEFAULT_COMMAND = new BsonDocument("serverStatus", new BsonInt32(1));

    @InjectMongoClient
    public void init(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    @Override
    public void handle(BsonRequest request, BsonResponse response) {
        if (request.isGet()) {
            var commandQP = request.getQueryParameters().get("command");

            final var command = commandQP != null ? BsonDocument.parse(commandQP.getFirst()) : DEFAULT_COMMAND;

            LOGGER.debug("### command=" + command);

            var serverStatus = mongoClient.getDatabase("admin").runCommand(command, BsonDocument.class);

            response.setContent(serverStatus);
            response.setStatusCode(HttpStatus.SC_OK);
            response.setContentTypeAsJson();
        } else {
            // Any other HTTP verb is a bad request
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }
}
