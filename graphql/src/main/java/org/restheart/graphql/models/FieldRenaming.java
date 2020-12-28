package org.restheart.graphql.models;

import graphql.schema.DataFetcher;
import org.bson.BsonValue;
import org.restheart.graphql.GQLRenamingDataFetcher;

public class FieldRenaming extends FieldMapping{

    private String alias;

    public FieldRenaming(String fieldName, String alias){
        super(fieldName);
        this.alias = alias;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    @Override
    public DataFetcher<BsonValue> getDataFetcher() {
        return new GQLRenamingDataFetcher(this);
    }
}
