package org.restheart.graphql;

import com.mongodb.MongoClient;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
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
import java.util.function.Consumer;
import java.util.function.Function;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;


@RegisterPlugin(name= "graphql",
        description = "handles GraphQL requests", defaultURI = "/graphql")
public class GraphQLService implements Service<ByteArrayRequest, MongoResponse> {
    private GraphQL gql;
    GraphQLDataFetchers graphQLDataFetchers;

    @InjectMongoClient
    public void init(MongoClient mclient) throws IOException, URISyntaxException {
        this.graphQLDataFetchers = new GraphQLDataFetchers(mclient);
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
        GraphQLSchema graphQLSchema = buildSchema(sdl);
        this.gql = GraphQL.newGraphQL(graphQLSchema).build();
    }

    private GraphQLSchema buildSchema(String sdl){
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = buildWiring();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private RuntimeWiring buildWiring(){
        return RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("first", this.graphQLDataFetchers.getFirstDataFetcher())
                        .dataFetcher("all", new TestDynamicDataFetcher(graphQLDataFetchers.mclient)))
                .build();
    }

    @Override
    public void handle(ByteArrayRequest request, MongoResponse response) throws Exception {

        if (this.graphQLDataFetchers == null) {
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

    private static class GraphQLDataFetchers {

        private MongoClient mclient;

        public GraphQLDataFetchers(MongoClient mclient) {
            this.mclient = mclient;
        }

        public DataFetcher getFirstDataFetcher() {
            return dataFetchingEnvironment -> {
                BsonDocument _query, _sort, _keys;
                String db = dataFetchingEnvironment.getArgumentOrDefault("db", "restheart");
                String query = dataFetchingEnvironment.getArgument("query");
                String coll = dataFetchingEnvironment.getArgument("coll");

                if (query == null) {
                    _query = new BsonDocument();
                } else {
                    _query = BsonDocument.parse(query);
                }

                String sort = dataFetchingEnvironment.getArgument("sort");

                if (sort == null) {
                    _sort = new BsonDocument();
                } else {
                    _sort = BsonDocument.parse(sort);
                }

                String keys = dataFetchingEnvironment.getArgument("keys");

                if (keys == null) {
                    _keys = new BsonDocument();
                } else {
                    _keys = BsonDocument.parse(keys);
                }

                return mclient.getDatabase(db)
                        .getCollection(coll)
                        .find(_query)
                        .projection(_keys)
                        .sort(_sort)
                        .first();
            };
        }

        public DataFetcher getAllDataFetcher(){
            return dataFetchingEnvironment -> {
                var ret = new LinkedHashSet<Document>();
                BsonDocument _query, _sort, _keys;
                String db = dataFetchingEnvironment.getArgumentOrDefault("db", "restheart");
                String query = dataFetchingEnvironment.getArgument("query");
                String coll = dataFetchingEnvironment.getArgument("coll");
                int skip = dataFetchingEnvironment.getArgument("skip");
                int limit = dataFetchingEnvironment.getArgument("limit");

                if (query == null) {
                    _query = new BsonDocument();
                } else {
                    _query = BsonDocument.parse(query);
                }

                String sort = dataFetchingEnvironment.getArgument("sort");

                if (sort == null) {
                    _sort = new BsonDocument();
                } else {
                    _sort = BsonDocument.parse(sort);
                }

                String keys = dataFetchingEnvironment.getArgument("keys");

                if (keys == null) {
                    _keys = new BsonDocument();
                } else {
                    _keys = BsonDocument.parse(keys);
                }

                var doc = mclient.getDatabase(db)
                        .getCollection(coll)
                        .find(_query)
                        .projection(_keys)
                        .sort(_sort)
                        .skip(skip)
                        .limit(limit)
                        .into(ret);

                return ret;
            };
        }
    }

    private class TestDynamicDataFetcher implements DataFetcher<List<Document>> {
        private MongoClient mclient;

        TestDynamicDataFetcher(MongoClient mclient) {
            this.mclient = mclient;
        }
        public List<Document> get(DataFetchingEnvironment env) throws Exception {
            var queryName = env.getField().getName();

            // get the query from mapping

            String db = env.getArgument("db");
            String coll = env.getArgument("coll");

            var query = this.mclient.getDatabase(db)
                    .getCollection(coll, Document.class)
                    .find();

            // interpolate the argument in the mapping query {"$var": "arg" } from evn.getArguments()

            // execute the query

            var ret = new ArrayList<Document>();

            query.into(ret);

            return ret;
        }
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

/**

 // Inserito commento prova

 @RegisterPlugin(name= "graphql",
 description = "handles GraphQL requests")
 public class GraphQLService implements Service<ByteArrayRequest, MongoResponse> {
 private static SchemaParserBuilder PARSER = SchemaParser.newParser();
 private MongoResolver resolver;
 private GraphQL gql;

 @InjectMongoClient
 public void init(MongoClient mclient) {
 this.resolver = new MongoResolver(mclient);

 var schema = "type Document { _id: Int msg: String } " +
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

 var schemaBuilder = PARSER
 .schemaString(schema)
 .resolvers(resolver)
 .build()
 .makeExecutableSchema();

 gql = GraphQL.newGraphQL(schemaBuilder).build();
 }

 @Override
 public void handle(ByteArrayRequest request, MongoResponse response) throws Exception {
 if (resolver == null) {
 response.setInError(500, "MongoClient not initialized");
 return;
 }

 if (!check(request)) {
 response.setInError(400, "Wrong request");
 return;
 }

 var query = new String(request.getContent());

 var result = gql.execute(query);

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

 private static class MongoResolver implements GraphQLQueryResolver {
 private MongoClient mclient;

 public MongoResolver(MongoClient mclient) {
 this.mclient = mclient;
 }

 public Document first(String db, String coll, String query, String sort, String keys) {
 if (db == null) {
 db = "restheart";
 }

 Objects.nonNull(coll);

 BsonDocument _query;

 if (query == null) {
 _query = new BsonDocument();
 } else {
 _query = BsonDocument.parse(query);
 }

 BsonDocument _sort;

 if (sort == null) {
 _sort = new BsonDocument();
 } else {
 _sort = BsonDocument.parse(sort);
 }

 BsonDocument _keys;

 if (keys == null) {
 _keys = new BsonDocument();
 } else {
 _keys = BsonDocument.parse(keys);
 }

 return mclient.getDatabase(db)
 .getCollection(coll)
 .find(_query)
 .projection(_keys)
 .sort(_sort)
 .first();
 }

 public Set<Document> all(String db, String coll, String query, String sort, String keys, int skip, int limit) {
 var ret = new LinkedHashSet<Document>();

 if (db == null) {
 db = "restheart";
 }

 Objects.nonNull(coll);

 BsonDocument _query;

 if (query == null) {
 _query = new BsonDocument();
 } else {
 _query = BsonDocument.parse(query);
 }

 BsonDocument _sort;

 if (sort == null) {
 _sort = new BsonDocument();
 } else {
 _sort = BsonDocument.parse(sort);
 }

 BsonDocument _keys;

 if (keys == null) {
 _keys = new BsonDocument();
 } else {
 _keys = BsonDocument.parse(keys);
 }

 var doc= mclient.getDatabase(db)
 .getCollection(coll)
 .find(_query)
 .projection(_keys)
 .sort(_sort)
 .skip(skip)
 .limit(limit)
 .into(ret);

 return ret;
 }
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
 **/