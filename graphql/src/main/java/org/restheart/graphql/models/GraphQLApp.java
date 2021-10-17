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

import graphql.schema.*;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.errors.SchemaProblem;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.graphql.scalars.BsonScalars;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GraphQLApp {

    private final static Logger logger = LoggerFactory.getLogger(GraphQLApp.class);

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
                throw new IllegalStateException("app descriptor must be not null!");
            }

            if (this.schema == null){
                throw new IllegalStateException("app schema must be not null");
            }

            if (this.mappings == null ){
                throw new IllegalStateException("app mappings must be not null");
            }
            else if(!this.mappings.containsKey("Query")){
                throw new IllegalStateException("mappings for type Query are mandatory");
            }


            String schemaWithBsonScalars = BsonScalars.getBsonScalarHeader() + this.schema;

            try{

                TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(schemaWithBsonScalars);

                RuntimeWiring.Builder RWBuilder = RuntimeWiring.newRuntimeWiring();
                Map<String, GraphQLScalarType> bsonScalars = BsonScalars.getBsonScalars();
                

                bsonScalars.forEach(((s, graphQLScalarType) -> {
                    RWBuilder.scalar(graphQLScalarType);
                }));

                this.mappings.forEach(((type, typeMapping) ->
                        RWBuilder.type(typeMapping.getTypeWiring(typeRegistry))
                ));



                RWBuilder.type(
                    newTypeWiring("Prova")
                    .typeResolver(env -> {
                        var type = "Movie";
                        var source = env.getObject(); // Fetched object from mongodb. size = 21
                        BsonDocument doc;
                        if(source instanceof BsonDocument) {
                            doc = (BsonDocument)source;

                        }

                        var typesWithFields = env.getSchema().getTypeMap().values()
                                .stream()
                                .filter(entry -> entry instanceof GraphQLObjectType)
                                .filter(obj -> !obj.getName().startsWith("__") || !obj.getName().equals("Query"))
                                .collect(Collectors.toUnmodifiableMap(
                                        GraphQLNamedSchemaElement::getName,
                                        val -> ((GraphQLObjectType)val).getFieldDefinitions()
                                                .stream()
                                                .map(GraphQLFieldDefinition::getName)
                                                .collect(Collectors.toSet())
                                ));


                        return env.getSchema().getObjectType(type);
                    })
                    .build()
                );

                RuntimeWiring runtimeWiring = RWBuilder.build();

                SchemaGenerator schemaGenerator = new SchemaGenerator();

                // FIX: fail to create executable schema when adding an interface!
                GraphQLSchema execSchema =  schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);

                return new GraphQLApp(this.descriptor, this.schema, this.mappings, execSchema);

            } catch (SchemaProblem schemaProblem){
                var errorMSg = schemaProblem.getMessage() != null
                    ? "Invalid GraphQL schema: " + schemaProblem.getMessage()
                    : "Invalid GraphQL schema";

                throw new IllegalArgumentException(errorMSg, schemaProblem);
            }
        }


    }


}
