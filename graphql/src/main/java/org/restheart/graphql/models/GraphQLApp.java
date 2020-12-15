package org.restheart.graphql.models;

import graphql.schema.GraphQLSchema;
import org.bson.types.ObjectId;

import java.util.Map;

public class GraphQLApp {

    private ObjectId _id;
    private String descriptor;
    private String schema;
    private Map<String, Map<String, Mapping>> mappings;
    private GraphQLSchema builtSchema;

    public GraphQLApp(){}

    public GraphQLApp(ObjectId id, String descriptor, String schema, Map<String, Map<String, Mapping>> mappings) {
        this._id = id;
        this.descriptor = descriptor;
        this.schema = schema;
        this.mappings = mappings;
    }

    public ObjectId getId() {
        return _id;
    }

    public void setId(ObjectId id) {
        this._id = id;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public void setDescriptor(String descriptor) {
        this.descriptor = descriptor;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public Map<String, Map<String, Mapping>> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, Map<String, Mapping>> mappings) {
        this.mappings = mappings;
    }

    public GraphQLSchema getBuiltSchema() {
        return builtSchema;
    }

    public void setBuiltSchema(GraphQLSchema builtSchema) {
        this.builtSchema = builtSchema;
    }

    public static class Builder{
        private ObjectId _id;
        private String descriptor;
        private String schema;
        private Map<String, Map<String, Mapping>> mappings;


        public Builder(ObjectId _id, String descriptor){
            this._id = _id;
            this.descriptor = descriptor;
        }

        public Builder newBuilder(ObjectId _id, String descriptor){
            return new Builder(_id, descriptor);
        }

        public Builder schema(String schema){
            this.schema = schema;
            return this;
        }

        public Builder mappings(Map<String, Map<String, Mapping>> mappings){
            this.mappings = mappings;
            return this;
        }

        public GraphQLApp build(){
            return new GraphQLApp(this._id, this.descriptor, this.schema, this.mappings);
        }

    }
}
