package org.restheart.graphql;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.RegisterPlugin;

import java.util.Map;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;

@RegisterPlugin(name= "graphql",
        description = "handles GraphQL requests")
public class GraphQLService implements JsonService {
    @Override
    public void handle(JsonRequest request, JsonResponse response) throws Exception {
        String schema = "type Query{hello: String}";

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        RuntimeWiring runtimeWiring = newRuntimeWiring()
                .type("Query", builder -> builder.dataFetcher("hello", new StaticDataFetcher("world")))
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        GraphQL build = GraphQL.newGraphQL(graphQLSchema).build();
        ExecutionResult executionResult = build.execute("{hello}");

        final Gson gson = new Gson();
        final JsonElement jsonTree = gson.toJsonTree(executionResult.toSpecification(), Map.class);

        response.setContent(jsonTree.getAsJsonObject().get("data"));
    }
}
