package org.restheart.graphql;

import com.mongodb.MongoClient;
import graphql.GraphQL;
import io.undertow.server.HttpServerExchange;
import org.restheart.ConfigurationException;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.plugins.*;
import org.restheart.utils.JsonUtils;

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
    public void handle(ByteArrayRequest request, MongoResponse response){

        if (this.mongoClient == null) {
            response.setInError(500, "MongoClient not initialized");
            return;
        }

        if (!check(request)) {
            response.setInError(400, "Bad Request");
            return;
        }

        String appName = request.getPath().substring(9);
        AppDefinitionLoadingCache appCache = AppDefinitionLoadingCache.getInstance();
        GraphQLApp appDefinition = appCache.get(appName);
        if (appDefinition != null){
            this.gql = GraphQL.newGraphQL(appDefinition.getSchema()).build();
            MultipleGraphQLDataFetcher.setCurrentApp(appDefinition);
            SingleGraphQLDataFetcher.setCurrentApp(appDefinition);
            var query = new String(request.getContent());
            var result = this.gql.execute(query);

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