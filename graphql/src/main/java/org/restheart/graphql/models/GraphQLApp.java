package org.restheart.graphql.models;

import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.restheart.graphql.BsonScalars;

import java.util.Map;

public class GraphQLApp {

    private AppDescriptor descriptor;
    private String schema;
    private Map<String, TypeMapping> mappings;
    private GraphQLSchema executableSchema;

    public static Builder newBuilder(){
        return new Builder();
    }

    public GraphQLApp(){}

    public GraphQLApp(AppDescriptor descriptor, String schema, Map<String, TypeMapping> mappings, GraphQLSchema executableSchema) {
        this.descriptor = descriptor;
        this.schema = schema;
        this.mappings = mappings;
        this.executableSchema = executableSchema;
    }

    public AppDescriptor getDescriptor() {
        return descriptor;
    }

    public void setDescriptor(AppDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public Map<String, TypeMapping> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, TypeMapping> mappings) {
        this.mappings = mappings;
    }

    public GraphQLSchema getExecutableSchema() {
        return executableSchema;
    }

    public void setExecutableSchema(GraphQLSchema executableSchema) {
        this.executableSchema = executableSchema;
    }

    public static class Builder{
        private AppDescriptor descriptor;
        private String schema;
        private Map<String, TypeMapping> mappings;
        private GraphQLSchema executableSchema;


        private Builder(){}

        public Builder appDescriptor(AppDescriptor descriptor){
            this.descriptor = descriptor;
            return this;
        }

        public Builder schema(String schema){
            this.schema = schema;
            return this;
        }

        public Builder mappings(Map<String, TypeMapping> mappings){
            this.mappings = mappings;
            return this;
        }

        public GraphQLApp build() throws IllegalAccessException {

            if (this.descriptor == null){
                throwIllegalException("descriptor");
            }

            if (this.schema == null){
                throwIllegalException("schema");
            }

            String schemaWithBsonScalars = BsonScalars.getBsonScalarHeader() + this.schema;
            TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(schemaWithBsonScalars);
            RuntimeWiring.Builder RWBuilder = RuntimeWiring.newRuntimeWiring();
            Map<String, GraphQLScalarType> bsonScalars = BsonScalars.getBsonScalars();

            bsonScalars.forEach(((s, graphQLScalarType) -> {
                RWBuilder.scalar(graphQLScalarType);
            }));

            if(mappings != null){

                this.mappings.forEach(((type, typeMapping) ->
                        RWBuilder.type(typeMapping.getTypeWiring(typeRegistry))));

            }

            RuntimeWiring runtimeWiring = RWBuilder.build();

            SchemaGenerator schemaGenerator = new SchemaGenerator();

            GraphQLSchema execSchema =  schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);

            return new GraphQLApp(this.descriptor, this.schema, this.mappings, execSchema);
        }

        private static void throwIllegalException(String varName){

            throw  new IllegalStateException(
                    varName + "could not be null!"
            );

        }

    }
}
