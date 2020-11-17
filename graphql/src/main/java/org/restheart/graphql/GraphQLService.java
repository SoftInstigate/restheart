package org.restheart.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.MongoClient;
import com.mongodb.util.JSON;
import graphql.ExecutionInput;
import graphql.GraphQL;
import io.undertow.server.HttpServerExchange;
import nonapi.io.github.classgraph.json.JSONUtils;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.json.JSONObject;
import org.restheart.ConfigurationException;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.plugins.*;
import org.restheart.utils.JsonUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;


@RegisterPlugin(name= "graphql",
        description = "handles GraphQL requests", defaultURI = "/graphql")
public class GraphQLService implements Service<ByteArrayRequest, MongoResponse> {
    private GraphQL gql;
    private MongoClient mongoClient;

    @InjectConfiguration
    public void init(Map<String, Object> args) throws ConfigurationException {
        String db = ConfigurablePlugin.argValue(args, "db");
        String collection = ConfigurablePlugin.argValue(args, "collection");
        AppDefinitionLoader.setup(db, collection);
        this.mongoClient = MongoClientSingleton.getInstance().getClient();
    }


    @Override
    public void handle(ByteArrayRequest request, MongoResponse response) throws IOException {

        if (this.mongoClient == null) {
            response.setInError(500, "MongoClient not initialized");
            return;
        }

        if (!check(request)) {
            response.setInError(400, "Bad Request");
            return;
        }

        // Fetching app definition from cache and/or MongoDB, if it is present.
        String appName = request.getPath().substring(9);
        AppDefinitionLoadingCache appCache = AppDefinitionLoadingCache.getInstance();
        GraphQLApp appDefinition = appCache.get(appName);

        // If app definition is found...
        if (appDefinition != null){

            // Get query from request
            JSONObject json = new JSONObject(new String(request.getContent()));
            var query = (String) json.get("query");
            var inputBuilder = ExecutionInput.newExecutionInput().query(query);

            // if request has GraphQL variables...
            if(json.has("variables")){
                Map<String, Object> variables = getVariables(json.getJSONObject("variables"));
                inputBuilder.variables(variables);
            }


            ExecutionInput input = inputBuilder.build();

            // Configuration of GraphQL environment for the current application
            this.gql = GraphQL.newGraphQL(appDefinition.getSchema()).build();
            MultipleGraphQLDataFetcher.setCurrentApp(appDefinition);
            SingleGraphQLDataFetcher.setCurrentApp(appDefinition);

            // Query execution
            var result = this.gql.execute(input);

            if (result.getErrors() != null && !result.getErrors().isEmpty()) {
                var error = new StringBuilder();
                result.getErrors().forEach(e -> error.append(e.getMessage()).append(";"));
                response.setInError(400, error.toString());
                return;
            } else if (result.isDataPresent()) {
                response.setContent(JsonUtils.toBsonDocument(result.getData()));
            }
        }
        else{
            response.setInError(400, "Bad Request");
            return;
        }

    }

    private boolean check(ByteArrayRequest request) {
        return request.isPost()
                && request.getContent() != null
                && isContentTypeGraphQL(request);
    }

    private boolean isContentTypeGraphQL(ByteArrayRequest request) {
        return "application/graphql".equals(request.getContentType())
                || (request.getContentType() != null
                && request.getContentType().startsWith("application/graphql;"));
    }

    private Map<String, Object> getVariables(JSONObject variables) {
        Map<String, Object> result = new HashMap<>();
        for (String varName: variables.keySet()){
            BsonValue varValue = JsonUtils.parse(variables.get(varName).toString());
            result.put(varName, varValue);
        }
        return result;
    }



    @Override
    public Consumer<HttpServerExchange> requestInitializer() {
        return e -> ByteArrayRequest.init(e);
    }

    @Override
    public Consumer<HttpServerExchange> responseInitializer() {
        return e -> MongoResponse.init(e);
    }

    @Override
    public Function<HttpServerExchange, ByteArrayRequest> request() {
        return e -> ByteArrayRequest.of(e);
    }

    @Override
    public Function<HttpServerExchange, MongoResponse> response() {
        return e -> MongoResponse.of(e);
    }

}