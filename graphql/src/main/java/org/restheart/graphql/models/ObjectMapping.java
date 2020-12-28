package org.restheart.graphql.models;

import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;

import java.util.Map;

public class ObjectMapping extends TypeMapping{

    public ObjectMapping(String typeName, Map<String, FieldMapping> fieldMappingMap){
        super(typeName, fieldMappingMap);
    }

    @Override
    public TypeRuntimeWiring.Builder getTypeWiring(TypeDefinitionRegistry typeRegistry) {

        TypeRuntimeWiring.Builder TWBuilder = TypeRuntimeWiring.newTypeWiring(this.typeName);

        this.fieldMappingMap.forEach(((fieldName, fieldMapping) -> {
            TWBuilder.dataFetcher(fieldName, fieldMapping.getDataFetcher());
        }));

        return TWBuilder;
    }
}
