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
    public void init(MongoClient mclient){
        this.mongoClient = mclient;
        appDefinition("Test App");
        this.gql = GraphQL.newGraphQL(this.app.getSchema()).build();
    }


    public void appDefinition(String appName){

        //fetching APP definition from database
        GraphQLApp newApp = new GraphQLApp(appName);
        Document appDesc = this.mongoClient.getDatabase("restheart")
                .getCollection("apps").find().first();

        Map<String, Map<String,QueryMapping>> mappings = new HashMap<>();

        for (String type: ((Document) appDesc.get("mappings")).keySet()){
            Map<String, QueryMapping> typeMappings = new HashMap<>();
            for (Document mapping: ((Document) appDesc.get("mappings")).getList(type, Document.class)){
                String name = mapping.getString("name");
                typeMappings.put(name, new QueryMapping.Builder(type,name, mapping.getString("db"), mapping.getString("collection"),
                        mapping.getBoolean("multiple"))
                        .filter((Document) mapping.get("filter"))
                        .sort((Document) mapping.get("sort"))
                        .skip((Document) mapping.get("skip"))
                        .limit((Document) mapping.get("limit"))
                        .first((Document) mapping.get("first"))
                        .build());
            }
            mappings.put(type, typeMappings);
        }


        newApp.setQueryMappings(mappings);
        this.app = newApp;
        //making executable the schema
        GraphQLSchema graphQLSchema = buildSchema(appDesc.getString("schema"));
        newApp.setSchema(graphQLSchema);
    }

    private GraphQLSchema buildSchema(String sdl){
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = buildWiring();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private RuntimeWiring buildWiring(){

        Map<String, Map<String, QueryMapping>> queries = this.app.getQueryMappings();
        if (queries.size() >0) {
            RuntimeWiring.Builder runWire = RuntimeWiring.newRuntimeWiring();
            for (String type: queries.keySet()){
                TypeRuntimeWiring.Builder queryTypeBuilder = newTypeWiring(type);
                for (String queryName : queries.get(type).keySet()) {
                    boolean isMultiple = this.app.getQueryMappingByType(type).get(queryName).isMultiple();
                    if (isMultiple) {
                        queryTypeBuilder.dataFetcher(queryName, MultipleGraphQLDataFetcher.getInstance());
                    } else {
                        queryTypeBuilder.dataFetcher(queryName, SingleGraphQLDataFetcher.getInstance());
                    }
                }
                runWire.type(queryTypeBuilder);
            }

            MultipleGraphQLDataFetcher.setCurrentApp(this.app);
            MultipleGraphQLDataFetcher.setMongoClient(this.mongoClient);
            SingleGraphQLDataFetcher.setCurrentApp(this.app);
            SingleGraphQLDataFetcher.setMongoClient(this.mongoClient);
            return runWire.build();
        }
        else return null;
    }

    @Override
    public void handle(ByteArrayRequest request, MongoResponse response){

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