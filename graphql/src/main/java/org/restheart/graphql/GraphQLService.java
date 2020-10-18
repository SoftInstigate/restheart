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

        Map<String, Map<String, AssociationMapping>> associationsDef = new HashMap<>();
        Map<String, QueryMapping> queryDef = new HashMap<>();

        //creating queries definitions and putting them inside App definition
        for (Document query: appDesc.getList("queries", Document.class)) {
            String queryName = query.getString("name");
            queryDef.put(queryName,
                    new QueryMapping.Builder(queryName, query.getString("db"), query.getString("collection"),
                            query.getBoolean("multiple"))
                            .filter((Document) query.get("filter"))
                            .sort((Document) query.get("sort"))
                            .skip((Document) query.get("skip"))
                            .limit((Document) query.get("skip"))
                            .first((Document) query.get("skip"))
                            .build());
        }
        Document associationsByTypes = appDesc.get("associations", Document.class);
        for (String type:  associationsByTypes.keySet()){
            Map<String, AssociationMapping> ass = new HashMap<>();
            for (Document association: associationsByTypes.getList(type, Document.class) ) {
                String associationName = association.getString("name");
                ass.put(associationName, new AssociationMapping(associationName, association.getString("target_db"),
                        association.getString("target_collection"), association.getString("type"),
                        association.getString("role"), association.getString("key"),
                        association.getString("ref_field")));
            }
            associationsDef.put(type, ass);
        }

        newApp.setQueryMappings(queryDef);
        newApp.setAssociationMappings(associationsDef);
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

        Map<String, QueryMapping> queries = this.app.getQueryMappings();
        if (queries.size() >0) {
            TypeRuntimeWiring.Builder queryTypeBuilder = newTypeWiring("Query");
            for (String queryName : queries.keySet()) {
                boolean isMultiple = this.app.getQueryMappingByName(queryName).isMultiple();
                if (isMultiple) {
                    queryTypeBuilder.dataFetcher(queryName, MultipleGraphQLDataFetcher.getInstance());
                } else {
                    queryTypeBuilder.dataFetcher(queryName, SingleGraphQLDataFetcher.getInstance());
                }
            }
            MultipleGraphQLDataFetcher.setCurrentApp(this.app);
            MultipleGraphQLDataFetcher.setMongoClient(this.mongoClient);
            SingleGraphQLDataFetcher.setCurrentApp(this.app);
            SingleGraphQLDataFetcher.setMongoClient(this.mongoClient);

            Map<String, Map<String, AssociationMapping>> associationByType = this.app.getAssociationMappings();
            if (associationByType.size()>0){
                TypeRuntimeWiring.Builder associationTypeBuilder = new TypeRuntimeWiring.Builder();
                for (String type: associationByType.keySet()) {
                    associationTypeBuilder = newTypeWiring(type);
                    Map<String, AssociationMapping> associations = this.app.getAssociationMappingByType(type);
                    for (String key: associations.keySet()){
                        AssociationMapping ass = associations.get(key);
                        String relType = ass.getType();
                        String role = ass.getRole();
                        //TODO: Adapt MultipleGraphQLDataFetcher to manage relations
                        if(relType.equals("ONE-TO-ONE")){
                            associationTypeBuilder.dataFetcher(ass.getName(), SingleGraphQLDataFetcher.getInstance());
                        }
                        else if(relType.equals("MANY-TO-MANY")){
                            associationTypeBuilder.dataFetcher(ass.getName(), MultipleGraphQLDataFetcher.getInstance());
                        }
                        else{
                            if(role.equals("OWNING")){
                                associationTypeBuilder.dataFetcher(ass.getName(), MultipleGraphQLDataFetcher.getInstance());
                            }
                            else{
                                associationTypeBuilder.dataFetcher(ass.getName(), SingleGraphQLDataFetcher.getInstance());
                            }
                        }

                    }

                }
                return RuntimeWiring.newRuntimeWiring().type(queryTypeBuilder).type(associationTypeBuilder).build();
            }

            return RuntimeWiring.newRuntimeWiring().type(queryTypeBuilder).build();
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