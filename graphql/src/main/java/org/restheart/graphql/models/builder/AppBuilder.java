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
package org.restheart.graphql.models.builder;

import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonInvalidOperationException;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import org.restheart.graphql.models.AppDescriptor;
import org.restheart.graphql.models.GraphQLApp;
import org.restheart.graphql.models.ObjectMapping;
import org.restheart.graphql.models.TypeMapping;
import org.restheart.utils.BsonUtils;

import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.errors.SchemaProblem;

public class AppBuilder extends Mappings {
    public static final GraphQLApp build(BsonDocument appDef) throws GraphQLIllegalAppDefinitionException {
        AppDescriptor descriptor = null;
        String schema = null;
        TypeDefinitionRegistry typeDefinitionRegistry;
        final Map<String, TypeMapping> objectsMappings;
        Map<String, Map<String, Object>> enumsMappings = null;
        Map<String, Map<String, io.undertow.predicate.Predicate>> unionsMappings = null;
        Map<String, Map<String, io.undertow.predicate.Predicate>> interfacesMappings = null;

        if (appDef.containsKey("descriptor")) {
            if (appDef.get("descriptor").isDocument()) {
                descriptor = descriptor(appDef);
            } else {
                throw new GraphQLIllegalAppDefinitionException("'Descriptor' field must be an Object but was " + appDef.get("descriptor").getBsonType());
            }
        }

        if (appDef.containsKey("schema")) {
            if (appDef.get("schema").isString()) {
                schema = appDef.getString("schema").getValue();
            } else {
                throw new GraphQLIllegalAppDefinitionException("'Schema' field must be a String but was " + appDef.get("descriptor").getBsonType());
            }
        }

        // check schema
        try {
            typeDefinitionRegistry = typeDefinitionRegistry(schema);
        } catch(SchemaProblem schemaProblem) {
            var errorMSg = schemaProblem.getMessage() != null
                ? "Invalid GraphQL schema: " + schemaProblem.getMessage()
                : "Invalid GraphQL schema";

            throw new GraphQLIllegalAppDefinitionException(errorMSg, schemaProblem);
        }



        if (appDef.containsKey("mappings")) {
            if (appDef.get("mappings").isDocument()) {
                var mappings = appDef.getDocument("mappings");
                objectsMappings = ObjectsMappings.get(BsonUtils.unescapeKeys(mappings).asDocument(), typeDefinitionRegistry);
                enumsMappings = EnumMappings.get(mappings, typeDefinitionRegistry);
                unionsMappings = UnionsMappings.get(mappings, typeDefinitionRegistry);
                interfacesMappings = InterfacesMappings.get(mappings, typeDefinitionRegistry);
            } else {
                throw new GraphQLIllegalAppDefinitionException("'mappings' field must be an Object but was " + appDef.get("mappings").getBsonType());
            }
        } else {
            // at least a mapping for a Query is needed
            throw new GraphQLIllegalAppDefinitionException("Missing mappings: please provide a mapping for at least one Query.");
        }

        if (!objectsMappings.containsKey("Query") || objectsMappings.get("Query").getFieldMappingMap().isEmpty()) {
            // at least a mapping for a Query is needed
            throw new GraphQLIllegalAppDefinitionException("Missing or empty mappings for type Query: please provide a mapping for at least one Query field.");
        }

        // Provide a default field mappings for Objects that are not explicitly mapped.
        // see ObjectsMappings.defaultObjectFieldMappings() javadoc for more information.
        typeDefinitionRegistry.types().entrySet().stream()
                    .filter(e -> !objectsMappings.containsKey(e.getKey()))
                    .filter(e -> e.getValue() instanceof ObjectTypeDefinition)
                    .forEach(e -> {
                        var objectFieldMappings = ObjectsMappings.defaultObjectFieldMappings(e.getKey(), typeDefinitionRegistry, new BsonDocument());
                        var objectMapping = new ObjectMapping(e.getKey(), objectFieldMappings);
                        objectsMappings.put(e.getKey(), objectMapping);
                    });

        try {
            return GraphQLApp.newBuilder().appDescriptor(descriptor).schema(schema)
                .objectsMappings(objectsMappings)
                .enumsMappings(enumsMappings)
                .unionMappings(unionsMappings)
                .interfacesMappings(interfacesMappings)
                .build();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new GraphQLIllegalAppDefinitionException(e.getMessage(), e);
        }
    }

    public static void setDefaultLimit(int _defaultLimit) {
        ObjectsMappings.setDefaultLimit(_defaultLimit);
    }

    public static void setMaxLimit(int _maxLimit) {
        ObjectsMappings.setMaxLimit(_maxLimit);
    }

    private static AppDescriptor descriptor(BsonDocument doc) throws GraphQLIllegalAppDefinitionException {
        try {
            var descriptor = doc.getDocument("descriptor");
            var descBuilder = AppDescriptor.newBuilder();

            if (descriptor.containsKey("name")) {
                descBuilder.appName(descriptor.getString("name").getValue());
            }

            if (descriptor.containsKey("uri")) {
                descBuilder.uri(descriptor.getString("uri").getValue());
            } else if (descriptor.containsKey("name")) {
                descBuilder.uri(descriptor.getString("name").getValue());
            }

            if (descriptor.containsKey("description")) {
                descBuilder.description(descriptor.getString("description").getValue());
            } else {
                descBuilder.description("");
            }

            if (descriptor.containsKey("enabled")) {
                descBuilder.enabled(descriptor.getBoolean("enabled").getValue());
            } else {
                descBuilder.enabled(true);
            }

            return descBuilder.build();
        } catch (BsonInvalidOperationException | IllegalStateException e) {
            throw new GraphQLIllegalAppDefinitionException("Error with GraphQL App Descriptor", e);
        }
    }
}
