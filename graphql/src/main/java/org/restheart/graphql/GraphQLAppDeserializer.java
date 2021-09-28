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
package org.restheart.graphql;

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInvalidOperationException;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.graphql.models.*;
import org.restheart.utils.BsonUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;


public class GraphQLAppDeserializer {

    public static final GraphQLApp fromBsonDocument(BsonDocument appDef) throws GraphQLIllegalAppDefinitionException {

        AppDescriptor descriptor = null;
        String schema = null;
        Map<String, TypeMapping> mappingsMap = null;

        if( appDef.containsKey("descriptor")){
            if (appDef.get("descriptor").isDocument()){
                descriptor = getAppDescriptor(appDef);
            }
            else{
                throw new GraphQLIllegalAppDefinitionException(
                        "'Descriptor' field must be a 'DOCUMENT' but was " + appDef.get("descriptor").getBsonType()
                );
            }
        }

        if (appDef.containsKey("schema")){
            if (appDef.get("schema").isString()){
                schema = appDef.getString("schema").getValue();
            }
            else{
                throw new GraphQLIllegalAppDefinitionException(
                        "'Schema' field must be a 'STRING' but was " + appDef.get("descriptor").getBsonType()
                );
            }
        }

        if(appDef.containsKey("mappings")){
            if (appDef.get("mappings").isDocument()){
                mappingsMap = getMappings(BsonUtils.unescapeKeys(appDef.getDocument("mappings")).asDocument());
            }
            else{
                throw new GraphQLIllegalAppDefinitionException(
                        "'Mappings' field must be a 'DOCUMENT' but was " + appDef.get("mappings").getBsonType()
                );
            }
        }

        try {
            return GraphQLApp.newBuilder()
                    .appDescriptor(descriptor)
                    .schema(schema)
                    .mappings(mappingsMap)
                    .build(); // build generates executable schema
        } catch (IllegalStateException | IllegalArgumentException e){
            throw new GraphQLIllegalAppDefinitionException(e.getMessage(), e);
        }
    }

    private static AppDescriptor getAppDescriptor(BsonDocument doc) throws GraphQLIllegalAppDefinitionException {

        try{
            BsonDocument descriptor = doc.getDocument("descriptor");

            AppDescriptor.Builder descBuilder = AppDescriptor.newBuilder();

            if (descriptor.containsKey("name")){
                descBuilder.appName(descriptor.getString("name").getValue());
            }

            if (descriptor.containsKey("uri")) {

                descBuilder.uri(descriptor.getString("uri").getValue());
            } else if (descriptor.containsKey("name")){
                descBuilder.uri(descriptor.getString("name").getValue());
            }

            if (descriptor.containsKey("description")){
                descBuilder.description(descriptor.getString("description").getValue());
            }
            else {
                descBuilder.description("");
            }

            if (descriptor.containsKey("enabled")) {

                descBuilder.enabled(descriptor.getBoolean("enabled").getValue());
            } else {
                descBuilder.enabled(true);
            }

            return descBuilder.build();

        } catch (BsonInvalidOperationException | IllegalStateException e){
            throw new GraphQLIllegalAppDefinitionException("Error with GraphQL App Descriptor", e);
        }
    }

    private static Map<String, TypeMapping> getMappings(BsonDocument doc) throws GraphQLIllegalAppDefinitionException {

        Map<String, TypeMapping> mappingMap = new HashMap<>();

        for (String type: doc.keySet()){

            if(doc.get(type).isDocument()){
                Map<String, FieldMapping> typeMappings = new HashMap<>();

                BsonDocument typeDoc = doc.getDocument(type);

                for (String field: typeDoc.keySet()){

                    BsonValue fieldMapping = typeDoc.get(field);

                    switch (fieldMapping.getBsonType()){
                        case STRING:
                            typeMappings.put(field, new FieldRenaming(field, fieldMapping.asString().getValue()));
                            break;
                        case DOCUMENT:{
                            BsonDocument fieldMappingDoc = fieldMapping.asDocument();

                            boolean isAggr = fieldMappingDoc.containsKey("stages");

                            // check if it's QueryMapping or AggrregationMapping
                            if(isAggregationMapping(fieldMappingDoc)) {

                                typeMappings.put(field, new AggregationMapping(
                                    field, 
                                    fieldMappingDoc.get("db").asString(),
                                    fieldMappingDoc.get("collection").asString(),
                                    fieldMappingDoc.get("stages").asArray(),
                                    hasKeyOfType(fieldMappingDoc, "allowDiskUse", t -> t.isBoolean()) 
                                        ? fieldMappingDoc.get("allowDiskUse").asBoolean()
                                        : new BsonBoolean(false)
                                ));
                            } else {

                                QueryMapping.Builder queryMappingBuilder = QueryMapping.newBuilder();

                                queryMappingBuilder.fieldName(field);

                                if (fieldMappingDoc.containsKey("db")){
                                    if(fieldMappingDoc.get("db").isString()){
                                        queryMappingBuilder.db(fieldMappingDoc.getString("db").getValue());
                                    }
                                    else {
                                        throwIllegalDefinitionException(field, type, "db", "'STRING'", fieldMappingDoc.get("db"));

                                    }
                                }
                                else {
                                    throw new NullPointerException(
                                            "Error with field '" + field + "' of type '" + type + "'. 'db' could not be null."
                                    );
                                }

                                if(fieldMappingDoc.containsKey("collection")){
                                    if(fieldMappingDoc.get("collection").isString()){
                                        queryMappingBuilder.collection(fieldMappingDoc.getString("collection").getValue());
                                    }
                                    else {
                                        throwIllegalDefinitionException(field, type, "collection", "'STRING'", fieldMappingDoc.get("collection"));
                                    }
                                }
                                else{
                                    throw new NullPointerException(
                                            "Error with field '" + field +"' of type '"+ type + "'. 'collection' could not be null."
                                    );
                                }

                                if(fieldMappingDoc.containsKey("find")){
                                    if(fieldMappingDoc.get("find").isDocument()){
                                        queryMappingBuilder.find(fieldMappingDoc.getDocument("find"));
                                    }
                                    else {
                                        throwIllegalDefinitionException(field, type, "find", "DOCUMENT", fieldMappingDoc.get("find"));
                                    }
                                }

                                if(fieldMappingDoc.containsKey("sort")){
                                    if(fieldMappingDoc.get("sort").isDocument()){
                                        queryMappingBuilder.sort(fieldMappingDoc.getDocument("sort"));

                                    }
                                    else {
                                        throwIllegalDefinitionException(field, type, "sort", "DOCUMENT", fieldMappingDoc.get("sort"));
                                    }
                                }

                                if(fieldMappingDoc.containsKey("limit")){
                                    if(fieldMappingDoc.get("limit").isDocument()){
                                        queryMappingBuilder.limit(fieldMappingDoc.getDocument("limit"));
                                    }
                                    else if(fieldMappingDoc.get("limit").isNumber()){
                                        queryMappingBuilder.limit(fieldMappingDoc.getNumber("limit"));
                                    }
                                    else {
                                        throwIllegalDefinitionException(field, type, "limit", "DOCUMENT", fieldMappingDoc.get("limit"));
                                    }
                                }

                                if (fieldMappingDoc.containsKey("skip")){
                                    if (fieldMappingDoc.get("skip").isDocument()){
                                        queryMappingBuilder.skip(fieldMappingDoc.getDocument("skip"));
                                    }
                                    else if(fieldMappingDoc.get("skip").isNumber()){
                                        queryMappingBuilder.limit(fieldMappingDoc.getNumber("skip"));
                                    }
                                    else {
                                        throwIllegalDefinitionException(field, type, "skip", "DOCUMENT", fieldMappingDoc.get("skip"));
                                    }
                                }

                                if (fieldMappingDoc.containsKey("dataLoader")){
                                    if (fieldMappingDoc.get("dataLoader").isDocument()){
                                        BsonDocument settings = fieldMappingDoc.getDocument("dataLoader");
                                        DataLoaderSettings.Builder dataLoaderBuilder = DataLoaderSettings.newBuilder();

                                        if (settings.containsKey("batching") && settings.get("batching").isBoolean()){
                                            dataLoaderBuilder.batching(settings.getBoolean("batching").getValue());
                                            if (settings.containsKey("maxBatchSize") && settings.get("maxBatchSize").isNumber()){
                                                dataLoaderBuilder.max_batch_size(settings.getNumber("maxBatchSize").intValue());
                                            }
                                        }

                                        if (settings.containsKey("caching") && settings.get("caching").isBoolean()){
                                            dataLoaderBuilder.caching(settings.getBoolean("caching").getValue());
                                        }

                                        queryMappingBuilder.DataLoaderSettings(dataLoaderBuilder.build());
                                    }
                                    else {
                                        throwIllegalDefinitionException(field, type, "dataLoader", "DOCUMENT", fieldMappingDoc.get("dataLoader"));
                                    }

                                }

                                typeMappings.put(field, queryMappingBuilder.build());
                                break;

                            }

                            break;
                        }
                        default:
                            throw  new GraphQLIllegalAppDefinitionException(
                                    "Error with mappings of type: '" + type
                                            + "'. A field mapping must be of type 'STRING' but was "
                                            + fieldMapping.getBsonType());
                    } // end switch

                } //end for

                TypeMapping typeMapping = new ObjectMapping(type, typeMappings);
                mappingMap.put(type, typeMapping);
            }
            else {
                throw new GraphQLIllegalAppDefinitionException(
                        "Error with mappings of type: '" + type + "'. Type mappings must be of type 'DOCUMENT' but was "
                                + doc.get(type).getBsonType()
                );
            }

        }

        return mappingMap;

    }

    private static void throwIllegalDefinitionException(String field, String type, String arg ,String typeExpected, BsonValue value) throws GraphQLIllegalAppDefinitionException {

        throw new GraphQLIllegalAppDefinitionException(
                "Error with field '" + field +"' of type '"+ type +
                        "'. The field '" + arg + "' must be a '" + typeExpected + "' but was '" + value.getBsonType() + "'."
        );

    }



    private static boolean isAggregationMapping(BsonDocument field) {        
        return hasKeyOfType(field, "db", f -> f.isString())
                && hasKeyOfType(field, "collection", f -> f.isString())
                && hasKeyOfType(field, "stages", f -> f.isArray());
    }

    // BsonValue is the base class for any Bson type
    // f(key, type) => key.is(type)
    private static boolean hasKeyOfType(BsonDocument source, String key, Predicate<BsonValue> isOfType) {
        Predicate<BsonDocument> containsKey = t -> t.containsKey(key);
        
        return containsKey.test(source) && isOfType.test(source.get(key));
        // return containsKey.and(isOfType).test(source);
    }
}

