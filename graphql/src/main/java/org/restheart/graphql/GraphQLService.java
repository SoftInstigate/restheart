package org.restheart.graphql;

import com.mongodb.MongoClient;
import graphql.GraphQL;
import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.kickstart.tools.SchemaParser;
import graphql.kickstart.tools.SchemaParserBuilder;
import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.Document;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InjectMongoClient;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;
import org.restheart.utils.JsonUtils;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

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
