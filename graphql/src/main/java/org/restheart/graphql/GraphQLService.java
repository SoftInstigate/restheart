package org.restheart.graphql;
import com.mongodb.MongoClient;
import graphql.ExecutionInput;
import graphql.GraphQL;
import org.json.JSONObject;
import io.undertow.server.HttpServerExchange;
import org.restheart.ConfigurationException;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.graphql.scalars.bsonCoercing.CoercingUtils;
import org.restheart.graphql.cache.AppDefinitionLoader;
import org.restheart.graphql.cache.AppDefinitionLoadingCache;
import org.restheart.graphql.datafetchers.GraphQLDataFetcher;
import org.restheart.graphql.models.GraphQLApp;
import org.restheart.plugins.*;
import org.restheart.utils.JsonUtils;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;


@RegisterPlugin(name= "graphql",
        description = "handles GraphQL requests", defaultURI = "/graphql")
public class GraphQLService implements Service<ByteArrayRequest, MongoResponse> {
    private GraphQL gql;
    private MongoClient mongoClient = null;
    private String db = null;
    private String collection = null;

    @InjectConfiguration
    public void initConf(Map<String, Object> args) throws ConfigurationException, NoSuchFieldException, IllegalAccessException {
        CoercingUtils.replaceBuiltInCoercing();
        this.db = ConfigurablePlugin.argValue(args, "db");
        this.collection = ConfigurablePlugin.argValue(args, "collection");

        if(mongoClient != null){
            GraphQLDataFetcher.setMongoClient(mongoClient);
            AppDefinitionLoader.setup(db, collection, mongoClient);
        }
    }

    @InjectMongoClient
    public void initMongoClient(MongoClient mClient){
        this.mongoClient = mClient;
        if (db!= null && collection != null){
            GraphQLDataFetcher.setMongoClient(mongoClient);
            AppDefinitionLoader.setup(db, collection, mongoClient);
        }
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

        String[] split = request.getPath().split("/");
        String appUri = String.join("/", Arrays.copyOfRange(split, 2, split.length));
        AppDefinitionLoadingCache appCache = AppDefinitionLoadingCache.getInstance();
        GraphQLApp appDefinition = appCache.get(appUri);

        if (appDefinition != null){

            JSONObject json = new JSONObject(new String(request.getContent()));
            String query = (String) json.get("query");
            var inputBuilder = ExecutionInput.newExecutionInput().query(query);

            if (json.has("variables")){
                Map<String, Object> variables = json.getJSONObject("variables").toMap();
                inputBuilder.variables(variables);
            }

            ExecutionInput input = inputBuilder.build();

            this.gql = GraphQL.newGraphQL(appDefinition.getExecutableSchema()).build();

            var result = this.gql.execute(input);

            if (result.getErrors() != null && !result.getErrors().isEmpty()) {
                var error = new StringBuilder();
                result.getErrors().forEach(e -> error.append(e.getMessage()).append(";"));
                response.setInError(400, error.toString());
                return;
            } else if (result.isDataPresent()) {
                response.setContent(JsonUtils.toBsonDocument(result.toSpecification()));
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