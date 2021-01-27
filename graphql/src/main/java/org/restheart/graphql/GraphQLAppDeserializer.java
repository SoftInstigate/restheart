package org.restheart.graphql;

import org.bson.BsonDocument;
import org.bson.BsonInvalidOperationException;
import org.bson.BsonValue;
import org.restheart.graphql.models.*;
import org.restheart.utils.JsonUtils;

import java.util.HashMap;
import java.util.Map;

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
                    mappingsMap = getMappings(JsonUtils.unescapeKeys(appDef.getDocument("mappings")).asDocument());
                }
                else{
                    throw new GraphQLIllegalAppDefinitionException(
                            "'Mappings' field must be a 'DOCUMENT' but was " + appDef.get("mappings").getBsonType()
                    );
                }
            }

            try{
                return GraphQLApp.newBuilder()
                        .appDescriptor(descriptor)
                        .schema(schema)
                        .mappings(mappingsMap)
                        .build();

            } catch (IllegalStateException | IllegalArgumentException e){
                throw  new GraphQLIllegalAppDefinitionException(
                        e.getMessage()
                );
            }


    }

    private static AppDescriptor getAppDescriptor(BsonDocument doc) throws GraphQLIllegalAppDefinitionException {

        try{
            BsonDocument descriptor = doc.getDocument("descriptor");

            AppDescriptor.Builder descBuilder = AppDescriptor.newBuilder()
                    .appName(descriptor.getString("name").getValue())
                    .description(descriptor.getString("description").getValue());

            if (descriptor.containsKey("uri") && descriptor.get("uri").isString()) {

                descBuilder.uri(descriptor.getString("uri").getValue());
            } else {
                descBuilder.uri(descriptor.getString("name").getValue());
            }

            if (descriptor.containsKey("enabled") && descriptor.get("enabled").isBoolean()) {

                descBuilder.enabled(descriptor.getBoolean("enabled").getValue());
            } else {
                descBuilder.enabled(true);
            }

            return descBuilder.build();

        }catch (BsonInvalidOperationException bsonEx){
            throw new GraphQLIllegalAppDefinitionException("Error with app descriptor. " + bsonEx.getMessage());
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
                                else {
                                    throwIllegalDefinitionException(field, type, "limit", "DOCUMENT", fieldMappingDoc.get("limit"));
                                }
                            }

                            if (fieldMappingDoc.containsKey("skip")){
                                if (fieldMappingDoc.get("skip").isDocument()){
                                    queryMappingBuilder.skip(fieldMappingDoc.getDocument("skip"));
                                }
                                else {
                                    throwIllegalDefinitionException(field, type, "skip", "DOCUMENT", fieldMappingDoc.get("skip"));
                                }
                            }

                            typeMappings.put(field, queryMappingBuilder.build());
                            break;
                        }
                        default:
                           throw  new GraphQLIllegalAppDefinitionException(
                                   "Error with mappings of type: '" + type
                                           + "'. A field mapping must be of type 'STRING' but was "
                                           + fieldMapping.getBsonType());
                    }

                }
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

}

