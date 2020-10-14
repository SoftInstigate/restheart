package org.restheart.graphql;

import com.mongodb.MongoClient;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonElement;
import org.bson.BsonValue;
import org.bson.Document;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InjectMongoClient;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;
import org.restheart.utils.JsonUtils;

import javax.print.Doc;
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
    private String sdl;

    @InjectMongoClient
    public void init(MongoClient mclient) throws IOException, URISyntaxException {
        this.mongoClient = mclient;
        this.sdl ="type Query { " +
                "first(_id: Int!): Document" +
                "}" +
                "type Document { " +
                "_id: Int " +
                "msg: String " +
                "}";

    }

    private GraphQLSchema buildSchema(String sdl){
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = buildWiring();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private RuntimeWiring buildWiring(){

        TypeRuntimeWiring.Builder obj = newTypeWiring("Query");
        //for each app query, create a dedicated data fetcher
        for (String queryName : this.app.getQueries().keySet()) {
            obj.dataFetcher(queryName, new GraphQLDataFetcher(this.mongoClient, this.app));
        }

        return RuntimeWiring.newRuntimeWiring().type(obj).build();
    }

    @Override
    public void handle(ByteArrayRequest request, MongoResponse response) throws Exception {

        if (!check(request)) {
            response.setInError(400, "RICHIESTA ERRATA");
            return;
        }

        //create APP
        this.app = new GraphQLApp("Test APP");
        Map<String, Query> queries = new HashMap<>();

        //retrieve query definition
        Document res = this.mongoClient.getDatabase("restheart")
                .getCollection("queries").find().first();

        //create Query Object by query definition
        Query firstQuery = QueryBuilder.newBuilder(res.getString("db"),
                res.getString("name"), res.getString("collection"))
                .filter((Document) res.get("filter"))
                .sort((Document) res.get("sort"))
                .skip((Document) res.get("skip"))
                .limit((Document) res.get("limit"))
                .first((Document) res.get("first"))
                .build();

        //add query to app queries
        queries.put(firstQuery.getName(), firstQuery);
        app.setQueries(queries);

        //assign and build app schema
        GraphQLSchema graphQLSchema = buildSchema(sdl);
        this.app.setSchema(graphQLSchema);
        this.gql = GraphQL.newGraphQL(graphQLSchema).build();


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