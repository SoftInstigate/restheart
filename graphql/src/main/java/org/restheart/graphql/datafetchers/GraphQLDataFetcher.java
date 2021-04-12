package org.restheart.graphql.datafetchers;

import com.mongodb.MongoClient;
import graphql.schema.DataFetcher;
import org.restheart.graphql.models.FieldMapping;

public abstract class GraphQLDataFetcher implements DataFetcher<Object> {

    protected static MongoClient mongoClient;
    protected FieldMapping fieldMapping;

    public static void setMongoClient(MongoClient mClient){
        mongoClient = mClient;
    }

    public GraphQLDataFetcher(FieldMapping fieldMapping){
        this.fieldMapping = fieldMapping;
    }

}
