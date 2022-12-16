/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2022 SoftInstigate
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
package org.restheart.graphql;

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInvalidOperationException;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.utils.LambdaUtils;
import org.restheart.exchange.Request;
import org.restheart.graphql.models.*;
import org.restheart.graphql.scalars.BsonScalars;
import org.restheart.utils.BsonUtils;

import graphql.language.EnumTypeDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.errors.SchemaProblem;
import io.undertow.predicate.PredicateParser;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphQLAppDeserializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLAppDeserializer.class);

    private static BsonInt32 defaultLimit = new BsonInt32(GraphQLService.DEFAULT_DEFAULT_LIMIT);
    private static int maxLimit = GraphQLService.DEFAULT_MAX_LIMIT;

    public static void setDefaultLimit(int _defaultLimit) {
        defaultLimit = new BsonInt32(_defaultLimit);
    }

    public static void setMaxLimit(int _maxLimit) {
        maxLimit = _maxLimit;
    }

    public static final GraphQLApp fromBsonDocument(BsonDocument appDef) throws GraphQLIllegalAppDefinitionException {
        AppDescriptor descriptor = null;
        String schema = null;
        TypeDefinitionRegistry typeDefinitionRegistry;
        Map<String, TypeMapping> objectsMappings = null;
        Map<String, Map<String, Object>> enumsMappings = null;
        Map<String, Map<String, io.undertow.predicate.Predicate>> unionMappings = null;

        if (appDef.containsKey("descriptor")) {
            if (appDef.get("descriptor").isDocument()) {
                descriptor = getAppDescriptor(appDef);
            } else {
                throw new GraphQLIllegalAppDefinitionException("'Descriptor' field must be a 'DOCUMENT' but was " + appDef.get("descriptor").getBsonType());
            }
        }

        if (appDef.containsKey("schema")) {
            if (appDef.get("schema").isString()) {
                schema = appDef.getString("schema").getValue();
            } else {
                throw new GraphQLIllegalAppDefinitionException("'Schema' field must be a 'STRING' but was " + appDef.get("descriptor").getBsonType());
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
                objectsMappings = objectsMappings(BsonUtils.unescapeKeys(mappings).asDocument(), typeDefinitionRegistry);
                enumsMappings = enumsMappings(mappings, typeDefinitionRegistry);
                unionMappings = unionMappings(mappings, typeDefinitionRegistry);
            } else {
                throw new GraphQLIllegalAppDefinitionException("'Mappings' field must be a 'DOCUMENT' but was " + appDef.get("mappings").getBsonType());
            }
        }

        try {
            return GraphQLApp.newBuilder().appDescriptor(descriptor).schema(schema)
                .objectsMappings(objectsMappings)
                .enumsMappings(enumsMappings)
                .unionMappings(unionMappings)
                .build();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new GraphQLIllegalAppDefinitionException(e.getMessage(), e);
        }
    }

    private static TypeDefinitionRegistry typeDefinitionRegistry(String schema) throws SchemaProblem {
        var schemaWithBsonScalars = BsonScalars.getBsonScalarHeader() + schema;
        return new SchemaParser().parse(schemaWithBsonScalars);
    }

    private static AppDescriptor getAppDescriptor(BsonDocument doc) throws GraphQLIllegalAppDefinitionException {
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

    private static Map<String, Map<String, Object>> enumsMappings(BsonDocument doc, TypeDefinitionRegistry typeDefinitionRegistry) throws GraphQLIllegalAppDefinitionException {
        var ret = new HashMap<String, Map<String, Object>>();

        var wrongEnumMapping = doc.keySet().stream()
            .filter(key -> isEnum(key, typeDefinitionRegistry))
            .filter(key -> !doc.get(key).isDocument())
            .findFirst();

        if (wrongEnumMapping.isPresent()) {
            var wrongEnum = wrongEnumMapping.get();
            throw new GraphQLIllegalAppDefinitionException("Error with mappings of enum: '" + wrongEnum + "'. Enum mappings must be of type 'DOCUMENT' but was " + doc.get(wrongEnum).getBsonType());
        }

        // enums
        // enums with mappings => use given mappings
        doc.keySet().stream()
            .filter(key -> doc.get(key).isDocument())
            .filter(key -> isEnum(key, typeDefinitionRegistry))
            .forEach(enumKey -> ret.put(enumKey, enumValuesMappings(enumKey, (EnumTypeDefinition) typeDefinitionRegistry.types().get(enumKey), doc.getDocument(enumKey))));

        // enums with no mappings => use default mappings
        typeDefinitionRegistry.types().entrySet().stream()
            .filter(e -> e.getValue() instanceof EnumTypeDefinition)
            .filter(e -> !doc.containsKey(e.getKey())) // mapping doc does not contain a document for the enum
            .forEach(e -> ret.put(e.getKey(), enumValuesMappings(e.getKey(), (EnumTypeDefinition) e.getValue(), new BsonDocument())));
        // end - enums

        return ret;
    }

    private static Map<String, TypeMapping> objectsMappings(BsonDocument doc, TypeDefinitionRegistry typeDefinitionRegistry) throws GraphQLIllegalAppDefinitionException {
        var ret = new HashMap<String, TypeMapping>();

        var _wrongObjectMapping = doc.keySet().stream()
            .filter(key -> isObject(key, typeDefinitionRegistry))
            .filter(key -> !doc.get(key).isDocument())
            .findFirst();

        if (_wrongObjectMapping.isPresent()) {
            var wrongObjectMapping = _wrongObjectMapping.get();
            throw new GraphQLIllegalAppDefinitionException("Error with mappings of type: '" + wrongObjectMapping + "'. Type mappings must be of type 'DOCUMENT' but was " + doc.get(wrongObjectMapping).getBsonType());
        }

        doc.keySet().stream()
            .filter(key -> isObject(key, typeDefinitionRegistry))
            .filter(key -> doc.get(key).isDocument())
            .forEach(type -> ret.put(type, new ObjectMapping(type, objectFieldMappings(type, doc.getDocument(type)))));

        return ret;
    }

    /**
     *
     * @param doc
     * @param typeDefinitionRegistry
     * @return the map key, typeResolver predicate
     * @throws GraphQLIllegalAppDefinitionException
     */
    private static Map<String, Map<String, io.undertow.predicate.Predicate>> unionMappings(BsonDocument doc, TypeDefinitionRegistry typeDefinitionRegistry) throws GraphQLIllegalAppDefinitionException {
        var ret = new HashMap<String, Map<String, io.undertow.predicate.Predicate>>();

        // check that all union have a mapping with a $typeResolver
         // check that the $typeResolver object, maps all the members of the union
        var _unionWithMissingMapping = typeDefinitionRegistry.types().entrySet().stream()
            .filter(e -> e.getValue() instanceof UnionTypeDefinition)
            .filter(e -> !doc.containsKey(e.getKey()) || !doc.get(e.getKey()).isDocument())
            .findFirst();

        if (_unionWithMissingMapping.isPresent()) {
            var unionWithMissingMapping = _unionWithMissingMapping.get().getKey();
            throw new GraphQLIllegalAppDefinitionException("Missing mappings for union '" + unionWithMissingMapping);
        }

        var _unionWithMissingTypeResolver = typeDefinitionRegistry.types().entrySet().stream()
            .filter(e -> e.getValue() instanceof UnionTypeDefinition)
            .filter(e -> !doc.get(e.getKey()).asDocument().containsKey("_$typeResolver"))
            .findFirst();

        if (_unionWithMissingTypeResolver.isPresent()) {
            var unionWithMissingTypeResolver = _unionWithMissingTypeResolver.get().getKey();
            throw new GraphQLIllegalAppDefinitionException("Missing $typeResolver for union '" + unionWithMissingTypeResolver);
        }


        // check that all union mappings are documents
        var _wrongMappingNoDoc = doc.keySet().stream()
            .filter(key -> isUnion(key, typeDefinitionRegistry))
            .filter(key -> !doc.get(key).isDocument())
            .findFirst();

        if (_wrongMappingNoDoc.isPresent()) {
            var wrongMapping = _wrongMappingNoDoc.get();
            throw new GraphQLIllegalAppDefinitionException("Wrong mappings for union '" + wrongMapping + "': mappings must be of type 'DOCUMENT' but was " + doc.get(wrongMapping).getBsonType());
        }

        // check that all union mappings have a $typeResolver field
        var _wrongMappingMissingTypeResolver = doc.keySet().stream()
            .filter(key -> isUnion(key, typeDefinitionRegistry))
            .filter(key -> !doc.get(key).asDocument().containsKey("_$typeResolver"))
            .findFirst();

        if (_wrongMappingMissingTypeResolver.isPresent()) {
            var wrongMapping = _wrongMappingMissingTypeResolver.get();
            throw new GraphQLIllegalAppDefinitionException("Wrong mappings for union '" + wrongMapping + "': it does not define $typeResolver");
        }

        // check that all union mappings have a valid $typeResolver predicate
        var _wrongMappingTypeResolverNotDoc = doc.keySet().stream()
            .filter(key -> isUnion(key, typeDefinitionRegistry))
            .filter(key -> !doc.get(key).asDocument().get("_$typeResolver").isDocument())
            .findFirst();

        if (_wrongMappingTypeResolverNotDoc.isPresent()) {
            var wrongMapping = _wrongMappingTypeResolverNotDoc.get();
            throw new GraphQLIllegalAppDefinitionException("Wrong mappings for union '" + wrongMapping + "': the $typeResolver is not an Object");
        }

        // check the predicates of all unions
        doc.keySet().stream()
            .filter(type -> isUnion(type, typeDefinitionRegistry))
            .flatMap(type -> doc.get(type).asDocument().get("_$typeResolver").asDocument().entrySet().stream())
            .forEach(e -> {
                try {
                    typeResolverPredicate(e.getValue());
                } catch(Throwable t) {
                    LambdaUtils.throwsSneakyException(t);
                }
            });

        // check that the $typeResolver object, maps all the members of the union
        typeDefinitionRegistry.types().entrySet().stream()
            .filter(e -> e.getValue() instanceof UnionTypeDefinition)
            .forEach(e -> {
                var unionName = e.getKey();
                var unionTypeDef = (UnionTypeDefinition) e.getValue();

                var memberNames = unionTypeDef.getMemberTypes().stream()
                    //Note that members of a union type need to be concrete object type
                    .filter(type -> type instanceof TypeName)
                    .map(type -> (TypeName) type)
                    .map(m -> m.getName()).collect(Collectors.toList());

                var mappedMemberNames = doc.get(unionName).asDocument().get("_$typeResolver").asDocument().keySet();

                if (!mappedMemberNames.containsAll(memberNames)) {
                    LambdaUtils.throwsSneakyException(new GraphQLIllegalAppDefinitionException("$typeResolver for union " + unionName + " does not map all union members"));
                }
            });


        // all checks done, create the ret
        doc.keySet().stream()
            .filter(key -> isUnion(key, typeDefinitionRegistry))
            .forEach(type -> {
                var trm = new HashMap<String, io.undertow.predicate.Predicate>();

                doc.get(type).asDocument().get("_$typeResolver").asDocument().entrySet().stream()
                    .forEach(e -> {
                        try {
                            trm.put(e.getKey(), typeResolverPredicate(e.getValue()));
                        } catch(Throwable t) {
                            // should never happen, already checked
                            LambdaUtils.throwsSneakyException(t);
                        }
                    });

                ret.put(type, trm);
            });

        return ret;
    }

    private static io.undertow.predicate.Predicate typeResolverPredicate(BsonValue predicate) throws GraphQLIllegalAppDefinitionException {
        if (predicate == null || predicate.isNull()) {
            throw new GraphQLIllegalAppDefinitionException("null $typeResolver predicate");
        }

        if (!predicate.isString()) {
            throw new GraphQLIllegalAppDefinitionException("$typeResolver predicate is not a String: " + BsonUtils.toJson(predicate));
        }

        var p = predicate.asString().getValue();

        try {
            return PredicateParser.parse(p, GraphQLAppDeserializer.class.getClassLoader());
        } catch(Throwable t) {
            throw new GraphQLIllegalAppDefinitionException("error parsing $typeResolver predicate: " + p, t);
        }
    }

    private static HashMap<String, Object> enumValuesMappings(String enumKey, EnumTypeDefinition typeDef, BsonDocument enumDoc) {
        var ret = new HashMap<String, Object>();
        enumDoc.entrySet().stream().forEach(e -> ret.put(e.getKey(), e.getValue()));

        // if the mapping is missing a key, then map to itself as default mapping
        // i.e. enum Colors { RED BLUE } are mapped by default to BsonString("BLUE") and BsonString("GREEN")
        typeDef.getEnumValueDefinitions().stream().map(vd -> vd.getName()).filter(key -> !enumDoc.containsKey(key)).forEach(key -> ret.put(key, new BsonString(key)));
        return ret;
    }

    /**
     *
     * @param type
     * @param typeDoc
     * @return the FieldMappings of the Object Type
     */
    private static HashMap<String, FieldMapping> objectFieldMappings(String type, BsonDocument typeDoc) {
        var typeMappings = new HashMap<String, FieldMapping>();

        for (var field : typeDoc.keySet()) {
            var fieldMapping = typeDoc.get(field);

            switch (fieldMapping.getBsonType()) {
                case STRING -> typeMappings.put(field, new FieldRenaming(field, fieldMapping.asString().getValue()));
                case DOCUMENT -> {
                    var fieldMappingDoc = fieldMapping.asDocument();

                    // Check if document has "db" and "collection" keys and both are strings.
                    // These are common to both Aggregation and Query mapping.
                    if (fieldMappingDoc.containsKey("db")) {
                        if (!fieldMappingDoc.get("db").isString()) {
                            throwIllegalDefinitionException(field, type, "db", "'STRING'", fieldMappingDoc.get("db"));
                        }
                    } else {
                        throw new NullPointerException("Error with field '" + field + "' of type '" + type + "'. 'db' could not be null.");
                    }

                    if (fieldMappingDoc.containsKey("collection")) {
                        if (!fieldMappingDoc.get("collection").isString()) {
                            throwIllegalDefinitionException(field, type, "db", "'STRING'", fieldMappingDoc.get("collection"));
                        }
                    } else {
                        throw new NullPointerException("Error with field '" + field + "' of type '" + type + "'. 'collection' could not be null.");
                    }

                    // if "stages" key is present -> Aggregation Mapping
                    // else it's Query Mapping
                    if (fieldMappingDoc.containsKey("stages")) {
                        if (fieldMappingDoc.get("stages").isArray()) {
                            var aggregationBuilder = new AggregationMapping.Builder();

                            aggregationBuilder
                                .fieldName(field)
                                .db(fieldMappingDoc.get("db").asString())
                                .collection(fieldMappingDoc.get("collection").asString())
                                .stages(fieldMappingDoc.get("stages").asArray())
                                .allowDiskUse(hasKeyOfType(fieldMappingDoc, "allowDiskUse", t -> t.isBoolean())
                                    ? fieldMappingDoc.get("allowDiskUse").asBoolean()
                                    : BsonBoolean.FALSE);

                            // Check if dataloader settings are present
                            if (fieldMappingDoc.containsKey("dataLoader")) {
                                if (fieldMappingDoc.get("dataLoader").isDocument()) {
                                    var settings = fieldMappingDoc.getDocument("dataLoader");
                                    var dataLoaderBuilder = DataLoaderSettings.newBuilder();

                                    if (settings.containsKey("batching") && settings.get("batching").isBoolean()) {
                                        dataLoaderBuilder.batching(settings.getBoolean("batching").getValue());
                                        if (settings.containsKey("maxBatchSize") && settings.get("maxBatchSize").isNumber()) {
                                            dataLoaderBuilder.max_batch_size(settings.getNumber("maxBatchSize").intValue());
                                        }
                                    }

                                    if (settings.containsKey("caching") && settings.get("caching").isBoolean()) {
                                        dataLoaderBuilder.caching(settings.getBoolean("caching").getValue());
                                    }

                                    aggregationBuilder.dataLoaderSettings(dataLoaderBuilder.build());
                                } else {
                                    throwIllegalDefinitionException(field, type, "dataLoader", "DOCUMENT", fieldMappingDoc.get("dataLoader"));
                                }
                            }

                            typeMappings.put(field, aggregationBuilder.build());

                            break;

                        } else {
                            throwIllegalDefinitionException(field, type, "db", "'ARRAY'", fieldMappingDoc.get("stages"));
                        }
                    } else {
                        var queryMappingBuilder = QueryMapping.newBuilder();

                        queryMappingBuilder.fieldName(field);

                        queryMappingBuilder.db(fieldMappingDoc.getString("db").getValue());
                        queryMappingBuilder.collection(fieldMappingDoc.getString("collection").getValue());

                        if (fieldMappingDoc.containsKey("find")) {
                            if (fieldMappingDoc.get("find").isDocument()) {
                                queryMappingBuilder.find(fieldMappingDoc.getDocument("find"));
                            } else {
                                throwIllegalDefinitionException(field, type, "find", "DOCUMENT", fieldMappingDoc.get("find"));
                            }
                        }

                        if (fieldMappingDoc.containsKey("sort")) {
                            if (fieldMappingDoc.get("sort").isDocument()) {
                                queryMappingBuilder.sort(fieldMappingDoc.getDocument("sort"));
                            } else {
                                throwIllegalDefinitionException(field, type, "sort", "DOCUMENT", fieldMappingDoc.get("sort"));
                            }
                        }

                        if (fieldMappingDoc.containsKey("limit")) {
                            if (fieldMappingDoc.get("limit").isDocument()) {
                                queryMappingBuilder.limit(fieldMappingDoc.getDocument("limit"));
                            } else if (fieldMappingDoc.get("limit").isInt32()) {
                                var ln = fieldMappingDoc.get("limit").asInt32();

                                if (ln.getValue() > maxLimit) {
                                    LambdaUtils.throwsSneakyException(new GraphQLIllegalAppDefinitionException("Error with field 'limit' of type '" + type + "', value cannot be grater than " + maxLimit));
                                } else {
                                    queryMappingBuilder.limit(fieldMappingDoc.getNumber("limit"));
                                }
                            } else {
                                throwIllegalDefinitionException(field, type, "limit", "DOCUMENT", fieldMappingDoc.get("limit"));
                            }
                        } else {
                            queryMappingBuilder.limit(defaultLimit);
                        }

                        if (fieldMappingDoc.containsKey("skip")) {
                            if (fieldMappingDoc.get("skip").isDocument()) {
                                queryMappingBuilder.skip(fieldMappingDoc.getDocument("skip"));
                            } else if (fieldMappingDoc.get("skip").isNumber()) {
                                queryMappingBuilder.skip(fieldMappingDoc.getNumber("skip"));
                            } else {
                                throwIllegalDefinitionException(field, type, "skip", "DOCUMENT", fieldMappingDoc.get("skip"));
                            }
                        }

                        if (fieldMappingDoc.containsKey("dataLoader")) {
                            if (fieldMappingDoc.get("dataLoader").isDocument()) {
                                var settings = fieldMappingDoc.getDocument("dataLoader");
                                var dataLoaderBuilder = DataLoaderSettings.newBuilder();

                                if (settings.containsKey("batching") && settings.get("batching").isBoolean()) {
                                    dataLoaderBuilder.batching(settings.getBoolean("batching").getValue());

                                    if (settings.containsKey("maxBatchSize") && settings.get("maxBatchSize").isNumber()) {
                                        dataLoaderBuilder.max_batch_size(settings.getNumber("maxBatchSize").intValue());
                                    }
                                }

                                if (settings.containsKey("caching") && settings.get("caching").isBoolean()) {
                                    dataLoaderBuilder.caching(settings.getBoolean("caching").getValue());
                                }

                                queryMappingBuilder.DataLoaderSettings(dataLoaderBuilder.build());
                            } else {
                                throwIllegalDefinitionException(field, type, "dataLoader", "DOCUMENT", fieldMappingDoc.get("dataLoader"));
                            }
                        }

                        typeMappings.put(field, queryMappingBuilder.build());

                        break;
                    }

                    break;
                }
                default -> LambdaUtils.throwsSneakyException(new GraphQLIllegalAppDefinitionException("Error with mappings of type: '" + type + "'. A field mapping must be of type 'STRING' but was " + fieldMapping.getBsonType()));
            }
        }

        return typeMappings;
    }

    private static void throwIllegalDefinitionException(String field, String type, String arg, String typeExpected, BsonValue value) {
        LambdaUtils.throwsSneakyException(new GraphQLIllegalAppDefinitionException("Error with field '" + field + "' of type '" + type + "'. The field '" + arg + "' must be a '" + typeExpected + "' but was '" + value.getBsonType() + "'."));
    }

    private static boolean hasKeyOfType(BsonDocument source, String key, Predicate<BsonValue> isOfType) {
        Predicate<BsonDocument> containsKey = t -> t.containsKey(key);
        return containsKey.test(source) && isOfType.test(source.get(key));
    }

    private static boolean isInterface(String key, TypeDefinitionRegistry typeDefinitionRegistry) {
        return typeDefinitionRegistry.types().entrySet().stream()
                    .filter(e -> e.getKey().equals(key))
                    .anyMatch(e -> e.getValue() instanceof InterfaceTypeDefinition);
    }

    private static boolean isUnion(String key, TypeDefinitionRegistry typeDefinitionRegistry) {
        return typeDefinitionRegistry.types().entrySet().stream()
                    .filter(e -> e.getKey().equals(key))
                    .anyMatch(e -> e.getValue() instanceof UnionTypeDefinition);
    }

    private static boolean isObject(String key, TypeDefinitionRegistry typeDefinitionRegistry) {
        return typeDefinitionRegistry.types().entrySet().stream()
                    .filter(e -> e.getKey().equals(key))
                    .anyMatch(e -> e.getValue() instanceof ObjectTypeDefinition);
    }

    private static boolean isEnum(String key, TypeDefinitionRegistry typeDefinitionRegistry) {
        return typeDefinitionRegistry.types().entrySet().stream()
                    .filter(e -> e.getKey().equals(key))
                    .anyMatch(e -> e.getValue() instanceof EnumTypeDefinition);
    }
}
