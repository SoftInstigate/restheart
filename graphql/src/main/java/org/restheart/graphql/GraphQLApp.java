package org.restheart.graphql;

import graphql.schema.GraphQLSchema;
import java.util.Map;

public class GraphQLApp {

    private String descriptor;
    private GraphQLSchema schema;
    private Map<String, QueryMapping> queryMappings;
    private Map<String, Map<String, AssociationMapping>> associationMappings;

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

    public Map<String, QueryMapping> getQueryMappings() {
        return this.queryMappings;
    }

    public void setQueryMappings(Map<String, QueryMapping> queryMappings) {
        this.queryMappings = queryMappings;
    }

    public Map<String, Map<String, AssociationMapping>> getAssociationMappings() {
        return associationMappings;
    }

    public void setAssociationMappings(Map<String, Map<String, AssociationMapping>> associationMappings) {
        this.associationMappings = associationMappings;
    }

    public QueryMapping getQueryMappingByName(String name){
        return this.getQueryMappings().get(name);
    }

    public Map<String, AssociationMapping> getAssociationMappingByType(String type){
        return this.associationMappings.get(type);
    }
}
