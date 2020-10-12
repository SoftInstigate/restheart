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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;


@RegisterPlugin(name= "graphql",
        description = "handles GraphQL requests", defaultURI = "/graphql")
public class GraphQLService implements Service<ByteArrayRequest, MongoResponse> {
    private GraphQL gql;
    GraphQLApp app;
    MongoClient mongoClient;

    @InjectMongoClient
    public void init(MongoClient mclient) throws IOException, URISyntaxException {
        this.mongoClient = mclient;
        var sdl = "type Document { _id: Int msg: String } " +
                "type Query { " +
                "first(db: String = \"restheart\"" +
                "coll: String!, " +
                "query: String = \"{}\", " +
                "sort: String, = \"{'_id': -1}\"," +
                "keys: String = \"{}\"): Document " +

                "all(db: String = \"restheart\", " +
                "coll: String!," +
                "query: String = \"{}\", " +
                "sort: String = \"{'_id': -1}\"," +
                "keys: String = \"{}\", " +
                "skip: Int = 0, " +
                "limit: Int = 100): [Document]" +
                "}";
        this.app = new GraphQLApp("Test APP");
        Map<String, Query> queries = null;
        Query firstQuery = QueryBuilder.newBuilder("restheart","first", "collection").first(true).build();
        Query allQuery = QueryBuilder.newBuilder("restheart", "all", "collection").build();
        queries.put(firstQuery.getName(), firstQuery);
        queries.put(allQuery.getName(), allQuery);
        app.setQueries(queries);
        GraphQLSchema graphQLSchema = buildSchema(sdl);
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