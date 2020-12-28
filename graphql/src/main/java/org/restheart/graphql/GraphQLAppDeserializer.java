package org.restheart.graphql;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.graphql.models.*;

import java.util.HashMap;
import java.util.Map;

public class GraphQLAppDeserializer {

    public static final GraphQLApp fromBsonDocument(BsonDocument appDef) throws IllegalAccessException {

        AppDescriptor descriptor;
        String schema;
        Map<String, TypeMapping> mappingsMap;

        if( appDef.containsKey("descriptor")){
            if (appDef.get("descriptor").isDocument()){
                descriptor = getAppDescriptor(appDef);
            }
            else{
                throw new IllegalArgumentException(
                        "'Descriptor' field must be a 'DOCUMENT' but was " + appDef.get("descriptor").getBsonType()
                );
            }
        }
        else{
            throw new NullPointerException(
                    "App descriptor not found. GraphQL app must have a descriptor."
            );
        }

        if (appDef.containsKey("schema")){
            if (appDef.get("schema").isString()){
                schema = appDef.getString("schema").getValue();
            }
            else{
                throw new IllegalArgumentException(
                        "'Schema' field must be a 'STRING' but was " + appDef.get("descriptor").getBsonType()
                );
            }
        }
        else{
            throw new NullPointerException(
                    "SDL schema not found. GraphQL app must have a schema."
            );
        }

        if(appDef.containsKey("mappings")){
            if (appDef.get("mappings").isDocument()){
                mappingsMap = getMappings(appDef.getDocument("mappings"));
            }
            else{
                throw new IllegalArgumentException(
                        "'Mappings' field must be a 'DOCUMENT' but was " + appDef.get("mappings").getBsonType()
                );
            }
        }
        else{
            throw new NullPointerException(
                    "Mappings not found. GraphQL app must have mappings."
            );
        }

        return GraphQLApp.newBuilder()
                .appDescriptor(descriptor)
                .schema(schema)
                .mappings(mappingsMap)
                .build();

    }

    private static AppDescriptor getAppDescriptor(BsonDocument doc){

        BsonDocument descriptor = doc.getDocument("descriptor");

        AppDescriptor.Builder descBuilder = AppDescriptor.newBuilder()
                .appName(descriptor.getString("name").getValue())
                .description(descriptor.getString("description").getValue());

        if(descriptor.containsKey("uri") && descriptor.get("uri").isString()){

            descBuilder.uri(descriptor.getString("uri").getValue());
        }
        else {
            descBuilder.uri(descriptor.getString("name").getValue());
        }

        if (descriptor.containsKey("enabled") && descriptor.get("enabled").isBoolean()){

            descBuilder.enabled(descriptor.getBoolean("enabled").getValue());
        }
        else {
            descBuilder.enabled(true);
        }

        return descBuilder.build();
    }

    private static Map<String, TypeMapping> getMappings(BsonDocument doc){

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
                                    throwIllegalArgumentException(field, type, "'db'", "'STRING'", fieldMappingDoc.get("db"));

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
                                    throwIllegalArgumentException(field, type, "'collection'", "'STRING'", fieldMappingDoc.get("collection"));
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
                                    throwIllegalArgumentException(field, type, "'find'", "DOCUMENT", fieldMappingDoc.get("find"));
                                }
                            }

                            if(fieldMappingDoc.containsKey("sort")){
                                if(fieldMappingDoc.get("sort").isDocument()){
                                    queryMappingBuilder.sort(fieldMappingDoc.getDocument("sort"));

                                }
                                else {
                                    throwIllegalArgumentException(field, type, "'sort'", "DOCUMENT", fieldMappingDoc.get("sort"));
                                }
                            }

                            if(fieldMappingDoc.containsKey("limit")){
                                if(fieldMappingDoc.get("limit").isDocument()){
                                    queryMappingBuilder.limit(fieldMappingDoc.getDocument("limit"));
                                }
                                else {
                                    throwIllegalArgumentException(field, type, "'limit'", "DOCUMENT", fieldMappingDoc.get("limit"));
                                }
                            }

                            if (fieldMappingDoc.containsKey("skip")){
                                if (fieldMappingDoc.get("skip").isDocument()){
                                    queryMappingBuilder.skip(fieldMappingDoc.getDocument("skip"));
                                }
                                else {
                                    throwIllegalArgumentException(field, type, "'skip'", "DOCUMENT", fieldMappingDoc.get("skip"));
                                }
                            }

                            typeMappings.put(field, queryMappingBuilder.build());
                            break;
                        }
                        default:
                            throwIllegalArgumentException(field, type, "A field mapping", "DOCUMENT", fieldMapping);
                    }

                }
                TypeMapping typeMapping = new ObjectMapping(type, typeMappings);
                mappingMap.put(type, typeMapping);
            }
            else {
                throw new IllegalArgumentException(
                        "Error with mappings of type: '" + type + "'. Type mappings must be of type 'DOCUMENT' but was " + doc.get(type).getBsonType()
                );
            }

        }

        return mappingMap;

    }

    private static void throwIllegalArgumentException(String field, String type, String arg ,String typeExpected, BsonValue value){

        throw new IllegalArgumentException(
                "Error with field '" + field +"' of type '"+ type +
                        "'." + arg + " must be a " + typeExpected + " but was " + value.getBsonType()
        );

    }

}
