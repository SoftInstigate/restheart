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
package org.restheart.graphql.models.builder;

import java.util.HashMap;
import java.util.Map;

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import org.restheart.graphql.GraphQLService;
import org.restheart.graphql.models.AggregationMapping;
import org.restheart.graphql.models.DataLoaderSettings;
import org.restheart.graphql.models.FieldMapping;
import org.restheart.graphql.models.FieldRenaming;
import org.restheart.graphql.models.ObjectMapping;
import org.restheart.graphql.models.QueryMapping;
import org.restheart.graphql.models.TypeMapping;
import org.restheart.utils.LambdaUtils;
import graphql.schema.idl.TypeDefinitionRegistry;

class ObjectsMappings extends Mappings {
    private static BsonInt32 defaultLimit = new BsonInt32(GraphQLService.DEFAULT_DEFAULT_LIMIT);
    private static int maxLimit = GraphQLService.DEFAULT_MAX_LIMIT;

    public static void setDefaultLimit(int _defaultLimit) {
        defaultLimit = new BsonInt32(_defaultLimit);
    }

    public static void setMaxLimit(int _maxLimit) {
        maxLimit = _maxLimit;
    }

    /**
     *
     * @param doc
     * @param typeDefinitionRegistry
     * @return the objects mappings
     * @throws GraphQLIllegalAppDefinitionException
     */
    static Map<String, TypeMapping> get(BsonDocument doc, TypeDefinitionRegistry typeDefinitionRegistry) throws GraphQLIllegalAppDefinitionException {
        var ret = new HashMap<String, TypeMapping>();

        var _wrongObjectMapping = doc.keySet().stream()
            .filter(key -> isObject(key, typeDefinitionRegistry))
            .filter(key -> !doc.get(key).isDocument())
            .findFirst();

        if (_wrongObjectMapping.isPresent()) {
            var wrongObjectMapping = _wrongObjectMapping.get();
            throw new GraphQLIllegalAppDefinitionException("Error with mappings of type: " + wrongObjectMapping + ". Type mappings must be of type Object but was " + doc.get(wrongObjectMapping).getBsonType());
        }

        doc.keySet().stream()
            .filter(key -> isObject(key, typeDefinitionRegistry))
            .filter(key -> doc.get(key).isDocument())
            .forEach(type -> ret.put(type, new ObjectMapping(type, objectFieldMappings(type, doc.getDocument(type)))));

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
                            throwIllegalDefinitionException(field, type, "db", "String", fieldMappingDoc.get("db"));
                        }
                    } else {
                        throw new NullPointerException("Error with field " + field + " of type " + type + ". db could not be null.");
                    }

                    if (fieldMappingDoc.containsKey("collection")) {
                        if (!fieldMappingDoc.get("collection").isString()) {
                            throwIllegalDefinitionException(field, type, "db", "String", fieldMappingDoc.get("collection"));
                        }
                    } else {
                        throw new NullPointerException("Error with field " + field + " of type " + type + ". collection could not be null.");
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
                                    throwIllegalDefinitionException(field, type, "dataLoader", "Object", fieldMappingDoc.get("dataLoader"));
                                }
                            }

                            typeMappings.put(field, aggregationBuilder.build());

                            break;

                        } else {
                            throwIllegalDefinitionException(field, type, "db", "ARRAY", fieldMappingDoc.get("stages"));
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
                                throwIllegalDefinitionException(field, type, "find", "Object", fieldMappingDoc.get("find"));
                            }
                        }

                        if (fieldMappingDoc.containsKey("sort")) {
                            if (fieldMappingDoc.get("sort").isDocument()) {
                                queryMappingBuilder.sort(fieldMappingDoc.getDocument("sort"));
                            } else {
                                throwIllegalDefinitionException(field, type, "sort", "Object", fieldMappingDoc.get("sort"));
                            }
                        }

                        if (fieldMappingDoc.containsKey("limit")) {
                            if (fieldMappingDoc.get("limit").isDocument()) {
                                queryMappingBuilder.limit(fieldMappingDoc.getDocument("limit"));
                            } else if (fieldMappingDoc.get("limit").isInt32()) {
                                var ln = fieldMappingDoc.get("limit").asInt32();

                                if (ln.getValue() > maxLimit) {
                                    LambdaUtils.throwsSneakyException(new GraphQLIllegalAppDefinitionException("Error with field 'limit' of type " + type + ", value cannot be grater than " + maxLimit));
                                } else {
                                    queryMappingBuilder.limit(fieldMappingDoc.getNumber("limit"));
                                }
                            } else {
                                throwIllegalDefinitionException(field, type, "limit", "Object", fieldMappingDoc.get("limit"));
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
                                throwIllegalDefinitionException(field, type, "skip", "Object", fieldMappingDoc.get("skip"));
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
                                throwIllegalDefinitionException(field, type, "dataLoader", "Object", fieldMappingDoc.get("dataLoader"));
                            }
                        }

                        typeMappings.put(field, queryMappingBuilder.build());

                        break;
                    }

                    break;
                }
                default -> LambdaUtils.throwsSneakyException(new GraphQLIllegalAppDefinitionException("Error with mappings of type: " + type + ". A field mapping must be of type String but was " + fieldMapping.getBsonType()));
            }
        }

        return typeMappings;
    }
}
