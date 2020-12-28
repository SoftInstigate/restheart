package org.restheart.graphql.models;

import graphql.schema.DataFetcher;
import org.bson.BsonValue;

public abstract class FieldMapping {

    protected final String fieldName;

    public FieldMapping(String fieldName){
        this.fieldName = fieldName;
    }

    public abstract DataFetcher<BsonValue> getDataFetcher();

}
