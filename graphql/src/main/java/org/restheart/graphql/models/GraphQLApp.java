/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2021 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.graphql.models;

import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.errors.SchemaProblem;
import org.restheart.graphql.scalars.BsonScalars;

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

        public GraphQLApp build() throws IllegalStateException {

            if (this.descriptor == null){
                throw new IllegalStateException("App descriptor must be not null!");
            }

            if (this.schema == null){
                throw new IllegalStateException("App schema must be not null");
            }

            String schemaWithBsonScalars = BsonScalars.getBsonScalarHeader() + this.schema;

            try{

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

            } catch (SchemaProblem schemaProblem){
                throw new IllegalArgumentException("Given String is not a valid GraphQL schema");
            }
        }

    }
}
