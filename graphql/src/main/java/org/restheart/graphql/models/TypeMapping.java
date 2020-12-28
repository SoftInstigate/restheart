package org.restheart.graphql.models;

import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;

import java.util.Map;

public abstract class TypeMapping {

    protected final String typeName;
    protected final Map<String, FieldMapping> fieldMappingMap;

    public TypeMapping(String typeName, Map<String, FieldMapping> fieldMappingMap){
        this.typeName = typeName;
        this.fieldMappingMap = fieldMappingMap;
    }

    public Map<String, FieldMapping> getFieldMappingMap() {
        return fieldMappingMap;
    }

    public abstract TypeRuntimeWiring.Builder getTypeWiring(TypeDefinitionRegistry typeRegistry);
}
