package org.restheart.graphql;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import org.bson.BsonDocument;

import java.util.Map;

public class GraphQLApp {

    private String descriptor;
    private GraphQLSchema schema;
    private Map<String, Query> Queries;


    public GraphQLApp(String descriptor) {
        this.descriptor = descriptor;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public void setDescriptor(String descriptor) {
        this.descriptor = descriptor;
    }

    public GraphQLSchema getSchema() {
        return schema;
    }

    public void setSchema(GraphQLSchema schema) {
        this.schema = schema;
    }

    public Map<String, Query> getQueries() {
        return Queries;
    }

    public void setQueries(Map<String, Query> queries) {
        Queries = queries;
    }

    public Query getQueryByName(String name){
        return this.Queries.get(name);
    }

}
