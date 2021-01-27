package org.restheart.graphql.datafetchers;

import com.mongodb.MongoClient;
import graphql.schema.DataFetcher;
import org.bson.BsonValue;
import org.restheart.graphql.models.FieldMapping;

public abstract class GraphQLDataFetcher implements DataFetcher<BsonValue> {

    protected static MongoClient mongoClient;
    protected FieldMapping fieldMapping;

    public static void setMongoClient(MongoClient mClient){
        mongoClient = mClient;
    }
    
    public GraphQLDataFetcher(FieldMapping fieldMapping){
        this.fieldMapping = fieldMapping;
    }



}
