/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2023 SoftInstigate
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

import graphql.TypeResolutionEnvironment;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.*;
import graphql.schema.idl.MapEnumValuesProvider;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeRuntimeWiring;
import graphql.schema.idl.errors.SchemaProblem;
import io.undertow.predicate.Predicate;
import org.bson.BsonValue;
import org.restheart.graphql.predicates.ExchangeWithBsonValue;
import org.restheart.graphql.scalars.BsonScalars;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;

public class GraphQLApp {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLApp.class);

    private AppDescriptor descriptor;
    private String schema;
    private Map<String, TypeMapping> objectsMappings;
    private GraphQLSchema executableSchema;

    public static Builder newBuilder() {
        return new Builder();
    }

    public GraphQLApp() {
    }

    public GraphQLApp(AppDescriptor descriptor, String schema, Map<String, TypeMapping> objectsMappings, GraphQLSchema executableSchema) {
        this.descriptor = descriptor;
        this.schema = schema;
        this.objectsMappings = objectsMappings;
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

    public Map<String, TypeMapping> objectsMappings() {
        return objectsMappings;
    }

    public void setObjectsMappings(Map<String, TypeMapping> mappings) {
        this.objectsMappings = mappings;
    }

    public GraphQLSchema getExecutableSchema() {
        return executableSchema;
    }

    public void setExecutableSchema(GraphQLSchema executableSchema) {
        this.executableSchema = executableSchema;
    }

    public static class Builder {
        private AppDescriptor descriptor;
        private String schema;
        private Map<String, TypeMapping> objectsMappings;
        private Map<String, Map<String, Object>> enumsMappings;
        private Map<String, Map<String, Predicate>> unionMappings;
        private Map<String, Map<String, Predicate>> interfacesMappings;

        private Builder() {
        }

        public Builder appDescriptor(AppDescriptor descriptor) {
            this.descriptor = descriptor;
            return this;
        }

        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        public Builder objectsMappings(Map<String, TypeMapping> mappings) {
            this.objectsMappings = mappings;
            return this;
        }

        public Builder unionMappings(Map<String, Map<String, Predicate>> mappings) {
            this.unionMappings = mappings;
            return this;
        }

        public Builder enumsMappings(Map<String, Map<String, Object>> mappings) {
            this.enumsMappings = mappings;
            return this;
        }

        public Builder interfacesMappings(Map<String, Map<String, Predicate>> mappings) {
            this.interfacesMappings = mappings;
            return this;
        }

        public GraphQLApp build() throws IllegalStateException {
            if (this.descriptor == null) {
                throw new IllegalStateException("app descriptor must be not null!");
            }

            if (this.schema == null) {
                throw new IllegalStateException("app schema must be not null");
            }

            if (this.objectsMappings == null) {
                throw new IllegalStateException("app mappings must be not null");
            } else if (!this.objectsMappings.containsKey("Query")) {
                throw new IllegalStateException("mappings for type Query are mandatory");
            }

            var schemaWithBsonScalars = BsonScalars.getBsonScalarHeader() + this.schema;

            try {
                var typeRegistry = new SchemaParser().parse(schemaWithBsonScalars);

                var RWBuilder = RuntimeWiring.newRuntimeWiring();
                var bsonScalars = BsonScalars.getBsonScalars();

                // Unions
                typeRegistry.types().entrySet().stream().filter(e -> e.getValue() instanceof UnionTypeDefinition).forEach(e ->{
                    var unionMapping = this.unionMappings.get(e.getKey());

                    RWBuilder.type(TypeRuntimeWiring.newTypeWiring(e.getKey()).typeResolver(new TypeResolver() {
                        @Override
                        public GraphQLObjectType getType(TypeResolutionEnvironment env) {
                            var obj = env.getObject();
                            final Optional<Entry<String, Predicate>> match;
                            if (obj instanceof BsonValue value) {
                                match = unionMapping.entrySet().stream()
                                    .filter(p -> p.getValue().resolve(ExchangeWithBsonValue.exchange(value)))
                                    .findFirst();
                            } else {
                                // predicates can only resolve on BsonValues
                                LOGGER.debug("no $typeResolver predicate can work for type {}", obj);
                                return null;
                            }

                            if (match.isPresent()) {
                                return env.getSchema().getObjectType(match.get().getKey());
                            } else {
                                return null;
                            }
                        }
                    }).build());
                });

                // Interfaces
                typeRegistry.types().entrySet().stream().filter(e -> e.getValue() instanceof InterfaceTypeDefinition).forEach(e ->{
                    LOGGER.debug("Interface: {} -> {}", e.getKey(),  e.getValue());

                    var interfaceMapping = this.interfacesMappings.get(e.getKey());

                    LOGGER.debug("\tmapping: {}", interfaceMapping);

                    RWBuilder.type(TypeRuntimeWiring.newTypeWiring(e.getKey()).typeResolver(new TypeResolver() {
                        @Override
                        public GraphQLObjectType getType(TypeResolutionEnvironment env) {
                            var obj = env.getObject();
                            final Optional<Entry<String, Predicate>> match;
                            if (obj instanceof BsonValue value) {
                                match = interfaceMapping.entrySet().stream()
                                    .filter(p -> p.getValue().resolve(ExchangeWithBsonValue.exchange(value)))
                                    .findFirst();
                            } else {
                                // predicates can resolve on BsonValues
                                LOGGER.debug("no $typeResolver predicate can work for type {}", obj);
                                return null;
                            }

                            if (match.isPresent()) {
                                return env.getSchema().getObjectType(match.get().getKey());
                            } else {
                                return null;
                            }
                        }
                    }).build());
                });

                // Enums
                this.enumsMappings.entrySet().forEach(em -> RWBuilder.type(TypeRuntimeWiring.newTypeWiring(em.getKey())
                    .enumValues(new MapEnumValuesProvider(em.getValue()))));

                bsonScalars.forEach(((s, graphQLScalarType) -> RWBuilder.scalar(graphQLScalarType)));
                this.objectsMappings.forEach(((type, typeMapping) -> RWBuilder.type(typeMapping.getTypeWiring(typeRegistry))));

                var runtimeWiring = RWBuilder.build();

                var schemaGenerator = new SchemaGenerator();

                var execSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);

                return new GraphQLApp(this.descriptor, this.schema, this.objectsMappings, execSchema);
            } catch (SchemaProblem schemaProblem) {
                var errorMSg = schemaProblem.getMessage() != null
                    ? "Invalid GraphQL schema: " + schemaProblem.getMessage()
                    : "Invalid GraphQL schema";

                throw new IllegalArgumentException(errorMSg, schemaProblem);
            }
        }
    }
}
