package org.restheart.graphql;

import graphql.schema.GraphQLSchema;
import java.util.Map;

public class GraphQLApp {

    private String descriptor;
    private GraphQLSchema schema;
    private Map<String, Map<String,QueryMapping>> queryMappings;

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

    public Map<String, Map<String,QueryMapping>> getQueryMappings() {
        return this.queryMappings;
    }

    public void setQueryMappings(Map<String, Map<String, QueryMapping>> queryMappings) {
        this.queryMappings = queryMappings;
    }

    public Map<String,QueryMapping> getQueryMappingByType(String type){
        return this.getQueryMappings().get(type);
    }


}
