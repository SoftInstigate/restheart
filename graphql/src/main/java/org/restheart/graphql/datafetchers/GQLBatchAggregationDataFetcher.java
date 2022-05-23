package org.restheart.graphql.datafetchers;

import org.bson.BsonArray;
import org.bson.BsonValue;
import org.dataloader.DataLoader;
import org.restheart.graphql.models.AggregationMapping;
import graphql.schema.GraphQLObjectType;

import graphql.schema.DataFetchingEnvironment;

public class GQLBatchAggregationDataFetcher extends GraphQLDataFetcher {

    public GQLBatchAggregationDataFetcher(AggregationMapping aggregationMapping) {
        super(aggregationMapping);
    }

    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {

        AggregationMapping aggregationMapping = (AggregationMapping) this.fieldMapping;

        DataLoader<BsonValue, BsonValue> dataLoader;

        String key = ((GraphQLObjectType) environment.getParentType()).getName() + "_" + aggregationMapping.getFieldName();
        
        dataLoader = environment.getDataLoader(key);

        var aggregationList = aggregationMapping.getResolvedStagesAsList(environment);
        
        BsonArray bsonArray = new BsonArray();
        bsonArray.addAll(aggregationList);
        
        return dataLoader.load(bsonArray, environment);

    }
    
}
