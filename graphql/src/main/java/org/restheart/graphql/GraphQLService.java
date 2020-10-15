package org.restheart.graphql;

import com.mongodb.MongoClient;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import io.undertow.server.HttpServerExchange;
import org.bson.Document;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InjectMongoClient;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;
import org.restheart.utils.JsonUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;


@RegisterPlugin(name= "graphql",
        description = "handles GraphQL requests", defaultURI = "/graphql")
public class GraphQLService implements Service<ByteArrayRequest, MongoResponse> {
    private GraphQL gql;
    private GraphQLApp app;
    private MongoClient mongoClient;

    @InjectMongoClient
    public void init(MongoClient mclient) throws IOException, URISyntaxException {
        this.mongoClient = mclient;

        //fetching APP definition from database
        this.app = new GraphQLApp("Test APP");
        Document appDesc = this.mongoClient.getDatabase("restheart")
                .getCollection("apps").find().first();


        Map<String, Query> queryDefinitions = new HashMap<>();

        //creating queries definitions and putting them inside App definition
        for (Document query: appDesc.getList("queries", Document.class)) {
            String queryName = query.getString("name");
            queryDefinitions.put(queryName,
                    QueryBuilder.newBuilder(query.getString("db"), queryName, query.getString("collection")
                            , query.getBoolean("multiple"))
                            .filter((Document) query.get("filter"))
                            .sort((Document) query.get("sort"))
                            .skip((Document) query.get("skip"))
                            .limit((Document) query.get("limit"))
                            .first((Document) query.get("first"))
                            .build());
        }
        this.app.setQueries(queryDefinitions);


        //making executable the schema
        GraphQLSchema graphQLSchema = buildSchema(appDesc.getString("schema"));
        this.app.setSchema(graphQLSchema);
        this.gql = GraphQL.newGraphQL(graphQLSchema).build();

    }

    private GraphQLSchema buildSchema(String sdl){
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = buildWiring();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private RuntimeWiring buildWiring(){

        TypeRuntimeWiring.Builder obj = newTypeWiring("Query");
        for (String queryName : this.app.getQueries().keySet()) {
            boolean isMultiple = this.app.getQueryByName(queryName).isMultiple();
            //TODO: create a null pointer exception for isMultiple --> it is mandatory!
            if (isMultiple){
                obj.dataFetcher(queryName, MultipleGraphQLDataFetcher.getInstance());
            }
            else{
                obj.dataFetcher(queryName, SingleGraphQLDataFetcher.getInstance());
            }
        }
        MultipleGraphQLDataFetcher.setCurrentApp(this.app);
        MultipleGraphQLDataFetcher.setMongoClient(this.mongoClient);
        SingleGraphQLDataFetcher.setCurrentApp(this.app);
        SingleGraphQLDataFetcher.setMongoClient(this.mongoClient);
        return RuntimeWiring.newRuntimeWiring().type(obj).build();
    }

    @Override
    public void handle(ByteArrayRequest request, MongoResponse response) throws Exception {

        if (this.mongoClient == null) {
            response.setInError(500, "MongoClient not initialized");
            return;
        }

        if (!check(request)) {
            response.setInError(400, "RICHIESTA ERRATA");
            return;
        }

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